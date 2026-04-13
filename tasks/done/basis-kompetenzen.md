# Phase 5: Basis-Kompetenzen aus BIS

## Kontext

BIS-Rohdaten (`stammberufe.xml`) enthalten pro Stammberuf typisierte Qualifikationslisten.
Wir nutzen nur `basisQualifikationen` — die Pflicht-Einstiegskompetenzen.

**Entscheidungen:**
- Nur `basisQualifikationen` (nicht `fach` oder `ueberfachlich`)
- Verknüpfung über Stammberuf (`beruf_spezialisierung.id = stammberuf_noteid`) — Spezialisierungen desselben Stammberufs teilen dieselben Basis-Kompetenzen (fachlich korrekt, so im BIS modelliert)
- Vorschläge statt Autofill — User klickt Kompetenzen einzeln an, keine automatische Übernahme
- Vorschläge auch beim Neu-Anlegen (nicht nur im Edit-Modus)

---

## Datengrundlage

| Quelle | Inhalt |
|--------|--------|
| `stammberufe.xml` | 518 Stammberufe, je mit `<basisQualifikationen>` |
| Mapping | `stammberuf noteid` → `beruf_spezialisierung.id` (kein Offset) |
| Mapping | `qualifikation noteid` → `bis_kompetenz.id = 20000 + noteid` |
| Volumen | ~1.222 Beruf→Qualifikation-Paare (nur `basis`) |

---

## Checkliste

### 1. Seed-Daten exportieren
- [x] `convert_bis.py` erweitern: neue Funktion `convert_beruf_kompetenzen()`
  - Parst `stammberufe.xml`, extrahiert nur `<basisQualifikationen>`
  - Output: `bis_beruf_kompetenzen.json` → `[{ "berufId": 323, "kompetenzId": 20188 }, ...]`
  - Schreibt nach `backend/src/main/resources/seed/`
- [x] Skript ausführen, JSON prüfen (1.222 Einträge, 518 Berufe, 184 eindeutige Kompetenzen)

### 2. Flyway Migration V13
- [x] `V13__beruf_basis_kompetenz.sql` anlegen

### 3. Seed-Runner erweitern
- [x] `BerufHierarchieSeedRunner`: neue Methode `seedBasisKompetenzen()` (idempotent)

### 4. JPA + Repository
- [x] Entity `BerufBasisKompetenz` + Composite-Key `BerufBasisKompetenzId`
- [x] `BerufBasisKompetenzRepository`: `findKompetenzenByBerufId()` → `List<KompetenzDto>`

### 5. Backend-Endpoint
- [x] `BisController`: `GET /bis/berufe/{berufId}/kompetenzen` → Fragment

### 6. Fragment: `bis/beruf-kompetenzen-vorschlaege.html`
- [x] Fragment `vorschlaege` mit Badge-Buttons (`data-kompetenz-id`, `.basis-kompetenz-btn`)

### 7. Frontend — `personen/formular.html`
- [x] Container `<div id="basis-kompetenzen-vorschlaege">` unter Beruf-Feld
- [x] JS Event-Delegation für `.basis-kompetenz-btn` (isNeu-Fallunterscheidung)
- [x] `berufe-vorschlaege.html` Click-Handler lädt Kompetenzen per HTMX

### 8. Frontend — `stellen/formular.html`
- [x] Container `<div id="basis-kompetenzen-vorschlaege">` unter Beruf-Feld
- [x] JS Event-Delegation für `.basis-kompetenz-btn` (pflicht=false beim POST)

### 9. Services / Bugfixes
- [x] `PersonService.erstellen()`: zweistufig (erst save, dann Kompetenzen) — UUID-Bug gefixt
- [x] `PersonService.aktualisiereFelder()`: verarbeitet jetzt `kompetenzIds` (nur neue hinzufügen)
- [x] `StelleService.erstellen()`: zweistufig (analog PersonService) — UUID-Bug gefixt

### 10. Verifikation
- [x] `mvn compile` erfolgreich

---

## Review

**Abgeschlossen: 2026-04-13**

Alle Punkte implementiert, build-verifiziert (`mvn package -DskipTests`) und im Browser getestet.

### Erweiterung gegenüber ursprünglichem Plan
Der ursprüngliche Plan umfasste nur `basisQualifikationen`. Auf Wunsch wurden zusätzlich
`fachQualifikationen` aus BIS eingebunden und in einem getrennten Abschnitt angezeigt.
Das `typ`-Feld (`"basis"` / `"fach"`) wurde in JSON, DB-Schema, Entity, SeedRunner und Template ergänzt.

### Geänderte Dateien
| Datei | Art |
|-------|-----|
| `scripts/convert_bis.py` | Erweitert: `basisQualifikationen` + `fachQualifikationen`, `typ`-Feld |
| `resources/seed/bis_beruf_kompetenzen.json` | Neu generiert: 6.830 Mappings (1.222 basis + 5.608 fach) |
| `db/migration/V13__beruf_basis_kompetenz.sql` | Neu: Tabelle + Index |
| `db/migration/V14__beruf_kompetenz_typ.sql` | Neu: `ADD COLUMN typ VARCHAR(10) NOT NULL DEFAULT 'basis'` |
| `seed/BerufHierarchieSeedRunner.java` | Erweitert: `seedBasisKompetenzen()` mit Count-Vergleich + Re-Seed |
| `bis/BerufBasisKompetenz.java` | Neu: Entity mit `typ` + `@ManyToOne kompetenz` |
| `bis/BerufBasisKompetenzRepository.java` | Neu: `JOIN FETCH` Query, gibt `List<BerufBasisKompetenz>` zurück |
| `bis/BisController.java` | Erweitert: gruppiert nach `typ`, zwei Model-Attribute |
| `templates/bis/beruf-kompetenzen-vorschlaege.html` | Neu: zwei Abschnitte (primary/secondary badges) |
| `templates/bis/berufe-vorschlaege.html` | Erweitert: HTMX-Trigger nach Beruf-Auswahl |
| `templates/personen/formular.html` | Erweitert: Container + JS-Handler + `th:inline="javascript"` Fix |
| `templates/stellen/formular.html` | Erweitert: Container + JS-Handler + `th:inline="javascript"` Fix |
| `person/PersonService.java` | Gefixt: zweistufiges `erstellen()`, `aktualisiereFelder()` |
| `stelle/StelleService.java` | Gefixt: zweistufiges `erstellen()` |

### Bugs gefixt
- `PersonService.erstellen()` und `StelleService.erstellen()`: UUID war beim Kompetenzen-Setzen noch `null` → transaktionales zweistufiges Speichern
- `PersonService.aktualisiereFelder()`: `kompetenzIds` aus Request wurde nie verarbeitet (stiller Bug)
- `th:inline="javascript"` fehlte in beiden Formular-Templates → PERSON_ID/STELLE_ID waren immer `null`
- `BerufBasisKompetenzRepository`: innere Records in JPQL Constructor-Expressions werden von Hibernate nicht gefunden → gelöst mit `@ManyToOne` auf Entity-Ebene + `JOIN FETCH`
