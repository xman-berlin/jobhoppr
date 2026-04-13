# AGENTS.md — JobHoppr

Guidelines for agentic coding agents working in this repository.
See `plan-matchmodell-migration.md` for the full implementation plan and phase checklist.

---

## Project Overview

**Spring Boot 3.3 backend** (`backend/`) with server-rendered frontend via **Thymeleaf + HTMX + DaisyUI**.
There is no separate frontend directory — all templates live in `backend/src/main/resources/templates/`.

Language of the domain: **German (Austrian)** — all domain terms, variable names, entity names,
and API paths use German (e.g. `Person`, `Stelle`, `Beruf`, `Kompetenz`, `PersonOrt`).
Code-level constructs (method logic, Java idioms) use English where Java conventions demand it,
but domain identifiers stay German.

---

## Build Commands

### Preferred dev workflow (schnell, kein Docker-Build)
```bash
# Terminal 1 — nur die DB starten (kein Backend-Container)
docker compose up db

# Terminal 2 — Backend lokal via Maven starten (port 8080)
cd backend && mvn spring-boot:run
```
> Warum: `docker compose up --build` kompiliert das Backend im Container (langsam).
> Lokales `mvn spring-boot:run` ist deutlich schneller für Entwicklung und Tests.

### Docker (nur wenn vollständiger Container-Stack benötigt wird)
```bash
docker compose up --build        # Build und Start aller Services (langsam)
docker compose up db             # Nur DB starten (für lokale Entwicklung)
docker compose down              # Stoppen und Container entfernen
docker compose down -v           # Auch Volumes löschen (DB-Reset)
```

### Backend (Spring Boot / Maven)
```bash
# Alle Befehle aus backend/ ausführen
mvn spring-boot:run                          # Backend starten (port 8080)
mvn compile                                  # Nur kompilieren
mvn test                                     # Alle Tests
mvn test -Dtest="MatchServiceTest"           # Einzelne Testklasse
mvn test -Dtest="MatchServiceTest#geoFilterBlocksOutOfRange"  # Einzelne Methode
mvn test -Dtest="*IT"                        # Alle Integrationstests
mvn package -DskipTests                      # JAR bauen ohne Tests
```

### Runtime-Verifikation (Backend starten + testen, innerhalb eines Agenten)

`mvn spring-boot:run` blockiert und überschreitet das 120 s-Timeout des Bash-Tools — der JVM-Prozess
läuft danach als Zombie weiter und blockiert Port 8080 für alle weiteren Versuche.

**Immer dieses Muster verwenden:**

```bash
# Schritt 1 — Port freimachen (eigener Bash-Call)
lsof -ti :8080 | xargs kill -9 2>/dev/null; sleep 1

# Schritt 2 — Detached starten mit setsid (eigener Bash-Call)
# setsid öffnet eine neue Process-Session — der JVM-Prozess überlebt das Ende des Bash-Tool-Shells.
# nohup allein reicht NICHT: der Shell sendet beim Exit SIGTERM an die Prozessgruppe.
setsid mvn spring-boot:run > /tmp/jobhoppr-boot.log 2>&1 &
echo "PID=$!"

# Schritt 3 — Polling bis Started (eigener Bash-Call, NICHT mit && verkettet)
for i in $(seq 1 30); do
  sleep 2
  grep -q "Started JobhopprApplication" /tmp/jobhoppr-boot.log 2>/dev/null && echo "UP" && break
  grep -q "APPLICATION FAILED\|BUILD FAILURE" /tmp/jobhoppr-boot.log 2>/dev/null && tail -30 /tmp/jobhoppr-boot.log && break
done

# Schritt 4 — Endpoint testen
curl -s -H "HX-Request: true" "http://localhost:8080/..."

# Schritt 5 — Backend wieder stoppen
lsof -ti :8080 | xargs kill -9 2>/dev/null
```

**Regeln:**
- Nie `mvn spring-boot:run` und curl in einem einzigen `&&`-Befehl verketten.
- Immer Port 8080 freimachen bevor neu gestartet wird.
- Start-Befehl und Poll-Schleife in **separate** Bash-Tool-Calls aufteilen.

### Linting
```bash
# Kein dedizierter Linter; Checkstyle/SpotBugs ggf. später
mvn verify                       # Tests + statische Analyse
```

---

## Repository Structure

