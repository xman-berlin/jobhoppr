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
    public String personMatches(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "score") String sortBy,
            @RequestParam(defaultValue = "desc")  String sortDir,
            Model model) {
        SortierParameter sort = SortierParameter.of(sortBy, sortDir);
        model.addAttribute("person",     personService.findById(id));
        model.addAttribute("ergebnisse", matchService.matchenFuerPerson(id, sort));
        model.addAttribute("modell",     matchModellService.getAktives());
        model.addAttribute("sortBy",     sortBy);
        model.addAttribute("sortDir",    sortDir);
        return "personen/matches";
    }

    @GetMapping("/stellen/{id}/matches")
    public String stelleMatches(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "score") String sortBy,
            @RequestParam(defaultValue = "desc")  String sortDir,
            Model model) {
        SortierParameter sort = SortierParameter.of(sortBy, sortDir);
        model.addAttribute("stelle",     stelleService.findById(id));
        model.addAttribute("ergebnisse", matchService.matchenFuerStelle(id, sort));
        model.addAttribute("modell",     matchModellService.getAktives());
        model.addAttribute("sortBy",     sortBy);
        model.addAttribute("sortDir",    sortDir);
        return "stellen/matches";
    }
}

