package at.jobhoppr.config;

import at.jobhoppr.domain.person.PersonRepository;
import at.jobhoppr.domain.stelle.StelleRepository;
import at.jobhoppr.domain.stelle.StelleTyp;
import at.jobhoppr.domain.bis.BerufRepository;
import at.jobhoppr.domain.bis.KompetenzRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class IndexController {

    private final PersonRepository    personRepository;
    private final StelleRepository    stelleRepository;
    private final BerufRepository     berufRepository;
    private final KompetenzRepository kompetenzRepository;

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("anzahlPersonen",     personRepository.count());
        model.addAttribute("anzahlStellen",      stelleRepository.count());
        model.addAttribute("anzahlLehrstellen",  stelleRepository.countByTyp(StelleTyp.LEHRSTELLE));
        model.addAttribute("anzahlBerufe",       berufRepository.count());
        model.addAttribute("anzahlKompetenzen",  kompetenzRepository.count());
        return "index";
    }
}
