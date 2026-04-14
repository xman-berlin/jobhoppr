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

- [x] **Phase 2 — Backend: PersonCreateRequest + Service + Controller**
  - [x] `PersonService.PersonCreateRequest`: `Set<String> arbeitszeitAusschluesse` hinzufügen
  - [x] `PersonService.erstellen()`: `p.getArbeitszeitAusschluesse().addAll(req.arbeitszeitAusschluesse())`
  - [x] `PersonService.aktualisiereFelder()`: `p.getArbeitszeitAusschluesse().clear(); addAll(...)`
  - [x] `PersonController.erstellen()` + `aktualisieren()`: `@RequestParam(required=false) Set<String> arbeitszeitAusschluesse`

- [x] **Phase 3 — MatchModell: gewichtArbeitszeit**
  - [x] `V15__arbeitszeit_matching.sql`: `ALTER TABLE match_modell ADD COLUMN IF NOT EXISTS gewicht_arbeitszeit`
  - [x] `MatchModell.java`: `private Double gewichtArbeitszeit = 0.0;`
  - [x] `MatchModellService.MatchModellRequest`: `double gewichtArbeitszeit`
  - [x] `MatchModellService.aktualisieren()`: `m.setGewichtArbeitszeit(req.gewichtArbeitszeit())`
  - [x] `MatchModellController.speichern()`: `@RequestParam(defaultValue="0.0") double gewichtArbeitszeit`
  - [x] `match-modell/editor.html`: Slider für `gewichtArbeitszeit`

- [x] **Phase 4 — MatchRepository: Scoring + Details**
  - [x] `V15__arbeitszeit_matching.sql`: `match_arbeitszeit()` + `match_arbeitszeit_details()` Funktionen
    - Semantik: markierte Modelle (in `person_arbeitszeit_ausschluss`) = **gewünscht**
    - Score = |Schnittmenge(Stelle-Modelle ∩ Person-Wünsche)| / |Stelle-Modelle|; 1.0 wenn Stelle keine Modelle hat
  - [x] `MatchRepository`: `match_arbeitszeit_details()` in CROSS JOIN LATERAL (`matching_az`); `NULL::TEXT[]` ersetzt
  - [x] `MatchRepository.baseParams()`: `w_arbeitszeit` vorhanden
  - [x] `MatchRepository.mapRowStatic()`: `matching_az` → `matchingAz` via `parseStringArray()`
  - [x] `MatchResult.java`: `List<String> matchingArbeitszeiten`

- [x] **Phase 5 — Matches-Templates**
  - [x] `personen/matches.html`: Arbeitszeit-Badges (badge-warning)
  - [x] `stellen/matches.html`: dasselbe

- [x] **Phase 6 — DevDataSeeder: Arbeitszeitmodelle seeden**
- [x] **Phase 7 — Build-Verifikation**: `mvn test` → BUILD SUCCESS
- [ ] **Phase 8 — Browser-Verifikation**: Backend starten, App im Browser testen

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

- **V15__arbeitszeit_matching.sql** neu angelegt — war die einzige fehlende Datei (Funktionen existierten nur direkt in der laufenden DB, nie als Migration)
- **Semantik korrigiert**: `match_arbeitszeit()` verwendet positive Logik (`IN` statt `NOT IN`) — markierte Modelle = gewünscht = matcht
- **`matching_az`** in beiden SQL-Queries von `NULL::TEXT[]` auf `ARRAY(SELECT match_arbeitszeit_details(...))` umgestellt — Badges in Matches-Templates funktionieren jetzt
- `bd.matching_az` ins `scores`-CTE aufgenommen damit outer SELECT darauf zugreifen kann
- Duplicate `setGewichtArbeitszeit()` in `MatchModellService` entfernt
- Dead-code DOMContentLoaded-Block (falsche CSS-Selektoren) in `personen/formular.html` entfernt
- `mvn test` → BUILD SUCCESS
