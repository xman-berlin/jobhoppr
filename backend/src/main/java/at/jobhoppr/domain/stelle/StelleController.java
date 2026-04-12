package at.jobhoppr.domain.stelle;

import at.jobhoppr.domain.bis.BerufSpezialisierungRepository;
import at.jobhoppr.domain.bis.InteressensgebietRepository;
import at.jobhoppr.domain.bis.KompetenzRepository;
import at.jobhoppr.domain.bis.VoraussetzungRepository;
import at.jobhoppr.domain.geo.GeoLocationRepository;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stellen")
@RequiredArgsConstructor
public class StelleController {

    private final StelleService stelleService;
    private final BerufSpezialisierungRepository berufSpezialisierungRepository;
    private final KompetenzRepository kompetenzRepository;
    private final InteressensgebietRepository interessensgebietRepository;
    private final VoraussetzungRepository voraussetzungRepository;
    private final GeoLocationRepository geoLocationRepository;

    @GetMapping
    public String liste(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) String typ,
                        Model model) {
        Pageable pageable = PageRequest.of(page, 20);
        if (typ != null && !typ.isBlank()) {
            StelleTyp stelleTyp = StelleTyp.valueOf(typ.toUpperCase());
            model.addAttribute("seite", stelleService.findAllByTyp(stelleTyp, pageable));
            model.addAttribute("typFilter", stelleTyp);
        } else {
            model.addAttribute("seite", stelleService.findAll(pageable));
        }
        model.addAttribute("currentPage", page);
        return "stellen/liste";
    }

    @GetMapping("/neu")
    public String neu(Model model) {
        model.addAttribute("stelle", new Stelle());
        model.addAttribute("isNeu", true);
        model.addAttribute("alleInteressen", interessensgebietRepository.findAll());
        model.addAttribute("alleVoraussetzungen", voraussetzungRepository.findAll());
        model.addAttribute("bundeslaender", geoLocationRepository.findByEbeneOrderByName("BUNDESLAND"));
        model.addAttribute("aktiveAzModelle", Set.of());
        model.addAttribute("pflichtAzModelle", Set.of());
        return "stellen/formular";
    }

    @GetMapping("/{id}")
    public String bearbeiten(@PathVariable UUID id, Model model) {
        Stelle stelle = stelleService.findById(id);
        model.addAttribute("stelle", stelle);
        model.addAttribute("isNeu", false);
        if (stelle.getBerufSpezialisierungId() != null) {
            berufSpezialisierungRepository.findByIdWithPfad(stelle.getBerufSpezialisierungId())
                    .ifPresent(b -> model.addAttribute("berufName", b.getName()));
        }
        List<StelleService.KompetenzEintrag> kompetenzen = stelleService.findKompetenzen(id);
        model.addAttribute("stelleKompetenzen", kompetenzen);
        if (!kompetenzen.isEmpty()) {
            List<Integer> ids = kompetenzen.stream()
                    .map(StelleService.KompetenzEintrag::kompetenzId).toList();
            Map<Integer, String> namen = kompetenzRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(k -> k.getId(), k -> k.getName()));
            model.addAttribute("kompetenzNamen", namen);
        }
        model.addAttribute("alleInteressen", interessensgebietRepository.findAll());
        model.addAttribute("alleVoraussetzungen", voraussetzungRepository.findAll());
        model.addAttribute("bundeslaender", geoLocationRepository.findByEbeneOrderByName("BUNDESLAND"));
        addArbeitszeitModelle(model, stelle);
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
            @RequestParam(required = false) Integer berufSpezialisierungId,
            @RequestParam(required = false) String typ,
            @RequestParam(required = false) Set<Integer> interessenIds,
            @RequestParam(required = false) Set<Integer> voraussetzungIds,
            @RequestParam(required = false) List<Integer> kompetenzIds,
            @RequestParam(required = false) List<Boolean> pflichtFlags,
            @RequestParam(required = false) Integer geoLocationId,
            @RequestParam(required = false) List<String> arbeitszeitModelle,
            @RequestParam(required = false) List<Boolean> arbeitszeitPflicht) {

        Stelle s = stelleService.erstellen(buildRequest(titel, unternehmen, beschreibung,
                ortBezeichnung, ortLat, ortLon, berufSpezialisierungId, typ,
                interessenIds, voraussetzungIds, kompetenzIds, pflichtFlags,
                geoLocationId, arbeitszeitModelle, arbeitszeitPflicht));
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
            @RequestParam(required = false) Integer berufSpezialisierungId,
            @RequestParam(required = false) String typ,
            @RequestParam(required = false) Set<Integer> interessenIds,
            @RequestParam(required = false) Set<Integer> voraussetzungIds,
            @RequestParam(required = false) List<Integer> kompetenzIds,
            @RequestParam(required = false) List<Boolean> pflichtFlags,
            @RequestParam(required = false) Integer geoLocationId,
            @RequestParam(required = false) List<String> arbeitszeitModelle,
            @RequestParam(required = false) List<Boolean> arbeitszeitPflicht) {

        stelleService.aktualisieren(id, buildRequest(titel, unternehmen, beschreibung,
                ortBezeichnung, ortLat, ortLon, berufSpezialisierungId, typ,
                interessenIds, voraussetzungIds, kompetenzIds, pflichtFlags,
                geoLocationId, arbeitszeitModelle, arbeitszeitPflicht));
        return "redirect:/stellen/" + id;
    }

    @DeleteMapping("/{id}")
    @HxRequest
    public ResponseEntity<String> loeschen(@PathVariable UUID id) {
        stelleService.loeschen(id);
        return ResponseEntity.ok()
                .header("HX-Trigger", "{\"showToast\":\"Stelle gelöscht\"}")
                .header("HX-Redirect", "/stellen")
                .body("");
    }

    // ── HTMX fragments for StelleKompetenz ───────────────────────────────────

    @PostMapping("/{id}/kompetenzen")
    @HxRequest
    public String kompetenzHinzufuegen(
            @PathVariable UUID id,
            @RequestParam Integer kompetenzId,
            @RequestParam(defaultValue = "true") boolean pflicht,
            Model model) {

        StelleKompetenz sk = stelleService.kompetenzHinzufuegen(id, kompetenzId, pflicht);
        model.addAttribute("sk", sk);
        model.addAttribute("stelleId", id);
        kompetenzRepository.findById(kompetenzId)
                .ifPresent(k -> model.addAttribute("kompetenzName", k.getName()));
        return "stellen/kompetenz-fragment :: kompetenz-eintrag";
    }

    @DeleteMapping("/{stelleId}/kompetenzen/{kompetenzId}")
    @HxRequest
    public ResponseEntity<String> kompetenzEntfernen(
            @PathVariable UUID stelleId, @PathVariable Integer kompetenzId) {
        stelleService.kompetenzEntfernen(stelleId, kompetenzId);
        return ResponseEntity.ok().body("");
    }

    private StelleService.StelleRequest buildRequest(
            String titel, String unternehmen, String beschreibung,
            String ortBezeichnung, double ortLat, double ortLon,
            Integer berufSpezialisierungId, String typStr,
            Set<Integer> interessenIds, Set<Integer> voraussetzungIds,
            List<Integer> kompetenzIds, List<Boolean> pflichtFlags,
            Integer geoLocationId,
            List<String> arbeitszeitModelle, List<Boolean> arbeitszeitPflicht) {

        StelleTyp typ = (typStr != null && !typStr.isBlank())
                ? StelleTyp.valueOf(typStr.toUpperCase()) : StelleTyp.STANDARD;

        List<StelleService.KompetenzEintrag> kompetenzen = null;
        if (kompetenzIds != null) {
            kompetenzen = new java.util.ArrayList<>();
            for (int i = 0; i < kompetenzIds.size(); i++) {
                boolean pflicht = pflichtFlags != null && i < pflichtFlags.size()
                        ? pflichtFlags.get(i) : true;
                kompetenzen.add(new StelleService.KompetenzEintrag(kompetenzIds.get(i), pflicht));
            }
        }

        List<StelleService.ArbeitszeitEintrag> arbeitszeiten = null;
        if (arbeitszeitModelle != null && !arbeitszeitModelle.isEmpty()) {
            arbeitszeiten = new java.util.ArrayList<>();
            for (int i = 0; i < arbeitszeitModelle.size(); i++) {
                boolean pflicht = arbeitszeitPflicht != null && i < arbeitszeitPflicht.size()
                        ? arbeitszeitPflicht.get(i) : false;
                arbeitszeiten.add(new StelleService.ArbeitszeitEintrag(arbeitszeitModelle.get(i), pflicht));
            }
        }

        return new StelleService.StelleRequest(titel, unternehmen, beschreibung,
                ortBezeichnung, ortLat, ortLon, berufSpezialisierungId, typ,
                interessenIds, voraussetzungIds, kompetenzen, geoLocationId, arbeitszeiten);
    }

    /** HTMX: returns Bezirk <select> for the given Bundesland parent. */
    @GetMapping("/orte/bezirke")
    @HxRequest
    public String bezirkeDropdown(@RequestParam Integer parentId, Model model) {
        model.addAttribute("bezirke", geoLocationRepository.findByParentIdOrderByName(parentId));
        return "stellen/bezirk-fragment :: bezirk-select";
    }

    private void addArbeitszeitModelle(Model model, Stelle stelle) {
        Set<String> aktive = new HashSet<>();
        Set<String> pflicht = new HashSet<>();
        if (stelle.getArbeitszeiten() != null) {
            for (StelleArbeitszeit az : stelle.getArbeitszeiten()) {
                aktive.add(az.getId().getModell());
                if (Boolean.TRUE.equals(az.getPflicht())) {
                    pflicht.add(az.getId().getModell());
                }
            }
        }
        model.addAttribute("aktiveAzModelle", aktive);
        model.addAttribute("pflichtAzModelle", pflicht);
    }
}
