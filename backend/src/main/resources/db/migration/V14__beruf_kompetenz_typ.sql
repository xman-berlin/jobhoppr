-- V14: typ-Spalte für beruf_basis_kompetenz
-- Unterscheidet Basis-Kompetenzen ("basis") von fachlichen Kompetenzen ("fach").
-- Bestehende Einträge (alle basisQualifikationen) erhalten DEFAULT 'basis'.

ALTER TABLE beruf_basis_kompetenz
    ADD COLUMN typ VARCHAR(10) NOT NULL DEFAULT 'basis';
