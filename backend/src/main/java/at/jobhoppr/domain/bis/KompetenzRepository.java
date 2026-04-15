package at.jobhoppr.domain.bis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KompetenzRepository extends JpaRepository<Kompetenz, Integer> {

    @Query("SELECT k FROM Kompetenz k WHERE LOWER(k.name) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY k.name")
    List<Kompetenz> findTop20ByNameContaining(@Param("q") String q);

    @Query("SELECT k FROM Kompetenz k WHERE k.parentId = :parentId AND k.id <> :excludeId ORDER BY k.name")
    List<Kompetenz> findByParentIdAndIdNot(@Param("parentId") Integer parentId, @Param("excludeId") Integer excludeId);
}
