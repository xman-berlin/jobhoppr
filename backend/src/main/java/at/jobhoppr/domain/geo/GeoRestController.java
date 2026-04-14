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
    private final GeoLocationRepository geoLocationRepository;
    private final RestTemplate restTemplate;

    @Value("${jobhoppr.nominatim.user-agent:JobHoppr/1.0}")
    private String userAgent;

    @Value("${jobhoppr.nominatim.rate-limit-ms:1100}")
    private long rateLimitMs;

    private volatile long lastNominatimCall = 0;

    @GetMapping("/suche")
    public List<GeoSearchResult> suche(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return List.of();

        List<GeoSearchResult> results = new java.util.ArrayList<>();

        String search = "%" + q.toLowerCase() + "%";
        String searchNoAccent = "%" + removeAccents(q.toLowerCase()) + "%";

        // 1. Bundesländer (aus geo_location, Ebene BUNDESLAND)
        List<GeoLocation> bundeslaender = geoLocationRepository
                .findByEbeneAndNameWithParent("BUNDESLAND", search);
        for (GeoLocation bl : bundeslaender) {
            results.add(new GeoSearchResult("BUNDESLAND", bl.getId(), bl.getName(), 
                    bl.getName() + " (Bundesland)", bl.getLat(), bl.getLon(), null, null, null, null));
        }

        // 2. Bezirke (aus geo_location, Ebene BEZIRK)
        List<GeoLocation> bezirke = geoLocationRepository
                .findByEbeneAndNameWithParent("BEZIRK", search);
        for (GeoLocation bz : bezirke) {
            String parentName = bz.getParent() != null ? bz.getParent().getName() : null;
            results.add(new GeoSearchResult("BEZIRK", bz.getId(), bz.getName(),
                    bz.getName() + (parentName != null ? " (" + parentName + ")" : ""),
                    bz.getLat(), bz.getLon(), null, null, null, parentName));
        }

        // 3. PLZ-Ort Kombinationen (with accent-insensitive search)
        String plzSearch = "%" + q + "%";
        List<PlzOrt> orte = plzOrtRepository.sucheVolltext(search, searchNoAccent, plzSearch);
        for (PlzOrt po : orte) {
            String label = po.getId().plz() + " " + po.getId().ortName();
            if (po.getBezirk() != null) label += " (" + po.getBezirk() + ")";
            results.add(new GeoSearchResult("PLZ_ORT", null, po.getId().ortName(),
                    label, po.getLat(), po.getLon(), 
                    po.getId().plz(), po.getBundesland(), po.getBezirk(), null));
        }

        // Limit to 30 total results
        return results.stream().limit(30).toList();
    }

    @GetMapping("/bundeslaender")
    public List<BundeslandResult> bundeslaender() {
        return bundeslandRepository.findAll().stream()
                .map(b -> new BundeslandResult(b.getKuerzel(), b.getName(),
                        b.getCentroidLat(), b.getCentroidLon(), b.getUmkreisKm()))
                .toList();
    }

    /** Returns top-level geo_location entries (Bundesländer) or children of a given parent. */
    @GetMapping("/locations")
    public List<GeoLocationResult> locations(@RequestParam(required = false) Integer parentId) {
        List<GeoLocation> result = parentId == null
                ? geoLocationRepository.findByEbeneAndParentIsNullOrderByName("BUNDESLAND")
                : geoLocationRepository.findByParentIdOrderByName(parentId);
        return result.stream()
                .map(g -> new GeoLocationResult(g.getId(), g.getName(), g.getEbene(),
                        g.getParentId(), g.getLat(), g.getLon()))
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
    public record GeoLocationResult(Integer id, String name, String ebene,
                                    Integer parentId, Double lat, Double lon) {}

    /** Combined search result for autocomplete: Bundesland, Bezirk, or PLZ-Ort */
    public record GeoSearchResult(
            String typ,          // BUNDESLAND, BEZIRK, PLZ_ORT
            Integer id,          // geo_location id (or null for PLZ_ORT)
            String name,         // plain name
            String label,        // display label
            Double lat, Double lon,  // coordinates
            String plz, String bundesland, String bezirk, String bezirkParent) {}

    private static String removeAccents(String input) {
        if (input == null) return null;
        return input.replace("ö", "o").replace("Ö", "O")
                    .replace("ä", "a").replace("Ä", "A")
                    .replace("ü", "u").replace("Ü", "U")
                    .replace("ß", "ss");
    }
}
