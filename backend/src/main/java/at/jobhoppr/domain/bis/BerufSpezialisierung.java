package at.jobhoppr.domain.bis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "beruf_spezialisierung")
@Getter @Setter @NoArgsConstructor
public class BerufSpezialisierung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "untergruppe_id", nullable = false)
    private BerufUntergruppe untergruppe;

    @Column(name = "isco_code")
    private String iscoCode;

    /** Convenience: returns "Bereich > Obergruppe > Untergruppe > Name" label */
    public String getPfadLabel() {
        BerufUntergruppe ug = untergruppe;
        BerufObergruppe og = ug.getObergruppe();
        BerufBereich b = og.getBereich();
        return b.getName() + " > " + og.getName() + " > " + ug.getName() + " > " + name;
    }
}
