# Plan: JobHoppr — Migration auf das vollständige Matchmodell

## Context

JobHoppr ist eine funktionierende Spring Boot + Thymeleaf + HTMX Webanwendung mit einem vereinfachten
Matching-Algorithmus: binärer Berufsvergleich (exakte beruf_id-Übereinstimmung), flat Kompetenz-Schnittmenge
(parent_id in bis_kompetenz vorhanden aber ignoriert), GPS-Radius-Geo (ST_DWithin).

Dieser Plan migriert JobHoppr auf das vollständige Matchmodell aus `docs/matchmodell.md`:
- Hierarchisches Beruf-Matching auf BUG-Ebene (4-stufige Hierarchie)
- Pfadgewichtetes Kompetenz-Scoring via Closure Table (Spec-Formel exakt)
- Lehrstellenmatching (FM: Interessensgebiete + QM: Voraussetzungen)
- Zweistufiges Geo-Matching (GPS-Radius + Location-Hierarchie + Bundesweit)
- Alle Knock-Out-Kriterien (Muss-Kompetenzen, Arbeitszeitmodelle, Vermittlungspost, Score-Schwellenwert)
- Erweiterbare Score-Architektur (PostgreSQL STABLE SQL-Funktionen, ein Roundtrip)
- Sortierbare Ergebnisse (Score oder erstellt_am, ASC/DESC)

Das System muss bei 1+ Mio. Personen und Stellen unter 1 Sekunde Antwortzeit bleiben.

## Progress

- [x] Phase 1: Schema-Migration (Beruf-Hierarchie + Closure Table + neue KO-Felder)
- [x] Phase 2: BIS-Seed-Daten (Scraping + Import der echten BIS-Daten)
- [x] Phase 3: JPA-Entities + Services anpassen
- [x] Phase 4: Closure-Table-Pflege (KompetenzClosureService)
- [x] Phase 5: PostgreSQL Score-Funktionen (Flyway)
- [x] Phase 6: MatchRepository + MatchService + Sortierung
- [x] Phase 7: Lehrstellenmatching
- [x] Phase 8: Frontend anpassen
- [ ] Phase 9: Tests
- [ ] Phase 10: Performance-Indizes + Load-Test

---

## Bekannte Probleme & Korrekturen (Analyse-Ergebnisse)

Die folgenden Probleme wurden bei der Plan-Analyse identifiziert und sind in den betroffenen Phasen-Checklisten bereits berücksichtigt.

### P1 — HIGH: V4 droppt `beruf_id` bevor der Seeder die Datenmigration abgeschlossen hat

**Problem**: V4 enthält `ALTER TABLE person DROP COLUMN beruf_id` und `ALTER TABLE stelle DROP COLUMN beruf_id`.
Flyway führt V4 beim App-Start aus, *bevor* `BerufHierarchieSeedRunner` (Java) läuft.
Der Seeder befüllt `beruf_spezialisierung_id` via Name-Matching — aber erst nach dem Flyway-Durchlauf.
Ergebnis: `beruf_id` wird gedroppt bevor die Migration der Werte stattgefunden hat. Daten gehen verloren.

**Fix**: `DROP COLUMN` aus V4 entfernen und in eine eigene Migration **V4b** auslagern.
`BerufHierarchieSeedRunner` wirft eine `IllegalStateException` wenn nach dem Name-Matching noch Zeilen
mit `beruf_spezialisierung_id IS NULL` existieren — so wird der Fehler laut sichtbar.
V4b darf erst nach manuellem Bestätigen (oder einem eigenen idempotenten Seeder-Check) ausgeführt werden.
Alternativ: `BerufHierarchieSeedRunner` als Flyway Java-Migration implementieren (direkt nach V4),
dann bleibt die Reihenfolge garantiert ohne manuellen Eingriff.

### P2 — HIGH: `match_kompetenz` — korrelierte Subquery-Struktur fehlerhaft

**Problem**: In der geplanten `match_kompetenz`-Funktion wird der Zähler (`S(J) ∩ W(s)`) als
eigenständige Subquery formuliert, die nicht korrekt mit der äußeren `stelle_kompetenz`-Zeile korreliert.
Das ergibt für alle `sk`-Zeilen denselben Zähler-Wert → falsche Scores.

**Korrektur** (ersetzt den Zähler-Block in V5):
```sql
CREATE OR REPLACE FUNCTION match_kompetenz(p_id UUID, s_id UUID)
RETURNS FLOAT LANGUAGE SQL STABLE AS $$
  SELECT COALESCE(AVG(
    (SELECT COUNT(*)::FLOAT
     FROM kompetenz_closure cc
     WHERE cc.nachfahre_id = sk.kompetenz_id
       AND cc.vorfahre_id IN (
         SELECT kompetenz_id FROM person_kompetenz WHERE person_id = p_id
       ))
    /
    NULLIF(
      (SELECT COUNT(*)::FLOAT FROM kompetenz_closure cc2
       WHERE cc2.nachfahre_id = sk.kompetenz_id),
      0
    )
  ), 0.0)
  FROM stelle_kompetenz sk
  WHERE sk.stelle_id = s_id
$$;
```
Beide Subqueries korrelieren explizit über `sk.kompetenz_id` mit der äußeren `FROM`-Zeile.

### P3 — MEDIUM: Score-Funktionen werden doppelt aufgerufen (Score + Breakdown)

**Problem**: Im `scores`-CTE wird jede Score-Funktion zweimal aufgerufen —
einmal für den Gesamt-Score und einmal für die Breakdown-Spalte (`om`, `sm`, usw.).
PostgreSQL garantiert kein CSE (Common Subexpression Elimination) für `STABLE`-Funktionen
mit nicht-konstantem Argument (`s.id`). Unter Last mit großen Kandidatenmengen verdoppelt das die Arbeit.

**Fix**: Breakdown via `CROSS JOIN LATERAL` einmalig berechnen:
```sql
FROM stelle s
CROSS JOIN LATERAL (
  SELECT
    match_beruf(:person_id, s.id)           AS om,
    match_kompetenz(:person_id, s.id)       AS sm,
    match_interessen(:person_id, s.id)      AS fm,
    match_voraussetzungen(:person_id, s.id) AS qm
) bd
```
Danach `bd.om`, `bd.sm` etc. im Score-Ausdruck und als Breakdown-Spalten referenzieren (je ein Aufruf).
Der korrigierte vollständige Query steht weiter unten im Abschnitt "Matching-Query".

### P4 — MEDIUM: Rekursiver CTE im Geo-EXISTS bei 1 Mio. Zeilen

**Problem**: Der `geo_kandidaten`-CTE enthält ein `WITH RECURSIVE` innerhalb eines `EXISTS(...)`.
PostgreSQL kann Prädikate nicht in den rekursiven Teil pushdown — der Planer wertet den EXISTS
für jede `stelle`-Zeile einzeln aus. Bei 1 Mio. Stellen und vielen `person_ort`-Einträgen
entstehen N×M rekursive Traversals.

