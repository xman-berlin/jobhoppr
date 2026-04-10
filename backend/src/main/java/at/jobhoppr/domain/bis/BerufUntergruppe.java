package at.jobhoppr.domain.bis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "beruf_untergruppe")
@Getter @Setter @NoArgsConstructor
public class BerufUntergruppe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obergruppe_id", nullable = false)
    private BerufObergruppe obergruppe;
}
