# JobHoppr

AMS-basiertes Matching zwischen Arbeitssuchenden und offenen Stellen.

## About

JobHoppr ist eine Webanwendung, die Arbeitssuchende (Personen) mit offenen Stellen matched. Als Datenbasis
für Berufe und Kompetenzen wird das österreichische AMS-Berufsinformationssystem (BIS) verwendet — Berufe
und Kompetenzen werden einmalig als Seed-Daten importiert. Das Matching basiert auf gewichteten Kriterien
(Kompetenzen, Beruf), wobei das Gewichtungsmodell zentral konfigurierbar ist.

Geo ist ein **binärer Pflicht-Filter** (kein Score-Anteil): Liegt keine Ortsangabe der Person (Wohnort
oder Arbeitsort) im konfigurierten Umkreis der Stelle, wird der Match vollständig ausgeblendet.
Beide Seiten (Person, Arbeitgeber) sehen ihre Match-Liste absteigend nach Punktzahl.

## Tech Stack

| Layer | Choice |
|-------|--------|
| Frontend | Angular 19, Standalone Components, Angular Material, TypeScript |
| Backend | Spring Boot 3.3, Java 21, Gradle |
| Datenbank | PostgreSQL 16 + PostGIS |
| ORM | Spring Data JPA + Hibernate Spatial |
| API-Stil | REST (OpenAPI 3 / Springdoc) |
| Geo-Filter | PostGIS `ST_DWithin` auf EPSG:4326 Koordinaten |
| Build/Deploy | Docker Compose |
| Tests Backend | JUnit 5, Testcontainers |
| Tests Frontend | Jasmine + Karma, Playwright (E2E, optional) |

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 21 (für lokale Backend-Entwicklung ohne Docker)
- Node.js 20+ / npm (für lokale Frontend-Entwicklung ohne Docker)

### Installation

```bash
git clone git@github.com:<your-user>/jobhoppr.git
cd jobhoppr
```

### Environment Variables

Kopiere `.env.example` nach `.env` und passe die Werte an:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/jobhoppr
SPRING_DATASOURCE_USERNAME=jobhoppr
SPRING_DATASOURCE_PASSWORD=jobhoppr
SPRING_JPA_HIBERNATE_DDL_AUTO=update
```

### Development

```bash
docker compose up --build
```

- Frontend: http://localhost:4200
- Backend API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

## Project Status

- [ ] Phase 1: Projektgerüst (Mono-Repo, Docker, CI-Basis)
- [ ] Phase 2: Backend — Domain-Modell & CRUD-APIs
- [ ] Phase 3: BIS-Seed-Import (Berufe + Kompetenzen)
- [ ] Phase 4: Matching-Engine & Match-Modell
- [ ] Phase 5: Frontend — Angular 19 CRUD & Match-Listen
- [ ] Phase 6: Integration, E2E-Tests, Feinschliff

See [plan.md](./plan.md) for the full implementation plan.
