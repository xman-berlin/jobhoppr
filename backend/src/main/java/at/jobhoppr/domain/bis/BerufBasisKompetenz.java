package at.jobhoppr.domain.bis;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "beruf_basis_kompetenz")
@Getter @Setter @NoArgsConstructor
public class BerufBasisKompetenz {

    @EmbeddedId
    private BerufBasisKompetenzId id;

    @Column(name = "typ", nullable = false)
    private String typ;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kompetenz_id", insertable = false, updatable = false)
    private Kompetenz kompetenz;

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class BerufBasisKompetenzId implements Serializable {
        @Column(name = "beruf_id")
        private Integer berufId;
        @Column(name = "kompetenz_id")
        private Integer kompetenzId;
    }
}
