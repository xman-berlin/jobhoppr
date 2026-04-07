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
            @RequestParam(defaultValue = "false") boolean berufFilterStrikt,
            @RequestParam double gewichtKompetenz,
            @RequestParam double gewichtBeruf) {

        matchModellService.aktualisieren(new MatchModellService.MatchModellRequest(
                geoAktiv, berufFilterStrikt, gewichtKompetenz, gewichtBeruf));

        return ResponseEntity.ok()
                .header("HX-Trigger", "{\"showToast\":\"Gespeichert\"}")
                .body("");
    }
}
