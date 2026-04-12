-- V7__kompetenz_details.sql
-- Zusatzfunktionen für Match-Details: gematchte und fehlende Kompetenzen

-- Gibt alle Kompetenzen der Stelle zurück, die die Person (teilweise) hat
-- Rückgabe: JSON-Array von Objekten {name, score, pflicht}
CREATE OR REPLACE FUNCTION match_kompetenz_details(p_id UUID, s_id UUID)
RETURNS TABLE(name TEXT, score FLOAT, pflicht BOOLEAN) LANGUAGE SQL STABLE AS $$
  SELECT
    k.name,
    COALESCE(
      (SELECT COUNT(*)::FLOAT
       FROM kompetenz_closure cc
       WHERE cc.nachfahre_id = sk.kompetenz_id
         AND cc.vorfahre_id IN (
           SELECT kompetenz_id FROM person_kompetenz WHERE person_id = p_id
         ))
      /
      NULLIF(
        (SELECT COUNT(*)::FLOAT FROM kompetenz_closure cc2
         WHERE cc2.nachfahre_id = sk.kompetenz_id),
        0
      ),
      0.0
    ) AS score,
    sk.pflicht
  FROM stelle_kompetenz sk
  JOIN bis_kompetenz k ON k.id = sk.kompetenz_id
  WHERE sk.stelle_id = s_id
$$;

-- Gibt fehlende PFLICHT-Kompetenzen der Stelle zurück
-- Rückgabe: JSON-Array von Objekten {name, pflicht}
CREATE OR REPLACE FUNCTION missing_kompetenz_details(p_id UUID, s_id UUID)
RETURNS TABLE(name TEXT, pflicht BOOLEAN) LANGUAGE SQL STABLE AS $$
  SELECT k.name, sk.pflicht
  FROM stelle_kompetenz sk
  JOIN bis_kompetenz k ON k.id = sk.kompetenz_id
  WHERE sk.stelle_id = s_id
    AND sk.pflicht = TRUE
    AND NOT EXISTS (
      SELECT 1 FROM kompetenz_closure cc
      JOIN person_kompetenz pk ON pk.kompetenz_id = cc.vorfahre_id
      WHERE cc.nachfahre_id = sk.kompetenz_id
        AND pk.person_id = p_id
    )
$$;