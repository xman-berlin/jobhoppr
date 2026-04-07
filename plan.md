# Plan: JobHoppr — AMS-Matching für Arbeitslose und offene Stellen

## Context

JobHoppr ist eine Webanwendung, die Arbeitssuchende (Personen) mit offenen Stellen matched. Als Datenbasis
für Berufe und Kompetenzen wird das österreichische AMS-Berufsinformationssystem (BIS) verwendet — Berufe
und Kompetenzen werden einmalig als Seed-Daten importiert. Das Matching basiert auf gewichteten Kriterien
(Kompetenzen, Beruf), wobei das Gewichtungsmodell zentral konfigurierbar ist.

Geo ist ein **binärer Pflicht-Filter** (kein Score-Anteil): Liegt keine Ortsangabe der Person (Wohnort
oder Arbeitsort) im konfigurierten Umkreis der Stelle, wird der Match vollständig ausgeblendet.
Beide Seiten (Person, Arbeitgeber) sehen ihre Match-Liste absteigend nach Punktzahl.

Die Matching-Engine ist für **1 Mio.+ Personen und Stellen** ausgelegt: der gesamte Match-Algorithmus
(Geo-Filter, Kompetenz-Score, Beruf-Score, Sortierung) läuft als einzelne PostgreSQL CTE-Query.

## Progress

- [ ] Phase 1: Projektgerüst (Mono-Repo, Docker, Spring Boot + Thymeleaf)
- [ ] Phase 2: Backend — Domain-Modell & CRUD-APIs
- [ ] Phase 3: Seed-Import (BIS, PLZ/Orte, Bundesländer)
- [ ] Phase 4: Matching-Engine (SQL CTE, MatchModell)
- [ ] Phase 5: Frontend (Thymeleaf + HTMX + DaisyUI)
- [ ] Phase 6: Testdaten-Generator & Integration-Tests

---

## Tech Stack

| Layer | Choice |
|-------|--------|
| Frontend | Thymeleaf (server-rendered), HTMX 2.x (Interaktivität), DaisyUI 5 (CSS-Komponenten) |
| Backend | Spring Boot 3.3, Java 21, Gradle |
| Datenbank | PostgreSQL 16 + PostGIS 3.4 |
| ORM | Spring Data JPA + native @Query für Geo/Match |
| Geocoding | Nominatim (OpenStreetMap, kostenlos, kein Key) |
| Geo-Filter | PostGIS `ST_DWithin` auf nativen `GEOGRAPHY(POINT,4326)` Spalten mit GiST-Index |
| Matching | Einzige PostgreSQL CTE-Query (skaliert auf 1M+ Zeilen) |
| Build/Deploy | Docker Compose (backend + postgres) — kein separater Frontend-Container |
| Tests Backend | JUnit 5, Testcontainers (postgis/postgis:16-3.4) |
| Testdaten | Java-Generator in `DevDataSeeder.java` (Spring Profile `dev`) |

---

## Projektstruktur

