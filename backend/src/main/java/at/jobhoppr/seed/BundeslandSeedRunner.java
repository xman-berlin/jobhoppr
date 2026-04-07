package at.jobhoppr.seed;

import at.jobhoppr.domain.geo.Bundesland;
import at.jobhoppr.domain.geo.BundeslandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class BundeslandSeedRunner implements ApplicationRunner {

    private final BundeslandRepository bundeslandRepository;

    private static final List<Object[]> BUNDESLAENDER = List.of(
        new Object[]{"W",   "Wien",             48.2082, 16.3738,  15.0},
        new Object[]{"NOE", "Niederösterreich", 48.1000, 15.6000, 110.0},
        new Object[]{"OOE", "Oberösterreich",   48.0000, 14.0000, 100.0},
        new Object[]{"ST",  "Steiermark",       47.3500, 14.5000, 110.0},
        new Object[]{"T",   "Tirol",            47.2000, 11.4000, 100.0},
        new Object[]{"K",   "Kärnten",          46.8000, 14.0000,  80.0},
        new Object[]{"S",   "Salzburg",         47.5500, 13.2000,  80.0},
        new Object[]{"V",   "Vorarlberg",       47.2500,  9.9000,  40.0},
        new Object[]{"B",   "Burgenland",       47.5000, 16.5000,  60.0}
    );

    @Override
    public void run(ApplicationArguments args) {
        if (bundeslandRepository.count() > 0) {
            log.debug("Bundesländer bereits vorhanden, überspringe Seed.");
            return;
        }
        log.info("Seede Bundesländer...");
        for (Object[] row : BUNDESLAENDER) {
            Bundesland b = new Bundesland();
            b.setKuerzel((String) row[0]);
            b.setName((String) row[1]);
            b.setCentroidLat((Double) row[2]);
            b.setCentroidLon((Double) row[3]);
            b.setUmkreisKm((Double) row[4]);
            bundeslandRepository.save(b);
        }
        log.info("9 Bundesländer gespeichert.");
    }
}
