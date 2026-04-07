package at.jobhoppr.domain.geo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bundesland")
@Getter @Setter @NoArgsConstructor
public class Bundesland {

    @Id
    private String kuerzel;

    @Column(nullable = false)
    private String name;

    @Column(name = "centroid_lat", nullable = false)
    private Double centroidLat;

    @Column(name = "centroid_lon", nullable = false)
    private Double centroidLon;

    @Column(name = "umkreis_km", nullable = false)
    private Double umkreisKm;
}