```
jobhoppr/
├── docker-compose.yml          # backend + postgres services
├── .env.example
│
└── backend/                    # Spring Boot Gradle-Projekt
    ├── build.gradle
    ├── Dockerfile               # multi-stage: Gradle build → JRE 21 slim
    └── src/
        ├── main/
        │   ├── java/at/jobhoppr/
        │   │   ├── JobhopprApplication.java
        │   │   ├── config/
        │   │   │   └── WebConfig.java          # MVC-Konfiguration
        │   │   ├── domain/
        │   │   │   ├── bis/
        │   │   │   │   ├── Beruf.java
        │   │   │   │   ├── Kompetenz.java
        │   │   │   │   ├── BerufRepository.java
        │   │   │   │   ├── KompetenzRepository.java
        │   │   │   │   └── BisRestController.java   # GET /api/bis/berufe?q=, /api/bis/kompetenzen?q=
        │   │   │   ├── geo/
        │   │   │   │   ├── PlzOrt.java              # Entität für PLZ-Lookup-Tabelle
        │   │   │   │   ├── PlzOrtRepository.java
        │   │   │   │   ├── Bundesland.java          # 9 Bundesländer mit Zentroid + Radius
        │   │   │   │   ├── BundeslandRepository.java
        │   │   │   │   └── GeoRestController.java   # GET /api/geo/suche?q=, /api/geo/nominatim?q=
        │   │   │   ├── person/
        │   │   │   │   ├── Person.java
        │   │   │   │   ├── PersonOrt.java           # GEOGRAPHY(POINT,4326) Spalte
        │   │   │   │   ├── PersonKompetenz.java
        │   │   │   │   ├── PersonRepository.java
        │   │   │   │   ├── PersonService.java
        │   │   │   │   └── PersonController.java    # Thymeleaf-Controller /personen
        │   │   │   ├── stelle/
        │   │   │   │   ├── Stelle.java              # GEOGRAPHY(POINT,4326) Spalte
        │   │   │   │   ├── StelleKompetenz.java
        │   │   │   │   ├── StelleRepository.java
        │   │   │   │   ├── StelleService.java
        │   │   │   │   └── StelleController.java    # Thymeleaf-Controller /stellen
        │   │   │   └── matching/
        │   │   │       ├── MatchModell.java
        │   │   │       ├── MatchModellRepository.java
        │   │   │       ├── MatchModellService.java
        │   │   │       ├── MatchModellController.java  # /match-modell
        │   │   │       ├── MatchResult.java            # Record DTO
        │   │   │       ├── MatchRepository.java        # native CTE @Query
        │   │   │       ├── MatchService.java
        │   │   │       └── MatchController.java        # /personen/{id}/matches, /stellen/{id}/matches
        │   │   └── seed/
        │   │       ├── BisSeedRunner.java
        │   │       ├── PlzSeedRunner.java
        │   │       ├── BundeslandSeedRunner.java
        │   │       ├── DevDataSeeder.java              # @Profile("dev") — Testdaten
        │   │       └── data/
        │   │           ├── bis_berufe.json
        │   │           ├── bis_kompetenzen.json
        │   │           └── AT_plz.tsv                  # GeoNames AT.txt (CC-BY 4.0)
        │   └── resources/
        │       ├── application.properties
        │       ├── application-dev.properties
        │       ├── db/migration/                       # Flyway-Migrationen
        │       │   ├── V1__schema.sql
        │       │   ├── V2__geography_columns.sql
        │       │   └── V3__indexes.sql
        │       └── templates/
        │           ├── layout.html                     # Thymeleaf-Layout (DaisyUI, HTMX)
        │           ├── index.html                      # Dashboard
        │           ├── personen/
        │           │   ├── liste.html
        │           │   ├── formular.html
        │           │   └── matches.html
        │           ├── stellen/
        │           │   ├── liste.html
        │           │   ├── formular.html
        │           │   └── matches.html
        │           └── match-modell/
        │               └── editor.html
        └── test/java/at/jobhoppr/
            ├── matching/
            │   └── MatchServiceTest.java
            └── integration/
                ├── PersonApiIT.java
                └── MatchApiIT.java
```

---

## Datenbankschema (PostgreSQL 16 + PostGIS 3.4)

