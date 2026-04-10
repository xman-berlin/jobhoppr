package at.jobhoppr.domain.matching;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ein einzelnes Match-Ergebnis aus dem Matching-Query.
 * Enthält Score, Breakdown und Metadaten zur Anzeige im Frontend.
 */
public record MatchResult(
        UUID targetId,
        String targetName,
        double score,
        StelleTypInfo stelleTyp,
        OffsetDateTime erstelltAm,
        Breakdown breakdown
) {
    /** Score-Breakdown aus dem CROSS JOIN LATERAL (ein DB-Aufruf pro Kandidat). */
    public record Breakdown(double om, double sm, double fm, double qm) {}

    /** Minimale Typ-Info die vom Query zurückgegeben wird. */
    public enum StelleTypInfo { STANDARD, LEHRSTELLE }

    // ── Convenience-Methoden für Thymeleaf ──────────────────────────────────

    public int scoreProzent()              { return (int) Math.round(score * 100); }
    public int berufScoreProzent()         { return (int) Math.round(breakdown.om() * 100); }
    public int kompetenzScoreProzent()     { return (int) Math.round(breakdown.sm() * 100); }
    public int interessenScoreProzent()    { return (int) Math.round(breakdown.fm() * 100); }
    public int voraussetzungScoreProzent() { return (int) Math.round(breakdown.qm() * 100); }
}
