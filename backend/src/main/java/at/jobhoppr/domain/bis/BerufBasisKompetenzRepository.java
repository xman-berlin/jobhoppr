package at.jobhoppr.domain.bis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BerufBasisKompetenzRepository
        extends JpaRepository<BerufBasisKompetenz, BerufBasisKompetenz.BerufBasisKompetenzId> {

    @Query("""
        SELECT b
        FROM BerufBasisKompetenz b
        JOIN FETCH b.kompetenz k
        WHERE b.id.berufId = :berufId
        ORDER BY k.name
        """)
    List<BerufBasisKompetenz> findKompetenzenByBerufId(@Param("berufId") Integer berufId);

    @Query("""
        SELECT DISTINCT b.id.berufId
        FROM BerufBasisKompetenz b
        WHERE b.id.kompetenzId = :kompetenzId
        """)
    List<Integer> findBerufIdsByKompetenzId(@Param("kompetenzId") Integer kompetenzId);
}