```sql
-- BIS-Referenzdaten (read-only nach Seed)
CREATE TABLE bis_beruf (
  id        INTEGER PRIMARY KEY,
  name      TEXT NOT NULL,
  bereich   TEXT,
  isco_code TEXT
);

CREATE TABLE bis_kompetenz (
  id        INTEGER PRIMARY KEY,
  name      TEXT NOT NULL,
  bereich   TEXT,
  parent_id INTEGER REFERENCES bis_kompetenz(id),
  typ       TEXT   -- FACHLICH | UEBERFACHLICH | ZERTIFIKAT
);

-- PLZ-Lookup (GeoNames AT, CC-BY 4.0)
CREATE TABLE plz_ort (
  plz        CHAR(4)          NOT NULL,
  ort_name   TEXT             NOT NULL,
  bundesland TEXT             NOT NULL,
  bezirk     TEXT,
  lat        DOUBLE PRECISION NOT NULL,
  lon        DOUBLE PRECISION NOT NULL,
  PRIMARY KEY (plz, ort_name)
);

-- Bundesländer (hard-coded seed)
CREATE TABLE bundesland (
  kuerzel      CHAR(3)          PRIMARY KEY,   -- 'W', 'NOE', 'OOE', ...
  name         TEXT             NOT NULL,
  centroid_lat DOUBLE PRECISION NOT NULL,
  centroid_lon DOUBLE PRECISION NOT NULL,
  umkreis_km   DOUBLE PRECISION NOT NULL
);

-- Personen
CREATE TABLE person (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  vorname         TEXT NOT NULL,
  nachname        TEXT NOT NULL,
  email           TEXT,
  beruf_id        INTEGER REFERENCES bis_beruf(id),
  erstellt_am     TIMESTAMPTZ DEFAULT NOW(),
  aktualisiert_am TIMESTAMPTZ DEFAULT NOW()
);

-- Orte einer Person
CREATE TABLE person_ort (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  person_id    UUID   NOT NULL REFERENCES person(id) ON DELETE CASCADE,
  ort_rolle    TEXT   NOT NULL,              -- WOHNORT | ARBEITSORT
  ort_typ      TEXT   NOT NULL,              -- GENAU | REGION
  bezeichnung  TEXT   NOT NULL,
  lat          DOUBLE PRECISION NOT NULL,
  lon          DOUBLE PRECISION NOT NULL,
  umkreis_km   DOUBLE PRECISION NOT NULL,
  standort     GEOGRAPHY(POINT,4326)         -- generiert aus lat/lon, trägt GiST-Index
    GENERATED ALWAYS AS (ST_MakePoint(lon, lat)::geography) STORED
);

CREATE INDEX idx_person_ort_standort ON person_ort USING GIST (standort);
CREATE INDEX idx_person_ort_person_id ON person_ort (person_id);

CREATE TABLE person_kompetenz (
  person_id    UUID    REFERENCES person(id) ON DELETE CASCADE,
  kompetenz_id INTEGER REFERENCES bis_kompetenz(id),
  niveau       TEXT,   -- GRUNDKENNTNISSE | FORTGESCHRITTEN | EXPERTE
  PRIMARY KEY (person_id, kompetenz_id)
);

CREATE INDEX idx_person_kompetenz_kid ON person_kompetenz (kompetenz_id, person_id);

-- Stellen
CREATE TABLE stelle (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  titel           TEXT NOT NULL,
  unternehmen     TEXT,
  beschreibung    TEXT,
  ort_bezeichnung TEXT             NOT NULL,
  ort_lat         DOUBLE PRECISION NOT NULL,
  ort_lon         DOUBLE PRECISION NOT NULL,
  beruf_id        INTEGER REFERENCES bis_beruf(id),
  erstellt_am     TIMESTAMPTZ DEFAULT NOW(),
  aktualisiert_am TIMESTAMPTZ DEFAULT NOW(),
  standort        GEOGRAPHY(POINT,4326)
    GENERATED ALWAYS AS (ST_MakePoint(ort_lon, ort_lat)::geography) STORED
);

CREATE INDEX idx_stelle_standort ON stelle USING GIST (standort);
CREATE INDEX idx_person_beruf_id ON person (beruf_id);
CREATE INDEX idx_stelle_beruf_id ON stelle (beruf_id);

CREATE TABLE stelle_kompetenz (
  stelle_id    UUID    REFERENCES stelle(id) ON DELETE CASCADE,
  kompetenz_id INTEGER REFERENCES bis_kompetenz(id),
  pflicht      BOOLEAN DEFAULT TRUE,
  PRIMARY KEY (stelle_id, kompetenz_id)
);

CREATE INDEX idx_stelle_kompetenz_sid ON stelle_kompetenz (stelle_id, kompetenz_id);

-- Match-Modell (genau ein aktiver Datensatz)
CREATE TABLE match_modell (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name                 TEXT             NOT NULL,
  aktiv                BOOLEAN          DEFAULT FALSE,
  geo_aktiv            BOOLEAN          DEFAULT TRUE,
  beruf_filter_strikt  BOOLEAN          DEFAULT FALSE,  -- TRUE = Beruf als Pflichtfilter
  gewicht_kompetenz    DOUBLE PRECISION DEFAULT 0.75,
  gewicht_beruf        DOUBLE PRECISION DEFAULT 0.25,
  erstellt_am          TIMESTAMPTZ      DEFAULT NOW()
);
```

---

## Matching-Algorithmus (SQL CTE)

Der gesamte Algorithmus läuft als eine PostgreSQL CTE-Query. Kein Java-Heap für Kandidaten.