**Mitigation für Phase 10**: Falls `< 1s` nicht erreicht wird, die Location-Hierarchie
vorexpandieren — analog zur `kompetenz_closure` eine `geo_location_closure`-Tabelle anlegen:
```sql
CREATE TABLE geo_location_closure (
  vorfahre_id  INTEGER NOT NULL REFERENCES geo_location(id),
  nachfahre_id INTEGER NOT NULL REFERENCES geo_location(id),
  PRIMARY KEY (vorfahre_id, nachfahre_id)
);
```
Dann ersetzt `EXISTS (SELECT 1 FROM geo_location_closure WHERE vorfahre_id = po.geo_location_id AND nachfahre_id = s.geo_location_id)` den rekursiven EXISTS — ein einfacher Index-Lookup.
Diese Optimierung ist für Phase 10 als optionale Maßnahme vorgesehen; zuerst messen.

### P5 — LOW: Closure-Seed — `SELECT DISTINCT` + `GROUP BY` redundant und irreführend

**Problem**: `SELECT DISTINCT vorfahre_id, nachfahre_id, MIN(tiefe) FROM closure GROUP BY ...`
mischt `DISTINCT` und `GROUP BY` — `DISTINCT` ist hier überflüssig und erhöht den kognitiven Aufwand.

**Fix**: `DISTINCT` weglassen, nur `GROUP BY` verwenden:
```sql
INSERT INTO kompetenz_closure
SELECT vorfahre_id, nachfahre_id, MIN(tiefe)
FROM closure GROUP BY vorfahre_id, nachfahre_id
ON CONFLICT DO NOTHING;
```

### P6 — LOW: `beruf_filter_strikt` — Timing vs. Hibernate Validation

**Problem**: V4 droppt `beruf_filter_strikt`. Wenn `spring.jpa.hibernate.ddl-auto=validate` aktiv ist
und die `MatchModell.java`-Entity noch das Feld `berufFilterStrikt` enthält,
schlägt Hibernate beim Start fehl bevor Flyway V4 ausgeführt wird — Henne-Ei-Problem.

**Fix**: `berufFilterStrikt` aus `MatchModell.java` und `MatchModellRequest` entfernen
*im selben Commit* wie V4 eingecheckt wird. Reihenfolge im Commit: Entity zuerst, dann Migration.
Alternativ: temporär `ddl-auto=update` für den Migrations-Deploy, danach zurück auf `validate`.

---

## Delta: Was sich ändert

| Bereich | Aktueller Stand | Zielzustand |
|---------|-----------------|-------------|
| Beruf-Hierarchie | flache `bis_beruf`-Liste, `bereich` als String | 4-stufige Hierarchie (Bereich → Obergruppe → BUG → Spezialisierung) |
| Beruf-Matching | exakter `beruf_id`-Vergleich (binär 0/1) | BUG-Schnittmenge (`untergruppe_id`) → 1.0 oder 0.0 |
| Kompetenz-Hierarchie | `parent_id` vorhanden, im Matching **ignoriert** | Closure Table; W(s)-Abfragen ohne rekursiven CTE |
| Kompetenz-Matching | flat set intersection, Pflicht doppelt gewichtet | `SM(J,O) = Σ |S(J)∩W(s)| / |W(s)| / |M(O)|` (Spec-Formel) |
| Lehrstellenmatching | nicht vorhanden | FM (Interessen) + QM (Voraussetzungen), Stelle-Typ LEHRSTELLE |
| Geo-Matching | GPS-Radius only (`ST_DWithin`) | GPS-Radius + Location-Hierarchie (Ort→Bezirk→Bundesland) + Bundesweit |
| KO-Kriterien | nur Geo + `berufFilterStrikt` | Muss-Kompetenzen, Arbeitszeitmodelle, Vermittlungspost, Score < 25% |
| Score-Architektur | monolithischer CTE in `MatchRepository.java` | PostgreSQL `STABLE LANGUAGE SQL`-Funktionen, ein Roundtrip, erweiterbar |
| Sortierung | immer `score DESC` | Score oder `erstellt_am`, ASC/DESC; Schwellenwert bleibt `WHERE` |
| Seed-Daten | 25 Berufe (erfunden), 35 Kompetenzen (erfunden) | echte BIS-Daten (Berufe + Kompetenzen) |
| Tests | **kein einziger Test** | Unit + Integration (Testcontainers bereits konfiguriert) |

---

## Tech Stack

Unverändert:

| Layer | Choice |
|-------|--------|
| Backend | Spring Boot 3.3.5, Java 21, Gradle |
| Datenbank | PostgreSQL 16 + PostGIS (postgis/postgis:16-3.4) |
| Schema-Migrations | Flyway |
| ORM | Spring Data JPA + JdbcTemplate (für native Queries) |
| Frontend | Thymeleaf + HTMX 1.9.12 + DaisyUI 4 |
| Build/Deploy | Docker Compose |

---

## Neue Datenbankstruktur

### Flyway V3: Beruf-Hierarchie + Closure Table

```sql
-- V3__beruf_hierarchie_und_closure.sql

-- Bestehende bis_beruf-Tabelle bleibt bis zur Datenmigration erhalten.

-- 4-stufige Beruf-Hierarchie
-- Matching erfolgt auf BUG-Ebene (Spec: "Vergleich auf Ebene der Berufsuntergruppen")
CREATE TABLE beruf_bereich (
  id   SERIAL PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE beruf_obergruppe (
  id         SERIAL PRIMARY KEY,
  name       TEXT NOT NULL,
  bereich_id INTEGER NOT NULL REFERENCES beruf_bereich(id)
);

-- BUG = Berufsuntergruppe — die Matching-Ebene laut Spec
CREATE TABLE beruf_untergruppe (
  id            SERIAL PRIMARY KEY,
  name          TEXT NOT NULL,
  obergruppe_id INTEGER NOT NULL REFERENCES beruf_obergruppe(id)
);

-- Spezialisierungen: Profile werden hier zugeordnet, Matching auf BUG-Ebene
CREATE TABLE beruf_spezialisierung (
  id             SERIAL PRIMARY KEY,
  name           TEXT NOT NULL,
  untergruppe_id INTEGER NOT NULL REFERENCES beruf_untergruppe(id),
  isco_code      TEXT
);

-- Closure Table für Kompetenz-Hierarchie
-- Speichert alle Vorfahren-Nachfahren-Paare explizit (inkl. Self-Paare mit tiefe=0).
-- Ermöglicht W(s)-Abfragen (Pfad einer Kompetenz bis Root) ohne rekursiven CTE.
-- Änderungen an der Hierarchie aktualisieren nur diese Tabelle — nie person/stelle-Daten.
CREATE TABLE kompetenz_closure (
  vorfahre_id  INTEGER NOT NULL REFERENCES bis_kompetenz(id) ON DELETE CASCADE,
  nachfahre_id INTEGER NOT NULL REFERENCES bis_kompetenz(id) ON DELETE CASCADE,
  tiefe        INTEGER NOT NULL,
  PRIMARY KEY (vorfahre_id, nachfahre_id)
);

-- person und stelle bekommen neue Beruf-Spalte (Referenz auf Spezialisierung)
ALTER TABLE person ADD COLUMN beruf_spezialisierung_id INTEGER REFERENCES beruf_spezialisierung(id);
ALTER TABLE stelle ADD COLUMN beruf_spezialisierung_id INTEGER REFERENCES beruf_spezialisierung(id);
-- Hinweis: beruf_id (Referenz auf bis_beruf) wird in V3 noch nicht gedroppt —
-- erst nach erfolgreicher Datenmigration in V4.
```

### Flyway V4: KO-Felder + Lehrstellenmatching + Geo-Hierarchie

