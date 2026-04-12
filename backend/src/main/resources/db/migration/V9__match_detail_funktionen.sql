-- V9__match_detail_funktionen.sql
-- Detail-Funktionen für Interessen, Voraussetzungen und zusätzliche Kompetenzen

-- Passende Interessensgebiete (Person hat sie, Stelle verlangt sie)
CREATE OR REPLACE FUNCTION match_interessen_details(p_id UUID, s_id UUID)
RETURNS TABLE(name TEXT) LANGUAGE SQL STABLE AS $$
  SELECT ig.name
  FROM stelle_interesse si
  JOIN interessensgebiet ig ON ig.id = si.interessensgebiet_id
  WHERE si.stelle_id = s_id
    AND EXISTS (
      SELECT 1 FROM person_interesse pi
      WHERE pi.person_id = p_id AND pi.interessensgebiet_id = si.interessensgebiet_id
    )
$$;

-- Fehlende Interessensgebiete (Stelle verlangt sie, Person hat sie nicht)
CREATE OR REPLACE FUNCTION missing_interessen_details(p_id UUID, s_id UUID)
RETURNS TABLE(name TEXT) LANGUAGE SQL STABLE AS $$
  SELECT ig.name
  FROM stelle_interesse si
  JOIN interessensgebiet ig ON ig.id = si.interessensgebiet_id
  WHERE si.stelle_id = s_id
    AND NOT EXISTS (
      SELECT 1 FROM person_interesse pi
      WHERE pi.person_id = p_id AND pi.interessensgebiet_id = si.interessensgebiet_id
    )
$$;

-- Zusätzliche Interessensgebiete (Person hat sie, Stelle verlangt sie nicht)
CREATE OR REPLACE FUNCTION extra_interessen_details(p_id UUID, s_id UUID)
RETURNS TABLE(name TEXT) LANGUAGE SQL STABLE AS $$
  SELECT ig.name
  FROM person_interesse pi
  JOIN interessensgebiet ig ON ig.id = pi.interessensgebiet_id
  WHERE pi.person_id = p_id
    AND NOT EXISTS (
      SELECT 1 FROM stelle_interesse si
      WHERE si.stelle_id = s_id AND si.interessensgebiet_id = pi.interessensgebiet_id
    )
$$;

-- Passende Voraussetzungen (Person erfüllt sie, Stelle verlangt sie)
CREATE OR REPLACE FUNCTION match_voraussetzung_details(p_id UUID, s_id UUID)
RETURNS TABLE(name TEXT) LANGUAGE SQL STABLE AS $$
  SELECT v.name
  FROM stelle_voraussetzung sv
  JOIN voraussetzung v ON v.id = sv.voraussetzung_id
  WHERE sv.stelle_id = s_id
    AND EXISTS (
      SELECT 1 FROM person_voraussetzung pv
      WHERE pv.person_id = p_id AND pv.voraussetzung_id = sv.voraussetzung_id
    )
$$;

-- Fehlende Voraussetzungen (Stelle verlangt sie, Person erfüllt sie nicht)
CREATE OR REPLACE FUNCTION missing_voraussetzung_details(p_id UUID, s_id UUID)
RETURNS TABLE(name TEXT) LANGUAGE SQL STABLE AS $$
  SELECT v.name
  FROM stelle_voraussetzung sv
  JOIN voraussetzung v ON v.id = sv.voraussetzung_id
  WHERE sv.stelle_id = s_id
    AND NOT EXISTS (
      SELECT 1 FROM person_voraussetzung pv
      WHERE pv.person_id = p_id AND pv.voraussetzung_id = sv.voraussetzung_id
    )
$$;

-- Zusätzliche Voraussetzungen (Person hat sie, Stelle verlangt sie nicht)
CREATE OR REPLACE FUNCTION extra_voraussetzung_details(p_id UUID, s_id UUID)
RETURNS TABLE(name TEXT) LANGUAGE SQL STABLE AS $$
  SELECT v.name
  FROM person_voraussetzung pv
  JOIN voraussetzung v ON v.id = pv.voraussetzung_id
  WHERE pv.person_id = p_id
    AND NOT EXISTS (
      SELECT 1 FROM stelle_voraussetzung sv
      WHERE sv.stelle_id = s_id AND sv.voraussetzung_id = pv.voraussetzung_id
    )
$$;

-- Zusätzliche Kompetenzen (Person hat sie, Stelle verlangt sie nicht — optionaler Bonus)
CREATE OR REPLACE FUNCTION extra_kompetenz_details(p_id UUID, s_id UUID)
RETURNS TABLE(name TEXT) LANGUAGE SQL STABLE AS $$
  SELECT DISTINCT k.name
  FROM person_kompetenz pk
  JOIN bis_kompetenz k ON k.id = pk.kompetenz_id
  WHERE pk.person_id = p_id
    AND NOT EXISTS (
      SELECT 1 FROM stelle_kompetenz sk
      WHERE sk.stelle_id = s_id AND sk.kompetenz_id = pk.kompetenz_id
    )
$$;