```sql
WITH stelle_data AS (
  SELECT s.id, s.standort, s.beruf_id,
         COUNT(sk.kompetenz_id)                                    AS total_kompetenzen,
         COUNT(sk.kompetenz_id) FILTER (WHERE sk.pflicht = TRUE)  AS total_pflicht,
         COUNT(sk.kompetenz_id) FILTER (WHERE sk.pflicht = FALSE) AS total_optional
  FROM stelle s
  LEFT JOIN stelle_kompetenz sk ON sk.stelle_id = s.id
  WHERE s.id = :stelleId
  GROUP BY s.id, s.standort, s.beruf_id
),
geo_kandidaten AS (
  -- Pflicht-Filter: mindestens ein PersonOrt im Umkreis der Stelle
  SELECT DISTINCT po.person_id
  FROM person_ort po, stelle_data sd
  WHERE :geoAktiv = FALSE
     OR ST_DWithin(po.standort, sd.standort, po.umkreis_km * 1000)
),
beruf_kandidaten AS (
  -- Optionaler Strict-Beruf-Filter (beruf_filter_strikt)
  SELECT p.id AS person_id, p.beruf_id
  FROM person p
  JOIN geo_kandidaten gk ON gk.person_id = p.id
  WHERE :berufFilterStrikt = FALSE
     OR p.beruf_id = (SELECT beruf_id FROM stelle_data)
),
kompetenz_scores AS (
  SELECT
    bk.person_id,
    bk.beruf_id,
    COALESCE(
      (COUNT(pk.kompetenz_id) FILTER (WHERE sk.pflicht = TRUE)  * 2.0
       + COUNT(pk.kompetenz_id) FILTER (WHERE sk.pflicht = FALSE))
      / NULLIF((SELECT total_pflicht * 2 + total_optional FROM stelle_data), 0),
      0.0
    ) AS kompetenz_score
  FROM beruf_kandidaten bk
  LEFT JOIN person_kompetenz pk ON pk.person_id = bk.person_id
  LEFT JOIN stelle_kompetenz sk
    ON sk.kompetenz_id = pk.kompetenz_id
   AND sk.stelle_id = (SELECT id FROM stelle_data)
  GROUP BY bk.person_id, bk.beruf_id
)
SELECT
  ks.person_id,
  p.vorname || ' ' || p.nachname                                          AS person_name,
  CASE WHEN ks.beruf_id = (SELECT beruf_id FROM stelle_data) THEN 1.0
       ELSE 0.0 END                                                        AS beruf_score,
  ks.kompetenz_score,
  (:gewichtBeruf * CASE WHEN ks.beruf_id = (SELECT beruf_id FROM stelle_data)
                        THEN 1.0 ELSE 0.0 END
   + :gewichtKompetenz * ks.kompetenz_score)
  / NULLIF(:gewichtBeruf + :gewichtKompetenz, 0)                          AS gesamt_score
FROM kompetenz_scores ks
JOIN person p ON p.id = ks.person_id
ORDER BY gesamt_score DESC
LIMIT 50;
```

Die symmetrische Query für `findTopStellenForPerson` funktioniert analog (Rollen vertauscht).

---

## Geocoding (Nominatim)

Zwei Geo-Lookup-Wege für die PLZ/Ort-Eingabe:

1. **PLZ-Suche** (`GET /api/geo/suche?q=1010`): Sucht in der lokalen `plz_ort`-Tabelle (GeoNames-Seed).
   Sofortantwort ohne externe API, für PLZ und Ortsnamen.

2. **Nominatim-Geocoding** (`GET /api/geo/nominatim?q=Wien+Mariahilfer+Straße+10`):
   Ruft `https://nominatim.openstreetmap.org/search?q=...&countrycodes=at&format=json` auf.
   Rate-Limit: 1 req/sec (via `RestTemplate` mit 1s-Delay).
   Liefert präzise Koordinaten für Adressen.

Im Frontend: HTMX-Autocomplete sucht zuerst lokal (PLZ), bietet dann "Genaue Adresse eingeben" → Nominatim.

---

## Bundesländer (hard-coded Seed)

| Kürzel | Name | Zentroid lat | Zentroid lon | Umkreis km |
|--------|------|-------------|-------------|------------|
| W      | Wien | 48.2082 | 16.3738 | 15 |
| NOE    | Niederösterreich | 48.1000 | 15.6000 | 110 |
| OOE    | Oberösterreich | 48.0000 | 14.0000 | 100 |
| ST     | Steiermark | 47.3500 | 14.5000 | 110 |
| T      | Tirol | 47.2000 | 11.4000 | 100 |
| K      | Kärnten | 46.8000 | 14.0000 | 80 |
| S      | Salzburg | 47.5500 | 13.2000 | 80 |
| V      | Vorarlberg | 47.2500 | 9.9000 | 40 |
| B      | Burgenland | 47.5000 | 16.5000 | 60 |