```sql
-- V4__ko_und_lehrstelle_und_geo_hierarchie.sql

-- HINWEIS: beruf_id wird hier NICHT gedroppt (siehe P1 in "Bekannte Probleme").
-- Das DROP erfolgt in V4b__drop_beruf_id.sql, nachdem BerufHierarchieSeedRunner
-- alle beruf_spezialisierung_id-Werte befüllt und validiert hat.

-- Person: Systemattribute für KO-Kriterien
ALTER TABLE person ADD COLUMN vermittlungspost BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE person ADD COLUMN max_bewegungen   INTEGER NOT NULL DEFAULT 999;
ALTER TABLE person ADD COLUMN sucht_lehrstelle BOOLEAN NOT NULL DEFAULT FALSE;

-- Person: ausgeschlossene Arbeitszeitmodelle
CREATE TABLE person_arbeitszeit_ausschluss (
  person_id UUID NOT NULL REFERENCES person(id) ON DELETE CASCADE,
  modell    TEXT NOT NULL,  -- VOLLZEIT | TEILZEIT | GERINGFUEGIG | NACHT | WOCHENENDE
  PRIMARY KEY (person_id, modell)
);

-- Stelle: Typ
ALTER TABLE stelle ADD COLUMN typ TEXT NOT NULL DEFAULT 'STANDARD';  -- STANDARD | LEHRSTELLE

-- Stelle: Arbeitszeitmodelle (pflicht=TRUE → KO wenn Person dieses Modell ausgeschlossen hat)
CREATE TABLE stelle_arbeitszeit (
  stelle_id UUID NOT NULL REFERENCES stelle(id) ON DELETE CASCADE,
  modell    TEXT NOT NULL,
  pflicht   BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (stelle_id, modell)
);

-- Lehrstellenmatching: Referenzdaten
CREATE TABLE interessensgebiet (
  id   SERIAL PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE voraussetzung (
  id   SERIAL PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

-- Person ↔ Interessen/Voraussetzungen
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

-- Stelle ↔ Interessen/Voraussetzungen
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

-- Geo-Hierarchie für Location-ID-Matching (zweite Geo-Stufe neben GPS-Radius)
-- Ebenen: ORT < BEZIRK < BUNDESLAND
-- Verknüpft mit bestehender plz_ort-Tabelle (plz_ort.bundesland, plz_ort.bezirk)
CREATE TABLE geo_location (
  id        SERIAL PRIMARY KEY,
  name      TEXT NOT NULL,
  ebene     TEXT NOT NULL,  -- ORT | BEZIRK | BUNDESLAND
  parent_id INTEGER REFERENCES geo_location(id),
  -- Zentroid für Radius-Fallback (aus bundesland-Tabelle übernehmen)
  lat       DOUBLE PRECISION,
  lon       DOUBLE PRECISION
);

-- person_ort: Location-Hierarchie + Bundesweit
ALTER TABLE person_ort ADD COLUMN geo_location_id INTEGER REFERENCES geo_location(id);
ALTER TABLE person_ort ADD COLUMN bundesweit BOOLEAN NOT NULL DEFAULT FALSE;

-- stelle: Location-Hierarchie
ALTER TABLE stelle ADD COLUMN geo_location_id INTEGER REFERENCES geo_location(id);

-- match_modell: neue Felder
ALTER TABLE match_modell ADD COLUMN score_schwellenwert     DOUBLE PRECISION NOT NULL DEFAULT 0.25;
ALTER TABLE match_modell ADD COLUMN gewicht_lehrberuf       DOUBLE PRECISION NOT NULL DEFAULT 0.20;
ALTER TABLE match_modell ADD COLUMN gewicht_interessen      DOUBLE PRECISION NOT NULL DEFAULT 0.40;
ALTER TABLE match_modell ADD COLUMN gewicht_voraussetzungen DOUBLE PRECISION NOT NULL DEFAULT 0.40;
ALTER TABLE match_modell DROP COLUMN beruf_filter_strikt;
```

### Flyway V4b: beruf_id droppen (nach Datenmigration)

```sql
-- V4b__drop_beruf_id.sql
-- Darf erst ausgeführt werden nachdem BerufHierarchieSeedRunner erfolgreich gelaufen ist
-- und sichergestellt hat dass alle person/stelle-Zeilen eine beruf_spezialisierung_id haben.
-- BerufHierarchieSeedRunner wirft IllegalStateException wenn noch NULL-Werte existieren.

ALTER TABLE person DROP COLUMN IF EXISTS beruf_id;
ALTER TABLE stelle DROP COLUMN IF EXISTS beruf_id;
```

### Flyway V5: Score-Funktionen

```sql
-- V5__score_funktionen.sql
-- Alle Funktionen sind STABLE LANGUAGE SQL → PostgreSQL inlined sie in den aufrufenden Query.
-- Kein separater Function-Scan pro Kandidat — PostgreSQL behandelt es als Set-Operation.

-- OM: Berufsmatching auf BUG-Ebene
-- Spec: "Wenn Schnittmenge der Untergruppen > 0 → Score 1, sonst 0"
CREATE OR REPLACE FUNCTION match_beruf(p_id UUID, s_id UUID)
RETURNS FLOAT LANGUAGE SQL STABLE AS $$
  SELECT CASE WHEN EXISTS (
    SELECT 1
    FROM beruf_spezialisierung bp
    JOIN beruf_spezialisierung bs ON bp.untergruppe_id = bs.untergruppe_id
    WHERE bp.id = (SELECT beruf_spezialisierung_id FROM person WHERE id = p_id)
      AND bs.id = (SELECT beruf_spezialisierung_id FROM stelle  WHERE id = s_id)
  ) THEN 1.0 ELSE 0.0 END
$$;

-- SM: Kompetenzmatching mit Closure Table (Spec-Formel exakt implementiert)
-- SM(J,O) = Σ_{s ∈ M(O)} [ |S(J) ∩ W(s)| / |W(s)| ] / |M(O)|
-- S(J): alle direkt zugeordneten Kompetenzen der Person (person_kompetenz)
-- W(s): Pfad der Stelle-Kompetenz s bis Root (= alle Einträge in kompetenz_closure WHERE nachfahre_id = s)
-- KORREKTUR (P2): beide Subqueries korrelieren explizit über sk.kompetenz_id —
-- kein eigenständiger SELECT-Block der nur einmal ausgewertet wird.
CREATE OR REPLACE FUNCTION match_kompetenz(p_id UUID, s_id UUID)
RETURNS FLOAT LANGUAGE SQL STABLE AS $$
  SELECT COALESCE(AVG(
    (SELECT COUNT(*)::FLOAT
     FROM kompetenz_closure cc
     WHERE cc.nachfahre_id = sk.kompetenz_id
       AND cc.vorfahre_id IN (
         SELECT kompetenz_id FROM person_kompetenz WHERE person_id = p_id
       ))
    /
    NULLIF(
      (SELECT COUNT(*)::FLOAT FROM kompetenz_closure cc2
       WHERE cc2.nachfahre_id = sk.kompetenz_id),
      0
    )
  ), 0.0)
  FROM stelle_kompetenz sk
  WHERE sk.stelle_id = s_id
$$;

-- FM: Interessenmatching für Lehrstellen
-- FM(J,O) = |F(J) ∩ F(O)| / |F(O)|
CREATE OR REPLACE FUNCTION match_interessen(p_id UUID, s_id UUID)
RETURNS FLOAT LANGUAGE SQL STABLE AS $$
  SELECT COALESCE(
    (SELECT COUNT(*)::FLOAT
     FROM person_interesse pi
     JOIN stelle_interesse si ON si.interessensgebiet_id = pi.interessensgebiet_id
     WHERE pi.person_id = p_id AND si.stelle_id = s_id)
    /
    NULLIF((SELECT COUNT(*)::FLOAT FROM stelle_interesse WHERE stelle_id = s_id), 0),
    0.0
  )
$$;

-- QM: Voraussetzungsmatching für Lehrstellen
-- QM(J,O) = |Q(J) ∩ Q(O)| / |Q(O)|
CREATE OR REPLACE FUNCTION match_voraussetzungen(p_id UUID, s_id UUID)
RETURNS FLOAT LANGUAGE SQL STABLE AS $$
  SELECT COALESCE(
    (SELECT COUNT(*)::FLOAT
     FROM person_voraussetzung pv
     JOIN stelle_voraussetzung sv ON sv.voraussetzung_id = pv.voraussetzung_id
     WHERE pv.person_id = p_id AND sv.stelle_id = s_id)
    /
    NULLIF((SELECT COUNT(*)::FLOAT FROM stelle_voraussetzung WHERE stelle_id = s_id), 0),
    0.0
  )
$$;
```

