package at.jobhoppr.domain.bis;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bis")
@RequiredArgsConstructor
public class BisRestController {

    private final BerufRepository berufRepository;
    private final KompetenzRepository kompetenzRepository;

    @GetMapping("/berufe")
    public List<BerufDto> berufe(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return List.of();
        return berufRepository.findTop20ByNameContaining(q).stream()
                .map(b -> new BerufDto(b.getId(), b.getName(), b.getBereich()))
                .toList();
    }

    @GetMapping("/kompetenzen")
    public List<KompetenzDto> kompetenzen(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return List.of();
        return kompetenzRepository.findTop20ByNameContaining(q).stream()
                .map(k -> new KompetenzDto(k.getId(), k.getName(), k.getBereich(), k.getTyp()))
                .toList();
    }

    public record BerufDto(Integer id, String name, String bereich) {}
    public record KompetenzDto(Integer id, String name, String bereich, String typ) {}
}
