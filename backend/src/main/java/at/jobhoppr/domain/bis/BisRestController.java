package at.jobhoppr.domain.bis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/bis")
@RequiredArgsConstructor
public class BisRestController {

    private final BerufSpezialisierungRepository berufSpezialisierungRepository;
    private final KompetenzRepository kompetenzRepository;
    private final KompetenzClosureService kompetenzClosureService;

    @GetMapping("/berufe")
    public List<BerufDto> berufe(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return List.of();
        return berufSpezialisierungRepository.findTop20ByNameContaining(q).stream()
                .map(b -> new BerufDto(b.getId(), b.getName(), b.getPfadLabel()))
                .toList();
    }

    @GetMapping("/kompetenzen")
    public List<KompetenzDto> kompetenzen(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return List.of();
        return kompetenzRepository.findTop20ByNameContaining(q).stream()
                .map(k -> new KompetenzDto(k.getId(), k.getName(), k.getBereich(), k.getTyp()))
                .toList();
    }

    /** Neue Kompetenz anlegen — aktualisiert automatisch die Closure-Table. */
    @PostMapping("/kompetenzen")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public KompetenzDto kompetenzAnlegen(@Valid @RequestBody KompetenzCreateRequest req) {
        Kompetenz k = new Kompetenz();
        k.setName(req.name());
        k.setBereich(req.bereich());
        k.setTyp(req.typ());
        k.setParentId(req.parentId());
        kompetenzRepository.save(k);
        if (req.parentId() != null) {
            kompetenzClosureService.einfuegenKind(k.getId(), req.parentId());
        } else {
            // Wurzelknoten: nur Self-Paar
            kompetenzClosureService.einfuegenKind(k.getId(), k.getId());
        }
        return new KompetenzDto(k.getId(), k.getName(), k.getBereich(), k.getTyp());
    }

    /** Kompetenz umbenennen oder zu neuem Elternknoten verschieben. */
    @PutMapping("/kompetenzen/{id}")
    @Transactional
    public KompetenzDto kompetenzAktualisieren(@PathVariable int id,
                                               @Valid @RequestBody KompetenzUpdateRequest req) {
        Kompetenz k = kompetenzRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kompetenz nicht gefunden"));
        k.setName(req.name());
        k.setBereich(req.bereich());
        k.setTyp(req.typ());
        if (req.parentId() != null && !req.parentId().equals(k.getParentId())) {
            kompetenzClosureService.verschieben(id, req.parentId());
            k.setParentId(req.parentId());
        }
        kompetenzRepository.save(k);
        return new KompetenzDto(k.getId(), k.getName(), k.getBereich(), k.getTyp());
    }

    /** Kompetenz löschen — entfernt Closure-Einträge für den gesamten Teilbaum. */
    @DeleteMapping("/kompetenzen/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void kompetenzLoeschen(@PathVariable int id) {
        if (!kompetenzRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Kompetenz nicht gefunden");
        }
        kompetenzClosureService.loeschen(id);
        kompetenzRepository.deleteById(id);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record BerufDto(Integer id, String name, String pfad) {}
    public record KompetenzDto(Integer id, String name, String bereich, String typ) {}

    public record KompetenzCreateRequest(
        @NotBlank String name,
        String bereich,
        String typ,
        Integer parentId
    ) {}

    public record KompetenzUpdateRequest(
        @NotBlank String name,
        String bereich,
        String typ,
        Integer parentId
    ) {}
}
