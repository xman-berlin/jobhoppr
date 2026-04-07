package at.jobhoppr.domain.stelle;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "stelle_kompetenz")
@Getter @Setter @NoArgsConstructor
public class StelleKompetenz {

    @EmbeddedId
    private StelleKompetenzId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("stelleId")
    @JoinColumn(name = "stelle_id")
    private Stelle stelle;

    @Column(nullable = false)
    private Boolean pflicht = true;

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class StelleKompetenzId implements Serializable {
        @Column(name = "stelle_id")
        private UUID stelleId;
        @Column(name = "kompetenz_id")
        private Integer kompetenzId;
    }
}
