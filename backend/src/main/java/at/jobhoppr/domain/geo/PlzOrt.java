package at.jobhoppr.domain.geo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plz_ort")
@Getter @Setter @NoArgsConstructor
public class PlzOrt {

    @EmbeddedId
    private PlzOrtId id;

    @Column(nullable = false)
    private String bundesland;

    private String bezirk;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lon;

    @Embeddable
    public record PlzOrtId(String plz, @Column(name = "ort_name") String ortName)
            implements java.io.Serializable {}
}