---

## Testdaten-Generator (`DevDataSeeder`)

Ziel: Realistisches Demo-Datensatz das alle Features zeigt.

**Aktivierung:** Spring Profile `dev` (`--spring.profiles.active=dev`).
**Idempotenz:** Prüft `COUNT(*) FROM person` > 0 vor dem Insert; nur bei leerer DB.

**Umfang:**
- 200 Personen mit österreichischen Namen, je 1–2 PersonOrte (echte österreichische PLZ)
- 80 Stellen in verschiedenen Städten und Bundesländern
- Alle Personen haben 2–8 Kompetenzen, alle Stellen haben 2–6 Kompetenzen
- Verteilung so gestaltet, dass je Person 3–15 gute Matches existieren (score > 60%)
- Mindestens 5 "Showcase"-Personen mit sehr hohen Match-Scores (>85%) für Demo

**Strategie für gute Matches:**
- 5 Berufscluster (z.B. IT, Pflege, Handel, Logistik, Verwaltung)
- Pro Cluster: gemeinsame Kern-Kompetenzen + individuelle Variation
- Stellen und Personen im selben Cluster teilen 60–80% der Kompetenzen
- Geo: Stellen in Wien, Graz, Linz, Salzburg; Personen mit Umkreisen die diese Städte abdecken

---

## API-Routen

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| GET | `/personen` | Personen-Liste (Thymeleaf, paginiert) |
| GET | `/personen/neu` | Neues Personen-Formular |
| POST | `/personen` | Person anlegen |
| GET | `/personen/{id}` | Person bearbeiten |
| PUT | `/personen/{id}` | Person aktualisieren |
| DELETE | `/personen/{id}` | Person löschen (HTMX) |
| GET | `/personen/{id}/matches` | Match-Liste für Person |
| GET | `/stellen` | Stellen-Liste |
| GET | `/stellen/neu` | Neue Stelle |
| POST | `/stellen` | Stelle anlegen |
| GET | `/stellen/{id}` | Stelle bearbeiten |
| PUT | `/stellen/{id}` | Stelle aktualisieren |
| DELETE | `/stellen/{id}` | Stelle löschen (HTMX) |
| GET | `/stellen/{id}/matches` | Match-Liste für Stelle |
| GET | `/match-modell` | Match-Modell Editor |
| PUT | `/match-modell` | Match-Modell speichern (HTMX auto-save) |
| GET | `/api/bis/berufe` | BIS-Berufe Suche (`?q=`) |
| GET | `/api/bis/kompetenzen` | BIS-Kompetenzen Suche (`?q=`) |
| GET | `/api/geo/suche` | PLZ/Ort-Suche lokal (`?q=`) |
| GET | `/api/geo/nominatim` | Adress-Geocoding via Nominatim (`?q=`) |

---

## Frontend-Architektur (Thymeleaf + HTMX + DaisyUI)

### Layout-Template (`layout.html`)
- DaisyUI `corporate` Theme (professionell, nicht verspielt)
- HTMX 2.x via CDN
- Navbar mit Links zu Personen, Stellen, Match-Modell
- HTMX-Toast via `HX-Trigger` Response-Header + `hx-on::htmx:response-error`

### HTMX-Muster
| Feature | HTMX-Attribut |
|---------|--------------|
| Beruf-Autocomplete | `hx-get="/api/bis/berufe?q=" hx-trigger="keyup changed delay:300ms"` |
| Kompetenz-Autocomplete | analog |
| PLZ/Ort-Suche | `hx-get="/api/geo/suche?q=" hx-trigger="keyup changed delay:300ms"` |
| Nominatim-Geocoding | `hx-get="/api/geo/nominatim?q=" hx-trigger="keyup changed delay:500ms"` |
| Ort hinzufügen | `hx-post="/personen/{id}/orte" hx-target="#ort-liste" hx-swap="beforeend"` |
| Ort entfernen | `hx-delete="/personen/{id}/orte/{ortId}" hx-target="closest .ort-eintrag" hx-swap="outerHTML"` |
| Zeile löschen | `hx-delete="..." hx-target="closest tr" hx-swap="outerHTML swap:200ms"` |
| Slider auto-save | `hx-post="/match-modell" hx-trigger="change"` |
| Score live-label | `oninput="..."` (2 Zeilen JS, kein Framework) |

