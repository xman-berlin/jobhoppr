-- V2: Convert CHAR(n) columns to VARCHAR to satisfy Hibernate schema validation.
-- PostgreSQL's CHAR(n) maps to bpchar which Hibernate does not recognise as VARCHAR.

ALTER TABLE bundesland ALTER COLUMN kuerzel TYPE VARCHAR(3);
ALTER TABLE plz_ort    ALTER COLUMN plz     TYPE VARCHAR(4);
