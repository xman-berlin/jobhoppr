package at.jobhoppr.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Seeds the 4-level Beruf hierarchy from bis_berufe_hierarchie.json.
 *
 * BIS hierarchy → schema mapping:
 *   beruf_bereich        ← stammdatenkategorie (15 entries)
 *   beruf_obergruppe     ← synthetic: one per bereich (pass-through to satisfy NOT NULL)
 *   beruf_untergruppe    ← berufsfeld (92 entries) — the BUG matching level
 *   beruf_spezialisierung ← stammberuf (518 entries) — assigned to person/stelle
 *
 * Also seeds bis_beruf (legacy flat table) from the same data to keep
 * old FK references intact until V4b drops beruf_id.
 *
 * Idempotent: skips if beruf_bereich already has rows.
 * Order(2) — runs after BisSeedRunner, before DevDataSeeder.
 *
 * Guard (P1): throws IllegalStateException if any beruf_spezialisierung rows
 * are missing after the seed — signals incomplete data before V4b can drop beruf_id.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class BerufHierarchieSeedRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM beruf_bereich", Integer.class);
        if (count == null || count == 0) {
            seedHierarchie();
        } else {
            log.debug("Beruf-Hierarchie bereits vorhanden, überspringe Seed.");
        }
        seedBasisKompetenzen();
    }

    private void seedHierarchie() throws Exception {

        log.info("Seede Beruf-Hierarchie (berufsfelder, stammberufe)...");
        try (InputStream in = new ClassPathResource("seed/bis_berufe_hierarchie.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            JsonNode bereiche = root.get("bereiche");

            int totalSpezialisierungen = 0;

            for (JsonNode bereich : bereiche) {
                int bereichBisId = bereich.get("id").asInt();
                String bereichName = bereich.get("name").asText();

                // beruf_bereich (uses BIS noteid as PK via sequence override)
                jdbc.update(
                    "INSERT INTO beruf_bereich (id, name) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    bereichBisId, bereichName);

                // beruf_obergruppe: synthetic single entry per bereich
                // Name = bereich name (it's just a structural pass-through)
                int obId = insertObergruppe(bereichBisId, bereichName);

                for (JsonNode bf : bereich.get("berufsfelder")) {
                    int bfId = bf.get("id").asInt();
                    String bfName = bf.get("name").asText();

                    // beruf_untergruppe = berufsfeld (the BUG matching level)
                    jdbc.update(
                        "INSERT INTO beruf_untergruppe (id, name, obergruppe_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                        bfId, bfName, obId);

                    for (JsonNode sb : bf.get("stammberufe")) {
                        int sbId = sb.get("id").asInt();
                        String sbName = sb.get("name").asText();

                        // beruf_spezialisierung = stammberuf
                        jdbc.update(
                            "INSERT INTO beruf_spezialisierung (id, name, untergruppe_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                            sbId, sbName, bfId);

                        // Also seed legacy bis_beruf for backward compatibility (until V4b)
                        jdbc.update(
                            "INSERT INTO bis_beruf (id, name, bereich) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                            sbId, sbName, bereichName);

                        totalSpezialisierungen++;
                    }
                }
            }

            log.info("Beruf-Hierarchie geseedet: {} Bereiche, {} Berufsfelder, {} Stammberufe",
                jdbc.queryForObject("SELECT COUNT(*) FROM beruf_bereich", Integer.class),
                jdbc.queryForObject("SELECT COUNT(*) FROM beruf_untergruppe", Integer.class),
                jdbc.queryForObject("SELECT COUNT(*) FROM beruf_spezialisierung", Integer.class));

            // P1 Guard: verify all spezialisierungen were inserted
            Integer seeded = jdbc.queryForObject("SELECT COUNT(*) FROM beruf_spezialisierung", Integer.class);
            if (seeded == null || seeded < totalSpezialisierungen) {
                throw new IllegalStateException(
                    "BerufHierarchieSeedRunner: Inkompletter Seed! Erwartet " + totalSpezialisierungen +
                    " Spezialisierungen, gefunden: " + seeded +
                    ". V4b darf erst nach vollständigem Seed deployed werden.");
            }
        }
    }

    // ── beruf_basis_kompetenz ─────────────────────────────────────────────────

    private void seedBasisKompetenzen() throws Exception {
        // Determine expected count from JSON to detect stale seed (e.g. after adding typ column).
        try (InputStream in = new ClassPathResource("seed/bis_beruf_kompetenzen.json").getInputStream()) {
            JsonNode mappings = objectMapper.readTree(in);
            int expected = mappings.size();

            Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM beruf_basis_kompetenz", Integer.class);
            if (existing != null && existing == expected) {
                log.debug("Beruf-Basis-Kompetenzen bereits vollständig vorhanden ({} Einträge), überspringe.", existing);
                return;
            }

            // Stale or empty — truncate and re-seed (table has no incoming FKs).
            log.info("Seede Beruf-Basis-Kompetenzen (vorher: {}, erwartet: {})...", existing, expected);
            jdbc.execute("TRUNCATE TABLE beruf_basis_kompetenz");

            int inserted = 0;
            for (JsonNode m : mappings) {
                int berufId     = m.get("berufId").asInt();
                int kompetenzId = m.get("kompetenzId").asInt();
                String typ      = m.get("typ").asText("basis");
                int rows = jdbc.update(
                    "INSERT INTO beruf_basis_kompetenz (beruf_id, kompetenz_id, typ) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                    berufId, kompetenzId, typ);
                inserted += rows;
            }
            log.info("Beruf-Basis-Kompetenzen geseedet: {} Einträge (basis + fach).", inserted);
        }
    }

    /**
     * Inserts a synthetic beruf_obergruppe for the given bereich and returns its generated ID.
     * Uses the sequence-generated SERIAL id.
     */
    private int insertObergruppe(int bereichId, String name) {
        return jdbc.queryForObject(
            "INSERT INTO beruf_obergruppe (name, bereich_id) VALUES (?, ?) " +
            "ON CONFLICT DO NOTHING RETURNING id",
            Integer.class, name, bereichId);
    }
}
