package at.jobhoppr.domain.stelle;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stelle")
@Getter @Setter @NoArgsConstructor
public class Stelle {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String titel;

    private String unternehmen;
    private String beschreibung;

    @Column(name = "ort_bezeichnung", nullable = false)
    private String ortBezeichnung;

    @Column(name = "ort_lat", nullable = false)
    private Double ortLat;

    @Column(name = "ort_lon", nullable = false)
    private Double ortLon;

    @Column(name = "beruf_id")
    private Integer berufId;

    @Column(name = "erstellt_am", insertable = false, updatable = false)
    private OffsetDateTime erstelltAm;

    @Column(name = "aktualisiert_am")
    private OffsetDateTime aktualisiertAm;

    // Generated column — never written by JPA
    @Column(name = "standort", insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    private Object standort;

    @OneToMany(mappedBy = "stelle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<StelleKompetenz> kompetenzen = new ArrayList<>();

    @PrePersist @PreUpdate
    void touch() { aktualisiertAm = OffsetDateTime.now(); }
}
