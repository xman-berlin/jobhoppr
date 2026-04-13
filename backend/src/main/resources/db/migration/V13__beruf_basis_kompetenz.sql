-- V13: Verknüpfungstabelle Beruf → Basis-Kompetenzen
-- Quelle: BIS stammberufe.xml <basisQualifikationen> pro Stammberuf
-- beruf_id     = beruf_spezialisierung.id (= stammberuf noteid, kein Offset)
-- kompetenz_id = bis_kompetenz.id (= qualifikation noteid + 20000)

CREATE TABLE beruf_basis_kompetenz (
    beruf_id     INTEGER NOT NULL REFERENCES beruf_spezialisierung(id),
    kompetenz_id INTEGER NOT NULL REFERENCES bis_kompetenz(id),
    PRIMARY KEY (beruf_id, kompetenz_id)
);

CREATE INDEX idx_beruf_basis_kompetenz_beruf_id ON beruf_basis_kompetenz(beruf_id);
