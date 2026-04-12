-- V5__score_funktionen.sql
-- Alle Funktionen sind STABLE LANGUAGE SQL → PostgreSQL inlined sie in den aufrufenden Query.
-- Kein separater Function-Scan pro Kandidat — PostgreSQL behandelt es als Set-Operation.

-- OM: Berufsmatching auf BUG-Ebene
-- Spec: "Wenn Schnittmenge der Untergruppen > 0 → Score 1, sonst 0"
CREATE OR REPLACE FUNCTION match_beruf(p_id UUID, s_id UUID)
RETURNS FLOAT LANGUAGE SQL STABLE AS $$
  SELECT CASE WHEN EXISTS (
    SELECT 1
    FROM beruf_spezialisierung bp
    JOIN beruf_spezialisierung bs ON bp.untergruppe_id = bs.untergruppe_id
    WHERE bp.id = (SELECT beruf_spezialisierung_id FROM person WHERE id = p_id)
      AND bs.id = (SELECT beruf_spezialisierung_id FROM stelle  WHERE id = s_id)
  ) THEN 1.0 ELSE 0.0 END
$$;

-- SM: Kompetenzmatching mit Closure Table
-- Spec: "Wenn Person die exakte Kompetenz ODER einen Vorfahren hat → 100%, sonst 0%"
-- W(s): alle Vorfahren der Stelle-Kompetenz s (inkl. s selbst)
-- S(J): Kompetenzen der Person
CREATE OR REPLACE FUNCTION match_kompetenz(p_id UUID, s_id UUID)
RETURNS FLOAT LANGUAGE SQL STABLE AS $$
  SELECT COALESCE(AVG(
    CASE 
      WHEN EXISTS (
        SELECT 1 FROM person_kompetenz pk 
        WHERE pk.person_id = p_id 
          AND pk.kompetenz_id = sk.kompetenz_id
      ) THEN 1.0  -- Person hat die exakte Kompetenz
      WHEN EXISTS (
        SELECT 1 FROM kompetenz_closure cc
        JOIN person_kompetenz pk ON pk.kompetenz_id = cc.vorfahre_id
        WHERE cc.nachfahre_id = sk.kompetenz_id
          AND pk.person_id = p_id
      ) THEN 1.0  -- Person hat einen Vorfahren (Generalisierung)
      ELSE 0.0
    END
  ), 0.0)
  FROM stelle_kompetenz sk
  WHERE sk.stelle_id = s_id
$$;

-- FM: Interessenmatching für Lehrstellen
-- FM(J,O) = |F(J) ∩ F(O)| / |F(O)|
CREATE OR REPLACE FUNCTION match_interessen(p_id UUID, s_id UUID)
RETURNS FLOAT LANGUAGE SQL STABLE AS $$
  SELECT COALESCE(
    (SELECT COUNT(*)::FLOAT
     FROM person_interesse pi
     JOIN stelle_interesse si ON si.interessensgebiet_id = pi.interessensgebiet_id
     WHERE pi.person_id = p_id AND si.stelle_id = s_id)
    /
    NULLIF((SELECT COUNT(*)::FLOAT FROM stelle_interesse WHERE stelle_id = s_id), 0),
    0.0
  )
$$;

-- QM: Voraussetzungsmatching für Lehrstellen
-- QM(J,O) = |Q(J) ∩ Q(O)| / |Q(O)|
CREATE OR REPLACE FUNCTION match_voraussetzungen(p_id UUID, s_id UUID)
RETURNS FLOAT LANGUAGE SQL STABLE AS $$
  SELECT COALESCE(
    (SELECT COUNT(*)::FLOAT
     FROM person_voraussetzung pv
     JOIN stelle_voraussetzung sv ON sv.voraussetzung_id = pv.voraussetzung_id
     WHERE pv.person_id = p_id AND sv.stelle_id = s_id)
    /
    NULLIF((SELECT COUNT(*)::FLOAT FROM stelle_voraussetzung WHERE stelle_id = s_id), 0),
    0.0
  )
$$;
