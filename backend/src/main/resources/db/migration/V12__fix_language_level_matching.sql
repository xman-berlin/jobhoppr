-- V12__fix_language_level_matching.sql
-- Bugfix: Sprachkenntnisse sollen level-basiert matching (wer C2 hat, hat auch A1, B1, etc.)
-- Lösung: Spezielle Logik für Kompetenzen die mit A1-C2 Pattern beginnen

CREATE OR REPLACE FUNCTION match_kompetenz(p_id UUID, s_id UUID)
RETURNS FLOAT LANGUAGE SQL STABLE AS $$
  SELECT COALESCE(AVG(
    CASE 
      -- Prüfe exakte Kompetenz
      WHEN EXISTS (
        SELECT 1 FROM person_kompetenz pk 
        WHERE pk.person_id = p_id 
          AND pk.kompetenz_id = sk.kompetenz_id
      ) THEN 1.0
      -- Prüfe Generalisierung über Vorfahren
      WHEN EXISTS (
        SELECT 1 FROM kompetenz_closure cc
        JOIN person_kompetenz pk ON pk.kompetenz_id = cc.vorfahre_id
        WHERE cc.nachfahre_id = sk.kompetenz_id
          AND pk.person_id = p_id
      ) THEN 1.0
      -- Prüfe Sprachlevel-Superset (A1-C2)
      WHEN (
        SELECT name FROM bis_kompetenz WHERE id = sk.kompetenz_id
      ) ~ '^[A-C][12] - ' THEN
        CASE WHEN EXISTS (
          SELECT 1 FROM person_kompetenz pk
          JOIN bis_kompetenz pk_k ON pk_k.id = pk.kompetenz_id
          WHERE pk.person_id = p_id
            AND pk_k.name ~ '^[A-C][12] - '
            -- Gleiche Sprache: regex_replace entfernt Prefix "A1-C2 - " und restliche Beschreibung
            AND regexp_replace(pk_k.name, '^[A-C][12] - .* ', '') = regexp_replace(
              (SELECT name FROM bis_kompetenz WHERE id = sk.kompetenz_id), '^[A-C][12] - .* ', ''
            )
            -- Person hat höheres oder gleiches Level: Alphabetisch A < B < C
            AND pk_k.name >= (SELECT name FROM bis_kompetenz WHERE id = sk.kompetenz_id)
        ) THEN 1.0 ELSE 0.0 END
      ELSE 0.0
    END
  ), 0.0)
  FROM stelle_kompetenz sk
  WHERE sk.stelle_id = s_id
$$;