package at.jobhoppr.domain.bis;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
        SELECT s FROM BerufSpezialisierung s
        JOIN FETCH s.untergruppe ug
        JOIN FETCH ug.obergruppe og
        JOIN FETCH og.bereich
        ORDER BY og.bereich.name, s.name
        """,
        countQuery = "SELECT COUNT(s) FROM BerufSpezialisierung s")
    Page<BerufSpezialisierung> findAllOrderedByBereich(Pageable pageable);

    @Query("""
        SELECT s FROM BerufSpezialisierung s
        JOIN FETCH s.untergruppe ug
        JOIN FETCH ug.obergruppe og
        JOIN FETCH og.bereich
        WHERE s.id IN :ids
        ORDER BY og.bereich.name, s.name
        """)
    List<BerufSpezialisierung> findAllByIdInWithPfad(@Param("ids") List<Integer> ids);
}
