# Plan: JobHoppr — AMS-Matching für Arbeitslose und offene Stellen

## Context

JobHoppr ist eine Webanwendung, die Arbeitssuchende (Personen) mit offenen Stellen matched. Als Datenbasis
für Berufe und Kompetenzen wird das österreichische AMS-Berufsinformationssystem (BIS, https://bis.ams.or.at/bis/)
verwendet — Berufe und Kompetenzen werden einmalig als Seed-Daten importiert. Das Matching basiert auf
gewichteten Kriterien (Kompetenzen, Beruf), wobei das Gewichtungsmodell zentral konfigurierbar ist.
Geo ist ein **binärer Pflicht-Filter** (kein Score-Anteil): Liegt keine Ortsangabe der Person (Wohnort
oder Arbeitsort) im konfigurierten Umkreis der Stelle, wird der Match vollständig ausgeblendet.
Beide Seiten (Person, Arbeitgeber) sehen ihre Match-Liste absteigend nach Punktzahl.

## Progress

- [ ] Phase 1: Projektgerüst (Mono-Repo, Docker, CI-Basis)
- [ ] Phase 2: Backend — Domain-Modell & CRUD-APIs
- [ ] Phase 3: BIS-Seed-Import (Berufe + Kompetenzen)
- [ ] Phase 4: Matching-Engine & Match-Modell
- [ ] Phase 5: Frontend — Angular 19 CRUD & Match-Listen
- [ ] Phase 6: Integration, E2E-Tests, Feinschliff

---

## Tech Stack

| Layer | Choice |
|-------|--------|
| Frontend | Angular 19, Standalone Components, Angular Material, TypeScript |
| Backend | Spring Boot 3.3, Java 21, Gradle |
| Datenbank | PostgreSQL 16 + PostGIS (für Geo-Abfragen) |
| ORM | Spring Data JPA + Hibernate Spatial |
| API-Stil | REST (OpenAPI 3 / Springdoc) |
| Geo-Filter | PostGIS `ST_DWithin` auf EPSG:4326 Koordinaten |
| Build/Deploy | Docker Compose (frontend + backend + postgres) |
| Tests Backend | JUnit 5, Testcontainers (PostgreSQL) |
| Tests Frontend | Jasmine + Karma (Unit), Playwright (E2E, optional) |

---

## Projektstruktur

```
jobhoppr/
├── docker-compose.yml          # frontend, backend, postgres services
├── .env.example                # Umgebungsvariablen-Vorlage
│
├── backend/                    # Spring Boot Gradle-Projekt
│   ├── build.gradle
│   ├── src/main/java/at/jobhoppr/
│   │   ├── JobhopprApplication.java
│   │   ├── config/
│   │   │   └── OpenApiConfig.java
│   │   ├── domain/
│   │   │   ├── person/
│   │   │   │   ├── Person.java              # JPA Entity
│   │   │   │   ├── PersonOrt.java           # JPA Entity (1:n zu Person)
│   │   │   │   ├── PersonRepository.java
│   │   │   │   ├── PersonService.java
│   │   │   │   └── PersonController.java    # REST /api/persons
│   │   │   ├── stelle/
│   │   │   │   ├── Stelle.java              # JPA Entity
│   │   │   │   ├── StelleRepository.java
│   │   │   │   ├── StelleService.java
│   │   │   │   └── StelleController.java    # REST /api/stellen
│   │   │   ├── bis/
│   │   │   │   ├── Beruf.java               # BIS-Beruf (id, name, bereich)
│   │   │   │   ├── Kompetenz.java           # BIS-Kompetenz (id, name, bereich, parent)
│   │   │   │   ├── BerufRepository.java
│   │   │   │   ├── KompetenzRepository.java
│   │   │   │   └── BisController.java       # REST /api/bis/berufe, /api/bis/kompetenzen (read-only)
│   │   │   └── matching/
│   │   │       ├── GeoPflichtFilter.java    # Vorfilter: prüft Umkreis, kein Score
│   │   │       ├── MatchKriterium.java      # Interface für gewichtete Score-Kriterien
│   │   │       ├── KompetenzMatchKriterium.java
│   │   │       ├── BerufMatchKriterium.java
│   │   │       ├── MatchModell.java         # Gewichtungs-Konfiguration
│   │   │       ├── MatchModellRepository.java
│   │   │       ├── MatchModellController.java  # REST /api/match-modell
│   │   │       ├── MatchResult.java         # DTO: {targetId, targetName, score, breakdown}
│   │   │       ├── MatchService.java        # Geo-Filter → Score-Kriterien → Sortierung
│   │   │       └── MatchController.java     # REST /api/match/person/{id}, /api/match/stelle/{id}
│   │   └── seed/
│   │       ├── BisSeedRunner.java           # ApplicationRunner: lädt JSON beim Start
│   │       └── data/
│   │           ├── bis_berufe.json          # ~500 Berufe aus BIS
│   │           └── bis_kompetenzen.json     # Kompetenzen (flach, mit parent_id)
│   └── src/test/java/at/jobhoppr/
│       ├── matching/MatchServiceTest.java
│       └── integration/PersonApiIT.java
│
└── frontend/                   # Angular 19 Standalone
    ├── package.json
    ├── angular.json
    ├── src/
    │   ├── app/
    │   │   ├── app.config.ts               # provideRouter, provideHttpClient, etc.
    │   │   ├── app.routes.ts
    │   │   ├── core/
    │   │   │   ├── api/
    │   │   │   │   ├── person.service.ts
    │   │   │   │   ├── stelle.service.ts
    │   │   │   │   ├── bis.service.ts
    │   │   │   │   ├── match.service.ts
    │   │   │   │   └── match-modell.service.ts
    │   │   │   └── models/                 # TypeScript-Interfaces (spiegeln Backend-DTOs)
    │   │   ├── features/
    │   │   │   ├── persons/
    │   │   │   │   ├── person-list/        # Standalone Component
    │   │   │   │   ├── person-form/        # Erstellen / Bearbeiten
    │   │   │   │   └── person-matches/     # Match-Liste für eine Person
    │   │   │   ├── stellen/
    │   │   │   │   ├── stellen-list/
    │   │   │   │   ├── stellen-form/
    │   │   │   │   └── stellen-matches/    # Match-Liste für eine Stelle
    │   │   │   └── match-modell/
    │   │   │       └── match-modell-editor/ # Gewichtungen + Geo-Filter konfigurieren
    │   │   └── shared/
    │   │       ├── person-ort-input/       # Ort-Eingabe: Typ (GENAU/REGION) + Koordinaten + Umkreis
    │   │       ├── kompetenz-select/       # Multi-Select Chips + Autocomplete
    │   │       └── beruf-select/           # Autocomplete für BIS-Berufe
    │   ├── environments/
    │   │   └── environment.ts              # apiUrl
    │   └── index.html
    └── nginx.conf                          # Proxy /api → backend:8080
```

---

## Datenbankschema (PostgreSQL + PostGIS)

```sql
-- BIS-Referenzdaten (read-only nach Seed)
CREATE TABLE bis_beruf (
  id          INTEGER PRIMARY KEY,           -- BIS-ID (z.B. 581)
  name        TEXT NOT NULL,                 -- "AnwendungsbetreuerIn"
  bereich     TEXT,                          -- "Informationstechnologie"
  isco_code   TEXT                           -- ISCO-08-Code (optional)
);

CREATE TABLE bis_kompetenz (
  id          INTEGER PRIMARY KEY,           -- BIS-ID
  name        TEXT NOT NULL,                 -- "Programmiersprachen-Kenntnisse"
  bereich     TEXT,                          -- "Fachliche berufliche Kompetenzen"
  parent_id   INTEGER REFERENCES bis_kompetenz(id),
  typ         TEXT                           -- FACHLICH | UEBERFACHLICH | ZERTIFIKAT
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

-- Orte einer Person (Wohnort = Pflicht, Arbeitsort = optional, erweiterbar)
-- Jeder Ort hat einen Typ und einen eigenen Umkreis (bei GENAU).
CREATE TABLE person_ort (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  person_id     UUID NOT NULL REFERENCES person(id) ON DELETE CASCADE,
  ort_rolle     TEXT NOT NULL,          -- WOHNORT | ARBEITSORT
  ort_typ       TEXT NOT NULL,          -- GENAU | REGION
  bezeichnung   TEXT NOT NULL,          -- "Wien", "1010 Wien, Wollzeile 10", "Oberösterreich"
  lat           DOUBLE PRECISION NOT NULL,
  lon           DOUBLE PRECISION NOT NULL,
  -- Nur bei ort_typ=GENAU: Suchradius in km.
  -- Bei ort_typ=REGION: NULL (Match via Polygon oder vereinfacht: großer Radius)
  umkreis_km    DOUBLE PRECISION
);

CREATE TABLE person_kompetenz (
  person_id     UUID REFERENCES person(id) ON DELETE CASCADE,
  kompetenz_id  INTEGER REFERENCES bis_kompetenz(id),
  niveau        TEXT,                   -- GRUNDKENNTNISSE | FORTGESCHRITTEN | EXPERTE
  PRIMARY KEY (person_id, kompetenz_id)
);

-- Stellen
-- Die Stelle hat immer exakte Koordinaten (GENAU).
-- Der Umkreis für das Matching wird pro Person-Ort definiert, nicht hier.
CREATE TABLE stelle (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  titel           TEXT NOT NULL,
  unternehmen     TEXT,
  beschreibung    TEXT,
  ort_bezeichnung TEXT NOT NULL,
  ort_lat         DOUBLE PRECISION NOT NULL,
  ort_lon         DOUBLE PRECISION NOT NULL,
  beruf_id        INTEGER REFERENCES bis_beruf(id),
  erstellt_am     TIMESTAMPTZ DEFAULT NOW(),
  aktualisiert_am TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE stelle_kompetenz (
  stelle_id     UUID REFERENCES stelle(id) ON DELETE CASCADE,
  kompetenz_id  INTEGER REFERENCES bis_kompetenz(id),
  pflicht       BOOLEAN DEFAULT TRUE,   -- TRUE = Pflicht, FALSE = Optional
  PRIMARY KEY (stelle_id, kompetenz_id)
);

-- Match-Modell (exakt ein aktiver Datensatz)
-- Geo ist kein Score-Kriterium, daher kein gewicht_geo.
-- geo_aktiv steuert ob der Geo-Filter überhaupt angewendet wird.
CREATE TABLE match_modell (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name                TEXT NOT NULL,
  aktiv               BOOLEAN DEFAULT FALSE,
  geo_aktiv           BOOLEAN DEFAULT TRUE,   -- FALSE = Geo-Filter deaktiviert (z.B. für Tests)
  -- Gewichtungen der Score-Kriterien (Normalisierung im Service, Summe muss nicht 1.0 sein)
  gewicht_kompetenz   DOUBLE PRECISION DEFAULT 0.75,
  gewicht_beruf       DOUBLE PRECISION DEFAULT 0.25,
  erstellt_am         TIMESTAMPTZ DEFAULT NOW()
);
```

---

## Matching-Algorithmus

### Schritt 1: Geo-Pflicht-Filter (binär, kein Score-Anteil)

Der Geo-Filter wird zuerst angewendet. Er arbeitet pro Person-Ort und prüft für jeden Ort
ob die Stelle erreichbar ist. Sobald **ein** Ort matcht, ist die Person/Stelle im Kandidaten-Set.

```
funktion geo_matcht(person, stelle, modell):
  wenn modell.geo_aktiv == FALSE:
    return TRUE                          -- Filter deaktiviert, alle durch

  für jeden ort in person.orte:
    wenn ort.typ == GENAU:
      distanz_km = ST_Distance(
        ST_Point(ort.lon, ort.lat)::geography,
        ST_Point(stelle.ort_lon, stelle.ort_lat)::geography
      ) / 1000.0
      wenn distanz_km <= ort.umkreis_km:
        return TRUE                      -- mindestens ein Ort im Umkreis → Match möglich

    wenn ort.typ == REGION:
      -- Vereinfachte Variante: Stelle liegt im Mittelpunkt-Radius der Region
      -- (Radius wird beim Anlegen des Orts aus Standardwerten für das Bundesland/PLZ befüllt)
      distanz_km = ST_Distance(
        ST_Point(ort.lon, ort.lat)::geography,
        ST_Point(stelle.ort_lon, stelle.ort_lat)::geography
      ) / 1000.0
      wenn distanz_km <= ort.umkreis_km:
        return TRUE

  return FALSE   -- kein Ort matcht → Person komplett aus Ergebnisliste ausschließen
```

**Beispiele:**
- Person wohnt in Wiener Neustadt (GENAU, 30km Umkreis), Arbeitsort Wien (REGION) → Stelle in Wien 1010: Wohnort-Distanz ~50km > 30km, aber Arbeitsort-Region Wien matcht → **Match**
- Person wohnt in Graz (GENAU, 20km Umkreis), kein Arbeitsort → Stelle in Wien: Distanz ~200km > 20km → **kein Match, ausgeblendet**

### Schritt 2: Score-Kriterien (gewichtet, 0–100 Punkte)

Nur Kandidaten die den Geo-Filter bestanden haben, werden bewertet.

#### Kriterium 1: Kompetenzen (`kompetenz_score`)

```
pflicht_kompetenzen    = Kompetenzen der Stelle mit pflicht=TRUE
optionale_kompetenzen  = Kompetenzen der Stelle mit pflicht=FALSE
person_kompetenzen     = alle Kompetenzen der Person

matches_pflicht    = |pflicht_kompetenzen ∩ person_kompetenzen|
matches_optional   = |optionale_kompetenzen ∩ person_kompetenzen|

# Pflicht-Skills zählen doppelt gegenüber optionalen
kompetenz_score = (
    (matches_pflicht * 2 + matches_optional) /
    MAX(1, pflicht_kompetenzen.size * 2 + optionale_kompetenzen.size)
) * 100
```

#### Kriterium 2: Beruf (`beruf_score`)

```
beruf_score = stelle.beruf_id == person.beruf_id ? 100 : 0
```

#### Gesamt-Score

```
gesamt = (
    kompetenz_score * modell.gewicht_kompetenz +
    beruf_score     * modell.gewicht_beruf
) / (modell.gewicht_kompetenz + modell.gewicht_beruf)
```

Division durch Summe der Gewichte → Normalisierung, Gewichte müssen nicht exakt 1.0 ergeben.

### Erweiterbarkeit

Zwei getrennte Erweiterungspunkte:

1. **Neue Pflicht-Filter**: Implementieren `PflichtFilter`-Interface: `boolean erlaubt(Person, Stelle, MatchModell)`.
   Alle Filter werden als Spring-Beans registriert und vor den Score-Kriterien ausgeführt (AND-Verknüpfung).

2. **Neue Score-Kriterien**: Implementieren `MatchKriterium`-Interface: `double berechne(Person, Stelle, MatchModell)`.
   Neues Gewichtsfeld in `MatchModell` + DB-Migration. Keine Änderung am `MatchService` nötig.

---

## API-Routen (Backend)

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| GET | `/api/persons` | Alle Personen (paginiert) |
| POST | `/api/persons` | Person erstellen |
| GET | `/api/persons/{id}` | Person abrufen |
| PUT | `/api/persons/{id}` | Person aktualisieren |
| DELETE | `/api/persons/{id}` | Person löschen |
| GET | `/api/stellen` | Alle Stellen (paginiert) |
| POST | `/api/stellen` | Stelle erstellen |
| GET | `/api/stellen/{id}` | Stelle abrufen |
| PUT | `/api/stellen/{id}` | Stelle aktualisieren |
| DELETE | `/api/stellen/{id}` | Stelle löschen |
| GET | `/api/bis/berufe` | BIS-Berufe suchen (`?q=Programm`) |
| GET | `/api/bis/kompetenzen` | BIS-Kompetenzen suchen (`?q=Java`) |
| GET | `/api/match-modell` | Aktives Match-Modell abrufen |
| PUT | `/api/match-modell` | Aktives Match-Modell aktualisieren |
| GET | `/api/match/person/{id}` | Match-Liste für Person (Top-Stellen, absteigend) |
| GET | `/api/match/stelle/{id}` | Match-Liste für Stelle (Top-Personen, absteigend) |

Alle List-Endpunkte geben `{ content: [...], totalElements, page, size }` zurück (Spring Page).

`MatchResult`-DTO:
```json
{
  "targetId": "uuid",
  "targetName": "Senior Java Developer – Acme GmbH",
  "score": 87.5,
  "geoMatcht": true,
  "breakdown": {
    "kompetenz": 91.7,
    "beruf": 100.0
  }
}
```

---

## BIS-Seed-Daten

Da das AMS BIS kein öffentliches REST-API anbietet, werden Berufe und Kompetenzen einmalig als
JSON-Seed-Dateien eingecheckt und beim ersten Start importiert.

**Format `bis_berufe.json`:**
```json
[
  { "id": 581,  "name": "AnwendungsbetreuerIn",        "bereich": "Informationstechnologie" },
  { "id": 1004, "name": "AllgemeineR HilfsarbeiterIn", "bereich": "Hilfsberufe" }
]
```

**Format `bis_kompetenzen.json`:**
```json
[
  { "id": 119, "name": "Programmiersprachen-Kenntnisse", "bereich": "Fachliche Kompetenzen", "parent_id": null, "typ": "FACHLICH" },
  { "id": 301, "name": "Java",                           "bereich": "Fachliche Kompetenzen", "parent_id": 119,  "typ": "FACHLICH" }
]
```

`BisSeedRunner` prüft beim Start `SELECT COUNT(*) FROM bis_beruf` — ist er 0, importiert er die JSONs.
Damit ist der Import idempotent.

**Seed-Daten beschaffen**: Die Seed-JSONs werden einmalig per Script aus dem BIS gescraped
(`/bis/berufe-von-a-bis-z` und `/bis/kompetenzen-nach-bereichen`) und statisch eingecheckt.
Ein optionales Update-Script (außerhalb des App-Starts) kann die Daten auffrischen.

---

## Environment Variables

```bash
# backend/.env (auch in docker-compose.yml referenziert)
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/jobhoppr
SPRING_DATASOURCE_USERNAME=jobhoppr
SPRING_DATASOURCE_PASSWORD=jobhoppr
SPRING_JPA_HIBERNATE_DDL_AUTO=update    # 'validate' in Prod

# frontend (über nginx-Proxy, kein separates .env nötig)
# API-URL in environment.ts: http://localhost:8080
```

---

## Docker Compose

```yaml
services:
  postgres:
    image: postgis/postgis:16-3.4
    environment:
      POSTGRES_DB: jobhoppr
      POSTGRES_USER: jobhoppr
      POSTGRES_PASSWORD: jobhoppr
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]

  backend:
    build: ./backend
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/jobhoppr
      SPRING_DATASOURCE_USERNAME: jobhoppr
      SPRING_DATASOURCE_PASSWORD: jobhoppr
    depends_on: [postgres]

  frontend:
    build: ./frontend
    ports: ["4200:80"]
    depends_on: [backend]

volumes:
  pgdata:
```

---

## Implementierungs-Checkliste

### Phase 1: Projektgerüst
- [ ] Mono-Repo `jobhoppr/` anlegen, `git init`
- [ ] `docker-compose.yml` mit postgis/postgis:16, backend, frontend Services
- [ ] Spring Boot Gradle-Projekt initialisieren (`spring initializr`: Web, JPA, PostgreSQL, Lombok, Springdoc)
- [ ] `build.gradle`: Hibernate Spatial Dependency hinzufügen
- [ ] Angular 19 Projekt anlegen: `ng new frontend --standalone --routing --style=scss`
- [ ] Angular Material installieren: `ng add @angular/material`
- [ ] `environment.ts` mit `apiUrl: 'http://localhost:8080'` konfigurieren
- [ ] Backend `Dockerfile` (multi-stage: Gradle build → JRE 21 slim)
- [ ] Frontend `Dockerfile` (multi-stage: Node build → nginx) + `nginx.conf`
- [ ] `docker compose up` — alle Services starten fehlerfrei

### Phase 2: Backend — Domain-Modell & CRUD

**BIS-Entitäten (read-only):**
- [ ] `Beruf.java` — `@Entity`, Felder: id (Integer), name, bereich, iscoCode
- [ ] `Kompetenz.java` — `@Entity`, Felder: id, name, bereich, parentId, typ (Enum: FACHLICH | UEBERFACHLICH | ZERTIFIKAT)
- [ ] `BerufRepository`, `KompetenzRepository` mit `findByNameContainingIgnoreCase`

**Person:**
- [ ] `Person.java` — Felder: id, vorname, nachname, email, berufId; `@OneToMany PersonOrt`, `@OneToMany PersonKompetenz`
- [ ] `PersonOrt.java` — Felder: id, personId, ortRolle (Enum: WOHNORT | ARBEITSORT), ortTyp (Enum: GENAU | REGION), bezeichnung, lat, lon, umkreisKm
- [ ] `PersonKompetenz.java` — Embeddable-PK (personId, kompetenzId) + niveau (Enum: GRUNDKENNTNISSE | FORTGESCHRITTEN | EXPERTE)
- [ ] `PersonRepository` — Standard CRUD + `findAll(Pageable)`
- [ ] `PersonService` — CRUD-Logik inkl. Orte und Kompetenzen; Validierung (Beruf/Kompetenzen müssen existieren)
- [ ] `PersonDto`, `PersonCreateRequest`, `PersonUpdateRequest` (Record-DTOs, Orte als eingebettete Liste)
- [ ] `PersonController` — GET/POST/PUT/DELETE `/api/persons`

**Stelle:**
- [ ] `Stelle.java` — Felder: id, titel, unternehmen, beschreibung, ortBezeichnung, ortLat, ortLon, berufId
- [ ] `StelleKompetenz.java` — Embeddable-PK + `pflicht` Boolean
- [ ] `StelleRepository`, `StelleService`, DTOs, `StelleController`

**BIS-API:**
- [ ] `BisController` — `GET /api/bis/berufe?q=`, `GET /api/bis/kompetenzen?q=` (max 20 Treffer)

**OpenAPI:**
- [ ] `OpenApiConfig.java` — Springdoc mit Titel "JobHoppr API", Version "1.0"
- [ ] Swagger-UI erreichbar unter `http://localhost:8080/swagger-ui.html`

### Phase 3: BIS-Seed-Import

- [ ] Script `scripts/scrape-bis.py` schreiben — scraped `bis.ams.or.at/bis/berufe-von-a-bis-z` und alle Kompetenzbereiche, speichert `bis_berufe.json` und `bis_kompetenzen.json`
- [ ] Script ausführen, Seed-JSONs unter `backend/src/main/resources/seed/` einchecken
- [ ] `BisSeedRunner.java` implementieren — liest JSONs mit Jackson, prüft Count, inserted alle Einträge
- [ ] Integration-Test `BisSeedRunnerIT` — leere DB, Seed läuft, Count > 0
- [ ] Idempotenz verifizieren: zweiter Start ändert nichts

### Phase 4: Matching-Engine

**Interfaces & Filter:**
- [ ] `PflichtFilter.java` — Interface: `String getName()`, `boolean erlaubt(Person, Stelle, MatchModell)`
- [ ] `GeoPflichtFilter.java` — iteriert über `person.orte`, prüft je nach `ortTyp` die Distanz via PostGIS `ST_Distance`; gibt `true` zurück sobald ein Ort im Umkreis liegt; respektiert `modell.geoAktiv`
- [ ] `MatchKriterium.java` — Interface: `String getName()`, `double berechne(Person, Stelle, MatchModell)`
- [ ] `KompetenzMatchKriterium.java` — Set-Schnittmenge, Pflicht-Skills doppelt gewichtet
- [ ] `BerufMatchKriterium.java` — exakter ID-Vergleich, gibt 100.0 oder 0.0 zurück

**Service & Controller:**
- [ ] `MatchService.java`:
  1. Alle Kandidaten (Personen für eine Stelle, oder Stellen für eine Person) laden
  2. Alle `PflichtFilter`-Beans sequenziell anwenden — Kandidaten die einen Filter nicht bestehen werden entfernt
  3. Für verbleibende Kandidaten alle `MatchKriterium`-Beans ausführen, gewichteten Score berechnen
  4. Ergebnisse als `List<MatchResult>` absteigend nach Score sortiert zurückgeben
- [ ] `MatchModell.java` — Entity: id, name, aktiv, geoAktiv, gewichtKompetenz, gewichtBeruf
- [ ] `MatchModellRepository`, `MatchModellService.getAktives()`
- [ ] `MatchModellController` — GET + PUT `/api/match-modell`
- [ ] `MatchController` — `GET /api/match/person/{id}` → Top-50 Stellen; `GET /api/match/stelle/{id}` → Top-50 Personen
- [ ] `MatchServiceTest.java` — Unit-Tests:
  - Person außerhalb Umkreis → nicht in Ergebnisliste
  - Person im Umkreis (Wohnort) → in Ergebnisliste
  - Person außerhalb Wohnort-Umkreis, aber Arbeitsort matcht → in Ergebnisliste
  - `geoAktiv=false` → alle Kandidaten durch, Geo-Distanz irrelevant
  - Score-Berechnung mit bekannten Kompetenz-/Beruf-Werten

### Phase 5: Frontend

**Shared Components:**
- [ ] `BerufSelectComponent` — Angular Material Autocomplete, ruft `/api/bis/berufe?q=` ab (debounce 300ms)
- [ ] `KompetenzSelectComponent` — Multi-Select Chips + Autocomplete für `/api/bis/kompetenzen?q=`
- [ ] `PersonOrtInputComponent` — Formgruppe für einen Ort:
  - Dropdown `ort_rolle` (Wohnort / Arbeitsort)
  - Dropdown `ort_typ` (GENAU / REGION)
  - Felder: Bezeichnung, Lat, Lon
  - Bei `ort_typ=GENAU`: Slider + Zahlenfeld für `umkreis_km` (1–200 km)
  - Bei `ort_typ=REGION`: `umkreis_km` ausgeblendet (wird serverseitig mit Standardwert gefüllt)

**Personen-Feature:**
- [ ] `PersonListComponent` — Material Table, paginiert, Spalten: Name, Beruf, Orte (kompakt), Aktionen
- [ ] `PersonFormComponent` — Reactive Form: Vorname, Nachname, Email, Beruf-Select, Kompetenz-Select, Orte-Liste (Wohnort Pflicht, Arbeitsort optional; `PersonOrtInputComponent` je Ort)
- [ ] `PersonMatchesComponent` — Tabelle mit: Stellen-Titel, Unternehmen, Score-Balken (`mat-progress-bar`), Breakdown (Kompetenz/Beruf) als aufklappbare Zeile

**Stellen-Feature:**
- [ ] `StellenListComponent` — analog zu PersonList
- [ ] `StellenFormComponent` — Reactive Form: Titel, Unternehmen, Beschreibung, Ort (Bezeichnung + Lat/Lon), Beruf-Select, Kompetenzen (je Kompetenz: Name + Pflicht-Toggle)
- [ ] `StellenMatchesComponent` — Tabelle mit: Personen-Name, Score-Balken, Breakdown

**Match-Modell-Feature:**
- [ ] `MatchModellEditorComponent`:
  - Toggle `geo_aktiv` (Geo-Filter ein/aus)
  - Slider + Zahlenfeld für `gewicht_kompetenz` (0.0–1.0)
  - Slider + Zahlenfeld für `gewicht_beruf` (0.0–1.0)
  - Hinweis: Gewichte werden normalisiert, Summe muss nicht 1.0 sein
  - Speichern via PUT `/api/match-modell`

**Routing:**
- [ ] `/persons` → List, `/persons/new` → Form, `/persons/:id` → Form, `/persons/:id/matches` → Matches
- [ ] `/stellen` → List, `/stellen/new` → Form, `/stellen/:id` → Form, `/stellen/:id/matches` → Matches
- [ ] `/match-modell` → Editor

**Services:**
- [ ] `PersonService`, `StelleService`, `BisService`, `MatchService`, `MatchModellService` — je ein `HttpClient`-basierter Angular-Service mit Observables

### Phase 6: Integration, Tests, Feinschliff

- [ ] Integration-Test `PersonApiIT` — Testcontainers: Person mit Orten erstellen, lesen, aktualisieren, löschen
- [ ] Integration-Test `MatchApiIT` — Testcontainers: Szenarien für Geo-Filter (im Umkreis, außerhalb, Arbeitsort rettet Match), Score-Ranking mit mehreren Kandidaten
- [ ] Frontend: HTTP-Interceptor mit Snackbar-Fehleranzeige bei 4xx/5xx
- [ ] Frontend: Loading-States (Spinner) bei allen async Operationen
- [ ] `README.md` mit Setup-Anleitung: `docker compose up`, Seed-Import, Swagger-URL
- [ ] `docker compose up --build` — Gesamtsystem startet, Seed läuft, Match-Endpunkte antworten korrekt

---

## Wichtige Hinweise

- **Geo-Filter ist Pflicht-Filter, kein Score**: Geo fließt nicht in den Score ein. Eine Person entweder im Ergebnis (Geo ok) oder gar nicht. Das Gewichtsmodell enthält daher nur `gewicht_kompetenz` und `gewicht_beruf`.
- **REGION-Ort**: Bei `ort_typ=REGION` wird `umkreis_km` mit einem Standardwert gefüllt, der die ungefähre Ausdehnung der Region abbildet (z.B. Wien ≈ 15km, Oberösterreich ≈ 100km). Das Backend befüllt diesen Wert anhand der Bezeichnung beim Anlegen, wenn `umkreis_km` nicht explizit angegeben wird.
- **PostGIS `ST_Distance`**: Gibt Meter zurück wenn auf `::geography` gecastet. Division durch 1000 → km. `ST_DWithin` kann als Performance-Optimierung für den Geo-Filter genutzt werden (nutzt Spatial Index).
- **BIS-IDs sind stabil**: Die numerischen IDs in den URLs (`/bis/beruf/581-...`) sind persistent und werden als Primärschlüssel in der DB verwendet.
- **Geo-Koordinaten**: Vorerst manuelle Eingabe (Lat/Lon). Optionale Erweiterung: Geocoding via Nominatim/OpenStreetMap API (kostenlos, kein Key nötig) — Bezeichnung eingeben → Koordinaten automatisch befüllen.
- **Erweiterbarkeit**: Neuer Pflicht-Filter = neues Spring Bean mit `PflichtFilter`. Neues Score-Kriterium = neues Spring Bean mit `MatchKriterium` + Gewichtsfeld in `MatchModell`. Kein Eingriff in `MatchService` nötig.
- **Nur ein aktives Match-Modell**: PUT `/api/match-modell` überschreibt das aktive Modell in-place. Historisierung kann später ergänzt werden.
- **Angular Standalone**: Kein `AppModule` — alle Components, Pipes und Directives werden direkt in `imports[]` des jeweiligen Standalone Component deklariert.
