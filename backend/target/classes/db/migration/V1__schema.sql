-- V1: Full schema for JobHoppr
-- Requires PostGIS extension

CREATE EXTENSION IF NOT EXISTS postgis;

-- ── BIS reference data (read-only after seed) ─────────────────────────────────

CREATE TABLE bis_beruf (
    id        INTEGER PRIMARY KEY,
    name      TEXT    NOT NULL,
    bereich   TEXT,
    isco_code TEXT
);

CREATE TABLE bis_kompetenz (
    id        INTEGER PRIMARY KEY,
    name      TEXT    NOT NULL,
    bereich   TEXT,
    parent_id INTEGER REFERENCES bis_kompetenz(id),
    typ       TEXT    -- FACHLICH | UEBERFACHLICH | ZERTIFIKAT
);

-- ── PLZ lookup (GeoNames AT, CC-BY 4.0) ──────────────────────────────────────

CREATE TABLE plz_ort (
    plz        CHAR(4)           NOT NULL,
    ort_name   TEXT              NOT NULL,
    bundesland TEXT              NOT NULL,
    bezirk     TEXT,
    lat        DOUBLE PRECISION  NOT NULL,
    lon        DOUBLE PRECISION  NOT NULL,
    PRIMARY KEY (plz, ort_name)
);

CREATE INDEX idx_plz_ort_plz      ON plz_ort (plz);
CREATE INDEX idx_plz_ort_name     ON plz_ort (lower(ort_name));
CREATE INDEX idx_plz_ort_bland    ON plz_ort (bundesland);

-- ── Bundesländer (hard-coded seed) ────────────────────────────────────────────

CREATE TABLE bundesland (
    kuerzel      CHAR(3)           PRIMARY KEY,
    name         TEXT              NOT NULL,
    centroid_lat DOUBLE PRECISION  NOT NULL,
    centroid_lon DOUBLE PRECISION  NOT NULL,
    umkreis_km   DOUBLE PRECISION  NOT NULL
);

-- ── Persons ───────────────────────────────────────────────────────────────────

CREATE TABLE person (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    vorname         TEXT        NOT NULL,
    nachname        TEXT        NOT NULL,
    email           TEXT,
    beruf_id        INTEGER     REFERENCES bis_beruf(id),
    erstellt_am     TIMESTAMPTZ DEFAULT NOW(),
    aktualisiert_am TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_person_beruf_id ON person (beruf_id);

CREATE TABLE person_ort (
    id          UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id   UUID             NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    ort_rolle   TEXT             NOT NULL,   -- WOHNORT | ARBEITSORT
    ort_typ     TEXT             NOT NULL,   -- GENAU | REGION
    bezeichnung TEXT             NOT NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lon         DOUBLE PRECISION NOT NULL,
    umkreis_km  DOUBLE PRECISION NOT NULL,
    standort    GEOGRAPHY(POINT, 4326)
                    GENERATED ALWAYS AS (ST_MakePoint(lon, lat)::geography) STORED
);

CREATE INDEX idx_person_ort_standort   ON person_ort USING GIST (standort);
CREATE INDEX idx_person_ort_person_id  ON person_ort (person_id);

CREATE TABLE person_kompetenz (
    person_id    UUID    NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    kompetenz_id INTEGER NOT NULL REFERENCES bis_kompetenz(id),
    niveau       TEXT,   -- GRUNDKENNTNISSE | FORTGESCHRITTEN | EXPERTE
    PRIMARY KEY (person_id, kompetenz_id)
);

CREATE INDEX idx_person_kompetenz_kid ON person_kompetenz (kompetenz_id, person_id);
CREATE INDEX idx_person_kompetenz_pid ON person_kompetenz (person_id);

-- ── Job postings ──────────────────────────────────────────────────────────────

CREATE TABLE stelle (
    id              UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    titel           TEXT             NOT NULL,
    unternehmen     TEXT,
    beschreibung    TEXT,
    ort_bezeichnung TEXT             NOT NULL,
    ort_lat         DOUBLE PRECISION NOT NULL,
    ort_lon         DOUBLE PRECISION NOT NULL,
    beruf_id        INTEGER          REFERENCES bis_beruf(id),
    erstellt_am     TIMESTAMPTZ      DEFAULT NOW(),
    aktualisiert_am TIMESTAMPTZ      DEFAULT NOW(),
    standort        GEOGRAPHY(POINT, 4326)
                        GENERATED ALWAYS AS (ST_MakePoint(ort_lon, ort_lat)::geography) STORED
);

CREATE INDEX idx_stelle_standort  ON stelle USING GIST (standort);
CREATE INDEX idx_stelle_beruf_id  ON stelle (beruf_id);

CREATE TABLE stelle_kompetenz (
    stelle_id    UUID    NOT NULL REFERENCES stelle(id) ON DELETE CASCADE,
    kompetenz_id INTEGER NOT NULL REFERENCES bis_kompetenz(id),
    pflicht      BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (stelle_id, kompetenz_id)
);

CREATE INDEX idx_stelle_kompetenz_sid ON stelle_kompetenz (stelle_id, kompetenz_id);

-- ── Match model (exactly one active record) ───────────────────────────────────

CREATE TABLE match_modell (
    id                  UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    name                TEXT             NOT NULL,
    aktiv               BOOLEAN          NOT NULL DEFAULT FALSE,
    geo_aktiv           BOOLEAN          NOT NULL DEFAULT TRUE,
    beruf_filter_strikt BOOLEAN          NOT NULL DEFAULT FALSE,
    gewicht_kompetenz   DOUBLE PRECISION NOT NULL DEFAULT 0.75,
    gewicht_beruf       DOUBLE PRECISION NOT NULL DEFAULT 0.25,
    erstellt_am         TIMESTAMPTZ      DEFAULT NOW()
);

INSERT INTO match_modell (name, aktiv, geo_aktiv, beruf_filter_strikt, gewicht_kompetenz, gewicht_beruf)
VALUES ('Standard-Modell', TRUE, TRUE, FALSE, 0.75, 0.25);