```
jobhoppr/
├── backend/                     # Spring Boot Maven project (Java 21)
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/at/jobhoppr/
│       │   │   ├── config/          # Web/OpenAPI config
│       │   │   ├── domain/
│       │   │   │   ├── bis/         # Beruf, Kompetenz (read-only BIS reference data)
│       │   │   │   ├── geo/         # Bundesland, PlzOrt, GeoLocation
│       │   │   │   ├── matching/    # MatchService, MatchModell, MatchRepository
│       │   │   │   ├── person/      # Person, PersonOrt, PersonKompetenz
│       │   │   │   └── stelle/      # Stelle, StelleKompetenz
│       │   │   └── seed/            # BisSeedRunner + JSON seed files
│       │   └── resources/
│       │       ├── db/migration/    # Flyway SQL migrations (V1, V2, ...)
│       │       ├── seed/            # bis_berufe.json, bis_kompetenzen.json
│       │       └── templates/       # Thymeleaf HTML templates
│       │           ├── layout.html
│       │           ├── index.html
│       │           ├── bis/
│       │           ├── personen/    # liste, formular, matches, fragments
│       │           ├── stellen/     # liste, formular, matches, fragments
│       │           └── match-modell/
│       └── test/java/at/jobhoppr/
│           ├── matching/        # Unit tests (plain JUnit 5 + Mockito)
│           └── integration/     # *IT.java (Testcontainers)
└── scripts/                     # Utility scripts (scraping, load testing)
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

## Code Style — Frontend (Thymeleaf + HTMX + DaisyUI)

### General
- All UI is **server-rendered Thymeleaf** — no SPA, no client-side routing.
- **HTMX** handles dynamic interactions: partial page updates, form submissions, autocomplete.
  Use `hx-get`, `hx-post`, `hx-swap`, `hx-target` attributes on HTML elements.
- **DaisyUI 4** (Tailwind CSS component library) for all UI components — use DaisyUI class names
  (`btn`, `card`, `table`, `modal`, `stat`, etc.) over raw Tailwind utilities.
- No JavaScript files — behaviour is driven entirely by HTMX attributes and Thymeleaf fragments.

### Template Structure
- `layout.html` — base layout with navbar; all pages extend it via `th:replace` or `th:insert`.
- Feature templates live in subdirectories matching the domain: `personen/`, `stellen/`, `match-modell/`, `bis/`.
- **Fragments** for HTMX partial responses: `*-fragment.html` files return only the swapped HTML chunk.
- Use `th:fragment` for reusable partials; reference with `th:replace="~{template :: fragment}"`.

### Controllers
- Controllers render Thymeleaf views for full-page requests and return fragment strings for HTMX partials.
- HTMX fragment endpoints are annotated with `@GetMapping` / `@PostMapping` and return a `String`
  view name (e.g. `"personen/kompetenz-fragment :: row"`).
- Pass model data via `Model.addAttribute()`; keep controllers thin — delegate to services.

### Forms
- Use standard HTML `<form>` with Thymeleaf `th:object` and `th:field` bindings.
- HTMX form submissions use `hx-post` / `hx-put` with `hx-swap="outerHTML"` or redirect via
  `HtmxResponse` / `HX-Redirect` response header.

### Naming
| Type | Convention | Example |
|------|------------|---------|
| Template files | `kebab-case.html` | `person-form.html`, `kompetenz-fragment.html` |
| Template dirs | `kebab-case` | `personen/`, `match-modell/` |
| `th:fragment` names | `camelCase` | `th:fragment="kompetenzRow"` |
| URL paths | `kebab-case` | `/personen/{id}/matches` |

---

## Domain Model Quick Reference

Key German domain terms used throughout the codebase:

| Term | Meaning |
|------|---------|
| `Person` | Job seeker |
| `Stelle` | Job posting |
| `Beruf` | Occupation (from BIS) |
| `Kompetenz` | Skill/competency (from BIS) |
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
- **Exactly one active `MatchModell`**: the single record is updated in-place; no new rows are created.
- **BIS data is read-only after seed**: Never mutate `bis_beruf` or `bis_kompetenz` via the API.
- **`BisSeedRunner` is idempotent**: It checks `COUNT(*) FROM bis_beruf` before inserting; safe to restart.
- **PostGIS `ST_Distance` returns metres**: Divide by 1000 to get km. Use `::geography` cast.
- **Coordinates are EPSG:4326** (lat/lon decimal degrees); stored as `DOUBLE PRECISION`, not PostGIS geometry columns.
- **`kompetenz_closure` must be populated before the app serves traffic**: `match_kompetenz()` reads from it; an empty closure table causes all competency scores to return 0.0. `BisSeedRunner` fills it on first start.
