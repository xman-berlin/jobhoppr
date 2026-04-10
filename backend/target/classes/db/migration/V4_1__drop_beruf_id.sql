-- V4b__drop_beruf_id.sql
-- Darf erst ausgeführt werden nachdem BerufHierarchieSeedRunner erfolgreich gelaufen ist
-- und sichergestellt hat dass alle person/stelle-Zeilen eine beruf_spezialisierung_id haben.
-- BerufHierarchieSeedRunner wirft IllegalStateException wenn noch NULL-Werte existieren.

ALTER TABLE person DROP COLUMN IF EXISTS beruf_id;
ALTER TABLE stelle DROP COLUMN IF EXISTS beruf_id;
