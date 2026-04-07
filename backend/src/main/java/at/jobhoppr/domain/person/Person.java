package at.jobhoppr.domain.person;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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

    @Column(name = "beruf_id")
    private Integer berufId;

    @Column(name = "erstellt_am", insertable = false, updatable = false)
    private OffsetDateTime erstelltAm;

    @Column(name = "aktualisiert_am")
    private OffsetDateTime aktualisiertAm;

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PersonOrt> orte = new ArrayList<>();

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<PersonKompetenz> kompetenzen = new HashSet<>();

    @PrePersist @PreUpdate
    void touch() { aktualisiertAm = OffsetDateTime.now(); }

    public String getVollerName() { return vorname + " " + nachname; }
}
