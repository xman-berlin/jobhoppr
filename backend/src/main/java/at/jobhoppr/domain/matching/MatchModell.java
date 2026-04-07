package at.jobhoppr.domain.matching;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "match_modell")
@Getter @Setter @NoArgsConstructor
public class MatchModell {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Boolean aktiv = false;

    @Column(name = "geo_aktiv", nullable = false)
    private Boolean geoAktiv = true;

    @Column(name = "beruf_filter_strikt", nullable = false)
    private Boolean berufFilterStrikt = false;

    @Column(name = "gewicht_kompetenz", nullable = false)
    private Double gewichtKompetenz = 0.75;

    @Column(name = "gewicht_beruf", nullable = false)
    private Double gewichtBeruf = 0.25;

    @Column(name = "erstellt_am", insertable = false, updatable = false)
    private OffsetDateTime erstelltAm;
}