### Score-Anzeige (DaisyUI)
```html
<progress class="progress progress-primary" value="78" max="100"></progress>
<div class="stat-value text-primary">78%</div>
```

---

## Implementierungs-Checkliste

### Phase 1: Projektgerüst
- [ ] `.gitignore` aktualisieren (db-Artefakte, kein Angular)
- [ ] `docker-compose.yml`: postgis/postgis:16-3.4 + backend (kein Frontend-Container)
- [ ] Spring Boot initialisieren: Web, Thymeleaf, JPA, Flyway, PostgreSQL, Lombok, Springdoc, Validation
- [ ] `build.gradle`: `htmx-spring-boot-thymeleaf` WebJar hinzufügen
- [ ] `application.properties`: DataSource, JPA, Flyway, Logging
- [ ] `Dockerfile` (multi-stage: Gradle build → eclipse-temurin:21-jre-alpine)
- [ ] Flyway-Migration V1: vollständiges Schema inkl. GEOGRAPHY-Spalten + Indizes
- [ ] Layout-Template `layout.html` mit DaisyUI corporate-Theme, Navbar, HTMX
- [ ] Index-Seite `/` mit Dashboard-Kacheln (Personen-Anzahl, Stellen-Anzahl, Link zu Match-Modell)
- [ ] `docker compose up` — Backend startet, Flyway läuft, DB erreichbar

### Phase 2: Domain-Modell & CRUD

**BIS:**
- [ ] `Beruf.java`, `Kompetenz.java` — @Entity
- [ ] `BerufRepository`, `KompetenzRepository` — `findByNameContainingIgnoreCase` + Limit 20
- [ ] `BisRestController` — `GET /api/bis/berufe?q=`, `/api/bis/kompetenzen?q=` → JSON-Fragmente

**Geo:**
- [ ] `PlzOrt.java`, `PlzOrtRepository` — `findTop20ByPlzStartingWithOrOrtNameContainingIgnoreCase`
- [ ] `Bundesland.java`, `BundeslandRepository`
- [ ] `GeoRestController` — `/api/geo/suche?q=` + `/api/geo/nominatim?q=` (Nominatim-Proxy, 1 req/s)

**Person:**
- [ ] `Person.java` — id, vorname, nachname, email, berufId; @OneToMany OrteList + KompetenzenList
- [ ] `PersonOrt.java` — standort GEOGRAPHY als @Column(columnDefinition), ort_rolle, ort_typ, umkreis_km
- [ ] `PersonKompetenz.java` — EmbeddedId (personId, kompetenzId) + niveau
- [ ] `PersonRepository`, `PersonService` (Validierung: Beruf/Kompetenzen müssen existieren)
- [ ] `PersonDto`, `PersonCreateRequest`, `PersonUpdateRequest` (Records)
- [ ] `PersonController` — Thymeleaf CRUD + HTMX-Fragment-Endpunkte für Ort hinzufügen/entfernen
- [ ] Templates: `personen/liste.html`, `personen/formular.html`

**Stelle:**
- [ ] `Stelle.java` — standort GEOGRAPHY als @Column(columnDefinition)
- [ ] `StelleKompetenz.java` — EmbeddedId + pflicht Boolean
- [ ] `StelleRepository`, `StelleService`, DTOs
- [ ] `StelleController` — analog zu PersonController
- [ ] Templates: `stellen/liste.html`, `stellen/formular.html`

### Phase 3: Seed-Import
- [ ] `scripts/scrape-bis.py` — scraped AMS-BIS, speichert `bis_berufe.json`, `bis_kompetenzen.json`
- [ ] Script ausführen, JSONs in `seed/data/` einchecken
- [ ] `BisSeedRunner` — Jackson-Import, idempotent (`COUNT(*) = 0` check)
- [ ] GeoNames `AT.zip` herunterladen, `AT.txt` als `AT_plz.tsv` in `seed/data/` einchecken
- [ ] `PlzSeedRunner` — parst TSV (tab-separated, 11 Spalten), importiert `plz_ort`, idempotent
- [ ] `BundeslandSeedRunner` — insertet 9 Bundesland-Zeilen (hard-coded), idempotent

