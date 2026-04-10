package at.jobhoppr.domain.bis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BerufSpezialisierungRepository extends JpaRepository<BerufSpezialisierung, Integer> {

    @Query("""
        SELECT s FROM BerufSpezialisierung s
        JOIN FETCH s.untergruppe ug
        JOIN FETCH ug.obergruppe og
        JOIN FETCH og.bereich
        WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY s.name
        LIMIT 20
        """)
    List<BerufSpezialisierung> findTop20ByNameContaining(@Param("q") String q);

    @Query("""
        SELECT s FROM BerufSpezialisierung s
        JOIN FETCH s.untergruppe ug
        JOIN FETCH ug.obergruppe og
        JOIN FETCH og.bereich
        WHERE s.id = :id
        """)
    java.util.Optional<BerufSpezialisierung> findByIdWithPfad(@Param("id") Integer id);
}
