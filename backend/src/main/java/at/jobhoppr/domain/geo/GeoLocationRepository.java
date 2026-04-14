package at.jobhoppr.domain.geo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GeoLocationRepository extends JpaRepository<GeoLocation, Integer> {

    List<GeoLocation> findByEbeneOrderByName(String ebene);

    List<GeoLocation> findByParentIdOrderByName(Integer parentId);

    List<GeoLocation> findByEbeneAndParentIsNullOrderByName(String ebene);

    List<GeoLocation> findByEbeneAndNameLikeIgnoreCaseOrderByName(String ebene, String namePattern);

    @Query("SELECT g FROM GeoLocation g LEFT JOIN FETCH g.parent WHERE g.ebene = :ebene AND LOWER(g.name) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY g.name")
    List<GeoLocation> findByEbeneAndNameWithParent(@Param("ebene") String ebene, @Param("name") String name);
}
