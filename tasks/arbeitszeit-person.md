# Feature: Arbeitszeitmodelle für Person + Matching

## Ziel

1. **Person-Formular**: Arbeitszeitmodelle unterhalb der Kompetenzen anzeigen und editierbar machen (analog zur Stelle).
2. **MatchModell**: `gewichtArbeitszeit` als neues Gewicht einführen, das die Übereinstimmung der Arbeitszeitmodelle in den Score einfließen lässt.
3. **Matches**: In den Match-Karten (Person→Stellen und Stelle→Personen) anzeigen, welche Arbeitszeitmodelle gematcht haben.

---

## Kontext / Analyse

### Was bereits existiert
- `ArbeitszeitModell.java` — Enum: `VOLLZEIT | TEILZEIT | GERINGFUEGIG | NACHT | WOCHENENDE`
- `stelle_arbeitszeit` DB-Tabelle — `(stelle_id, modell, pflicht)` — vollständig implementiert inkl. UI
- `person_arbeitszeit_ausschluss` DB-Tabelle — `(person_id, modell)` — **DB vorhanden, kein UI**
- `Person.arbeitszeitAusschluesse` — `@ElementCollection Set<String>` — **Feld vorhanden, kein UI**
- `MatchRepository`: `arbeitszeit_ko` CTE — KO-Filter läuft bereits, schließt Personen aus die ein Pflicht-Modell ausgeschlossen haben
- `MatchResult`, `MatchModell`, `MatchRepository` — keine Arbeitszeit-Scoring-Logik

### Was fehlt
| Layer | Fehlt |
|-------|-------|
| `PersonCreateRequest` | `Set<String> arbeitszeitAusschluesse` |
| `PersonController.erstellen()` / `aktualisieren()` | `@RequestParam Set<String> arbeitszeitAusschluesse` |
| `PersonService.aktualisiereFelder()` | `p.getArbeitszeitAusschluesse()` clear + addAll |
| `personen/formular.html` | Checkbox-Grid (wie bei Stelle) + `serializeArbeitszeiten()` JS |
| `MatchModell.java` | `gewichtArbeitszeit` Feld |
| Flyway Migration `V15` | `ALTER TABLE match_modell ADD COLUMN gewicht_arbeitszeit ...` |
| `MatchModellService.MatchModellRequest` | `gewichtArbeitszeit` |
| `MatchModellController.speichern()` | `@RequestParam gewichtArbeitszeit` |
| `match-modell/editor.html` | Slider für `gewichtArbeitszeit` |
| `MatchRepository` SQL (beide Queries) | `match_arbeitszeit()` Score-Funktion oder Inline-SQL + `matching_az` Array zurückgeben |
| Flyway Migration `V15` (oder `V16`) | `match_arbeitszeit(person_id, stelle_id)` PostgreSQL-Funktion |
| `MatchResult.java` | `List<String> matchingArbeitszeiten` Feld |
| `MatchRepository.mapRowStatic()` | `matching_az` Array parsen |
| `personen/matches.html` | Matching-Arbeitszeiten als Badges anzeigen |
| `stellen/matches.html` | Matching-Arbeitszeiten als Badges anzeigen |

---

## Design-Entscheidungen

### Semantik: Ausschluss vs. Präferenz
Die Person speichert **Ausschlüsse** (`arbeitszeitAusschluesse`) — das bedeutet: "Dieses Modell will ich NICHT."
Die Stelle speichert **angebotene Modelle** + ob sie **Pflicht** sind.

Für das **Scoring** gilt:
- Ein Modell **matcht**, wenn die Stelle es anbietet UND die Person es **nicht** ausgeschlossen hat.
- Score = Anzahl gematchter Modelle / Anzahl angebotener Modelle der Stelle (0.0–1.0)
- Wenn die Stelle keine Modelle hat → Score = 1.0 (kein Nachteil)

Für die **Anzeige**:
- `matchingArbeitszeiten`: Modelle die die Stelle anbietet und die Person nicht ausgeschlossen hat

### MatchModell: `gewichtArbeitszeit`
- Neues Gewicht im Standard-Score: `(w_beruf*om + w_kompetenz*sm + w_arbeitszeit*am) / (w_beruf + w_kompetenz + w_arbeitszeit)`
- Default: `0.0` (kein Einfluss, rückwärtskompatibel — KO-Filter läuft weiterhin immer)
- Im Lehrstellen-Matching: **kein** Arbeitszeit-Gewicht (bleibt wie bisher)

---

## Implementierungs-Schritte

