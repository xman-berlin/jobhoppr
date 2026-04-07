package at.jobhoppr.domain.geo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlzOrtRepository extends JpaRepository<PlzOrt, PlzOrt.PlzOrtId> {

    @Query("""
        SELECT p FROM PlzOrt p
        WHERE p.id.plz LIKE CONCAT(:q, '%')
           OR LOWER(p.id.ortName) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY p.id.plz, p.id.ortName
        LIMIT 20
        """)
    List<PlzOrt> suche(@Param("q") String q);
}
