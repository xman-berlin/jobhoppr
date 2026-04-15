package at.jobhoppr.domain.bis;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/bis")
@RequiredArgsConstructor
public class BisExplorerController {

    private static final int PAGE_SIZE = 20;

    private final BerufSpezialisierungRepository berufRepo;
    private final KompetenzRepository kompetenzRepo;
    private final BerufBasisKompetenzRepository basisKompetenzRepo;

    // ── Vollseite ─────────────────────────────────────────────────────────────

    @GetMapping
    public String index(Model model) {
        Page<BerufSpezialisierung> seite = berufRepo.findAllOrderedByBereich(PageRequest.of(0, PAGE_SIZE));
        model.addAttribute("berufeNachBereich", gruppiereNachBereich(seite.getContent(), Map.of()));
        model.addAttribute("hatMehr", seite.hasNext());
        model.addAttribute("naechsteSeite", 1);
        return "bis/index";
    }

    // ── HTMX: Pagination Default-Liste ────────────────────────────────────────

    @GetMapping("/berufe-liste")
    @HxRequest
    public String berufeListe(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<BerufSpezialisierung> seite = berufRepo.findAllOrderedByBereich(PageRequest.of(page, PAGE_SIZE));
        model.addAttribute("berufeNachBereich", gruppiereNachBereich(seite.getContent(), Map.of()));
        model.addAttribute("hatMehr", seite.hasNext());
        model.addAttribute("naechsteSeite", page + 1);
        return "bis/beruf-liste-fragment :: liste";
    }

    // ── HTMX: Suche ───────────────────────────────────────────────────────────

    @GetMapping("/suche")
    @HxRequest
    public String suche(@RequestParam(defaultValue = "") String q, Model model) {
        if (q.isBlank()) {
            // Zurück zur Default-Liste
            Page<BerufSpezialisierung> seite = berufRepo.findAllOrderedByBereich(PageRequest.of(0, PAGE_SIZE));
            model.addAttribute("berufeNachBereich", gruppiereNachBereich(seite.getContent(), Map.of()));
            model.addAttribute("hatMehr", seite.hasNext());
            model.addAttribute("naechsteSeite", 1);
            return "bis/beruf-liste-fragment :: liste";
        }

        // 1. Direkte Beruf-Treffer
        List<BerufSpezialisierung> berufTreffer = berufRepo.findTop20ByNameContaining(q);

        // 2. Kompetenz-Treffer → zugehörige Berufe ermitteln
        List<BisRestController.KompetenzDto> kompetenzTreffer = kompetenzRepo.findTop20ByNameContaining(q).stream()
                .map(k -> new BisRestController.KompetenzDto(k.getId(), k.getName(), k.getBereich(), k.getTyp()))
                .toList();

        // Für jeden Kompetenz-Treffer: betroffene Beruf-IDs sammeln
        Map<Integer, List<BisRestController.KompetenzDto>> berufIdZuKompetenzen = new LinkedHashMap<>();
        for (var kDto : kompetenzTreffer) {
            List<Integer> berufIds = basisKompetenzRepo.findBerufIdsByKompetenzId(kDto.id());
            for (Integer bid : berufIds) {
                berufIdZuKompetenzen.computeIfAbsent(bid, k -> new ArrayList<>()).add(kDto);
            }
        }

        // Berufe via Kompetenz laden (die noch nicht in berufTreffer sind)
        List<Integer> direkteIds = berufTreffer.stream().map(BerufSpezialisierung::getId).toList();
        List<Integer> viaKompetenzIds = berufIdZuKompetenzen.keySet().stream()
                .filter(id -> !direkteIds.contains(id))
                .limit(20)
                .toList();
        List<BerufSpezialisierung> viaKompetenzBerufe = viaKompetenzIds.isEmpty()
                ? List.of()
                : berufRepo.findAllByIdInWithPfad(viaKompetenzIds);

        // Zusammenführen: direkte Treffer (keine Kompetenzen aufgeklappt) + Kompetenz-Treffer
        Map<Integer, List<BisRestController.KompetenzDto>> alleKompetenzen = new LinkedHashMap<>(berufIdZuKompetenzen);

        List<BerufSpezialisierung> alleBerufe = new ArrayList<>(berufTreffer);
        alleBerufe.addAll(viaKompetenzBerufe);

        model.addAttribute("berufeNachBereich", gruppiereNachBereich(alleBerufe, alleKompetenzen));
        model.addAttribute("hatMehr", false);
        model.addAttribute("naechsteSeite", 0);
        return "bis/beruf-liste-fragment :: liste";
    }

