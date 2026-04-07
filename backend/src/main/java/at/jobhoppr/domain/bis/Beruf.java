package at.jobhoppr.domain.bis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bis_beruf")
@Getter @Setter @NoArgsConstructor
public class Beruf {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String name;

    private String bereich;

    @Column(name = "isco_code")
    private String iscoCode;
}
