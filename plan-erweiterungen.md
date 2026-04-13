# Plan: JobHoppr — Erweiterungen

## Context

Nach Abschluss des vollständigen Matchmodells sollen folgende Erweiterungen umgesetzt werden:

1. **Geo-Daten nutzen** — Kein externes Geocoding mehr, sondern die bereitgestellten Geo-Daten (Bundesländer, Bezirke, Orte) für Standort-Auswahl
2. **Stellen + Lehrstellen zusammenführen** — Eine Card statt zwei separate Cards auf dem Dashboard
3. **Berufe/Kompetenzen Cards?** —.reference data, evtl. entfernen vom Dashboard
4. **Standort CRUD** — Standort-Auswahl basierend auf Geo-Hierarchie (Bundesland → Bezirk → Ort)
5. **Lat/Lon nicht anzeigen** — Nur für Matching verwenden, nicht in UI zeigen
6. **Match-Details** — Gematchte vs. nicht-gematchte Kompetenzen anzeigen
7. **Basis-Kompetenzen aus BIS** — Automatisch laden bei Beruf-Zuordnung in Person/Stelle

---

## Progress

- [ ] Phase 1: Standort CRUD mit Geo-Hierarchie (Bundesland → Bezirk → Ort)
- [ ] Phase 2: Berufe/Kompetenzen Cards entfernen vom Dashboard
- [ ] Phase 3: Stellen + Lehrstellen zu einer Card zusammenführen
- [x] Phase 4: Match-Details — gematchte/nicht-gematchte Kompetenzen anzeigen
- [ ] Phase 5: Basis-Kompetenzen aus BIS automatisch laden → [tasks/basis-kompetenzen.md](tasks/basis-kompetenzen.md)

---

## Delta: Was sich ändert

| Bereich | Aktueller Stand | Zielzustand |
|---------|---------------|------------|
| Standort-Auswahl | Freitext oder separate PLZ-Suche | Dropdown: Bundesland → Bezirk → Ort |
| Lat/Lon Anzeige | In person_ort/stelle sichtbar | Nur intern für Matching, nicht in UI |
| Dashboard Cards | 5 Cards (Personen, Stellen, Lehrstellen, Berufe, Kompetenzen) | 3 Cards (Personen, Stellen, Berufe/Kompetenzen) oder 4 |
| Match-View | Score + Breakdown | Zusätzlich: genau Liste der gematchten vs. fehlenden Kompetenzen |
| Beruf-Zuordnung | Manuell Kompetenzen wählen | Kompetenzen aus BIS-Basis (wenn vorhanden) vorausgefüllt |

---

## Phase 1: Standort CRUD mit Geo-Hierarchie

### Datenmodell erweitern

Die `geo_location`-Tabelle hat bereits eine Hierarchie: ORT → BEZIRK → BUNDESLAND.

Aktuell fehlt eine Verknüpfung zu den `plz_ort`-Daten (die PLZ-Daten wurden via `PlzSeedRunner` importiert).

```sql
-- Verknüpfung: geo_location.zu_plz_ort_id
ALTER TABLE geo_location ADD COLUMN plz_ort_id INTEGER REFERENCES plz_ort(id);
```

### Endpunkte

- `GET /api/geo/bundeslaender` — alle Bundesländer
- `GET /api/geo/bezirke?bundeslandId={id}` — Bezirke eines Bundeslands
- `GET /api/geo/orte?bezirkId={id}` — Orte eines Bezirks

### UI-Anpassung

`personen/formular.html` und `stellen/formular.html`:

- Neues Feld `standort` mit drei Dropdowns (Bundesland → Bezirk → Ort)
- Bei Auswahl wird automatisch `lat`/`lon` aus `geo_location`-Centroid gesetzt
- Koordinaten-Eingabefelder werden versteckt (`type="hidden"`)

---

## Phase 2: Berufe/Kompetenzen Cards entfernen

Im `IndexController` die counts für Berufe und Kompetenzen entfernen.

```java
// Aktuell:
model.addAttribute("anzahlBeruf", berufRepository.count());
model.addAttribute("anzahlKompetenz", kompetenzRepository.count());

// Entfernen — diese reference data braucht keine eigene Card
```

Template `index.html` — die zwei Cards für Berufe und Kompetenzen entfernen.

---

## Phase 3: Stellen + Lehrstellen zusammenführen

Eine Card zeigt Gesamtzahl, bei Klick werden beide Typen angezeigt (oder Filter-Dropdown).

```java
// IndexController: Gesamtstadt (STANDARD + LEHRSTELLE)
model.addAttribute("anzahlStelle", stelleRepository.count());  // alle
model.addAttribute("anzahlLehrstelle", stelleRepository.countByTyp(StelleTyp.LEHRSTELLE));
```

`index.html` — eine Card mit:
- Titel: "Stellen" (statt zwei Cards)
- Zahl: Gesamt (mit Breakdown in Tooltip oder Klick)

---

## Phase 4: Match-Details — Kompetenzen-Liste

Im `MatchResult` already vorhanden: `Breakdown(om, sm, fm, qm)`.

Erweitern für detaillierte Anzeige:

