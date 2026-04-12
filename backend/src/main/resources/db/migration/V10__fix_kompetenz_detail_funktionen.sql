-- V10__fix_kompetenz_detail_funktionen.sql
-- Bugfix: match_kompetenz_details lieferte alle Stelle-Kompetenzen zurück,
-- auch wenn die Person sie gar nicht hat (score = 0.0).
-- missing_kompetenz_details zeigte nur Pflicht-Kompetenzen als fehlend —
-- optionale Kompetenzen ohne Person-Match wurden fälschlicherweise in
-- matching_ko aufgenommen.

-- Gibt Kompetenzen zurück, die die Person tatsächlich (teilweise) abdeckt.
-- Nur Einträge mit score > 0 werden zurückgegeben.
-- Logik: 1.0 = exakte/Closure-Match, 1.0 = Sprachlevel-Match, 0.0 = kein Match
CREATE OR REPLACE FUNCTION match_kompetenz_details(p_id UUID, s_id UUID)
RETURNS TABLE(name TEXT, score FLOAT, pflicht BOOLEAN) LANGUAGE SQL STABLE AS $$
  SELECT
    k.name,
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
      WHEN k.name ~ '^[A-C][12] - ' THEN
        CASE WHEN EXISTS (
          SELECT 1 FROM person_kompetenz pk
          JOIN bis_kompetenz pk_k ON pk_k.id = pk.kompetenz_id
          WHERE pk.person_id = p_id
            AND pk_k.name ~ '^[A-C][12] - '
            AND regexp_replace(pk_k.name, '^[A-C][12] - .* ', '') = regexp_replace(k.name, '^[A-C][12] - .* ', '')
            AND pk_k.name >= k.name
        ) THEN 1.0 ELSE 0.0 END
      ELSE 0.0
    END AS score,
    sk.pflicht
  FROM stelle_kompetenz sk
  JOIN bis_kompetenz k ON k.id = sk.kompetenz_id
  WHERE sk.stelle_id = s_id
    AND (
      EXISTS (SELECT 1 FROM person_kompetenz pk WHERE pk.person_id = p_id AND pk.kompetenz_id = sk.kompetenz_id)
      OR EXISTS (SELECT 1 FROM kompetenz_closure cc JOIN person_kompetenz pk ON pk.kompetenz_id = cc.vorfahre_id WHERE cc.nachfahre_id = sk.kompetenz_id AND pk.person_id = p_id)
      OR (k.name ~ '^[A-C][12] - ' AND EXISTS (
        SELECT 1 FROM person_kompetenz pk
        JOIN bis_kompetenz pk_k ON pk_k.id = pk.kompetenz_id
        WHERE pk.person_id = p_id
          AND pk_k.name ~ '^[A-C][12] - '
          AND regexp_replace(pk_k.name, '^[A-C][12] - .* ', '') = regexp_replace(k.name, '^[A-C][12] - .* ', '')
          AND pk_k.name >= k.name
      ))
    )
$$;

-- Gibt ALLE fehlenden Kompetenzen zurück (sowohl Pflicht als auch optional),
-- bei denen die Person keine Abdeckung hat (weder exakt, noch Closure, noch Sprachlevel).
CREATE OR REPLACE FUNCTION missing_kompetenz_details(p_id UUID, s_id UUID)
RETURNS TABLE(name TEXT, pflicht BOOLEAN) LANGUAGE SQL STABLE AS $$
  SELECT k.name, sk.pflicht
  FROM stelle_kompetenz sk
  JOIN bis_kompetenz k ON k.id = sk.kompetenz_id
  WHERE sk.stelle_id = s_id
    AND NOT EXISTS (
      SELECT 1 FROM person_kompetenz pk 
      WHERE pk.person_id = p_id AND pk.kompetenz_id = sk.kompetenz_id
    )
    AND NOT EXISTS (
      SELECT 1 FROM kompetenz_closure cc
      JOIN person_kompetenz pk ON pk.kompetenz_id = cc.vorfahre_id
      WHERE cc.nachfahre_id = sk.kompetenz_id AND pk.person_id = p_id
    )
    AND NOT (
      k.name ~ '^[A-C][12] - ' AND EXISTS (
        SELECT 1 FROM person_kompetenz pk
        JOIN bis_kompetenz pk_k ON pk_k.id = pk.kompetenz_id
        WHERE pk.person_id = p_id
          AND pk_k.name ~ '^[A-C][12] - '
          AND regexp_replace(pk_k.name, '^[A-C][12] - .* ', '') = regexp_replace(k.name, '^[A-C][12] - .* ', '')
          AND pk_k.name >= k.name
      )
    )
$$;
