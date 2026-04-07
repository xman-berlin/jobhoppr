package at.jobhoppr.domain.person;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "person_kompetenz")
@Getter @Setter @NoArgsConstructor
public class PersonKompetenz {

    @EmbeddedId
    private PersonKompetenzId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("personId")
    @JoinColumn(name = "person_id")
    private Person person;

    private String niveau; // GRUNDKENNTNISSE | FORTGESCHRITTEN | EXPERTE

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class PersonKompetenzId implements Serializable {
        @Column(name = "person_id")
        private UUID personId;
        @Column(name = "kompetenz_id")
        private Integer kompetenzId;
    }
}
