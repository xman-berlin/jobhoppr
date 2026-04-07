package at.jobhoppr.domain.stelle;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stellen")
@RequiredArgsConstructor
public class StelleController {

    private final StelleService stelleService;

    @GetMapping
    public String liste(@RequestParam(defaultValue = "0") int page, Model model) {
        Pageable pageable = PageRequest.of(page, 20);
        model.addAttribute("seite", stelleService.findAll(pageable));
        model.addAttribute("currentPage", page);
        return "stellen/liste";
    }

    @GetMapping("/neu")
    public String neu(Model model) {
        model.addAttribute("stelle", new Stelle());
        model.addAttribute("isNeu", true);
        return "stellen/formular";
    }

    @GetMapping("/{id}")
    public String bearbeiten(@PathVariable UUID id, Model model) {
        model.addAttribute("stelle", stelleService.findById(id));
        model.addAttribute("isNeu", false);
        return "stellen/formular";
    }

    @PostMapping
    public String erstellen(
            @RequestParam String titel,
            @RequestParam(required = false) String unternehmen,
            @RequestParam(required = false) String beschreibung,
            @RequestParam String ortBezeichnung,
            @RequestParam double ortLat,
            @RequestParam double ortLon,
            @RequestParam(required = false) Integer berufId,
            @RequestParam(required = false) List<Integer> kompetenzIds,
            @RequestParam(required = false) List<Boolean> pflichtFlags) {

        Stelle s = stelleService.erstellen(buildRequest(titel, unternehmen, beschreibung,
                ortBezeichnung, ortLat, ortLon, berufId, kompetenzIds, pflichtFlags));
        return "redirect:/stellen/" + s.getId();
    }

    @PostMapping("/{id}")
    public String aktualisieren(
            @PathVariable UUID id,
            @RequestParam String titel,
            @RequestParam(required = false) String unternehmen,
            @RequestParam(required = false) String beschreibung,
            @RequestParam String ortBezeichnung,
            @RequestParam double ortLat,
            @RequestParam double ortLon,
            @RequestParam(required = false) Integer berufId,
            @RequestParam(required = false) List<Integer> kompetenzIds,
            @RequestParam(required = false) List<Boolean> pflichtFlags) {

        stelleService.aktualisieren(id, buildRequest(titel, unternehmen, beschreibung,
                ortBezeichnung, ortLat, ortLon, berufId, kompetenzIds, pflichtFlags));
        return "redirect:/stellen/" + id;
    }

    @DeleteMapping("/{id}")
    @HxRequest
    public ResponseEntity<String> loeschen(@PathVariable UUID id) {
        stelleService.loeschen(id);
        return ResponseEntity.ok()
                .header("HX-Trigger", "{\"showToast\":\"Stelle gelöscht\"}")
                .body("");
    }

    private StelleService.StelleRequest buildRequest(
            String titel, String unternehmen, String beschreibung,
            String ortBezeichnung, double ortLat, double ortLon,
            Integer berufId, List<Integer> kompetenzIds, List<Boolean> pflichtFlags) {

        List<StelleService.KompetenzEintrag> kompetenzen = null;
        if (kompetenzIds != null) {
            kompetenzen = new java.util.ArrayList<>();
            for (int i = 0; i < kompetenzIds.size(); i++) {
                boolean pflicht = pflichtFlags != null && i < pflichtFlags.size()
                        ? pflichtFlags.get(i) : true;
                kompetenzen.add(new StelleService.KompetenzEintrag(kompetenzIds.get(i), pflicht));
            }
        }
        return new StelleService.StelleRequest(titel, unternehmen, beschreibung,
                ortBezeichnung, ortLat, ortLon, berufId, kompetenzen);
    }
}
