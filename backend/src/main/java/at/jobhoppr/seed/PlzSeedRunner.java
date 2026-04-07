package at.jobhoppr.seed;

import at.jobhoppr.domain.geo.PlzOrt;
import at.jobhoppr.domain.geo.PlzOrtRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the plz_ort table from GeoNames AT.txt (CC-BY 4.0, geonames.org).
 * Format: country_code TAB postal_code TAB place_name TAB admin_name1 (Bundesland)
 *         TAB admin_name2 TAB admin_name3 TAB admin_code1 TAB admin_code2 TAB admin_code3
 *         TAB latitude TAB longitude TAB accuracy
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class PlzSeedRunner implements ApplicationRunner {

    private final PlzOrtRepository plzOrtRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (plzOrtRepository.count() > 0) {
            log.debug("PLZ-Orte bereits vorhanden, überspringe Seed.");
            return;
        }
        log.info("Seede PLZ-Orte aus GeoNames AT_plz.tsv...");

        ClassPathResource resource = new ClassPathResource("seed/AT_plz.tsv");
        List<Object[]> batch = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split("\t");
                if (cols.length < 11) continue;

                String plz       = cols[1].trim();
                String ortName   = cols[2].trim();
                String bundesland= cols[3].trim();
                String bezirk    = cols[5].trim();
                double lat       = Double.parseDouble(cols[9].trim());
                double lon       = Double.parseDouble(cols[10].trim());

                batch.add(new Object[]{plz, ortName, bundesland, bezirk, lat, lon});

                if (batch.size() >= 500) {
                    insertBatch(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) insertBatch(batch);
        }

        log.info("PLZ-Seed abgeschlossen: {} Einträge.", plzOrtRepository.count());
    }

    private void insertBatch(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO plz_ort (plz, ort_name, bundesland, bezirk, lat, lon) " +
            "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
            batch);
    }
}
