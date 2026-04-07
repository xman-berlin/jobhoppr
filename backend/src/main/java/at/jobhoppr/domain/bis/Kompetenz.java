package at.jobhoppr.domain.bis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bis_kompetenz")
@Getter @Setter @NoArgsConstructor
public class Kompetenz {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String name;

    private String bereich;

    @Column(name = "parent_id")
    private Integer parentId;

    private String typ; // FACHLICH | UEBERFACHLICH | ZERTIFIKAT
}
