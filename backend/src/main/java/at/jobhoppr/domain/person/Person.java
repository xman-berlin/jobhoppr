package at.jobhoppr.domain.person;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "person")
@Getter @Setter @NoArgsConstructor
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String vorname;

    @Column(nullable = false)
    private String nachname;

    private String email;

    @Column(name = "beruf_spezialisierung_id")
    private Integer berufSpezialisierungId;

    // KO-Felder
    @Column(name = "vermittlungspost", nullable = false)
    private Boolean vermittlungspost = true;

    @Column(name = "max_bewegungen", nullable = false)
    private Integer maxBewegungen = 999;

    @Column(name = "sucht_lehrstelle", nullable = false)
    private Boolean suchtLehrstelle = false;

    @Column(name = "erstellt_am", insertable = false, updatable = false)
    private OffsetDateTime erstelltAm;

    @Column(name = "aktualisiert_am")
    private OffsetDateTime aktualisiertAm;

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PersonOrt> orte = new ArrayList<>();

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private Set<PersonKompetenz> kompetenzen = new HashSet<>();

    /** IDs der Interessensgebiete (person_interesse join table) */
    @ElementCollection
    @CollectionTable(name = "person_interesse", joinColumns = @JoinColumn(name = "person_id"))
    @Column(name = "interessensgebiet_id")
    private Set<Integer> interessenIds = new LinkedHashSet<>();

    /** IDs der Voraussetzungen (person_voraussetzung join table) */
    @ElementCollection
    @CollectionTable(name = "person_voraussetzung", joinColumns = @JoinColumn(name = "person_id"))
    @Column(name = "voraussetzung_id")
    private Set<Integer> voraussetzungIds = new LinkedHashSet<>();

    /** Ausgeschlossene Arbeitszeitmodelle (person_arbeitszeit_ausschluss join table) */
    @ElementCollection
    @CollectionTable(name = "person_arbeitszeit_ausschluss", joinColumns = @JoinColumn(name = "person_id"))
    @Column(name = "modell")
    private Set<String> arbeitszeitAusschluesse = new LinkedHashSet<>();

    @PrePersist @PreUpdate
    void touch() { aktualisiertAm = OffsetDateTime.now(); }

    public String getVollerName() { return vorname + " " + nachname; }
}
