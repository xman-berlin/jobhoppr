package at.jobhoppr.domain.matching;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxResponse;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/match-modell")
@RequiredArgsConstructor
public class MatchModellController {

    private final MatchModellService matchModellService;

    @GetMapping
    public String editor(Model model) {
        model.addAttribute("modell", matchModellService.getAktives());
        return "match-modell/editor";
    }

    @PutMapping
    @HxRequest
    public ResponseEntity<String> speichern(
            @RequestParam(defaultValue = "false") boolean geoAktiv,
            @RequestParam double gewichtKompetenz,
            @RequestParam double gewichtBeruf,
            @RequestParam(defaultValue = "0.25") double scoreSchwellenwert,
            @RequestParam(defaultValue = "0.20") double gewichtLehrberuf,
            @RequestParam(defaultValue = "0.40") double gewichtInteressen,
            @RequestParam(defaultValue = "0.40") double gewichtVoraussetzungen,
            @RequestParam(defaultValue = "0.0") double gewichtArbeitszeit) {

        matchModellService.aktualisieren(new MatchModellService.MatchModellRequest(
                geoAktiv, gewichtKompetenz, gewichtBeruf,
                scoreSchwellenwert, gewichtLehrberuf, gewichtInteressen, gewichtVoraussetzungen,
                gewichtArbeitszeit));

        return ResponseEntity.ok()
                .header("HX-Trigger", "{\"showToast\":\"Gespeichert\"}")
                .body("");
    }
}
