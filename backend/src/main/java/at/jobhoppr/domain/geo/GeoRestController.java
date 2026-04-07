package at.jobhoppr.domain.geo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
@Slf4j
public class GeoRestController {

    private final PlzOrtRepository plzOrtRepository;
    private final BundeslandRepository bundeslandRepository;
    private final RestTemplate restTemplate;

    @Value("${jobhoppr.nominatim.user-agent:JobHoppr/1.0}")
    private String userAgent;

    @Value("${jobhoppr.nominatim.rate-limit-ms:1100}")
    private long rateLimitMs;

    private volatile long lastNominatimCall = 0;

    @GetMapping("/suche")
    public List<OrtResult> suche(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return List.of();
        return plzOrtRepository.suche(q).stream()
                .map(p -> new OrtResult(
                        p.getId().plz() + " " + p.getId().ortName(),
                        p.getId().ortName(),
                        p.getId().plz(),
                        p.getBundesland(),
                        p.getLat(),
                        p.getLon()))
                .toList();
    }

    @GetMapping("/bundeslaender")
    public List<BundeslandResult> bundeslaender() {
        return bundeslandRepository.findAll().stream()
                .map(b -> new BundeslandResult(b.getKuerzel(), b.getName(),
                        b.getCentroidLat(), b.getCentroidLon(), b.getUmkreisKm()))
                .toList();
    }

    @GetMapping("/nominatim")
    public List<OrtResult> nominatim(@RequestParam String q) throws InterruptedException {
        // Enforce rate limit
        long now = System.currentTimeMillis();
        long wait = rateLimitMs - (now - lastNominatimCall);
        if (wait > 0) Thread.sleep(wait);
        lastNominatimCall = System.currentTimeMillis();

        String url = "https://nominatim.openstreetmap.org/search"
                + "?q=" + q.replace(" ", "+")
                + "&countrycodes=at&format=json&limit=5&addressdetails=1";

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            if (response.getBody() == null) return List.of();
            return response.getBody().stream()
                    .map(r -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) r;
                        double lat = Double.parseDouble(m.get("lat").toString());
                        double lon = Double.parseDouble(m.get("lon").toString());
                        String display = m.get("display_name").toString();
                        // Shorten display name to first 2 parts
                        String[] parts = display.split(", ");
                        String label = parts.length >= 2 ? parts[0] + ", " + parts[1] : display;
                        return new OrtResult(label, label, null, "AT", lat, lon);
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("Nominatim request failed: {}", e.getMessage());
            return List.of();
        }
    }

    public record OrtResult(String label, String name, String plz, String bundesland,
                            double lat, double lon) {}
    public record BundeslandResult(String kuerzel, String name, double lat, double lon,
                                   double umkreisKm) {}
}
