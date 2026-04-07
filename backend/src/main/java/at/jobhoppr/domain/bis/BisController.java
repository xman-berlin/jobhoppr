package at.jobhoppr.domain.bis;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/bis")
@RequiredArgsConstructor
public class BisController {

    private final BerufRepository berufRepository;
    private final KompetenzRepository kompetenzRepository;

    @GetMapping("/berufe")
    @HxRequest
    public String berufeVorschlaege(@RequestParam(defaultValue = "") String q, Model model) {
        if (q.isBlank()) {
            model.addAttribute("berufe", java.util.List.of());
        } else {
            model.addAttribute("berufe",
                berufRepository.findTop20ByNameContaining(q).stream()
                    .map(b -> new BisRestController.BerufDto(b.getId(), b.getName(), b.getBereich()))
                    .toList());
        }
        return "bis/berufe-vorschlaege :: vorschlaege";
    }

    @GetMapping("/kompetenzen")
    @HxRequest
    public String kompetenzenVorschlaege(@RequestParam(defaultValue = "") String q, Model model) {
        if (q.isBlank()) {
            model.addAttribute("kompetenzen", java.util.List.of());
        } else {
            model.addAttribute("kompetenzen",
                kompetenzRepository.findTop20ByNameContaining(q).stream()
                    .map(k -> new BisRestController.KompetenzDto(k.getId(), k.getName(), k.getBereich(), k.getTyp()))
                    .toList());
        }
        return "bis/kompetenzen-vorschlaege :: vorschlaege";
    }
}
