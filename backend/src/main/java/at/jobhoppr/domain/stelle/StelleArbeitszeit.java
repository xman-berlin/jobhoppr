package at.jobhoppr.domain.stelle;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stelle_arbeitszeit")
@Getter @Setter @NoArgsConstructor
public class StelleArbeitszeit {

    @EmbeddedId
    private StelleArbeitszeitId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("stelleId")
    @JoinColumn(name = "stelle_id")
    private Stelle stelle;

    @Column(nullable = false)
    private Boolean pflicht = false;

    public StelleArbeitszeit(Stelle stelle, String modell, boolean pflicht) {
        this.stelle = stelle;
        this.id = new StelleArbeitszeitId(stelle.getId(), modell);
        this.pflicht = pflicht;
    }

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class StelleArbeitszeitId implements java.io.Serializable {
        @Column(name = "stelle_id")
        private java.util.UUID stelleId;

        @Column(name = "modell")
        private String modell;
    }
}
