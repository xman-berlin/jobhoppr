-- V3: Beruf-Hierarchie (4 Stufen) + Kompetenz-Closure-Table
-- Bestehende bis_beruf-Tabelle bleibt bis zur Datenmigration erhalten (siehe V4b).

-- ── 4-stufige Beruf-Hierarchie ────────────────────────────────────────────────
-- Matching erfolgt auf BUG-Ebene (Spec: "Vergleich auf Ebene der Berufsuntergruppen")

CREATE TABLE beruf_bereich (
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE beruf_obergruppe (
    id         SERIAL PRIMARY KEY,
    name       TEXT    NOT NULL,
    bereich_id INTEGER NOT NULL REFERENCES beruf_bereich(id)
);

-- BUG = Berufsuntergruppe — die Matching-Ebene laut Spec
CREATE TABLE beruf_untergruppe (
    id            SERIAL PRIMARY KEY,
    name          TEXT    NOT NULL,
    obergruppe_id INTEGER NOT NULL REFERENCES beruf_obergruppe(id)
);

-- Spezialisierungen: Profile werden hier zugeordnet, Matching auf BUG-Ebene
CREATE TABLE beruf_spezialisierung (
    id             SERIAL PRIMARY KEY,
    name           TEXT    NOT NULL,
    untergruppe_id INTEGER NOT NULL REFERENCES beruf_untergruppe(id),
    isco_code      TEXT
);

-- person und stelle bekommen neue Beruf-Spalte (Referenz auf Spezialisierung)
-- beruf_id (Referenz auf bis_beruf) wird hier noch NICHT gedroppt —
-- erst nach erfolgreicher Datenmigration in V4b.
ALTER TABLE person ADD COLUMN beruf_spezialisierung_id INTEGER REFERENCES beruf_spezialisierung(id);
ALTER TABLE stelle ADD COLUMN beruf_spezialisierung_id INTEGER REFERENCES beruf_spezialisierung(id);

-- ── Kompetenz-Closure-Table ───────────────────────────────────────────────────
-- Speichert alle Vorfahren-Nachfahren-Paare explizit (inkl. Self-Paare mit tiefe=0).
-- Ermöglicht W(s)-Abfragen ohne rekursiven CTE im Matching-Query.
-- BisSeedRunner befüllt diese Tabelle nach dem Kompetenz-Import.

CREATE TABLE kompetenz_closure (
    vorfahre_id  INTEGER NOT NULL REFERENCES bis_kompetenz(id) ON DELETE CASCADE,
    nachfahre_id INTEGER NOT NULL REFERENCES bis_kompetenz(id) ON DELETE CASCADE,
    tiefe        INTEGER NOT NULL,
    PRIMARY KEY (vorfahre_id, nachfahre_id)
);

CREATE INDEX idx_closure_nachfahre ON kompetenz_closure(nachfahre_id);
CREATE INDEX idx_closure_vorfahre  ON kompetenz_closure(vorfahre_id);
CREATE INDEX idx_beruf_spec_ug     ON beruf_spezialisierung(untergruppe_id);
