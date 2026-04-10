package at.jobhoppr.domain.bis;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pflegt die {@code kompetenz_closure}-Tabelle bei Änderungen an der {@code bis_kompetenz}-Hierarchie.
 * <p>
 * Die Closure-Table speichert alle Vorfahren-Nachfahren-Paare (inkl. Self-Paare mit tiefe=0)
 * und ermöglicht schnelle W(s)-Abfragen im Matching ohne rekursiven CTE.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class KompetenzClosureService {

    private final JdbcTemplate jdbc;

    /**
     * Fügt einen neuen Kompetenz-Knoten in die Closure-Table ein.
     * <p>
     * Algorithmus: Alle Vorfahren von {@code parentId} (inkl. {@code parentId} selbst)
     * werden mit {@code kompetenzId} als Nachfahre verknüpft; zusätzlich wird das Self-Paar
     * ({@code kompetenzId} → {@code kompetenzId}, tiefe=0) eingefügt.
     * </p>
     *
     * @param kompetenzId ID des neu eingefügten Knotens
     * @param parentId    ID des Elternknotens (bereits in der Closure vorhanden)
     */
    @Transactional
    public void einfuegenKind(int kompetenzId, int parentId) {
        jdbc.update("""
            INSERT INTO kompetenz_closure (vorfahre_id, nachfahre_id, tiefe)
            SELECT c.vorfahre_id, ?, c.tiefe + 1
            FROM kompetenz_closure c
            WHERE c.nachfahre_id = ?
            UNION ALL
            SELECT ?, ?, 0
            """,
            kompetenzId, parentId, kompetenzId, kompetenzId);
    }

    /**
     * Löscht einen Kompetenz-Knoten und alle seine Nachfahren aus der Closure-Table.
     * <p>
     * Alle Zeilen, bei denen {@code kompetenzId} als Nachfahre in einem Teilbaum
     * erscheint, werden entfernt. Die {@code ON DELETE CASCADE}-Constraint auf
     * {@code bis_kompetenz} entfernt danach die eigentlichen Knoten-Zeilen.
     * </p>
     *
     * @param kompetenzId ID des zu löschenden Knotens (inkl. gesamtem Teilbaum)
     */
    @Transactional
    public void loeschen(int kompetenzId) {
        jdbc.update("""
            DELETE FROM kompetenz_closure
            WHERE nachfahre_id IN (
                SELECT nachfahre_id
                FROM kompetenz_closure
                WHERE vorfahre_id = ?
            )
            """,
            kompetenzId);
    }

    /**
     * Verschiebt einen Kompetenz-Knoten (inkl. Teilbaum) zu einem neuen Elternknoten.
     * <p>
     * Algorithmus:
     * <ol>
     *   <li>Entfernt alle Closure-Zeilen, die einen Vorfahren <em>von außerhalb</em>
     *       des Teilbaums mit einem Knoten <em>innerhalb</em> des Teilbaums verbinden.</li>
     *   <li>Fügt neue Verbindungen von allen Vorfahren des neuen Elternknotens zu allen
     *       Knoten im Teilbaum ein.</li>
     * </ol>
     * </p>
     *
     * @param kompetenzId  ID des zu verschiebenden Knotens (Teilbaum-Wurzel)
     * @param newParentId  ID des neuen Elternknotens
     */
    @Transactional
    public void verschieben(int kompetenzId, int newParentId) {
        // 1. Alte Vorfahren-Verbindungen entfernen (außerhalb des verschobenen Teilbaums)
        jdbc.update("""
            DELETE FROM kompetenz_closure
            WHERE nachfahre_id IN (
                SELECT nachfahre_id FROM kompetenz_closure WHERE vorfahre_id = ?
            )
            AND vorfahre_id NOT IN (
                SELECT nachfahre_id FROM kompetenz_closure WHERE vorfahre_id = ?
            )
            """,
            kompetenzId, kompetenzId);

        // 2. Neue Vorfahren-Verbindungen für alle Knoten im Teilbaum einfügen
        jdbc.update("""
            INSERT INTO kompetenz_closure (vorfahre_id, nachfahre_id, tiefe)
            SELECT p.vorfahre_id, c.nachfahre_id, p.tiefe + c.tiefe + 1
            FROM kompetenz_closure p
            CROSS JOIN kompetenz_closure c
            WHERE p.nachfahre_id = ?
              AND c.vorfahre_id  = ?
            ON CONFLICT DO NOTHING
            """,
            newParentId, kompetenzId);
    }
}
