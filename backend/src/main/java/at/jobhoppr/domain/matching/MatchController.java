package at.jobhoppr.domain.matching;

import at.jobhoppr.domain.stelle.StelleService;
import at.jobhoppr.domain.person.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final PersonService personService;
    private final StelleService stelleService;
    private final MatchModellService matchModellService;

    @GetMapping("/personen/{id}/matches")
    public String personMatches(@PathVariable UUID id, Model model) {
        model.addAttribute("person", personService.findById(id));
        model.addAttribute("ergebnisse", matchService.matchenFuerPerson(id));
        model.addAttribute("modell", matchModellService.getAktives());
        return "personen/matches";
    }

    @GetMapping("/stellen/{id}/matches")
    public String stelleMatches(@PathVariable UUID id, Model model) {
        model.addAttribute("stelle", stelleService.findById(id));
        model.addAttribute("ergebnisse", matchService.matchenFuerStelle(id));
        model.addAttribute("modell", matchModellService.getAktives());
        return "stellen/matches";
    }
}
