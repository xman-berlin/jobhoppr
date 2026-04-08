-- V4: KO-Felder + Lehrstellenmatching + Geo-Hierarchie
-- HINWEIS: beruf_id wird hier NICHT gedroppt (P1).
-- Das DROP erfolgt in V4b__drop_beruf_id.sql, nachdem BerufHierarchieSeedRunner
-- alle beruf_spezialisierung_id-Werte befüllt und validiert hat.

-- ── Person: Systemattribute für KO-Kriterien ─────────────────────────────────

ALTER TABLE person ADD COLUMN vermittlungspost  BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE person ADD COLUMN max_bewegungen    INTEGER NOT NULL DEFAULT 999;
ALTER TABLE person ADD COLUMN sucht_lehrstelle  BOOLEAN NOT NULL DEFAULT FALSE;

-- ── Person: ausgeschlossene Arbeitszeitmodelle ────────────────────────────────

CREATE TABLE person_arbeitszeit_ausschluss (
    person_id UUID NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    modell    TEXT NOT NULL,  -- VOLLZEIT | TEILZEIT | GERINGFUEGIG | NACHT | WOCHENENDE
    PRIMARY KEY (person_id, modell)
);

-- ── Stelle: Typ + Arbeitszeitmodelle ─────────────────────────────────────────

ALTER TABLE stelle ADD COLUMN typ TEXT NOT NULL DEFAULT 'STANDARD';  -- STANDARD | LEHRSTELLE

CREATE TABLE stelle_arbeitszeit (
    stelle_id UUID    NOT NULL REFERENCES stelle(id) ON DELETE CASCADE,
    modell    TEXT    NOT NULL,
    pflicht   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (stelle_id, modell)
);

-- ── Lehrstellenmatching: Referenzdaten ───────────────────────────────────────

CREATE TABLE interessensgebiet (
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE voraussetzung (
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

-- ── Person ↔ Interessen / Voraussetzungen ────────────────────────────────────

CREATE TABLE person_interesse (
    person_id            UUID    NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    interessensgebiet_id INTEGER NOT NULL REFERENCES interessensgebiet(id),
    PRIMARY KEY (person_id, interessensgebiet_id)
);

CREATE TABLE person_voraussetzung (
    person_id        UUID    NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    voraussetzung_id INTEGER NOT NULL REFERENCES voraussetzung(id),
    PRIMARY KEY (person_id, voraussetzung_id)
);

-- ── Stelle ↔ Interessen / Voraussetzungen ────────────────────────────────────

CREATE TABLE stelle_interesse (
    stelle_id            UUID    NOT NULL REFERENCES stelle(id) ON DELETE CASCADE,
    interessensgebiet_id INTEGER NOT NULL REFERENCES interessensgebiet(id),
    PRIMARY KEY (stelle_id, interessensgebiet_id)
);

CREATE TABLE stelle_voraussetzung (
    stelle_id        UUID    NOT NULL REFERENCES stelle(id) ON DELETE CASCADE,
    voraussetzung_id INTEGER NOT NULL REFERENCES voraussetzung(id),
    PRIMARY KEY (stelle_id, voraussetzung_id)
);

-- ── Geo-Hierarchie (zweite Geo-Stufe neben GPS-Radius) ───────────────────────
-- Ebenen: ORT < BEZIRK < BUNDESLAND
-- Verknüpft mit bestehender plz_ort-Tabelle über bundesland/bezirk-Felder.

CREATE TABLE geo_location (
    id        SERIAL PRIMARY KEY,
    name      TEXT    NOT NULL,
    ebene     TEXT    NOT NULL,  -- ORT | BEZIRK | BUNDESLAND
    parent_id INTEGER REFERENCES geo_location(id),
    lat       DOUBLE PRECISION,
    lon       DOUBLE PRECISION
);

CREATE INDEX idx_geo_loc_parent ON geo_location(parent_id);
CREATE INDEX idx_geo_loc_ebene  ON geo_location(ebene);

-- ── person_ort: Location-Hierarchie + Bundesweit ──────────────────────────────

ALTER TABLE person_ort ADD COLUMN geo_location_id INTEGER REFERENCES geo_location(id);
ALTER TABLE person_ort ADD COLUMN bundesweit      BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_port_geo_loc ON person_ort(geo_location_id);

-- ── stelle: Location-Hierarchie ──────────────────────────────────────────────

ALTER TABLE stelle ADD COLUMN geo_location_id INTEGER REFERENCES geo_location(id);

CREATE INDEX idx_stelle_geo_loc ON stelle(geo_location_id);

-- ── match_modell: neue Felder, beruf_filter_strikt entfernen ─────────────────
-- (P6: MatchModell.java muss berufFilterStrikt im selben Deploy entfernt haben)

ALTER TABLE match_modell ADD COLUMN score_schwellenwert     DOUBLE PRECISION NOT NULL DEFAULT 0.25;
ALTER TABLE match_modell ADD COLUMN gewicht_lehrberuf       DOUBLE PRECISION NOT NULL DEFAULT 0.20;
ALTER TABLE match_modell ADD COLUMN gewicht_interessen      DOUBLE PRECISION NOT NULL DEFAULT 0.40;
ALTER TABLE match_modell ADD COLUMN gewicht_voraussetzungen DOUBLE PRECISION NOT NULL DEFAULT 0.40;
ALTER TABLE match_modell DROP COLUMN beruf_filter_strikt;

-- ── Sortierungs-Indizes ───────────────────────────────────────────────────────

CREATE INDEX idx_stelle_erstellt_am ON stelle(erstellt_am DESC);
CREATE INDEX idx_person_erstellt_am ON person(erstellt_am DESC);
