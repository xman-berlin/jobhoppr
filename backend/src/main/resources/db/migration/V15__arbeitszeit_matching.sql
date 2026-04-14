-- V15: Arbeitszeit-Matching
-- Semantik:
--   person_arbeitszeit_ausschluss.modell  = gewünschte Modelle der Person  (Name historisch bedingt)
--   stelle_arbeitszeit.modell             = angebotene Modelle der Stelle
--   Match = Schnittmenge: Stelle bietet an UND Person wünscht
--   Score = |Schnittmenge| / |Stelle-Modelle|   (0.0–1.0; 1.0 wenn Stelle keine Modelle hat)

-- ── match_modell: Gewicht für Arbeitszeitmatching ────────────────────────────

ALTER TABLE match_modell
    ADD COLUMN IF NOT EXISTS gewicht_arbeitszeit DOUBLE PRECISION NOT NULL DEFAULT 0.0;

-- ── match_arbeitszeit(person_id, stelle_id) → FLOAT ─────────────────────────
-- Gibt den Arbeitszeit-Score zurück.
-- 1.0 wenn Stelle keine Modelle hat (kein Nachteil).
-- Sonst: Anteil der Stelle-Modelle die Person auch gewünscht hat.

CREATE OR REPLACE FUNCTION match_arbeitszeit(p_id UUID, s_id UUID)
RETURNS FLOAT
LANGUAGE sql STABLE AS $$
    SELECT CASE
        WHEN COUNT(sa.modell) = 0 THEN 1.0
        ELSE COUNT(CASE WHEN sa.modell IN (
                 SELECT modell FROM person_arbeitszeit_ausschluss WHERE person_id = p_id
             ) THEN 1 END)::FLOAT / COUNT(sa.modell)
    END
    FROM stelle_arbeitszeit sa
    WHERE sa.stelle_id = s_id
$$;

-- ── match_arbeitszeit_details(person_id, stelle_id) → TABLE(modell TEXT) ────
-- Gibt die gematchten Modelle zurück (Stelle bietet an, Person wünscht).

CREATE OR REPLACE FUNCTION match_arbeitszeit_details(p_id UUID, s_id UUID)
RETURNS TABLE(modell TEXT)
LANGUAGE sql STABLE AS $$
    SELECT sa.modell
    FROM stelle_arbeitszeit sa
    WHERE sa.stelle_id = s_id
      AND sa.modell IN (
          SELECT modell FROM person_arbeitszeit_ausschluss WHERE person_id = p_id
      )
    ORDER BY sa.modell
$$;
