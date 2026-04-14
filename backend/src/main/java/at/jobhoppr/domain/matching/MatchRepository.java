package at.jobhoppr.domain.matching;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Führt die Matching-CTE-Queries via {@link NamedParameterJdbcTemplate} aus.
 * <p>
 * Zwei symmetrische Queries:
 * <ul>
 *   <li>{@link #findTopStellenForPerson} — feste Person, rankt passende Stellen</li>
 *   <li>{@link #findTopPersonenForStelle} — feste Stelle, rankt passende Personen</li>
 * </ul>
 * Score-Funktionen werden via {@code CROSS JOIN LATERAL} einmalig aufgerufen (P3-Fix).
 * Sortierung wird in Java aus {@link SortierParameter} validiert — kein String-Concat (kein SQL-Injection-Risiko).
 * </p>
 */
@Repository
@RequiredArgsConstructor
public class MatchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    // ────────────────────────────────────────────────────────────────────────────
    // Query: Stellen für Person
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Sortier-sichere ORDER BY-Klausel: zwei separate Templates je nach Sortierkriterium
     * damit PostgreSQL den richtigen Index (score vs. erstellt_am) nutzen kann (P4-Hinweis).
     */
    private static final String STELLEN_FOR_PERSON_ORDER_SCORE_DESC =
        "ORDER BY score DESC, s.erstellt_am DESC";
    private static final String STELLEN_FOR_PERSON_ORDER_SCORE_ASC =
        "ORDER BY score ASC,  s.erstellt_am DESC";
    private static final String STELLEN_FOR_PERSON_ORDER_DATUM_DESC =
        "ORDER BY s.erstellt_am DESC, score DESC";
    private static final String STELLEN_FOR_PERSON_ORDER_DATUM_ASC =
        "ORDER BY s.erstellt_am ASC,  score DESC";

    private static final String STELLEN_FOR_PERSON_BASE = """
        WITH
        geo_kandidaten AS (
          SELECT DISTINCT s.id
          FROM stelle s
          WHERE EXISTS (
            SELECT 1 FROM person_ort po
            WHERE po.person_id = :person_id
              AND (
                po.bundesweit = TRUE
                OR ST_DWithin(po.standort::geography, s.standort::geography, po.umkreis_km * 1000)
                OR (
                  po.geo_location_id IS NOT NULL
                  AND s.geo_location_id IS NOT NULL
                  AND EXISTS (
                    WITH RECURSIVE ancestors AS (
                      SELECT id, parent_id FROM geo_location WHERE id = s.geo_location_id
                      UNION ALL
                      SELECT gl.id, gl.parent_id FROM geo_location gl
                      JOIN ancestors a ON gl.id = a.parent_id
                    )
                    SELECT 1 FROM ancestors WHERE id = po.geo_location_id
                  )
                )
              )
          )
        ),
        muss_ko AS (
          SELECT DISTINCT sk.stelle_id
          FROM stelle_kompetenz sk
          WHERE sk.pflicht = TRUE
            AND sk.stelle_id IN (SELECT id FROM geo_kandidaten)
            AND NOT EXISTS (
              SELECT 1 FROM person_kompetenz pk 
              WHERE pk.person_id = :person_id 
                AND pk.kompetenz_id = sk.kompetenz_id
            )
            AND NOT (
              -- Sprachlevel-Matching (A1-C2) - Person hat gleiche oder höhere Stufe
              (SELECT name FROM bis_kompetenz WHERE id = sk.kompetenz_id) ~ '^[A-C][12] - '
              AND EXISTS (
                SELECT 1 FROM person_kompetenz pk
                JOIN bis_kompetenz pk_k ON pk_k.id = pk.kompetenz_id
                WHERE pk.person_id = :person_id
                  AND pk_k.name ~ '^[A-C][12] - '
                  AND regexp_replace(pk_k.name, '^[A-C][12] - .* ', '') = regexp_replace(
                    (SELECT name FROM bis_kompetenz WHERE id = sk.kompetenz_id), '^[A-C][12] - .* ', ''
                  )
                  AND pk_k.name >= (SELECT name FROM bis_kompetenz WHERE id = sk.kompetenz_id)
              )
            )
            AND NOT EXISTS (
              SELECT 1 FROM kompetenz_closure cc
              JOIN person_kompetenz pk ON pk.kompetenz_id = cc.vorfahre_id
              WHERE cc.nachfahre_id = sk.kompetenz_id 
                AND pk.person_id = :person_id
                -- Ausschluss für Sprachlevel: nur non-language matches via closure
                AND (SELECT name FROM bis_kompetenz WHERE id = sk.kompetenz_id) !~ '^[A-C][12] - '
            )
        ),
        -- arbeitszeit_ko: KO wenn beide Seiten Modelle haben und Schnittmenge leer
        arbeitszeit_ko AS (
          SELECT DISTINCT s.id
          FROM stelle s
          WHERE s.id IN (SELECT id FROM geo_kandidaten)
            AND EXISTS (SELECT 1 FROM stelle_arbeitszeit WHERE stelle_id = s.id)
            AND EXISTS (SELECT 1 FROM person_arbeitszeit_ausschluss WHERE person_id = :person_id)
            AND NOT EXISTS (
              SELECT 1 FROM stelle_arbeitszeit sa
              WHERE sa.stelle_id = s.id
                AND sa.modell IN (
                  SELECT modell FROM person_arbeitszeit_ausschluss WHERE person_id = :person_id
                )
            )
        ),
        kandidaten AS (
          SELECT id FROM geo_kandidaten
          EXCEPT SELECT stelle_id FROM muss_ko
          EXCEPT SELECT id FROM arbeitszeit_ko
        ),
        scores AS (
          SELECT
            s.id,
            s.titel,
            s.unternehmen,
            s.typ,
            s.erstellt_am,
            bd.om,
            bd.sm,
            bd.fm,
            bd.qm,
            bd.matching_ko,
            bd.missing_ko,
            bd.extra_ko,
            bd.matching_ig,
            bd.missing_ig,
            bd.extra_ig,
            bd.matching_vr,
            bd.missing_vr,
            bd.extra_vr,
            bd.matching_az,
            CASE s.typ
              WHEN 'STANDARD'   THEN
                CASE WHEN :w_arbeitszeit > 0 THEN
                  (:w_beruf     * bd.om + :w_kompetenz * bd.sm + :w_arbeitszeit * bd.am)
                  / NULLIF(:w_beruf + :w_kompetenz + :w_arbeitszeit, 0)
                ELSE
                  (:w_beruf * bd.om + :w_kompetenz * bd.sm)
                  / NULLIF(:w_beruf + :w_kompetenz, 0)
                END
              WHEN 'LEHRSTELLE' THEN
                (:w_lehrberuf * bd.om + :w_interessen * bd.fm + :w_voraussetzungen * bd.qm)
                / NULLIF(:w_lehrberuf + :w_interessen + :w_voraussetzungen, 0)
              ELSE 0.0
            END AS score
          FROM stelle s
          CROSS JOIN LATERAL (
            SELECT
              match_beruf(:person_id, s.id)                AS om,
              match_kompetenz(:person_id, s.id)            AS sm,
              match_interessen(:person_id, s.id)           AS fm,
              match_voraussetzungen(:person_id, s.id)      AS qm,
              match_arbeitszeit(:person_id, s.id)          AS am,
              ARRAY(SELECT match_kompetenz_details(:person_id, s.id))       AS matching_ko,
              ARRAY(SELECT missing_kompetenz_details(:person_id, s.id))     AS missing_ko,
              ARRAY(SELECT extra_kompetenz_details(:person_id, s.id))       AS extra_ko,
              ARRAY(SELECT match_interessen_details(:person_id, s.id))      AS matching_ig,
              ARRAY(SELECT missing_interessen_details(:person_id, s.id))    AS missing_ig,
              ARRAY(SELECT extra_interessen_details(:person_id, s.id))      AS extra_ig,
              ARRAY(SELECT match_voraussetzung_details(:person_id, s.id))   AS matching_vr,
              ARRAY(SELECT missing_voraussetzung_details(:person_id, s.id)) AS missing_vr,
              ARRAY(SELECT extra_voraussetzung_details(:person_id, s.id))   AS extra_vr,
              ARRAY(SELECT modell FROM match_arbeitszeit_details(:person_id, s.id)) AS matching_az
          ) bd
          WHERE s.id IN (SELECT id FROM kandidaten)
        )
        SELECT
          s.id                                                          AS target_id,
          s.titel || COALESCE(' – ' || s.unternehmen, '')              AS target_name,
          s.typ,
          s.erstellt_am,
          s.om, s.sm, s.fm, s.qm, s.score,
          s.matching_ko, s.missing_ko, s.extra_ko,
          s.matching_ig, s.missing_ig, s.extra_ig,
          s.matching_vr, s.missing_vr, s.extra_vr,
          s.matching_az
        FROM scores s
        WHERE s.score >= :schwellenwert
        """;

    // ────────────────────────────────────────────────────────────────────────────
    // Query: Personen für Stelle (symmetrisch: Rollen getauscht)
    // ────────────────────────────────────────────────────────────────────────────

    private static final String PERSONEN_FOR_STELLE_ORDER_SCORE_DESC =
        "ORDER BY score DESC, p.erstellt_am DESC";
    private static final String PERSONEN_FOR_STELLE_ORDER_SCORE_ASC =
        "ORDER BY score ASC,  p.erstellt_am DESC";
    private static final String PERSONEN_FOR_STELLE_ORDER_DATUM_DESC =
        "ORDER BY p.erstellt_am DESC, score DESC";
    private static final String PERSONEN_FOR_STELLE_ORDER_DATUM_ASC =
        "ORDER BY p.erstellt_am ASC,  score DESC";

    private static final String PERSONEN_FOR_STELLE_BASE = """
        WITH
        stelle_data AS (
          SELECT s.id, s.standort, s.geo_location_id, s.typ
          FROM stelle s
          WHERE s.id = :stelle_id
        ),
        geo_kandidaten AS (
          SELECT DISTINCT po.person_id
          FROM person_ort po
          CROSS JOIN stelle_data sd
          WHERE po.bundesweit = TRUE
             OR ST_DWithin(po.standort::geography, sd.standort::geography, po.umkreis_km * 1000)
             OR (
               po.geo_location_id IS NOT NULL
               AND sd.geo_location_id IS NOT NULL
               AND EXISTS (
                 WITH RECURSIVE ancestors AS (
                   SELECT id, parent_id FROM geo_location WHERE id = sd.geo_location_id
                   UNION ALL
                   SELECT gl.id, gl.parent_id FROM geo_location gl
                   JOIN ancestors a ON gl.id = a.parent_id
                 )
                 SELECT 1 FROM ancestors WHERE id = po.geo_location_id
               )
             )
        ),
        muss_ko AS (
          SELECT DISTINCT gk.person_id
          FROM geo_kandidaten gk
          WHERE EXISTS (
            SELECT 1 FROM stelle_kompetenz sk
            WHERE sk.pflicht = TRUE
              AND sk.stelle_id = :stelle_id
              AND NOT EXISTS (
                SELECT 1 FROM person_kompetenz pk WHERE pk.person_id = gk.person_id AND pk.kompetenz_id = sk.kompetenz_id
              )
              AND NOT (
                -- Sprachlevel-Matching (A1-C2) - Person hat gleiche oder höhere Stufe
                (SELECT name FROM bis_kompetenz WHERE id = sk.kompetenz_id) ~ '^[A-C][12] - '
                AND EXISTS (
                  SELECT 1 FROM person_kompetenz pk
                  JOIN bis_kompetenz pk_k ON pk_k.id = pk.kompetenz_id
                  WHERE pk.person_id = gk.person_id
                    AND pk_k.name ~ '^[A-C][12] - '
                    AND regexp_replace(pk_k.name, '^[A-C][12] - .* ', '') = regexp_replace(
                      (SELECT name FROM bis_kompetenz WHERE id = sk.kompetenz_id), '^[A-C][12] - .* ', ''
                    )
                    AND pk_k.name >= (SELECT name FROM bis_kompetenz WHERE id = sk.kompetenz_id)
                )
              )
              AND NOT EXISTS (
                SELECT 1 FROM kompetenz_closure cc
                JOIN person_kompetenz pk ON pk.kompetenz_id = cc.vorfahre_id
                WHERE cc.nachfahre_id = sk.kompetenz_id 
                  AND pk.person_id = gk.person_id
                  -- Ausschluss für Sprachlevel: nur non-language matches via closure
                  AND (SELECT name FROM bis_kompetenz WHERE id = sk.kompetenz_id) !~ '^[A-C][12] - '
              )
          )
        ),
        -- arbeitszeit_ko: KO wenn beide Seiten Modelle haben und Schnittmenge leer
        arbeitszeit_ko AS (
          SELECT DISTINCT gk.person_id
          FROM geo_kandidaten gk
          WHERE EXISTS (SELECT 1 FROM stelle_arbeitszeit WHERE stelle_id = :stelle_id)
            AND EXISTS (SELECT 1 FROM person_arbeitszeit_ausschluss WHERE person_id = gk.person_id)
            AND NOT EXISTS (
              SELECT 1 FROM stelle_arbeitszeit sa
              WHERE sa.stelle_id = :stelle_id
                AND sa.modell IN (
                  SELECT modell FROM person_arbeitszeit_ausschluss WHERE person_id = gk.person_id
                )
            )
        ),
        kandidaten AS (
          SELECT person_id AS id FROM geo_kandidaten
          EXCEPT SELECT person_id FROM muss_ko
          EXCEPT SELECT person_id FROM arbeitszeit_ko
        ),
        scores AS (
          SELECT
            p.id,
            p.vorname || ' ' || p.nachname  AS naam,
            p.erstellt_am,
            bd.om,
            bd.sm,
            bd.fm,
            bd.qm,
            bd.matching_ko,
            bd.missing_ko,
            bd.extra_ko,
            bd.matching_ig,
            bd.missing_ig,
            bd.extra_ig,
            bd.matching_vr,
            bd.missing_vr,
            bd.extra_vr,
            bd.matching_az,
            CASE (SELECT typ FROM stelle_data)
              WHEN 'STANDARD'   THEN
                CASE WHEN :w_arbeitszeit > 0 THEN
                  (:w_beruf     * bd.om + :w_kompetenz * bd.sm + :w_arbeitszeit * bd.am)
                  / NULLIF(:w_beruf + :w_kompetenz + :w_arbeitszeit, 0)
                ELSE
                  (:w_beruf * bd.om + :w_kompetenz * bd.sm)
                  / NULLIF(:w_beruf + :w_kompetenz, 0)
                END
              WHEN 'LEHRSTELLE' THEN
                (:w_lehrberuf * bd.om + :w_interessen * bd.fm + :w_voraussetzungen * bd.qm)
                / NULLIF(:w_lehrberuf + :w_interessen + :w_voraussetzungen, 0)
              ELSE 0.0
            END AS score
          FROM person p
          CROSS JOIN LATERAL (
            SELECT
              match_beruf(p.id, :stelle_id)                AS om,
              match_kompetenz(p.id, :stelle_id)            AS sm,
              match_interessen(p.id, :stelle_id)           AS fm,
              match_voraussetzungen(p.id, :stelle_id)      AS qm,
              match_arbeitszeit(p.id, :stelle_id)          AS am,
              ARRAY(SELECT match_kompetenz_details(p.id, :stelle_id))       AS matching_ko,
              ARRAY(SELECT missing_kompetenz_details(p.id, :stelle_id))     AS missing_ko,
              ARRAY(SELECT extra_kompetenz_details(p.id, :stelle_id))       AS extra_ko,
              ARRAY(SELECT match_interessen_details(p.id, :stelle_id))      AS matching_ig,
              ARRAY(SELECT missing_interessen_details(p.id, :stelle_id))    AS missing_ig,
              ARRAY(SELECT extra_interessen_details(p.id, :stelle_id))      AS extra_ig,
              ARRAY(SELECT match_voraussetzung_details(p.id, :stelle_id))   AS matching_vr,
              ARRAY(SELECT missing_voraussetzung_details(p.id, :stelle_id)) AS missing_vr,
              ARRAY(SELECT extra_voraussetzung_details(p.id, :stelle_id))   AS extra_vr,
              ARRAY(SELECT modell FROM match_arbeitszeit_details(p.id, :stelle_id)) AS matching_az
          ) bd
          WHERE p.id IN (SELECT id FROM kandidaten)
        )
        SELECT
          p.id                AS target_id,
          p.naam              AS target_name,
          (SELECT typ FROM stelle_data) AS typ,
          p.erstellt_am,
          p.om, p.sm, p.fm, p.qm, p.score,
          p.matching_ko, p.missing_ko, p.extra_ko,
          p.matching_ig, p.missing_ig, p.extra_ig,
          p.matching_vr, p.missing_vr, p.extra_vr,
          p.matching_az
        FROM scores p
        WHERE p.score >= :schwellenwert
        """;

    // ────────────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────────────

    public List<MatchResult> findTopStellenForPerson(UUID personId, MatchModell modell,
                                                     SortierParameter sort) {
        String orderBy = stellenOrderBy(sort);
        String sql = STELLEN_FOR_PERSON_BASE + orderBy + "\nLIMIT 50";
        MapSqlParameterSource params = baseParams(modell)
                .addValue("person_id", personId);
        return jdbc.query(sql, params, (rs, i) -> mapRowStatic(rs));
    }

    public List<MatchResult> findTopPersonenForStelle(UUID stelleId, MatchModell modell,
                                                      SortierParameter sort) {
        String orderBy = personenOrderBy(sort);
        String sql = PERSONEN_FOR_STELLE_BASE + orderBy + "\nLIMIT 50";
        MapSqlParameterSource params = baseParams(modell)
                .addValue("stelle_id", stelleId);
        return jdbc.query(sql, params, (rs, i) -> mapRowStatic(rs));
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────────

    private MapSqlParameterSource baseParams(MatchModell m) {
        return new MapSqlParameterSource()
                .addValue("w_beruf",            m.getGewichtBeruf())
                .addValue("w_kompetenz",         m.getGewichtKompetenz())
                .addValue("w_lehrberuf",         m.getGewichtLehrberuf())
                .addValue("w_interessen",        m.getGewichtInteressen())
                .addValue("w_voraussetzungen",   m.getGewichtVoraussetzungen())
                .addValue("w_arbeitszeit",       m.getGewichtArbeitszeit())
                .addValue("schwellenwert",       m.getScoreSchwellenwert());
    }

    private static MatchResult mapRowStatic(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID targetId = UUID.fromString(rs.getString("target_id"));
        String targetName = rs.getString("target_name");
        double score = rs.getDouble("score");

        String typStr = rs.getString("typ");
        MatchResult.StelleTypInfo typ = typStr != null
                ? MatchResult.StelleTypInfo.valueOf(typStr)
                : MatchResult.StelleTypInfo.STANDARD;

        Timestamp ts = rs.getTimestamp("erstellt_am");
        OffsetDateTime erstelltAm = ts != null
                ? ts.toInstant().atOffset(java.time.ZoneOffset.UTC)
                : null;

        MatchResult.Breakdown breakdown = new MatchResult.Breakdown(
                rs.getDouble("om"),
                rs.getDouble("sm"),
                rs.getDouble("fm"),
                rs.getDouble("qm"));

        List<MatchResult.KompetenzMatch> matching = new ArrayList<>();
        List<MatchResult.KompetenzMatch> missing  = new ArrayList<>();
        List<MatchResult.KompetenzMatch> extra    = new ArrayList<>();
        List<String> matchingIg  = new ArrayList<>();
        List<String> missingIg   = new ArrayList<>();
        List<String> extraIg     = new ArrayList<>();
        List<String> matchingVr  = new ArrayList<>();
        List<String> missingVr   = new ArrayList<>();
        List<String> extraVr     = new ArrayList<>();
        List<String> matchingAz  = new ArrayList<>();

        try {
            matching  = parseKompetenzArray(rs.getArray("matching_ko"), true);
            missing   = parseKompetenzArray(rs.getArray("missing_ko"),  false);
            extra     = parseKompetenzArray(rs.getArray("extra_ko"),    false);
            matchingIg = parseStringArray(rs.getArray("matching_ig"));
            missingIg  = parseStringArray(rs.getArray("missing_ig"));
            extraIg    = parseStringArray(rs.getArray("extra_ig"));
            matchingVr = parseStringArray(rs.getArray("matching_vr"));
            missingVr  = parseStringArray(rs.getArray("missing_vr"));
            extraVr    = parseStringArray(rs.getArray("extra_vr"));
            matchingAz = parseStringArray(rs.getArray("matching_az"));
        } catch (Exception e) {
            // ignore – columns absent or empty
        }

        return new MatchResult(targetId, targetName, score, typ, erstelltAm, breakdown,
                matching, missing, extra,
                matchingIg, missingIg, extraIg,
                matchingVr, missingVr, extraVr,
                matchingAz);
    }

    private static List<MatchResult.KompetenzMatch> parseKompetenzArray(
            java.sql.Array arr, boolean hasScore) throws java.sql.SQLException {
        List<MatchResult.KompetenzMatch> result = new ArrayList<>();
        if (arr == null) return result;
        Object obj = arr.getArray();
        if (obj instanceof Object[] rows) {
            for (Object row : rows) {
                MatchResult.KompetenzMatch km = parsePgComposite(row, hasScore);
                if (km != null) result.add(km);
            }
        }
        return result;
    }

    private static List<String> parseStringArray(java.sql.Array arr) throws java.sql.SQLException {
        List<String> result = new ArrayList<>();
        if (arr == null) return result;
        Object obj = arr.getArray();
        if (obj instanceof Object[] rows) {
            for (Object row : rows) {
                if (row == null) continue;
                // PGobject wrapping a single-column composite "(name)"
                String raw;
                try {
                    raw = (String) row.getClass().getMethod("getValue").invoke(row);
                } catch (Exception e) {
                    raw = row.toString();
                }
                if (raw == null || raw.isBlank()) continue;
                // Strip outer parens from composite: "(Teamarbeit)" → "Teamarbeit"
                String name = raw.startsWith("(") && raw.endsWith(")")
                        ? raw.substring(1, raw.length() - 1)
                        : raw;
                if (!name.isBlank()) result.add(name);
            }
        }
        return result;
    }

    /**
     * Parst einen PostgreSQL-Composite-Wert der Form {@code (name,score,pflicht)}
     * aus einem {@code PGobject} (oder beliebigem Objekt mit passendem toString).
     * <p>
     * Beispiel-Strings:
     * <ul>
     *   <li>{@code (Didaktikkenntnisse,0.5,t)} – matching, mit Score</li>
     *   <li>{@code (Pädagogikkenntnisse,,f)}   – missing, kein Score</li>
     * </ul>
     * Wir vermeiden den direkten Compile-Zeit-Import von {@code org.postgresql.util.PGobject}
     * (scope=runtime), indem wir den Wert per Reflection lesen oder per toString() extrahieren.
     *
     * @param row        Objekt aus dem JDBC-Array (PGobject zur Laufzeit)
     * @param hasScore   {@code true} für matching_ko (3 Felder), {@code false} für missing_ko (2 Felder)
     */
    private static MatchResult.KompetenzMatch parsePgComposite(Object row, boolean hasScore) {
        if (row == null) return null;

        // PGobject.getValue() liefert den Rohstring, z.B. "(Java,0.8,t)" oder "(\"A2 - Elementare Deutschkenntnisse\",1,t)"
        // Wir rufen getValue() per Reflection auf, um den compile-time import zu vermeiden.
        String value;
        try {
            value = (String) row.getClass().getMethod("getValue").invoke(row);
        } catch (Exception e) {
            // Fallback: toString() liefert bei PGobject denselben String
            value = row.toString();
        }

        if (value == null || value.length() < 3) return null;

        // Strip outer parentheses
        String inner = value.startsWith("(") && value.endsWith(")")
                ? value.substring(1, value.length() - 1)
                : value;

        // Strip leading/trailing quotes (PostgreSQL quotes composite elements with special chars)
        if (inner.startsWith("\"") && inner.endsWith("\"")) {
            inner = inner.substring(1, inner.length() - 1);
        }

        // Split on first two commas only – name may contain no commas (BIS names don't)
        String[] parts = inner.split(",", hasScore ? 3 : 2);
        if (parts.length < (hasScore ? 3 : 2)) return null;

        String name = parts[0];
        boolean pflicht = "t".equalsIgnoreCase(parts[hasScore ? 2 : 1].trim())
                       || "true".equalsIgnoreCase(parts[hasScore ? 2 : 1].trim());
        double score = 0.0;
        if (hasScore && parts.length > 1 && !parts[1].isBlank()) {
            try { score = Double.parseDouble(parts[1].trim()); } catch (NumberFormatException ignored) {}
        }

        return new MatchResult.KompetenzMatch(name, score, pflicht);
    }

    private static String stellenOrderBy(SortierParameter sort) {
        if (sort.kriterium() == SortierKriterium.ERSTELLT_AM) {
            return sort.richtung() == SortierRichtung.ASC
                    ? STELLEN_FOR_PERSON_ORDER_DATUM_ASC
                    : STELLEN_FOR_PERSON_ORDER_DATUM_DESC;
        }
        return sort.richtung() == SortierRichtung.ASC
                ? STELLEN_FOR_PERSON_ORDER_SCORE_ASC
                : STELLEN_FOR_PERSON_ORDER_SCORE_DESC;
    }

    private static String personenOrderBy(SortierParameter sort) {
        if (sort.kriterium() == SortierKriterium.ERSTELLT_AM) {
            return sort.richtung() == SortierRichtung.ASC
                    ? PERSONEN_FOR_STELLE_ORDER_DATUM_ASC
                    : PERSONEN_FOR_STELLE_ORDER_DATUM_DESC;
        }
        return sort.richtung() == SortierRichtung.ASC
                ? PERSONEN_FOR_STELLE_ORDER_SCORE_ASC
                : PERSONEN_FOR_STELLE_ORDER_SCORE_DESC;
    }
}