### Flyway V6: Performance-Indizes

```sql
-- V6__indizes.sql

-- Geo (kritischster Pfad — PostGIS Spatial Index)
CREATE INDEX idx_stelle_standort     ON stelle     USING GIST(standort);
CREATE INDEX idx_person_ort_standort ON person_ort USING GIST(standort);

-- Closure Table (für W(s)-Abfragen in match_kompetenz)
CREATE INDEX idx_closure_nachfahre ON kompetenz_closure(nachfahre_id);
CREATE INDEX idx_closure_vorfahre  ON kompetenz_closure(vorfahre_id);

-- BUG-Lookup für Beruf-Matching
CREATE INDEX idx_beruf_spec_ug ON beruf_spezialisierung(untergruppe_id);

-- Muss-Kompetenz-KO (Partial Index — nur Pflicht-Kompetenzen)
CREATE INDEX idx_stelle_komp_pflicht ON stelle_kompetenz(stelle_id) WHERE pflicht = TRUE;
CREATE INDEX idx_person_komp_pid     ON person_kompetenz(person_id);

-- Location-Hierarchie
CREATE INDEX idx_geo_loc_parent  ON geo_location(parent_id);
CREATE INDEX idx_stelle_geo_loc  ON stelle(geo_location_id);
CREATE INDEX idx_port_geo_loc    ON person_ort(geo_location_id);

-- Sortierung nach erstellt_am
CREATE INDEX idx_stelle_erstellt_am ON stelle(erstellt_am DESC);
CREATE INDEX idx_person_erstellt_am ON person(erstellt_am DESC);
```

---

## BIS-Seed-Daten (Phase 2)

Das BIS rendert seine Inhalte via JavaScript — ein einfaches HTTP-Scraping funktioniert nicht.
Das Seed-Script muss mit einem Headless-Browser arbeiten.

### Scraping-Strategie

```
scripts/scrape_bis.py
```

- **Technologie**: Playwright (Python) — `pip install playwright`
- **Ziel-URLs**:
  - Berufsbereiche: `https://bis.ams.or.at/bis/berufsprofile-nach-berufsbereichen`
  - Kompetenzen nach Bereichen: `https://bis.ams.or.at/bis/kompetenzen-nach-bereichen`
  - Einzelne Berufsprofile: `https://bis.ams.or.at/bis/beruf/{id}-{slug}` (für BUG-Zuordnung)
- **Output**: `backend/src/main/resources/seed/bis_berufe_hierarchie.json` + `bis_kompetenzen.json`

### JSON-Format Beruf-Hierarchie

```json
{
  "bereiche": [
    {
      "name": "Informations- und Kommunikationstechnologie",
      "obergruppen": [
        {
          "name": "Softwareentwicklung und Programmierung",
          "untergruppen": [
            {
              "name": "Anwendungsentwicklung",
              "spezialisierungen": [
                { "name": "AnwendungsbetreuerIn", "isco_code": "2511" },
                { "name": "SoftwareentwicklerIn", "isco_code": "2512" }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

### JSON-Format Kompetenzen

Kompetenzen werden mit ihrer vollen Pfad-Hierarchie aus dem BIS exportiert:

```json
[
  { "id": 119, "name": "Programmiersprachen-Kenntnisse", "bereich": "Fachliche Kompetenzen", "parent_id": null, "typ": "FACHLICH" },
  { "id": 301, "name": "Java",                           "bereich": "Fachliche Kompetenzen", "parent_id": 119,  "typ": "FACHLICH" },
  { "id": 302, "name": "Spring Boot",                    "bereich": "Fachliche Kompetenzen", "parent_id": 301,  "typ": "FACHLICH" }
]
```

### Seed-Runner Erweiterungen

- `BisSeedRunner` — bestehend, wird erweitert: nach Berufe/Kompetenzen-Import auch `kompetenz_closure` befüllen:

```sql
-- Self-Paare (tiefe=0)
INSERT INTO kompetenz_closure (vorfahre_id, nachfahre_id, tiefe)
SELECT id, id, 0 FROM bis_kompetenz
ON CONFLICT DO NOTHING;

-- Direkte Eltern-Kind-Paare (tiefe=1)
INSERT INTO kompetenz_closure (vorfahre_id, nachfahre_id, tiefe)
SELECT parent_id, id, 1 FROM bis_kompetenz
WHERE parent_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Transitiver Abschluss für tiefere Hierarchien (rekursiver CTE, einmalig beim Seed)
WITH RECURSIVE closure AS (
  SELECT vorfahre_id, nachfahre_id, tiefe FROM kompetenz_closure
  UNION ALL
  SELECT c.vorfahre_id, kc.nachfahre_id, c.tiefe + kc.tiefe
  FROM closure c
  JOIN kompetenz_closure kc ON kc.vorfahre_id = c.nachfahre_id AND kc.tiefe = 1
  WHERE c.tiefe + kc.tiefe <= 10  -- Schutz vor Zyklen
)
-- KORREKTUR (P5): kein SELECT DISTINCT — GROUP BY genügt und ist semantisch klarer
INSERT INTO kompetenz_closure
SELECT vorfahre_id, nachfahre_id, MIN(tiefe)
FROM closure GROUP BY vorfahre_id, nachfahre_id
ON CONFLICT DO NOTHING;
```

- `BerufHierarchieSeedRunner` — neu: liest `bis_berufe_hierarchie.json`, importiert alle 4 Ebenen; setzt danach `person.beruf_spezialisierung_id` und `stelle.beruf_spezialisierung_id` anhand von Name-Matching auf die alten `bis_beruf`-Einträge.

- `DevDataSeeder` — neu generiert alle Testdaten mit `beruf_spezialisierung_id` statt `beruf_id`; min. 10 Lehrstellen mit Interessen + Voraussetzungen.

---

## Matching-Query (ein Roundtrip, vollständig)

Dieser Query ersetzt den bisherigen monolithischen CTE in `MatchRepository.java`.
Stellen für eine Person — symmetrischer Query (Personen für Stelle) tauscht die Rollen.

```sql
-- Parameter: :person_id, :w_beruf, :w_kompetenz, :w_lehrberuf, :w_interessen,
--            :w_voraussetzungen, :schwellenwert, :sort_col ('score'|'erstellt_am'), :sort_dir ('ASC'|'DESC')
WITH

