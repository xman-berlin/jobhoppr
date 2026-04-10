package at.jobhoppr.domain.stelle;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    @Column(name = "beruf_spezialisierung_id")
    private Integer berufSpezialisierungId;

    @Column(name = "geo_location_id")
    private Integer geoLocationId;

    /** STANDARD | LEHRSTELLE — stored as string in DB */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StelleTyp typ = StelleTyp.STANDARD;

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

    /** IDs der Interessensgebiete (stelle_interesse join table) */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stelle_interesse", joinColumns = @JoinColumn(name = "stelle_id"))
    @Column(name = "interessensgebiet_id")
    private Set<Integer> interessenIds = new LinkedHashSet<>();

    /** IDs der Voraussetzungen (stelle_voraussetzung join table) */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stelle_voraussetzung", joinColumns = @JoinColumn(name = "stelle_id"))
    @Column(name = "voraussetzung_id")
    private Set<Integer> voraussetzungIds = new LinkedHashSet<>();

    @PrePersist @PreUpdate
    void touch() { aktualisiertAm = OffsetDateTime.now(); }
}