```java
// MatchResult erweitern:
record MatchResult(...) {
    record Breakdown(...) {}
    
    // NEU: Listen der Kompetenzen
    List<KompetenzInfo> matchingKompetenzen;   // die gematcht haben
    List<KompetenzInfo> missingKompetenzen;   // die fehlen (bei KO wichtig)
    
    record KompetenzInfo(String name, double score) {}
}
```

### Matching-Query erweitern

Zusätzlich zu `om, sm, fm, qm` die Kompetenzen zurückgeben:

```sql
-- Im scores CTE:
array_agg(k.name) FILTER (WHERE score > 0) AS matching_kompetenzen,
array_agg(k.name) FILTER (WHERE score = 0 AND sk.pflicht = TRUE) AS missing_kompetenzen
```

Template `personen/matches.html` / `stellen/matches.html`:
- Abschnitt "Kompetenzen" mit zwei Listen: ✓ gematcht, ✗ fehlen

---

## Phase 5: Basis-Kompetenzen aus BIS

**Frage:** Gibt es im BIS "Basis-Kompetenzen" für Berufe?

Falls ja: Bei Zuordnung eines Berufs zu Person/Stelle automatisch Kompetenzen vorausfillen.

### Datenmodell (falls nicht vorhanden)

```sql
-- Basis-Kompetenzen für einen Beruf (Berufsuntergruppe/BUG-Ebene)
CREATE TABLE beruf_basis_kompetenz (
    beruf_untergruppe_id INTEGER NOT NULL REFERENCES beruf_untergruppe(id),
    kompetenz_id     INTEGER NOT NULL REFERENCES bis_kompetenz(id),
    pflicht        BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (beruf_untergruppe_id, kompetenz_id)
);
```

### Seeder erweitern

Falls BIS Daten entsprechende Kompetenzen enthalten → importieren.

### UI-Anpassung

Bei Beruf-Auswahl in `personen/formular.html` / `stellen/formular.html`:

1. Beruf auswählen → BUG-ID ermitteln
2. `SELECT * FROM beruf_basis_kompetenz WHERE beruf_untergruppe_id = ?`
3. Diese Kompetenzen automatisch vorausgewählt anzeigen
4. User kann manuell anpassen

---

## Tech Stack

Unverändert:

| Layer | Choice |
|-------|--------|
| Backend | Spring Boot 3.3.5, Java 21, Maven |
| Database | PostgreSQL 16 + PostGIS |
| Frontend | Thymeleaf + HTMX + DaisyUI 4 |

---

## Implementierungs-Checkliste

### Phase 1: Standort CRUD mit Geo-Hierarchie

- [ ] `geo_location.plz_ort_id` Verknüpfung via Flyway
- [ ] `GeoLocationRepository.findByEbene(String)` oder neue Methods
- [ ] `GeoRestController` erweitern: `/api/geo/bezirke`, `/api/geo/orte`
- [ ] `personen/formular.html` — 3 Dropdowns für Standort
- [ ] `stellen/formular.html` — analog
- [ ] Koordinaten-Felder verstecken (type="hidden")

### Phase 2: Berufe/Kompetenzen Cards entfernen

- [ ] `IndexController` — counts entfernen
- [ ] `index.html` — Cards entfernen

### Phase 3: Stellen + Lehrstellen zusammenführen

- [ ] `IndexController` — `anzahlStelle` (Gesamt) + `anzahlLehrstelle`
- [ ] `index.html` — eine Card mit Breakdown

### Phase 4: Match-Details

- [x] `MatchResult` erweitern: Listen für Kompetenzen
- [x] `MatchRepository` SQL erweitern
- [x] `personen/matches.html` — Kompetenzen-Listen
- [x] `stellen/matches.html` — analog

### Phase 5: Basis-Kompetenzen

- [ ] Prüfen:Hat BIS Basis-Kompetenzen für Berufe?
- [ ] Falls ja: Tabelle + Seeder
- [ ] `PersonService` / `StelleService` anpassen: bei Beruf-Wechsel Kompetenzen aktualisieren
- [ ] UI: bei Beruf-Auswahl Kompetenzen vorausfüllen

---

## Wichtige Hinweise

- **Lat/Lon nur für Matching**: Die Koordinaten sollen nicht in der UI sichtbar sein, aber für `ST_DWithin` verwendet werden
- **Geo-Hierarchie**: Die `geo_location`-Tabelle enthält bereits Bundesländer, Bezirke, Orte — nur die Verknüpfung zu `plz_ort` fehlt
- **BIS Daten**: Ob Basis-Kompetenzen in den BIS-Daten vorhanden sind, muss beim Scraper/Seed geprüft werden

---

## Offene Fragen

1. **Hat BIS Basis-Kompetenzen?** — Wir müssen die BIS-Daten prüfen (oder erneut scrapen)
2. **Wie viele Cards am Ende?** — Aktuell 5, Ziel evtl. 3 oder 4:
   - Personen (bleibt)
   - Stellen (zusammen)
   - Berufe/Kompetenzen (zusammen oder weg?)
 
Entscheidung: Berufe/Kompetenzen sind reference data — vielleicht besser als eigene Seite statt Card?