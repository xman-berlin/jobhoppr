-- V6: Performance-Indizes für Matching-Queries
-- Beschleunigt die Geo-Filter, KO-Checks und Score-Berechnungen

-- Stelle: Typ-Filter + Erstellungsdatum
CREATE INDEX IF NOT EXISTS idx_stelle_typ ON stelle(typ);
CREATE INDEX IF NOT EXISTS idx_stelle_erstellt_am ON stelle(erstellt_am DESC);

-- Person: Vermittlungsbereitschaft
CREATE INDEX IF NOT EXISTS idx_person_vermittlungspost ON person(vermittlungspost, max_bewegungen);

-- Closure-Tabellen: Schnelle Vorfahren-Suche (korrekte Namen!)
CREATE INDEX IF NOT EXISTS idx_kompetenz_closure_vorfahre ON kompetenz_closure(vorfahre_id);
CREATE INDEX IF NOT EXISTS idx_kompetenz_closure_nachfahre ON kompetenz_closure(nachfahre_id);
CREATE INDEX IF NOT EXISTS idx_kompetenz_closure_tiefe ON kompetenz_closure(vorfahre_id, nachfahre_id, tiefe);

-- Stelle Kompetenz: KO-Prüfung beschleunigen
CREATE INDEX IF NOT EXISTS idx_stelle_kompetenz_pflicht ON stelle_kompetenz(stelle_id, pflicht, kompetenz_id);
CREATE INDEX IF NOT EXISTS idx_stelle_interesse ON stelle_interesse(stelle_id, interessensgebiet_id);
CREATE INDEX IF NOT EXISTS idx_stelle_voraussetzung ON stelle_voraussetzung(stelle_id, voraussetzung_id);
CREATE INDEX IF NOT EXISTS idx_stelle_arbeitszeit ON stelle_arbeitszeit(stelle_id, pflicht, modell);

-- Person Kompetenz: Schneller Kompetenz-Abgleich
CREATE INDEX IF NOT EXISTS idx_person_kompetenz ON person_kompetenz(person_id, kompetenz_id);
CREATE INDEX IF NOT EXISTS idx_person_interesse ON person_interesse(person_id, interessensgebiet_id);
CREATE INDEX IF NOT EXISTS idx_person_voraussetzung ON person_voraussetzung(person_id, voraussetzung_id);
CREATE INDEX IF NOT EXISTS idx_person_arbeitszeit_ausschluss ON person_arbeitszeit_ausschluss(person_id, modell);

-- Person Ort: Geo-Suche beschleunigen
CREATE INDEX IF NOT EXISTS idx_person_ort_standort ON person_ort USING GIST(standort);
CREATE INDEX IF NOT EXISTS idx_person_ort_geo ON person_ort(geo_location_id);