    // ── HTMX: Kompetenzen Inline (Accordion lazy-load) ───────────────────────

    @GetMapping("/beruf/{id}/kompetenzen-inline")
    @HxRequest
    public String kompetenzInline(@PathVariable Integer id, Model model) {
        var kompetenzen = basisKompetenzRepo.findKompetenzenByBerufId(id);
        model.addAttribute("basisKompetenzen",
                kompetenzen.stream().filter(b -> "basis".equals(b.getTyp()))
                        .map(b -> new BisRestController.KompetenzDto(b.getKompetenz().getId(), b.getKompetenz().getName(), b.getKompetenz().getBereich(), b.getKompetenz().getTyp()))
                        .toList());
        model.addAttribute("fachKompetenzen",
                kompetenzen.stream().filter(b -> "fach".equals(b.getTyp()))
                        .map(b -> new BisRestController.KompetenzDto(b.getKompetenz().getId(), b.getKompetenz().getName(), b.getKompetenz().getBereich(), b.getKompetenz().getTyp()))
                        .toList());
        return "bis/kompetenzen-inline-fragment :: inhalt";
    }

    // ── HTMX: Beruf-Detail ────────────────────────────────────────────────────

    @GetMapping("/beruf/{id}")
    @HxRequest
    public String berufDetail(@PathVariable Integer id, Model model) {
        var beruf = berufRepo.findByIdWithPfad(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var kompetenzen = basisKompetenzRepo.findKompetenzenByBerufId(id);
        model.addAttribute("beruf", beruf);
        model.addAttribute("basisKompetenzen",
                kompetenzen.stream().filter(b -> "basis".equals(b.getTyp()))
                        .map(b -> new BisRestController.KompetenzDto(b.getKompetenz().getId(), b.getKompetenz().getName(), b.getKompetenz().getBereich(), b.getKompetenz().getTyp()))
                        .toList());
        model.addAttribute("fachKompetenzen",
                kompetenzen.stream().filter(b -> "fach".equals(b.getTyp()))
                        .map(b -> new BisRestController.KompetenzDto(b.getKompetenz().getId(), b.getKompetenz().getName(), b.getKompetenz().getBereich(), b.getKompetenz().getTyp()))
                        .toList());
        return "bis/beruf-detail :: detail";
    }

    // ── HTMX: Kompetenz-Detail ────────────────────────────────────────────────

    @GetMapping("/kompetenz/{id}")
    @HxRequest
    public String kompetenzDetail(@PathVariable Integer id, Model model) {
        var kompetenz = kompetenzRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<Kompetenz> verwandte = kompetenz.getParentId() != null
                ? kompetenzRepo.findByParentIdAndIdNot(kompetenz.getParentId(), id)
                : List.of();
        model.addAttribute("kompetenz", kompetenz);
        model.addAttribute("verwandte", verwandte);
        return "bis/kompetenz-detail :: detail";
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    /** Gruppiert Berufe nach BerufBereich-Name, erhält dabei die Sortierung. */
    private Map<String, List<BerufMitKompetenzenDto>> gruppiereNachBereich(
            List<BerufSpezialisierung> berufe,
            Map<Integer, List<BisRestController.KompetenzDto>> matchKompetenzen) {
        return berufe.stream()
                .map(b -> new BerufMitKompetenzenDto(
                        b.getId(),
                        b.getName(),
                        b.getPfadLabel(),
                        b.getUntergruppe().getObergruppe().getBereich().getName(),
                        matchKompetenzen.getOrDefault(b.getId(), List.of())
                ))
                .collect(Collectors.groupingBy(
                        BerufMitKompetenzenDto::bereich,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record BerufMitKompetenzenDto(
            Integer id,
            String name,
            String pfad,
            String bereich,
            List<BisRestController.KompetenzDto> matchKompetenzen
    ) {}
}
