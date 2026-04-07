package at.jobhoppr.config;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResource(NoResourceFoundException ex) {
        // Static resources (favicon.ico etc.) — no model needed, just 404
        return "fehler";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(EntityNotFoundException ex, Model model) {
        log.warn("Nicht gefunden: {}", ex.getMessage());
        model.addAttribute("fehler", ex.getMessage());
        model.addAttribute("status", 404);
        return "fehler";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException ex, Model model) {
        log.warn("Ungültige Anfrage: {}", ex.getMessage());
        model.addAttribute("fehler", ex.getMessage());
        model.addAttribute("status", 400);
        return "fehler";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneral(Exception ex, Model model) {
        log.error("Unerwarteter Fehler", ex);
        model.addAttribute("fehler", "Ein unerwarteter Fehler ist aufgetreten.");
        model.addAttribute("status", 500);
        return "fehler";
    }
}
