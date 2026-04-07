package at.jobhoppr.domain.matching;

import java.util.UUID;

/**
 * Projection of a single match result row returned by the CTE query.
 */
public record MatchResult(
        UUID targetId,
        String targetName,
        double berufScore,
        double kompetenzScore,
        double gesamtScore
) {
    public int gesamtScoreProzent() { return (int) Math.round(gesamtScore * 100); }
    public int kompetenzScoreProzent() { return (int) Math.round(kompetenzScore * 100); }
    public int berufScoreProzent() { return (int) Math.round(berufScore * 100); }
}
