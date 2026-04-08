package at.jobhoppr.domain.matching;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Executes the matching CTE queries via JdbcTemplate (native SQL, PostGIS).
 * Two symmetric queries:
 *  - findTopPersonenForStelle: given a Stelle, rank Persons
 *  - findTopStellenForPerson:  given a Person, rank Stellen
 */
@Repository
@RequiredArgsConstructor
public class MatchRepository {

    private final JdbcTemplate jdbc;

    private static final String PERSONEN_FOR_STELLE = """
        WITH stelle_data AS (
            SELECT s.id, s.standort, s.beruf_id,
                   COUNT(sk.kompetenz_id) FILTER (WHERE sk.pflicht = TRUE)  AS total_pflicht,
                   COUNT(sk.kompetenz_id) FILTER (WHERE sk.pflicht = FALSE) AS total_optional
            FROM stelle s
            LEFT JOIN stelle_kompetenz sk ON sk.stelle_id = s.id
            WHERE s.id = ?::uuid
            GROUP BY s.id, s.standort, s.beruf_id
        ),
        geo_kandidaten AS (
            SELECT DISTINCT po.person_id
            FROM person_ort po, stelle_data sd
            WHERE ? = FALSE
               OR ST_DWithin(po.standort, sd.standort, po.umkreis_km * 1000)
        ),
        beruf_kandidaten AS (
            SELECT p.id AS person_id, p.beruf_id, p.vorname, p.nachname
            FROM person p
            JOIN geo_kandidaten gk ON gk.person_id = p.id
            WHERE ? = FALSE
               OR p.beruf_id = (SELECT beruf_id FROM stelle_data)
        ),
        kompetenz_scores AS (
            SELECT
                bk.person_id,
                bk.beruf_id,
                bk.vorname,
                bk.nachname,
                COALESCE(
                    (COUNT(pk.kompetenz_id) FILTER (WHERE sk.pflicht = TRUE)  * 2.0
                   + COUNT(pk.kompetenz_id) FILTER (WHERE sk.pflicht = FALSE))
                   / NULLIF((SELECT total_pflicht * 2 + total_optional FROM stelle_data), 0),
                   0.0
                ) AS kompetenz_score
            FROM beruf_kandidaten bk
            LEFT JOIN person_kompetenz pk ON pk.person_id = bk.person_id
            LEFT JOIN stelle_kompetenz sk
                ON sk.kompetenz_id = pk.kompetenz_id
               AND sk.stelle_id = (SELECT id FROM stelle_data)
            GROUP BY bk.person_id, bk.beruf_id, bk.vorname, bk.nachname
        )
        SELECT
            ks.person_id                                                                    AS target_id,
            ks.vorname || ' ' || ks.nachname                                               AS target_name,
            CASE WHEN ks.beruf_id = (SELECT beruf_id FROM stelle_data) THEN 1.0 ELSE 0.0 END AS beruf_score,
            ks.kompetenz_score,
            (? * CASE WHEN ks.beruf_id = (SELECT beruf_id FROM stelle_data) THEN 1.0 ELSE 0.0 END
             + ? * ks.kompetenz_score)
            / NULLIF(? + ?, 0)                                                             AS gesamt_score
        FROM kompetenz_scores ks
        ORDER BY gesamt_score DESC
        LIMIT 50
        """;

