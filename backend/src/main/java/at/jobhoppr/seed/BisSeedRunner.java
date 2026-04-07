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
 * Seeds bis_beruf and bis_kompetenz from JSON files in resources/seed/.
 * Idempotent: skips if bis_beruf already has rows.
 * Order(1) — runs before PlzSeedRunner and DevDataSeeder.
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
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM bis_beruf", Integer.class);
        if (count != null && count > 0) {
            log.debug("BIS-Daten bereits vorhanden, überspringe Seed.");
            return;
        }

        log.info("Seede BIS-Berufe...");
        try (InputStream in = new ClassPathResource("seed/bis_berufe.json").getInputStream()) {
            JsonNode berufe = objectMapper.readTree(in);
            for (JsonNode b : berufe) {
                jdbc.update(
                    "INSERT INTO bis_beruf (id, name, bereich, isco_code) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    b.get("id").asInt(),
                    b.get("name").asText(),
                    b.has("bereich") ? b.get("bereich").asText() : null,
                    b.has("isco_code") ? b.get("isco_code").asText() : null
                );
            }
        }
        log.info("BIS-Berufe geseedet: {} Einträge.", jdbc.queryForObject("SELECT COUNT(*) FROM bis_beruf", Integer.class));

        log.info("Seede BIS-Kompetenzen...");
        try (InputStream in = new ClassPathResource("seed/bis_kompetenzen.json").getInputStream()) {
            JsonNode komps = objectMapper.readTree(in);
            // Two passes: first insert top-level (parent_id = null), then children
            for (JsonNode k : komps) {
                if (k.get("parent_id").isNull()) {
                    jdbc.update(
                        "INSERT INTO bis_kompetenz (id, name, bereich, typ, parent_id) VALUES (?, ?, ?, ?, NULL) ON CONFLICT DO NOTHING",
                        k.get("id").asInt(),
                        k.get("name").asText(),
                        k.has("bereich") ? k.get("bereich").asText() : null,
                        k.has("typ") ? k.get("typ").asText() : null
                    );
                }
            }
            for (JsonNode k : komps) {
                if (!k.get("parent_id").isNull()) {
                    jdbc.update(
                        "INSERT INTO bis_kompetenz (id, name, bereich, typ, parent_id) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                        k.get("id").asInt(),
                        k.get("name").asText(),
                        k.has("bereich") ? k.get("bereich").asText() : null,
                        k.has("typ") ? k.get("typ").asText() : null,
                        k.get("parent_id").asInt()
                    );
                }
            }
        }
        log.info("BIS-Kompetenzen geseedet: {} Einträge.", jdbc.queryForObject("SELECT COUNT(*) FROM bis_kompetenz", Integer.class));
    }
}
