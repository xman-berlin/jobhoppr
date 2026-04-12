package at.jobhoppr.domain.geo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "geo_location")
@Getter @Setter @NoArgsConstructor
public class GeoLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    /** ORT | BEZIRK | BUNDESLAND */
    @Column(nullable = false)
    private String ebene;

    @Column(name = "parent_id", insertable = false, updatable = false)
    private Integer parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private GeoLocation parent;

    private Double lat;
    private Double lon;
}
