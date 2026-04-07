# AGENTS.md — JobHoppr

Guidelines for agentic coding agents working in this repository.
See `plan.md` for the full implementation plan and phase checklist.

---

## Project Overview

Monorepo: **Angular 19 frontend** (`frontend/`) + **Spring Boot 3.3 backend** (`backend/`).
Language of the domain: **German (Austrian)** — all domain terms, variable names, entity names,
and API paths use German (e.g. `Person`, `Stelle`, `Beruf`, `Kompetenz`, `PersonOrt`).
Code-level constructs (types, interfaces, method logic) use English idioms where Java/TypeScript
conventions demand it, but domain identifiers stay German.

---

## Build Commands

### Docker (full stack)
```bash
docker compose up --build        # Build and start all services
docker compose up                # Start without rebuild
docker compose down              # Stop and remove containers
```

### Backend (Spring Boot / Gradle)
```bash
# Run from repo root or backend/
./gradlew bootRun                            # Start backend dev server (port 8080)
./gradlew build                             # Full build (compile + test + jar)
./gradlew compileJava                       # Compile only
./gradlew test                              # Run all tests
./gradlew test --tests "at.jobhoppr.matching.MatchServiceTest"   # Single test class
./gradlew test --tests "at.jobhoppr.matching.MatchServiceTest.geoFilterBlocksOutOfRange"  # Single method
./gradlew test --tests "at.jobhoppr.integration.*"               # All integration tests
```
Run Gradle commands from `backend/` (or prefix with `-p backend` from repo root).

### Frontend (Angular / npm)
```bash
# Run from frontend/
npm start                        # ng serve — dev server on port 4200
npm run build                    # ng build (production)
npm test                         # ng test — Karma/Jasmine unit tests (watch mode)
npm run test -- --watch=false    # Single run, no watch
# Single spec file:
npx ng test --include="**/match.service.spec.ts" --watch=false
npx ng test --include="**/person-form/**" --watch=false
```

### Linting
```bash
# Frontend
npm run lint                     # ng lint (ESLint)

# Backend — no dedicated linter; Checkstyle/SpotBugs may be added later
./gradlew check                  # Runs tests + any static analysis configured
```

---

## Repository Structure

```
jobhoppr/
├── backend/                     # Spring Boot Gradle project (Java 21)
│   ├── build.gradle
│   └── src/
│       ├── main/java/at/jobhoppr/
│       │   ├── config/          # OpenApiConfig
│       │   ├── domain/
│       │   │   ├── bis/         # Beruf, Kompetenz (read-only BIS reference data)
│       │   │   ├── matching/    # MatchService, MatchModell, criteria, filters
│       │   │   ├── person/      # Person, PersonOrt, PersonKompetenz
│       │   │   └── stelle/      # Stelle, StelleKompetenz
│       │   └── seed/            # BisSeedRunner + JSON seed files
│       └── test/java/at/jobhoppr/
│           ├── matching/        # MatchServiceTest (unit)
│           └── integration/     # *IT.java (Testcontainers)
└── frontend/                    # Angular 19 standalone
    └── src/app/
        ├── core/
        │   ├── api/             # Angular services (HttpClient)
        │   └── models/          # TypeScript interfaces mirroring backend DTOs
        ├── features/
        │   ├── persons/         # person-list, person-form, person-matches
        │   ├── stellen/         # stellen-list, stellen-form, stellen-matches
        │   └── match-modell/    # match-modell-editor
        └── shared/              # person-ort-input, kompetenz-select, beruf-select
```

---

## Code Style — Backend (Java / Spring Boot)

