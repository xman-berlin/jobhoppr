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
 * Seeds BIS reference data from JSON files generated from the official BIS XML exports.
 *
 * Seeds (in order):
 *   1. bis_kompetenz     — from bis_kompetenzen.json (qualifikationsbereiche + qualifikationen + detailqualifikationen)
 *   2. kompetenz_closure — transitiver Abschluss der Kompetenz-Hierarchie
 *   3. interessensgebiet — from bis_interessensgebiete.json (Themengebiete für Lehrstellenmatching)
 *   4. voraussetzung     — from bis_voraussetzungen.json (Voraussetzungen für Lehrstellenmatching)
 *
 * Idempotent: skips if bis_kompetenz already has rows.
 * Order(1) — runs before BerufHierarchieSeedRunner and DevDataSeeder.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class BisSeedRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM bis_kompetenz", Integer.class);
        if (count != null && count > 0) {
            log.debug("BIS-Kompetenzdaten bereits vorhanden, überspringe Seed.");
            return;
        }

        seedKompetenzen();
        seedKompetenzClosure();
        seedInteressensgebiete();
        seedVoraussetzungen();
    }

    // ── bis_kompetenz ─────────────────────────────────────────────────────────

    private void seedKompetenzen() throws Exception {
        log.info("Seede BIS-Kompetenzen...");
        try (InputStream in = new ClassPathResource("seed/bis_kompetenzen.json").getInputStream()) {
            JsonNode komps = objectMapper.readTree(in);

            // Multi-pass insertion: repeat until all entries are inserted.
            // Needed because detailqualifikationen can reference other detailqualifikationen
            // (up to 6 levels deep), so a single two-pass approach is not sufficient.
            int total = 0;
            int maxPasses = 8;
            boolean anyInserted = true;

            for (int pass = 0; pass < maxPasses && anyInserted; pass++) {
                anyInserted = false;
                for (JsonNode k : komps) {
                    int id = k.get("id").asInt();
                    String name = k.get("name").asText();
                    String typ = k.has("typ") ? k.get("typ").asText() : null;
                    Integer parentId = k.get("parent_id").isNull() ? null : k.get("parent_id").asInt();

                    // Skip if parent not yet inserted
                    if (parentId != null) {
                        Integer parentExists = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM bis_kompetenz WHERE id = ?", Integer.class, parentId);
                        if (parentExists == null || parentExists == 0) continue;
                    }

                    int rows = jdbc.update(
                        "INSERT INTO bis_kompetenz (id, name, typ, parent_id) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                        id, name, typ, parentId);
                    if (rows > 0) {
                        anyInserted = true;
                        total++;
                    }
                }
                log.debug("Kompetenz-Seed Pass {}: {} gesamt eingefügt", pass + 1, total);
            }
        }
        log.info("BIS-Kompetenzen geseedet: {} Einträge.",
            jdbc.queryForObject("SELECT COUNT(*) FROM bis_kompetenz", Integer.class));
    }

    // ── kompetenz_closure ─────────────────────────────────────────────────────

    private void seedKompetenzClosure() {
        log.info("Befülle kompetenz_closure...");

        // Self-Paare (tiefe=0)
        jdbc.update("""
            INSERT INTO kompetenz_closure (vorfahre_id, nachfahre_id, tiefe)
            SELECT id, id, 0 FROM bis_kompetenz
            ON CONFLICT DO NOTHING
            """);

        // Direkte Eltern-Kind-Paare (tiefe=1)
        jdbc.update("""
            INSERT INTO kompetenz_closure (vorfahre_id, nachfahre_id, tiefe)
            SELECT parent_id, id, 1 FROM bis_kompetenz
            WHERE parent_id IS NOT NULL
            ON CONFLICT DO NOTHING
            """);

        // Transitiver Abschluss via rekursivem CTE (einmalig beim Seed)
        // Korrektur P5: kein SELECT DISTINCT — GROUP BY genügt
        jdbc.update("""
            WITH RECURSIVE closure AS (
                SELECT vorfahre_id, nachfahre_id, tiefe FROM kompetenz_closure
                UNION ALL
                SELECT c.vorfahre_id, kc.nachfahre_id, c.tiefe + kc.tiefe
                FROM closure c
                JOIN kompetenz_closure kc ON kc.vorfahre_id = c.nachfahre_id AND kc.tiefe = 1
                WHERE c.tiefe + kc.tiefe <= 10
            )
            INSERT INTO kompetenz_closure
            SELECT vorfahre_id, nachfahre_id, MIN(tiefe)
            FROM closure GROUP BY vorfahre_id, nachfahre_id
            ON CONFLICT DO NOTHING
            """);

        log.info("kompetenz_closure befüllt: {} Einträge.",
            jdbc.queryForObject("SELECT COUNT(*) FROM kompetenz_closure", Integer.class));
    }

    // ── interessensgebiet ─────────────────────────────────────────────────────

    private void seedInteressensgebiete() throws Exception {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM interessensgebiet", Integer.class);
        if (count != null && count > 0) {
            log.debug("Interessensgebiete bereits vorhanden, überspringe.");
            return;
        }
        log.info("Seede Interessensgebiete...");
        try (InputStream in = new ClassPathResource("seed/bis_interessensgebiete.json").getInputStream()) {
            JsonNode gebiete = objectMapper.readTree(in);
            for (JsonNode g : gebiete) {
                jdbc.update(
                    "INSERT INTO interessensgebiet (id, name) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    g.get("id").asInt(),
                    g.get("name").asText());
            }
        }
        log.info("Interessensgebiete geseedet: {} Einträge.",
            jdbc.queryForObject("SELECT COUNT(*) FROM interessensgebiet", Integer.class));
    }

    // ── voraussetzung ─────────────────────────────────────────────────────────

    private void seedVoraussetzungen() throws Exception {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM voraussetzung", Integer.class);
        if (count != null && count > 0) {
            log.debug("Voraussetzungen bereits vorhanden, überspringe.");
            return;
        }
        log.info("Seede Voraussetzungen...");
        try (InputStream in = new ClassPathResource("seed/bis_voraussetzungen.json").getInputStream()) {
            JsonNode vors = objectMapper.readTree(in);
            for (JsonNode v : vors) {
                jdbc.update(
                    "INSERT INTO voraussetzung (id, name) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    v.get("id").asInt(),
                    v.get("name").asText());
            }
        }
        log.info("Voraussetzungen geseedet: {} Einträge.",
            jdbc.queryForObject("SELECT COUNT(*) FROM voraussetzung", Integer.class));
    }
}