- [x] **Phase 1 — Person-Formular UI**
  - [x] `personen/formular.html`: Arbeitszeit-Checkboxes im Hauptformular (nicht Sidebar, da Sidebar `th:if="${!isNeu}"`)
    - Checkbox-Grid: alle 5 Modelle (VOLLZEIT, TEILZEIT, GERINGFUEGIG, NACHT, WOCHENENDE)
    - Checked = Modell wird **nicht** gewünscht (Ausschluss)
    - CSS-Klasse `.az-modell` + `data-modell` für JS-Serialisierung
    - `onsubmit="serializeArbeitszeitAusschluesse(this)"` auf dem `<form>`-Tag
    - JS `serializeArbeitszeitAusschluesse()`: injiziert `input[name="arbeitszeitAusschluesse"]` für jeden gecheckten Checkbox
  - [x] Für `isNeu=true`: Funktioniert direkt mit dem POST
  - [x] Für `isNeu=false` (Bearbeiten): Dasselbe — beim Speichern werden alle Werte ersetzt
  - **BUG FIX**: Ursprünglich war die Arbeitszeit-Section in der Sidebar mit `th:if="${!isNeu}"` — für neue Personen nie gerendert. Jetzt im Hauptformular.
- **BUG FIX**: LazyInitializationException — `arbeitszeitAusschluesse` lazy geladen, Session geschlossen bevor Template zugreift. Gelöst durch:
  - JOIN FETCH in `PersonRepository.findByIdWithDetails()`
  - `new HashSet<>(person.getArbeitszeitAusschluesse())` im Controller

- [ ] **Phase 2 — Backend: PersonCreateRequest + Service + Controller**
  - [ ] `PersonService.PersonCreateRequest`: `Set<String> arbeitszeitAusschluesse` hinzufügen
  - [ ] `PersonService.erstellen()`: `p.getArbeitszeitAusschluesse().addAll(req.arbeitszeitAusschluesse())` nach erstem save
  - [ ] `PersonService.aktualisiereFelder()`: `p.getArbeitszeitAusschluesse().clear(); p.getArbeitszeitAusschluesse().addAll(...)`
  - [ ] `PersonController.erstellen()` + `aktualisieren()`: `@RequestParam(required=false) Set<String> arbeitszeitAusschluesse` + an Request weitergeben

- [ ] **Phase 3 — MatchModell: gewichtArbeitszeit**
  - [ ] `V15__match_modell_gewicht_arbeitszeit.sql`: `ALTER TABLE match_modell ADD COLUMN gewicht_arbeitszeit DOUBLE PRECISION NOT NULL DEFAULT 0.0`
  - [ ] `MatchModell.java`: `private Double gewichtArbeitszeit = 0.0;`
  - [ ] `MatchModellService.MatchModellRequest`: `double gewichtArbeitszeit`
  - [ ] `MatchModellService.aktualisieren()`: `m.setGewichtArbeitszeit(req.gewichtArbeitszeit())`
  - [ ] `MatchModellController.speichern()`: `@RequestParam(defaultValue="0.0") double gewichtArbeitszeit`
  - [ ] `match-modell/editor.html`: Slider für `gewichtArbeitszeit` im Standard-Abschnitt

- [ ] **Phase 4 — MatchRepository: Scoring + Details**
  - [ ] `V15` (oder separate `V16`): PostgreSQL-Funktion `match_arbeitszeit(person_id UUID, stelle_id UUID) RETURNS FLOAT`
    ```sql
    -- Score: Anteil angebotener Modelle die Person NICHT ausgeschlossen hat
    -- 0 Modelle an Stelle → 1.0
    CREATE OR REPLACE FUNCTION match_arbeitszeit(p_id UUID, s_id UUID) RETURNS FLOAT AS $$
      SELECT CASE
        WHEN COUNT(sa.modell) = 0 THEN 1.0
        ELSE COUNT(CASE WHEN sa.modell NOT IN (
               SELECT modell FROM person_arbeitszeit_ausschluss WHERE person_id = p_id
             ) THEN 1 END)::FLOAT / COUNT(sa.modell)
      END
      FROM stelle_arbeitszeit sa
      WHERE sa.stelle_id = s_id
    $$ LANGUAGE sql STABLE;
    ```
  - [ ] `V15/V16`: Funktion `match_arbeitszeit_details(p_id UUID, s_id UUID) RETURNS TABLE(modell TEXT)`
    ```sql
    -- Gibt gematchte Modelle zurück (Stelle bietet an, Person hat nicht ausgeschlossen)
    CREATE OR REPLACE FUNCTION match_arbeitszeit_details(p_id UUID, s_id UUID)
    RETURNS TABLE(modell TEXT) AS $$
      SELECT sa.modell
      FROM stelle_arbeitszeit sa
      WHERE sa.stelle_id = s_id
        AND sa.modell NOT IN (
          SELECT modell FROM person_arbeitszeit_ausschluss WHERE person_id = p_id
        )
      ORDER BY sa.modell
    $$ LANGUAGE sql STABLE;
    ```
  - [ ] `MatchRepository.STELLEN_FOR_PERSON_BASE`: `match_arbeitszeit(:person_id, s.id) AS am` + `ARRAY(SELECT match_arbeitszeit_details(...))` in CROSS JOIN LATERAL; Score-Formel um `w_arbeitszeit * am` erweitern; `matching_az` in SELECT
  - [ ] `MatchRepository.PERSONEN_FOR_STELLE_BASE`: symmetrisch
  - [ ] `MatchRepository.baseParams()`: `w_arbeitszeit` hinzufügen
  - [ ] `MatchRepository.mapRowStatic()`: `matching_az` parsen → `List<String>`
  - [ ] `MatchResult.java`: `List<String> matchingArbeitszeiten` Feld hinzufügen (nach `extraVoraussetzungen`)