    private static final String STELLEN_FOR_PERSON = """
        WITH person_data AS (
            SELECT p.id, p.beruf_id
            FROM person p WHERE p.id = ?::uuid
        ),
        person_orte AS (
            SELECT po.standort, po.umkreis_km
            FROM person_ort po WHERE po.person_id = ?::uuid
        ),
        geo_kandidaten AS (
            SELECT DISTINCT s.id AS stelle_id
            FROM stelle s, person_orte po
            WHERE ? = FALSE
               OR ST_DWithin(po.standort, s.standort, po.umkreis_km * 1000)
        ),
        beruf_kandidaten AS (
            SELECT s.id AS stelle_id, s.beruf_id, s.titel, s.unternehmen
            FROM stelle s
            JOIN geo_kandidaten gk ON gk.stelle_id = s.id
            WHERE ? = FALSE
               OR s.beruf_id = (SELECT beruf_id FROM person_data)
        ),
        stelle_kompetenz_counts AS (
            SELECT
                bk.stelle_id,
                bk.beruf_id,
                bk.titel,
                bk.unternehmen,
                COUNT(sk.kompetenz_id) FILTER (WHERE sk.pflicht = TRUE)  AS total_pflicht,
                COUNT(sk.kompetenz_id) FILTER (WHERE sk.pflicht = FALSE) AS total_optional
            FROM beruf_kandidaten bk
            LEFT JOIN stelle_kompetenz sk ON sk.stelle_id = bk.stelle_id
            GROUP BY bk.stelle_id, bk.beruf_id, bk.titel, bk.unternehmen
        ),
        kompetenz_scores AS (
            SELECT
                skc.stelle_id,
                skc.beruf_id,
                skc.titel,
                skc.unternehmen,
                COALESCE(
                    (COUNT(pk.kompetenz_id) FILTER (WHERE sk.pflicht = TRUE)  * 2.0
                   + COUNT(pk.kompetenz_id) FILTER (WHERE sk.pflicht = FALSE))
                   / NULLIF(skc.total_pflicht * 2 + skc.total_optional, 0),
                   0.0
                ) AS kompetenz_score
            FROM stelle_kompetenz_counts skc
            LEFT JOIN stelle_kompetenz sk ON sk.stelle_id = skc.stelle_id
            LEFT JOIN person_kompetenz pk
                ON pk.kompetenz_id = sk.kompetenz_id
               AND pk.person_id = (SELECT id FROM person_data)
            GROUP BY skc.stelle_id, skc.beruf_id, skc.titel, skc.unternehmen,
                     skc.total_pflicht, skc.total_optional
        )
        SELECT
            ks.stelle_id                                                                       AS target_id,
            ks.titel || CASE WHEN ks.unternehmen IS NOT NULL THEN ' – ' || ks.unternehmen
                             ELSE '' END                                                        AS target_name,
            CASE WHEN ks.beruf_id = (SELECT beruf_id FROM person_data) THEN 1.0 ELSE 0.0 END   AS beruf_score,
            ks.kompetenz_score,
            (? * CASE WHEN ks.beruf_id = (SELECT beruf_id FROM person_data) THEN 1.0 ELSE 0.0 END
             + ? * ks.kompetenz_score)
            / NULLIF(? + ?, 0)                                                                  AS gesamt_score
        FROM kompetenz_scores ks
        ORDER BY gesamt_score DESC
        LIMIT 50
        """;

    public List<MatchResult> findTopPersonenForStelle(UUID stelleId, MatchModell modell) {
        boolean geoAktiv = Boolean.TRUE.equals(modell.getGeoAktiv());
        double wB = modell.getGewichtBeruf();
        double wK = modell.getGewichtKompetenz();

        return jdbc.query(PERSONEN_FOR_STELLE,
                (rs, i) -> new MatchResult(
                        UUID.fromString(rs.getString("target_id")),
                        rs.getString("target_name"),
                        rs.getDouble("beruf_score"),
                        rs.getDouble("kompetenz_score"),
                        rs.getDouble("gesamt_score")),
                stelleId.toString(), geoAktiv, false,
                wB, wK, wB, wK);
    }

    public List<MatchResult> findTopStellenForPerson(UUID personId, MatchModell modell) {
        boolean geoAktiv = Boolean.TRUE.equals(modell.getGeoAktiv());
        double wB = modell.getGewichtBeruf();
        double wK = modell.getGewichtKompetenz();

        return jdbc.query(STELLEN_FOR_PERSON,
                (rs, i) -> new MatchResult(
                        UUID.fromString(rs.getString("target_id")),
                        rs.getString("target_name"),
                        rs.getDouble("beruf_score"),
                        rs.getDouble("kompetenz_score"),
                        rs.getDouble("gesamt_score")),
                personId.toString(), personId.toString(), geoAktiv, false,
                wB, wK, wB, wK);
    }
}