### General
- Java 21; use records for DTOs, sealed interfaces where appropriate.
- Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor`) is allowed but prefer records for DTOs.
- All JPA entities use `UUID` primary keys via `DEFAULT gen_random_uuid()`.
- Timestamps: `OffsetDateTime` (maps to `TIMESTAMPTZ`).

### Naming
| Type | Convention | Example |
|------|------------|---------|
| Classes | `PascalCase` | `PersonService`, `GeoPflichtFilter` |
| DTOs | `PascalCase` + `Dto`/`Request`/`Response` | `PersonDto`, `PersonCreateRequest` |
| Enums | `PascalCase` class, `SCREAMING_SNAKE_CASE` values | `OrtTyp.GENAU`, `KompetenzNiveau.EXPERTE` |
| Packages | `lowercase` | `at.jobhoppr.domain.person` |
| DB columns | `snake_case` | `beruf_id`, `umkreis_km`, `erstellt_am` |
| Repository | `PascalCase` + `Repository` | `PersonRepository` |
| Controller | `PascalCase` + `Controller` | `PersonController` |

### Structure
- Follow domain package structure: one package per aggregate (`person/`, `stelle/`, `matching/`).
- Controllers are thin: delegate all logic to services.
- Services validate that referenced foreign-key IDs exist before persisting.
- Interfaces for extension points: `PflichtFilter`, `MatchKriterium` — register as Spring beans.

### Error Handling
- Use `@ControllerAdvice` + `@ExceptionHandler` for global REST error responses.
- Return standard `ProblemDetail` (Spring 6) or a consistent `{ error, message, status }` envelope.
- Validate inputs with Bean Validation (`@NotNull`, `@NotBlank`, `@Valid`).

### Tests
- Unit tests: plain JUnit 5 + Mockito, no application context.
- Integration tests: suffix `IT` (e.g. `PersonApiIT`), use `@SpringBootTest` + Testcontainers
  (`postgis/postgis:16-3.4`).
- Test method names: `camelCase` describing the scenario, e.g. `geoFilterBlocksOutOfRange`.

---

## Code Style — Frontend (Angular 19 / TypeScript)

### General
- Angular 19 **Standalone Components** only — no `NgModule`.
- `"strict": true` in `tsconfig.json` — no `any`, no non-null assertion abuse.
- Use Angular signals or RxJS `Observable`s for async; prefer signals for local state.
- All HTTP calls go through Angular services in `core/api/`; components never call `HttpClient` directly.

### Naming
| Type | Convention | Example |
|------|------------|---------|
| Components | `PascalCase` + `Component` | `PersonListComponent` |
| Services | `PascalCase` + `Service` | `PersonService` |
| Interfaces/Models | `PascalCase` | `Person`, `MatchResult`, `PersonOrt` |
| Enums | `PascalCase` | `OrtTyp`, `OrtRolle` |
| Files | `kebab-case` | `person-list.component.ts` |
| Feature folders | `kebab-case` | `person-list/`, `match-modell-editor/` |
| Enum values | `SCREAMING_SNAKE_CASE` | `OrtTyp.GENAU`, `KompetenzNiveau.EXPERTE` |

### Imports
- Use path aliases configured in `tsconfig.json` (e.g. `@core/`, `@features/`, `@shared/`).
- Order: Angular core → Angular CDK/Material → third-party → `@core` → `@features` → relative.
- No barrel `index.ts` files unless a module boundary genuinely benefits from one.

### Components
- Declare all dependencies (imports) directly in the `@Component` `imports` array.
- Use `OnPush` change detection for all components.
- Reactive Forms (`FormGroup`, `FormControl`) for all user-input forms.
- Angular Material components for all UI elements (tables, forms, dialogs, snackbars).

### Error Handling
- A global HTTP interceptor catches 4xx/5xx and displays `MatSnackBar` messages.
- Components show `mat-spinner` during async operations; use a `loading` signal/observable.
- Never swallow errors silently — always surface them via the interceptor or component UI.

### TypeScript
- Interfaces (not classes) for data models in `core/models/`.
- Avoid `any`; use `unknown` + type narrowing when type is genuinely unknown.
- Prefer `readonly` arrays and properties on interfaces.
- Enums mirror their Java counterparts exactly (same values, same casing).

---

## Domain Model Quick Reference

Key German domain terms used throughout the codebase:

| Term | Meaning |
|------|---------|
| `Person` | Job seeker |
| `Stelle` | Job posting |
| `Beruf` | Occupation (from AMS BIS) |
| `Kompetenz` | Skill/competency (from AMS BIS) |
| `PersonOrt` | Location entry for a person (Wohnort or Arbeitsort) |
| `OrtRolle` | `WOHNORT` (home) \| `ARBEITSORT` (work) |
| `OrtTyp` | `GENAU` (precise, with radius) \| `REGION` (regional area) |
| `umkreis_km` | Search radius in km |
| `GeoPflichtFilter` | Mandatory geo filter (binary, not scored) |
| `MatchModell` | Weighting configuration for the matching algorithm |
| `BisSeedRunner` | Startup runner that imports BIS reference data |
| `geoAktiv` | Flag to disable geo filter (e.g. in tests) |

---

## Important Constraints

- **Geo is a mandatory filter, not a score**: A person either passes the geo filter and is ranked,
  or is completely excluded. `gewicht_geo` does not exist — only `gewicht_kompetenz` and `gewicht_beruf`.
- **Exactly one active `MatchModell`**: PUT `/api/match-modell` updates the single active record in-place.
- **BIS data is read-only after seed**: Never mutate `bis_beruf` or `bis_kompetenz` via the API.
- **`BisSeedRunner` is idempotent**: It checks `COUNT(*) FROM bis_beruf` before inserting; safe to restart.
- **PostGIS `ST_Distance` returns metres**: Divide by 1000 to get km. Use `::geography` cast.
- **Coordinates are EPSG:4326** (lat/lon decimal degrees); stored as `DOUBLE PRECISION`, not PostGIS geometry columns.
