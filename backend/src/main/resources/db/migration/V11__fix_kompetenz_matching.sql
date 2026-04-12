-- V11__fix_kompetenz_matching.sql
-- Bugfix: Kompetenz-Matching bewertet falsch (0.5 statt 1.0) wenn Person exakte Kompetenz hat
-- Problem: Alte Formel prüfte, ob ALLE Vorfahren in kompetenz_closure vorhanden sind
-- Lösung: Wenn Person die exakte Kompetenz ODER einen Vorfahren (Generalisierung) hat → 100%

CREATE OR REPLACE FUNCTION match_kompetenz(p_id UUID, s_id UUID)
RETURNS FLOAT LANGUAGE SQL STABLE AS $$
  SELECT COALESCE(AVG(
    CASE 
      WHEN EXISTS (
        SELECT 1 FROM person_kompetenz pk 
        WHERE pk.person_id = p_id 
          AND pk.kompetenz_id = sk.kompetenz_id
      ) THEN 1.0
      WHEN EXISTS (
        SELECT 1 FROM kompetenz_closure cc
        JOIN person_kompetenz pk ON pk.kompetenz_id = cc.vorfahre_id
        WHERE cc.nachfahre_id = sk.kompetenz_id
          AND pk.person_id = p_id
      ) THEN 1.0
      ELSE 0.0
    END
  ), 0.0)
  FROM stelle_kompetenz sk
  WHERE sk.stelle_id = s_id
$$;