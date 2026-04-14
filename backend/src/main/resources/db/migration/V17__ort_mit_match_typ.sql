-- V17: Neue Ortseingabe mit match_typ und plz_ort Integration
-- Zeige lat/lon nur noch intern, nicht mehr im UI

-- 1. match_typ für Person (UMKREIS = radius-basiert, EXAKT = exakte Übereinstimmung)
ALTER TABLE person_ort ADD COLUMN match_typ VARCHAR(20) DEFAULT 'UMKREIS' NOT NULL;

-- 2. match_typ für Stelle
ALTER TABLE stelle ADD COLUMN match_typ VARCHAR(20) DEFAULT 'UMKREIS' NOT NULL;

-- 3. Verknüpfung: geo_location (ORT-Ebene) zu plz_ort via natural key (plz, ort_name)
-- Da plz_ort keinen single-column PK hat, speichern wir plz und ort_name als JSON oder separate Spalten
-- Einfacher: Neue Tabelle für die Verknüpfung
CREATE TABLE IF NOT EXISTS geo_location_plz (
    geo_location_id INTEGER PRIMARY KEY REFERENCES geo_location(id),
    plz VARCHAR(4) NOT NULL,
    ort_name TEXT NOT NULL,
    FOREIGN KEY (plz, ort_name) REFERENCES plz_ort(plz, ort_name)
);

-- Index für Suche
CREATE INDEX IF NOT EXISTS idx_plz_ort_ort_name ON plz_ort(ort_name);
CREATE INDEX IF NOT EXISTS idx_plz_ort_bezirk ON plz_ort(bezirk);
CREATE INDEX IF NOT EXISTS idx_plz_ort_bundesland ON plz_ort(bundesland);
CREATE INDEX IF NOT EXISTS idx_geo_location_plz_ort ON geo_location_plz(geo_location_id);