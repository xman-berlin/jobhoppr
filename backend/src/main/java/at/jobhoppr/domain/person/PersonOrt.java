package at.jobhoppr.domain.person;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "person_ort")
@Getter @Setter @NoArgsConstructor
public class PersonOrt {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "ort_rolle", nullable = false)
    private String ortRolle; // WOHNORT | ARBEITSORT

    @Column(name = "ort_typ", nullable = false)
    private String ortTyp; // GENAU | REGION

    @Column(nullable = false)
    private String bezeichnung;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lon;

    @Column(name = "umkreis_km", nullable = false)
    private Double umkreisKm;

    // Generated column — never written by JPA
    @Column(name = "standort", insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    private Object standort;
}