-- KO-Stufe 1: Geo-Filter
-- Reihenfolge: Bundesweit (cheapest check) → GPS-Radius (Spatial Index) → Location-Hierarchie
geo_kandidaten AS (
  SELECT DISTINCT s.id
  FROM stelle s
  WHERE EXISTS (
    SELECT 1 FROM person_ort po
    WHERE po.person_id = :person_id
      AND (
        po.bundesweit = TRUE
        OR ST_DWithin(po.standort, s.standort, po.umkreis_km * 1000)
        OR (
          po.geo_location_id IS NOT NULL
          AND s.geo_location_id IS NOT NULL
          AND EXISTS (
            -- Stelle liegt im Teilbaum der gesuchten Location der Person
            -- (z.B. Person sucht in Bundesland Wien, Stelle ist in Bezirk Innere Stadt)
            WITH RECURSIVE ancestors AS (
              SELECT id, parent_id FROM geo_location WHERE id = s.geo_location_id
              UNION ALL
              SELECT gl.id, gl.parent_id FROM geo_location gl
              JOIN ancestors a ON gl.id = a.parent_id
            )
            SELECT 1 FROM ancestors WHERE id = po.geo_location_id
          )
        )
      )
  )
),

-- KO-Stufe 2: Muss-Kompetenz-KO
-- Ausgeschlossen: Stellen bei denen mindestens eine Pflicht-Kompetenz fehlt.
-- Die Person "besitzt" eine Kompetenz c wenn c in person_kompetenz liegt —
-- oder wenn ein Nachfahre von c in person_kompetenz liegt (via Closure).
muss_ko AS (
  SELECT DISTINCT sk.stelle_id
  FROM stelle_kompetenz sk
  WHERE sk.pflicht = TRUE
    AND sk.stelle_id IN (SELECT id FROM geo_kandidaten)
    AND NOT EXISTS (
      SELECT 1 FROM person_kompetenz pk
      JOIN kompetenz_closure cc ON cc.vorfahre_id = pk.kompetenz_id
      WHERE pk.person_id = :person_id
        AND cc.nachfahre_id = sk.kompetenz_id
    )
),

-- KO-Stufe 3: Arbeitszeit-KO
arbeitszeit_ko AS (
  SELECT DISTINCT sa.stelle_id
  FROM stelle_arbeitszeit sa
  WHERE sa.pflicht = TRUE
    AND sa.stelle_id IN (SELECT id FROM geo_kandidaten)
    AND sa.stelle_id NOT IN (SELECT stelle_id FROM muss_ko)
    AND sa.modell IN (
      SELECT modell FROM person_arbeitszeit_ausschluss WHERE person_id = :person_id
    )
),

-- Verbleibende Kandidaten nach allen KO-Filtern
kandidaten AS (
  SELECT id FROM geo_kandidaten
  EXCEPT SELECT stelle_id FROM muss_ko
  EXCEPT SELECT stelle_id FROM arbeitszeit_ko
),

-- Score-Berechnung: eine Zeile pro Kandidat, ein DB-Roundtrip
-- KORREKTUR (P3): Score-Funktionen via CROSS JOIN LATERAL einmalig aufrufen —
-- verhindert doppelten Funktionsaufruf für Score + Breakdown.
scores AS (
  SELECT
    s.id,
    s.titel,
    s.unternehmen,
    s.typ,
    s.erstellt_am,
    bd.om,
    bd.sm,
    bd.fm,
    bd.qm,
    -- Gesamt-Score: je nach Stellen-Typ unterschiedliche Gewichtung (Spec §7)
    CASE s.typ
      WHEN 'STANDARD' THEN
        (:w_beruf     * bd.om
       + :w_kompetenz * bd.sm)
        / NULLIF(:w_beruf + :w_kompetenz, 0)
      WHEN 'LEHRSTELLE' THEN
        (:w_lehrberuf       * bd.om
       + :w_interessen      * bd.fm
       + :w_voraussetzungen * bd.qm)
        / NULLIF(:w_lehrberuf + :w_interessen + :w_voraussetzungen, 0)
    END AS score
  FROM stelle s
  CROSS JOIN LATERAL (
    SELECT
      match_beruf(:person_id, s.id)           AS om,
      match_kompetenz(:person_id, s.id)       AS sm,
      match_interessen(:person_id, s.id)      AS fm,
      match_voraussetzungen(:person_id, s.id) AS qm
  ) bd
  WHERE s.id IN (SELECT id FROM kandidaten)
)

-- Score-Schwellenwert als harter WHERE-Filter (bleibt auch bei Datum-Sortierung aktiv)
SELECT * FROM scores
WHERE score >= :schwellenwert
ORDER BY
  -- :sort_col und :sort_dir werden in Java als Enum validiert — kein String-Concat, kein SQL-Injection-Risiko
  CASE WHEN :sort_col = 'score'       AND :sort_dir = 'DESC' THEN score       END DESC,
  CASE WHEN :sort_col = 'score'       AND :sort_dir = 'ASC'  THEN score       END ASC,
  CASE WHEN :sort_col = 'erstellt_am' AND :sort_dir = 'DESC' THEN erstellt_am END DESC,
  CASE WHEN :sort_col = 'erstellt_am' AND :sort_dir = 'ASC'  THEN erstellt_am END ASC
LIMIT 50

-- PERFORMANCE-HINWEIS (für Produktion falls < 1s nicht erreicht wird):
-- Der CASE-basierte ORDER BY verhindert Index-Nutzung für inaktive Sortier-Spalten.
-- Lösung: zwei separate Query-Strings in MatchRepository (je nach SortierParameter),
-- sodass PostgreSQL idx_stelle_erstellt_am für Datum-Sortierung nutzen kann.
-- Messbar via EXPLAIN ANALYZE: bei Datum-Sortierung muss "Index Scan using idx_stelle_erstellt_am"
-- erscheinen, nicht "Sort (cost=...)".
```

---

## Closure Table Pflege

Alle drei Operationen laufen atomar in einer Transaktion in `KompetenzClosureService`.

### Neuen Knoten einfügen (Kind von parent_id)

```sql
-- Self-Pair
INSERT INTO kompetenz_closure VALUES (:neu_id, :neu_id, 0);

-- Alle Vorfahren des Eltern-Knotens werden auch Vorfahren des neuen Knotens
INSERT INTO kompetenz_closure (vorfahre_id, nachfahre_id, tiefe)
SELECT vorfahre_id, :neu_id, tiefe + 1
FROM kompetenz_closure WHERE nachfahre_id = :parent_id;
```

### Knoten löschen (inkl. gesamten Teilbaum)

```sql
-- Alle Nachfahren des zu löschenden Knotens ermitteln, dann deren Closure-Einträge entfernen
-- (CASCADE auf bis_kompetenz → kompetenz_closure übernimmt den Rest)
DELETE FROM kompetenz_closure
WHERE nachfahre_id IN (
  SELECT nachfahre_id FROM kompetenz_closure WHERE vorfahre_id = :del_id
);
DELETE FROM bis_kompetenz WHERE id = :del_id;
```

### Knoten verschieben (neuer parent)

```sql
-- Schritt 1: Alle Pfade entfernen die DURCH den verschobenen Teilbaum von außen kommen
DELETE FROM kompetenz_closure
WHERE nachfahre_id IN (SELECT nachfahre_id FROM kompetenz_closure WHERE vorfahre_id = :node_id)
  AND vorfahre_id  NOT IN (SELECT nachfahre_id FROM kompetenz_closure WHERE vorfahre_id = :node_id);

