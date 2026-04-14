# JobHoppr

Job-Matching zwischen Arbeitssuchenden und offenen Stellen.

[![CI](https://github.com/xman-berlin/jobhoppr/actions/workflows/ci.yml/badge.svg)](https://github.com/xman-berlin/jobhoppr/actions/workflows/ci.yml)

## About

JobHoppr matches job seekers (**Personen**) with open positions (**Stellen**) using Austrian occupational data (BIS) for Berufe and Kompetenzen.

Matching runs entirely as a **native PostgreSQL CTE query** with a PostGIS geo-filter — scales to 1M+ rows with no Java-side processing.

Geo is a **binary mandatory filter**: if no Personort falls within the Stelle's radius, the match is excluded entirely. The weighting model (Beruf vs. Kompetenz) is configurable in the UI.

## Tech Stack

| Layer | Choice |
|-------|--------|
| Frontend | Thymeleaf + HTMX + DaisyUI (CDN, Corporate theme) |
| Backend | Spring Boot 3.3, Java 21, Maven |
| Database | PostgreSQL 16 + PostGIS |
| ORM | Spring Data JPA + `JdbcTemplate` (native SQL for matching) |
| Geo-filter | PostGIS `ST_DWithin` on `GEOGRAPHY(POINT,4326)` with GiST index |
| Geocoding | Nominatim (OpenStreetMap) proxy + GeoNames AT PLZ lookup |
| Schema | Flyway (migrations in `backend/src/main/resources/db/migration/`) |
| Build | Multi-stage Dockerfile (`maven:3.9` → `eclipse-temurin:21-jre`) |
| Deploy | Docker Compose |

## Quick Start (Docker — recommended)

**Prerequisites:** Docker 24+ with Docker Compose v2.

```bash
git clone https://github.com/xman-berlin/jobhoppr.git
cd jobhoppr

# Start with demo data (200 Personen, 80 Stellen)
SPRING_PROFILES_ACTIVE=dev docker compose up --build
```

Open **http://localhost:8080** — the app is ready once you see:

```
Started JobhopprApplication in X seconds
Dev-Testdaten vollständig generiert.
```

To stop and remove all data:

```bash
docker compose down -v
```

## Configuration

Copy `.env.example` to `.env` to override defaults:

```bash
cp .env.example .env
```

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_DB` | `jobhoppr` | Database name |
| `POSTGRES_USER` | `jobhoppr` | DB user |
| `POSTGRES_PASSWORD` | `jobhoppr` | DB password |
| `SPRING_PROFILES_ACTIVE` | `prod` | Use `dev` for seed data |

## Local Development (empfohlen)

Run PostGIS in Docker, then start Spring Boot locally — deutlich schneller als `docker compose up --build`:

```bash
# 1. Nur die Datenbank starten
docker compose up db -d

# 2. Backend starten (ohne Testdaten)
cd backend
mvn spring-boot:run

# 2b. Backend mit Dev-Testdaten starten (200 Personen, 80 Stellen, 10 Lehrstellen)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Running Tests

```bash
cd backend

# Alle Tests (benötigt laufende DB — vorher: docker compose up db -d)
mvn test

# Einzelne Testklasse
mvn test -Dtest="MatchServiceTest"

# Einzelne Methode
mvn test -Dtest="MatchServiceTest#geoFilterBlocksOutOfRange"

# Alle Integrationstests
mvn test -Dtest="*IT"
```

## Project Structure

```
jobhoppr/
├── Dockerfile                    Multi-stage: Maven build → JRE runtime
├── docker-compose.yml            PostGIS 16 + Spring Boot backend
├── .env.example                  Environment variable template
└── backend/
    └── src/
        ├── main/
        │   ├── java/at/jobhoppr/
        │   │   ├── config/           IndexController, GlobalExceptionHandler, WebConfig
        │   │   ├── domain/bis/       Beruf, Kompetenz (BIS reference data, read-only)
        │   │   ├── domain/geo/       PlzOrt, Bundesland, GeoRestController
        │   │   ├── domain/person/    Person, PersonOrt, PersonKompetenz, PersonService
        │   │   ├── domain/stelle/    Stelle, StelleKompetenz, StelleService
        │   │   ├── domain/matching/  MatchModell, MatchService (CTE), MatchResult
        │   │   └── seed/             BisSeedRunner, PlzSeedRunner, BundeslandSeedRunner,
        │   │                         DevDataSeeder (@Profile("dev"))
        │   └── resources/
        │       ├── application.properties
        │       ├── application-dev.properties
        │       ├── db/migration/     V1__schema.sql, V2__fix_char_columns.sql
        │       ├── seed/             AT_plz.tsv (GeoNames), bis_berufe.json, bis_kompetenzen.json
        │       └── templates/        Thymeleaf: layout, index, personen/*, stellen/*, match-modell/*
        └── test/
            └── java/at/jobhoppr/
                ├── matching/         MatchServiceTest (unit)
                └── integration/      *IT.java (Testcontainers)
```

## Seed Data

| Runner | Content | Source |
|--------|---------|--------|
| `BundeslandSeedRunner` | 9 Austrian states with centroid + radius | Hard-coded |
| `PlzSeedRunner` | 18,957 Austrian postal codes with coordinates | GeoNames AT.txt (CC-BY 4.0) |
| `BisSeedRunner` | 25 Berufe + 35 Kompetenzen across 5 clusters | Simplified BIS data |
| `BerufHierarchieSeedRunner` | 4-tier Beruf hierarchy + kompetenz_closure table | BIS scraping |
| `DevDataSeeder` | 200 Personen + 80 Stellen with realistic Austrian names | `@Profile("dev")` only |

## Matching Algorithm

The matching runs as a single PostgreSQL CTE query (`MatchRepository`):

1. **Geo-filter** (mandatory when `geoAktiv=true`): `ST_DWithin(person_ort.standort, stelle.standort, umkreis_km * 1000)`
2. **Beruf-filter**: hierarchical match on BUG-level (4-tier hierarchy)
3. **Kompetenz-score**: weighted intersection of Pflicht (mandatory) and optional Kompetenzen via closure table
4. **Arbeitszeit-score**: intersection of Stelle's offered work models and Person's desired models
5. **Total score**: `(gewicht_beruf × beruf_score + gewicht_kompetenz × kompetenz_score + gewicht_arbeitszeit × arbeitszeit_score)`
6. **Top 50** by total score descending

The active `MatchModell` (weights + flags) is edited at `/match-modell` and takes effect immediately on all subsequent queries.

Match results include detailed breakdowns:
- Beruf score (om)
- Kompetenz score (sm) — with list of matching and missing Kompetenzen
- Interessen score (fm) — for Lehrstellen
- Voraussetzungen score (qm) — for Lehrstellen
- Arbeitszeit score (am) — with list of matched work models

## Key Domain Terms

| Term | Meaning |
|------|---------|
| `Person` | Job seeker |
| `Stelle` | Job posting (includes Lehrstellen) |
| `Beruf` | Occupation (from BIS) |
| `Kompetenz` | Skill/competency (from BIS) |
| `PersonOrt` | Location entry for a person (Wohnort or Arbeitsort) |
| `OrtRolle` | `WOHNORT` (home) / `ARBEITSORT` (work) |
| `OrtTyp` | `GENAU` (precise location with radius) / `REGION` (regional area) |
| `MatchTyp` | `UMKREIS` (radius-based) / `EXAKT` (exact match) |
| `MatchModell` | Active weighting configuration |

## Project Status

- [x] Phase 1: Project scaffold (monorepo, Docker, Flyway schema)
- [x] Phase 2: Backend domain model & CRUD (Person, Stelle, MatchModell)
- [x] Phase 3: BIS seed import (Berufe + Kompetenzen)
- [x] Phase 4: Matching engine (PostgreSQL CTE, PostGIS)
- [x] Phase 5: Frontend (Thymeleaf + HTMX + DaisyUI — lists, forms, match views)
- [x] Phase 6: Docker Compose, DevDataSeeder, full integration
- [x] Phase 7–10: Vollständiges Matchmodell (BIS-Hierarchie, Closure Tables, Score-Funktionen, Lehrstellenmatching, Dashboard, Performance-Indizes)
- [x] Phase 11: BIS-Kompetenz-Vorschläge in Formularen — nach Beruf-Auswahl werden passende Basis- und fachliche Kompetenzen aus BIS als klickbare Badges angezeigt (getrennte Abschnitte, 6.830 Mappings aus `stammberufe.xml`)
- [x] Phase 12: Arbeitszeitmodelle (VOLLZEIT, TEILZEIT, GERINGFUEGIG, NACHT, WOCHENENDE) — Personen geben Wunsch-Modelle an, Stellen bieten Modelle an; `gewichtArbeitszeit` im MatchModell steuerbar; gematchte Modelle als Badges in Matches-Karten
- [x] Phase 13: Ort-Autocomplete-Suche — Standort-Auswahl via Autocomplete (Bundesland, Bezirk, PLZ-Ort) mit Lazy-Fetch für Hierarchie
- [x] Phase 14: Match-Details — gematchte und fehlende Kompetenzen in separaten Listen anzeigen
- [x] Phase 15: Lat/Lon verstecken — Koordinaten nur intern für Matching, nicht in UI

## License

MIT
