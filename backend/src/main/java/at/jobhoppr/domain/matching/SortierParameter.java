package at.jobhoppr.domain.matching;

/** Kombination aus Sortierkriterium und -richtung. Default: Score absteigend. */
public record SortierParameter(SortierKriterium kriterium, SortierRichtung richtung) {

    public static final SortierParameter DEFAULT = new SortierParameter(
            SortierKriterium.SCORE, SortierRichtung.DESC);

    public static SortierParameter of(String kriteriumStr, String richtungStr) {
        SortierKriterium k;
        SortierRichtung r;
        try {
            k = SortierKriterium.valueOf(kriteriumStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            k = SortierKriterium.SCORE;
        }
        try {
            r = SortierRichtung.valueOf(richtungStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            r = SortierRichtung.DESC;
        }
        return new SortierParameter(k, r);
    }
}