- [ ] **Phase 5 — Matches-Templates**
  - [ ] `personen/matches.html`: Arbeitszeit-Badges pro Match-Karte (falls `matchingArbeitszeiten` nicht leer)
  - [ ] `stellen/matches.html`: dasselbe

- [ ] **Phase 6 — DevDataSeeder: Arbeitszeitmodelle seeden**
- [ ] **Phase 7 — Build-Verifikation**: `mvn package -DskipTests` → BUILD SUCCESS
- [ ] **Phase 8 — Browser-Verifikation**: Backend starten, Puppeteer-Tests

---

## Dateien die geändert werden

```
backend/src/main/
├── resources/
│   ├── db/migration/
│   │   └── V15__arbeitszeit_matching.sql           # Neu
│   └── templates/
│       ├── personen/
│       │   ├── formular.html                        # +Arbeitszeit-Checkboxen im Sidebar
│       │   └── matches.html                         # +matchingArbeitszeiten Badges
│       ├── stellen/
│       │   └── matches.html                         # +matchingArbeitszeiten Badges
│       └── match-modell/
│           └── editor.html                          # +gewichtArbeitszeit Slider
└── java/at/jobhoppr/
    ├── domain/person/
    │   ├── PersonService.java                       # +arbeitszeitAusschluesse
    │   └── PersonController.java                    # +@RequestParam arbeitszeitAusschluesse
    ├── domain/matching/
    │   ├── MatchModell.java                         # +gewichtArbeitszeit
    │   ├── MatchModellService.java                  # +gewichtArbeitszeit
    │   ├── MatchModellController.java               # +@RequestParam gewichtArbeitszeit
    │   ├── MatchRepository.java                     # +am-Score + matching_az
    │   └── MatchResult.java                         # +matchingArbeitszeiten
    └── seed/
        └── DevDataSeeder.java                       # +Arbeitszeit-Seeding für Stellen + Personen
```
backend/src/main/
├── resources/
│   ├── db/migration/
│   │   └── V15__arbeitszeit_matching.sql           # Neu: match_arbeitszeit() + match_arbeitszeit_details() + gewicht_arbeitszeit
│   └── templates/
│       ├── personen/
│       │   ├── formular.html                        # +Arbeitszeit-Checkboxen im Sidebar
│       │   └── matches.html                         # +matchingArbeitszeiten Badges
│       ├── stellen/
│       │   └── matches.html                         # +matchingArbeitszeiten Badges
│       └── match-modell/
│           └── editor.html                          # +gewichtArbeitszeit Slider
└── java/at/jobhoppr/
    ├── domain/person/
    │   ├── PersonService.java                       # +arbeitszeitAusschluesse in Request + Service-Logik
    │   └── PersonController.java                    # +@RequestParam arbeitszeitAusschluesse
    └── domain/matching/
        ├── MatchModell.java                         # +gewichtArbeitszeit
        ├── MatchModellService.java                  # +gewichtArbeitszeit in Request + aktualisieren()
        ├── MatchModellController.java               # +@RequestParam gewichtArbeitszeit
        ├── MatchRepository.java                     # +am-Score + matching_az in beiden Queries
        └── MatchResult.java                         # +matchingArbeitszeiten List<String>
```

---

## Offene Fragen / Annahmen

- `gewichtArbeitszeit` default `0.0` → Feature ist rückwärtskompatibel; aktivierbar durch den User im MatchModell-Editor
- Ausschlüsse werden bei Neuanlage einer Person im Haupt-Formular gesetzt (kein separater HTMX-Endpunkt nötig — einfachere Lösung)
- Beim Bearbeiten: Formularsave ersetzt alle Ausschlüsse (clear + addAll, analog zu Interessen/Voraussetzungen)
- Keine HTMX-Add/Remove-Einzelaktionen für Arbeitszeit (unnötige Komplexität)

---

## Review

_Wird nach Abschluss ergänzt._