### Phase 4: Matching-Engine
- [ ] `MatchModell.java` — Entity: id, name, aktiv, geoAktiv, berufFilterStrikt, gewichtKompetenz, gewichtBeruf
- [ ] `MatchModellRepository`, `MatchModellService.getAktives()`
- [ ] `MatchResult.java` — Record: personId/stelleId, name, berufScore, kompetenzScore, gesamtScore
- [ ] `MatchRepository.java` — zwei native @Query (CTE): `findTopPersonenForStelle`, `findTopStellenForPerson`
- [ ] `MatchService.java` — ruft Repository auf, mapped Rows auf MatchResult-Records
- [ ] `MatchModellController` — GET `/match-modell` (Thymeleaf) + PUT `/match-modell` (HTMX auto-save)
- [ ] `MatchController` — GET `/personen/{id}/matches`, `/stellen/{id}/matches`
- [ ] Templates: `personen/matches.html`, `stellen/matches.html`, `match-modell/editor.html`
- [ ] `MatchServiceTest.java` — Testcontainers: Geo-Filter-Szenarien, Score-Berechnung

### Phase 5: Frontend-Feinschliff
- [ ] Globaler HTMX-Fehler-Handler → DaisyUI Toast (via `HX-Trigger: {"showToast": "..."}`)
- [ ] Loading-Indicator: DaisyUI `loading loading-spinner` via `hx-indicator`
- [ ] Person-Formular: Ort-Sektion mit dynamischem Add/Remove via HTMX
- [ ] Stelle-Formular: Kompetenz-Liste mit Pflicht-Toggle
- [ ] Match-Seite: Score-Balken (DaisyUI progress), Breakdown-Kacheln (DaisyUI stat)
- [ ] Responsive Navbar (DaisyUI drawer für Mobile)
- [ ] Swagger-UI erreichbar unter `/swagger-ui.html`

### Phase 6: Testdaten & Integration
- [ ] `DevDataSeeder.java` — @Profile("dev"), 200 Personen + 80 Stellen, realistische Matches
- [ ] `application-dev.properties` — `spring.profiles.active=dev`
- [ ] Integration-Test `PersonApiIT` — Testcontainers: CRUD-Szenarien
- [ ] Integration-Test `MatchApiIT` — Testcontainers: Geo-Szenarien + Score-Ranking
- [ ] Performance-Smoke-Test: 10.000 Personen + 5.000 Stellen, Match-Query < 500ms
- [ ] `docker compose up --build` — Gesamtsystem startet, Seeds laufen, Match-Endpunkte antworten
- [ ] `README.md` mit Setup-Anleitung aktualisieren

---

## Wichtige Hinweise

- **Geo ist Pflicht-Filter, kein Score**: `gewicht_geo` existiert nicht. Nur `gewicht_kompetenz` und `gewicht_beruf`.
- **GEOGRAPHY vs GEOMETRY**: `GEOGRAPHY(POINT,4326)` verwendet die Spheroid-Berechnung automatisch. `ST_DWithin` auf geography erwartet Meter (nicht Grad). `umkreis_km * 1000` in der Query.
- **Generated Column**: `standort` ist eine PostgreSQL GENERATED ALWAYS AS STORED Spalte — nie direkt schreiben, wird aus lat/lon berechnet. JPA-Mapping: `@Column(insertable=false, updatable=false)`.
- **Nominatim Rate-Limit**: Max 1 Request/Sekunde, User-Agent setzen (`JobHoppr/1.0`). Im Backend als Proxy implementieren — nie direkt vom Browser.
- **Nur ein aktives Match-Modell**: PUT überschreibt in-place. Kein Create neuer Zeilen.
- **BIS-Daten read-only**: Nie `bis_beruf` oder `bis_kompetenz` via API mutieren.
- **BisSeedRunner ist idempotent**: `COUNT(*) FROM bis_beruf = 0` vor Insert prüfen.
- **DevDataSeeder nur im dev-Profil**: Nie im Produktiv-Betrieb aktivieren.
- **Flyway statt `ddl-auto`**: Schema wird durch Flyway-Migrationen verwaltet. `spring.jpa.hibernate.ddl-auto=validate`.
