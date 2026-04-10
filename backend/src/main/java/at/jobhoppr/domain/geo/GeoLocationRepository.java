package at.jobhoppr.domain.geo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeoLocationRepository extends JpaRepository<GeoLocation, Integer> {

    List<GeoLocation> findByEbeneOrderByName(String ebene);
}