-- Schritt 2: Neue Verbindungen vom neuen Parent zum verschobenen Teilbaum einsetzen
INSERT INTO kompetenz_closure (vorfahre_id, nachfahre_id, tiefe)
SELECT p.vorfahre_id, c.nachfahre_id, p.tiefe + c.tiefe + 1
FROM kompetenz_closure p, kompetenz_closure c
WHERE p.nachfahre_id = :new_parent_id
  AND c.vorfahre_id  = :node_id
ON CONFLICT DO NOTHING;
```

---

## Erweiterbarkeit: Neues Score-Kriterium

Das ist die vollständige Checkliste für jede zukünftige Erweiterung des Scoring-Modells:

1. **Flyway-Migration**: `CREATE OR REPLACE FUNCTION match_neues_kriterium(p_id UUID, s_id UUID) RETURNS FLOAT LANGUAGE SQL STABLE AS $$ ... $$`
2. **Flyway-Migration**: `ALTER TABLE match_modell ADD COLUMN gewicht_neues_kriterium DOUBLE PRECISION DEFAULT 0`
3. **`MatchRepository.java`**: eine Zeile im `scores`-CTE ergänzen: `+ :w_neu * match_neues_kriterium(:person_id, s.id)`; neuen Parameter aus `MatchModell`-Entity lesen
4. **`MatchModell.java`**: neues Feld `gewichtNeuesKriterium`
5. **`MatchModellRequest`**: neues Feld
6. **`MatchResult.java`**: neues Breakdown-Feld
7. **`match-modell/editor.html`**: neuer Slider

Kein Eingriff in bestehende Funktionen, KO-Filter oder andere Services.

---

## Neue und geänderte Klassen

### Komplett neu

| Klasse | Package | Zweck |
|--------|---------|-------|
| `BerufBereich` | `domain/bis` | JPA-Entity |
| `BerufObergruppe` | `domain/bis` | JPA-Entity |
| `BerufUntergruppe` | `domain/bis` | JPA-Entity |
| `BerufSpezialisierung` | `domain/bis` | JPA-Entity — Zuordnungsebene für Person/Stelle |
| `GeoLocation` | `domain/geo` | JPA-Entity mit `@ManyToOne parent` |
| `Interessensgebiet` | `domain/bis` | JPA-Entity (read-only nach Seed) |
| `Voraussetzung` | `domain/bis` | JPA-Entity (read-only nach Seed) |
| `KompetenzClosureService` | `domain/bis` | Einfügen/Löschen/Verschieben mit Closure-Pflege |
| `BerufHierarchieSeedRunner` | `seed` | importiert `bis_berufe_hierarchie.json` |
| `StelleTyp` | `domain/stelle` | Enum: `STANDARD`, `LEHRSTELLE` |
| `ArbeitszeitModell` | `domain/stelle` | Enum: `VOLLZEIT`, `TEILZEIT`, `GERINGFUEGIG`, `NACHT`, `WOCHENENDE` |
| `SortierKriterium` | `domain/matching` | Enum: `SCORE`, `ERSTELLT_AM` — verhindert SQL-Injection bei ORDER BY |
| `SortierRichtung` | `domain/matching` | Enum: `ASC`, `DESC` |
| `SortierParameter` | `domain/matching` | Record: `SortierKriterium`, `SortierRichtung`; Default `SCORE DESC` |

### Komplett ersetzt

| Klasse | Was sich ändert |
|--------|-----------------|
| `MatchRepository.java` | Neuer Query (oben); zwei Methoden je mit `SortierParameter` |
| `MatchResult.java` | Neues Record: `score`, `om`, `sm`, `fm`, `qm`, `stelleTyp`, `erstelltAm` |
| `MatchModell.java` | Neue Felder; `berufFilterStrikt` entfernt |
| `MatchModellRequest` | Neue Felder; `berufFilterStrikt` entfernt |

### Erweitert

| Klasse | Was sich ändert |
|--------|-----------------|
| `Person.java` | Neue Felder: `vermittlungspost`, `maxBewegungen`, `suchtLehrstelle`; neue `@OneToMany`: `arbeitszeitAusschluesse`, `interessen`, `voraussetzungen`; `berufId` → `berufSpezialisierungId` |
| `PersonOrt.java` | Neue Felder: `geoLocationId` (Integer), `bundesweit` (Boolean) |
| `PersonService.java` | CRUD für Interessen, Voraussetzungen, Arbeitszeitausschlüsse; `KompetenzClosureService` aufrufen bei Kompetenz-Änderungen |
| `Stelle.java` | `typ` (Enum); neue `@OneToMany`: `arbeitszeiten`, `interessen`, `voraussetzungen`; `berufId` → `berufSpezialisierungId` |
| `StelleService.java` | Analog PersonService |
| `MatchService.java` | System-KO vor Query prüfen: `if (!person.isVermittlungspost() || person.getMaxBewegungen() == 0) return List.of()`; `SortierParameter` annehmen |
| `MatchController.java` | Query-Parameter `?sortBy=score&sortDir=desc` (Default: SCORE/DESC) |
| `BisSeedRunner.java` | Nach Berufe/Kompetenzen-Import: `kompetenz_closure` befüllen (SQL oben) |
| `BisRestController.java` | `/api/bis/berufe` liefert jetzt Spezialisierungen mit Pfad-Label |

---

## Implementierungs-Checkliste

### Phase 1: Schema-Migration

- [ ] Flyway `V3__beruf_hierarchie_und_closure.sql` anlegen — Tabellen aus "Flyway V3"-Sektion oben
- [ ] Flyway `V4__ko_und_lehrstelle_und_geo_hierarchie.sql` anlegen — Tabellen aus "Flyway V4"-Sektion oben (ohne DROP COLUMN — siehe P1)
- [ ] `MatchModell.java` und `MatchModellRequest`: Feld `berufFilterStrikt` entfernen **im selben Commit** wie V4 eingecheckt wird (P6: Hibernate Validation schlägt sonst fehl)
- [ ] `docker compose up` — Flyway läuft durch, Schema valide (Hibernate Validation darf noch nicht aktiv sein: `ddl-auto=update` temporär)

### Phase 2: BIS-Seed-Daten

- [ ] `scripts/scrape_bis.py` schreiben — Playwright scrapt BIS:
  - Berufsbereiche → `bis_berufe_hierarchie.json` (Format oben)
  - Kompetenzen nach Bereichen → `bis_kompetenzen.json` (Format oben)
  - Hinweis: BIS ist JavaScript-rendered, `requests`/`httpx` reicht nicht — Playwright mit `page.wait_for_selector()` verwenden
- [ ] Script ausführen, JSON-Dateien nach `backend/src/main/resources/seed/` einchecken
- [ ] `BisSeedRunner` erweitern: nach `bis_kompetenz`-Import `kompetenz_closure` befüllen (korrigiertes SQL aus "Seed-Runner Erweiterungen"-Sektion — kein `SELECT DISTINCT`, P5)
- [ ] `BerufHierarchieSeedRunner` implementieren: liest `bis_berufe_hierarchie.json`, importiert alle 4 Hierarchie-Ebenen, migriert `person.beruf_id` → `beruf_spezialisierung_id` via Name-Matching; wirft `IllegalStateException` wenn nach dem Migration noch Zeilen mit `beruf_spezialisierung_id IS NULL` existieren (P1-Guard)
- [ ] Nach erfolgreichem `BerufHierarchieSeedRunner`-Lauf: Flyway `V4b__drop_beruf_id.sql` anlegen und deploy — erst jetzt darf `beruf_id` gedroppt werden (P1)
- [ ] `DevDataSeeder` anpassen: `beruf_spezialisierung_id` statt `beruf_id`; neue Felder befüllen; min. 10 Lehrstellen mit Interessen + Voraussetzungen

### Phase 3: JPA-Entities + Services

- [ ] `BerufBereich.java`, `BerufObergruppe.java`, `BerufUntergruppe.java`, `BerufSpezialisierung.java` — JPA-Entities mit `@ManyToOne`-Beziehungen
- [ ] Repositories für alle 4 Hierarchie-Ebenen
- [ ] `GeoLocation.java` — JPA-Entity mit `@ManyToOne parent`; Repository mit `findByEbene(String)`
- [ ] `Interessensgebiet.java`, `Voraussetzung.java` — JPA-Entities mit Repositories
- [ ] `StelleTyp.java`, `ArbeitszeitModell.java`, `SortierKriterium.java`, `SortierRichtung.java`, `SortierParameter.java` — Enums + Record
- [ ] `Person.java` anpassen — neue Felder + Assoziationen (siehe "Erweitert"-Tabelle)
- [ ] `PersonOrt.java` anpassen — `geoLocationId`, `bundesweit`
- [ ] `PersonService.java` anpassen — neue CRUD-Methoden für Interessen, Voraussetzungen, Arbeitszeitausschlüsse
- [ ] `Stelle.java` anpassen — `typ`, neue Assoziationen
- [ ] `StelleService.java` anpassen — analog PersonService
- [ ] `MatchModell.java` anpassen — neue Felder, `berufFilterStrikt` entfernen
- [ ] `MatchModellRequest` anpassen — neue Felder, `berufFilterStrikt` entfernen
- [ ] `BisRestController` anpassen — `/api/bis/berufe` liefert Spezialisierungen mit Pfad-Label (z.B. "IKT > Softwareentwicklung > Anwendungsentwicklung > SoftwareentwicklerIn")

### Phase 4: Closure-Table-Pflege

- [ ] `KompetenzClosureService.java` implementieren — drei `@Transactional`-Methoden:
  - `einfuegenKind(int kompetenzId, int parentId)` — SQL aus "Neuen Knoten einfügen"-Sektion
  - `loeschen(int kompetenzId)` — SQL aus "Knoten löschen"-Sektion
  - `verschieben(int kompetenzId, int newParentId)` — SQL aus "Knoten verschieben"-Sektion
- [ ] `PersonService.kompetenzHinzufuegen()` / `kompetenzEntfernen()` — **keine** Closure-Änderung nötig (Person-Kompetenzen sind Blatt-Zuordnungen, nicht Hierarchie-Knoten). Closure wird nur bei `bis_kompetenz`-Änderungen via `KompetenzClosureService` aktualisiert.
- [ ] Neuen Endpunkt für Kompetenz-CRUD: `POST /api/bis/kompetenzen`, `PUT /api/bis/kompetenzen/{id}`, `DELETE /api/bis/kompetenzen/{id}` — rufen `KompetenzClosureService` auf. (Für die Demo: nur über Swagger-UI zugänglich, kein eigenes Frontend)

### Phase 5: PostgreSQL Score-Funktionen

- [ ] Flyway `V5__score_funktionen.sql` anlegen — alle 4 Funktionen aus "Flyway V5"-Sektion oben (korrigierte `match_kompetenz`-Version mit explizit korrelierten Subqueries, P2)
- [ ] Funktionen einzeln in psql testen: `SELECT match_kompetenz('uuid-person', 'uuid-stelle')` mit bekannten Testdaten, Ergebnis manuell verifizieren
- [ ] `EXPLAIN (ANALYZE, BUFFERS)` für `match_kompetenz`: muss `Index Scan using idx_closure_nachfahre` zeigen, kein `Seq Scan` auf `kompetenz_closure`

### Phase 6: MatchRepository + MatchService + Sortierung

- [ ] `MatchResult.java` komplett neu — Record mit: `targetId`, `targetName`, `score`, `stelleTyp`, `erstelltAm`, `breakdown` (inner Record: `om`, `sm`, `fm`, `qm`)
- [ ] `MatchRepository.java` komplett neu — zwei Methoden mit vollständigem Query aus "Matching-Query"-Sektion (LATERAL-Version, P3 — Score-Funktionen werden nur einmal pro Kandidat aufgerufen):
  - `findTopStellenForPerson(UUID personId, MatchModell modell, SortierParameter sort)`
  - `findTopPersonenForStelle(UUID stelleId, MatchModell modell, SortierParameter sort)` (Rollen getauscht)
  - `ORDER BY`-Spalte wird in Java aus `SortierParameter`-Enum zu String `'score'` / `'erstellt_am'` aufgelöst — kein String-Concat in SQL
- [ ] `MatchService.java` anpassen — System-KO vor Query: `if (!person.isVermittlungspost() || person.getMaxBewegungen() == 0) return List.of()`; `SortierParameter` annehmen
- [ ] `MatchController.java` anpassen — Query-Parameter `?sortBy=score&sortDir=desc`; `SortierKriterium.valueOf(sortBy.toUpperCase())` mit Fallback auf Default

### Phase 7: Lehrstellenmatching

- [x] `stellen/formular.html` — Typ-Toggle (STANDARD / LEHRSTELLE); bei LEHRSTELLE: Kompetenz-Bereich per HTMX `hx-swap` ausblenden, Interessen- + Voraussetzungs-Auswahl einblenden
- [x] `personen/formular.html` — `suchtLehrstelle`-Checkbox; bei TRUE: Interessen- + Voraussetzungs-Auswahl einblenden
- [x] `personen/matches.html` + `stellen/matches.html` — Breakdown-Anzeige je nach `stelleTyp`: STANDARD zeigt OM/SM, LEHRSTELLE zeigt OM/FM/QM
- [x] `match-modell/editor.html` — Lehrstellen-Gewichte (`gewichtLehrberuf`, `gewichtInteressen`, `gewichtVoraussetzungen`) als eigene Slider-Gruppe

### Phase 8: Frontend anpassen

- [x] `personen/formular.html` — Beruf-Autocomplete auf Spezialisierungen umstellen (Pfad-Label)
- [x] `personen/formular.html` — `person_ort`-Formular: neues Dropdown für `geo_location` (Bundesland/Bezirk aus `/api/geo/bundeslaender` + neuer Endpoint `/api/geo/locations`); Bundesweit-Checkbox (versteckt Koordinaten-Felder per HTMX)
- [x] `stellen/formular.html` — analog: Beruf-Autocomplete, `geo_location`-Dropdown, Arbeitszeitmodelle
- [x] `personen/matches.html` + `stellen/matches.html` — Sortier-Buttons (Score ↑↓, Datum ↑↓) als HTMX-Links mit `hx-get` + Query-Parametern; aktive Sortierung visuell hervorheben (DaisyUI `btn-active`)
- [x] `match-modell/editor.html` — `berufFilterStrikt`-Checkbox entfernen; `scoreSchwellenwert`-Slider (0–100%) hinzufügen
- [x] `index.html` — Dashboard: Lehrstellenanzahl als eigene Stat-Kachel

### Phase 9: Tests

- [ ] `KompetenzClosureServiceTest.java` — Unit-Tests (Testcontainers PostgreSQL):
  - Neuen Root-Knoten einfügen → Self-Pair vorhanden
  - Kind einfügen → Vorfahren-Paare korrekt
  - Blatt löschen → Closure-Einträge weg, Eltern unberührt
  - Inneren Knoten löschen → gesamter Teilbaum entfernt
  - Knoten verschieben → alte Pfade weg, neue korrekt
- [ ] `ScoreFunctionIT.java` (Testcontainers) — alle 4 Funktionen mit bekannten Eingaben:
  - `match_beruf`: gleiche BUG → 1.0, unterschiedliche BUG → 0.0
  - `match_kompetenz`: exaktes Match → 1.0, Teilmatch via Hierarchie korrekt berechnet, leere Kompetenzen → 0.0
  - `match_interessen`, `match_voraussetzungen`: Grenzfälle (leer, voll, partial)
- [ ] `MatchRepositoryIT.java` (Testcontainers) — alle KO-Szenarien:
  - Geo außerhalb → nicht in Ergebnissen
  - Bundesweit → alle Stellen durch
  - Location-Hierarchie: Person sucht in Bundesland, Stelle liegt in Bezirk → Match
  - Muss-Kompetenz fehlt → KO
  - Arbeitszeit-KO greift
  - Score < Schwellenwert → nicht in Ergebnissen
  - Sortierung nach Score DESC und erstellt_am ASC korrekt
  - Lehrstellen-Score: FM + QM korrekt berechnet
- [ ] `PersonApiIT.java` (Testcontainers) — Person erstellen mit Kompetenzen, Closure-Tabelle korrekt befüllt

### Phase 10: Performance-Indizes + Load-Test

- [ ] Flyway `V6__indizes.sql` anlegen — alle Indizes aus "Flyway V6"-Sektion oben
- [ ] Load-Test-Script `scripts/seed_load_test.py` — generiert via `psycopg2 COPY` (kein INSERT für 1 Mio. Zeilen):
  - 1 Mio. Personen mit realistischer Verteilung über Berufe, Kompetenzen, Geo-Koordinaten (österreichisches Bounding Box)
  - 1 Mio. Stellen analog
- [ ] Match-Endpunkt messen: `GET /personen/{id}/matches` muss < 1s bei 1 Mio. Datensätzen
- [ ] `EXPLAIN (ANALYZE, BUFFERS)` für beide Match-Queries dokumentieren:
  - `geo_kandidaten`-CTE: muss `Index Scan using idx_stelle_standort` (GiST) zeigen
  - `muss_ko`-CTE: muss `Index Scan using idx_stelle_komp_pflicht` zeigen
  - `match_kompetenz`-Funktion: muss inlined sein (kein separater `Function Scan`-Knoten)
- [ ] Falls < 1s nicht erreicht wird — Geo-Hierarchie-EXISTS optimieren (P4): `geo_location_closure`-Tabelle anlegen (analog `kompetenz_closure`), rekursiven EXISTS durch einfachen Index-Lookup ersetzen; `EXPLAIN ANALYZE` muss danach `Index Scan using geo_location_closure_pkey` zeigen

---

## Wichtige Hinweise & Gotchas

- **Closure-Table-Initialfüllung ist einmalig und muss vor V5 laufen**: Die Score-Funktion `match_kompetenz` liest aus `kompetenz_closure`. Wenn die Tabelle leer ist, gibt `match_kompetenz` immer 0.0 zurück. `BisSeedRunner` muss die Closure befüllen bevor die App in Betrieb geht.

- **`match_kompetenz` Inlining verifizieren**: `LANGUAGE SQL STABLE` ist notwendig aber nicht hinreichend für Inlining. `EXPLAIN ANALYZE` im Load-Test muss zeigen dass kein separater `Function Scan`-Knoten erscheint. Falls doch: Funktionen auf `BEGIN ATOMIC ... END`-Syntax (PostgreSQL 14+) umstellen oder als CTE direkt in den Haupt-Query integrieren.

- **ORDER BY Performance-Hinweis ist im Query kommentiert**: Der CASE-basierte ORDER BY verhindert Index-Nutzung für inaktive Sortier-Spalten. Falls < 1s unter Last nicht erreicht wird → zwei separate Query-Strings in `MatchRepository` (je nach `SortierParameter`). Messbar via `EXPLAIN ANALYZE`: bei `erstellt_am`-Sortierung muss `Index Scan using idx_stelle_erstellt_am` erscheinen.

- **BIS-Scraping ist JavaScript-dependent**: `bis.ams.or.at` rendert alle Inhalte via JavaScript. `requests`/`httpx` liefert leere Listen. Playwright mit `page.wait_for_selector('.beruf-list')` oder ähnlichem Selector verwenden. Rate-Limiting beachten (Nominatim-Proxy in JobHoppr wartet 1100ms — ähnlich für BIS).

- **Datenmigration `beruf_id`**: In V3 werden `beruf_spezialisierung_id`-Spalten hinzugefügt, `beruf_id`-Spalten aber noch nicht gedroppt. Erst `BerufHierarchieSeedRunner` migriert die Werte via Name-Matching, danach droppt V4 die alten Spalten. Das ermöglicht einen sicheren Rollback falls das Name-Matching nicht vollständig ist.

- **`DevDataSeeder` neu generiert**: Da der Seeder unter `@Profile("dev")` läuft und `COUNT(*) FROM person > 0` als Idempotenz-Check hat, reicht es die Logik zu ersetzen und die DB einmalig neu zu starten. Keine Datenmigration für Dev-Daten nötig.

- **Keine Tests vorhanden**: Das Testcontainers-Setup in `build.gradle` ist korrekt konfiguriert. Phase 9 schreibt alle Tests von Grund auf — das ist technische Schuld aus dem ursprünglichen JobHoppr-Plan.

- **`berufFilterStrikt` entfällt ersatzlos**: Im neuen Modell ist Beruf ein Score-Kriterium (OM), kein harter Filter. Der Flyway-V4-`DROP COLUMN` ist eine Breaking Change — bestehende `match_modell`-Rows müssen davor manuell bereinigt werden (oder via `ALTER TABLE match_modell DROP COLUMN beruf_filter_strikt` im DDL direkt). **`MatchModell.java` und `MatchModellRequest` müssen das Feld `berufFilterStrikt` im selben Commit entfernen wie V4 eingecheckt wird** (P6).

- **Datenmigration `beruf_id` — zweistufig** (P1): V3 fügt `beruf_spezialisierung_id` hinzu. `BerufHierarchieSeedRunner` befüllt die Werte und wirft `IllegalStateException` bei unvollständiger Migration. Erst danach darf V4b (`DROP COLUMN beruf_id`) deployed werden. Diese Trennung vermeidet Datenverlust bei unvollständigem Name-Matching.
