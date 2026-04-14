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

    @Query("SELECT p FROM PlzOrt p WHERE LOWER(p.id.ortName) LIKE :search OR LOWER(p.id.ortName) LIKE :searchNoAccent OR LOWER(REPLACE(REPLACE(REPLACE(p.id.ortName, 'ö','o'),'ä','a'),'ü','u')) LIKE :searchNoAccent OR LOWER(p.bezirk) LIKE :search OR LOWER(p.bezirk) LIKE :searchNoAccent OR LOWER(p.bundesland) LIKE :search OR p.id.plz LIKE :plzSearch ORDER BY p.id.plz, p.id.ortName")
    List<PlzOrt> sucheVolltext(@Param("search") String search, @Param("searchNoAccent") String searchNoAccent, @Param("plzSearch") String plzSearch);
}
