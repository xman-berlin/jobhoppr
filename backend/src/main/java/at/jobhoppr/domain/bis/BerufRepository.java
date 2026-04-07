package at.jobhoppr.domain.bis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BerufRepository extends JpaRepository<Beruf, Integer> {

    @Query("SELECT b FROM Beruf b WHERE LOWER(b.name) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY b.name")
    List<Beruf> findTop20ByNameContaining(@Param("q") String q);
}
