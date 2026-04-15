# Task: BIS Explorer View

## Ziel

Eine zentrale Seite zum Durchsuchen und Erkunden von:
- Berufen (aus BIS)
- Kompetenzen (aus BIS)
- Verknüpfungen (Beruf → Basis/Fach-Kompetenzen)

---

## UI-Konzept (aktuell, v2)

```
┌─────────────────────────────────────────────────────────────────┐
│                        BIS-Explorer                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────┬──────────────────────────────────────┐ │
│  │ SIDEBAR              │  DETAIL                              │ │
│  │                      │                                      │ │
│  │ [  Suche ...      ]  │  (kein Element gewählt)             │ │
│  │                      │                                      │ │
│  │ Ergebnisse / Default │  Wähle einen Beruf aus der Liste.   │ │
│  │ ─────────────────    │                                      │ │
│  │ ▼ Soziales (45)      │                                      │ │
│  │   ▶ Altenpfleger     │                                      │ │
│  │     [Kompetenz 1]    │                                      │ │
│  │     [Kompetenz 2]    │                                      │ │
│  │   ▶ Krankenpfleger   │                                      │ │
│  │ ▼ Technik (23)       │                                      │ │
│  │   ▶ Tischler         │                                      │ │
│  │ [Mehr laden]         │                                      │ │
│  └──────────────────────┴──────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Suchverhalten
- Ein einziges Suchfeld
- Sucht parallel in Berufen **und** Kompetenzen
- Wenn Beruf matcht → direkt in der Liste
- Wenn Kompetenz matcht → die zugehörigen Berufe werden angezeigt (mit den gematchten Kompetenzen bereits aufgeklappt)
- Ergebnisliste zeigt immer **Berufe** (nie nackte Kompetenzen)

### Default-Liste (ohne Suchbegriff)
- Alle Berufe paginiert (20 pro Seite)
- Gruppiert nach **BerufBereich** (Accordion-Gruppen)
- "Mehr laden" Button (HTMX, append)

### Beruf-Eintrag (Accordion)
- Klick auf Beruf-Zeile → klappt Kompetenzen auf (Basis + Fach)
- Klick auf Beruf-Name → lädt Detail-Panel rechts
- Kompetenzen als klickbare Badges → laden Kompetenz-Detail rechts

---

## Tech-Design (v2)

### Routes

| Route | Beschreibung |
|-------|--------------|
| `GET /bis` | Vollseite, übergibt initiale Beruf-Liste (Seite 1, nach Bereich) |
| `GET /bis/suche?q=` | HTMX: Suchergebnisse (Berufe + Kompetenz-Treffer) |
| `GET /bis/berufe?page=` | HTMX: Default-Liste, paginiert, nach Bereich gruppiert |
| `GET /bis/beruf/{id}` | HTMX: Beruf-Detail (rechtes Panel) |
| `GET /bis/kompetenz/{id}` | HTMX: Kompetenz-Detail (rechtes Panel) |

### Neues DTO

```java
record BerufMitKompetenzenDto(
    Integer id,
    String name,
    String pfad,
    String bereich,          // BerufBereich.name für Gruppierung
    List<KompetenzDto> matchKompetenzen  // leer bei Beruf-Direkttreffer
)
```

### Repositories (neu/erweitert)

- `BerufSpezialisierungRepository.findAllOrderedByBereich(Pageable)` — Default-Liste
- `BerufBasisKompetenzRepository.findBerufIdsByKompetenzId(Integer)` — Berufe zu Kompetenz
- `KompetenzRepository.findByParentIdAndIdNot()` — bereits vorhanden

---

## Implementierungsschritte

### Phase 1 (bereits erledigt)
- [x] Dashboard: zwei BIS-Cards zu einer "BIS Explorer"-Card zusammenführen
- [x] Navbar: BIS-Link hinzufügen (Desktop + Mobile)
- [x] `BisExplorerController` Grundgerüst (`GET /bis`, `GET /bis/beruf/{id}`, `GET /bis/kompetenz/{id}`)
- [x] `bis/beruf-detail.html` Fragment
- [x] `bis/kompetenz-detail.html` Fragment

### Phase 2 (offen)
- [ ] Repository: `findAllOrderedByBereich(Pageable)` in `BerufSpezialisierungRepository`
- [ ] Repository: `findBerufIdsByKompetenzId()` in `BerufBasisKompetenzRepository`
- [ ] DTO: `BerufMitKompetenzenDto`
- [ ] `BisExplorerController`: `GET /bis` initiale Liste + `GET /bis/suche` neue Logik + `GET /bis/berufe` Pagination
- [ ] `bis/index.html`: ein Suchfeld, Sidebar-Liste, Detail-Panel
- [ ] `bis/beruf-liste-fragment.html`: Accordion-Beruf-Liste (Default + Suchergebnisse)
- [ ] `bis/suche-ergebnisse.html`: entfernen oder auf Fragment reduzieren (durch beruf-liste-fragment ersetzt)
- [x] Build + Tests (compile clean)
- [x] Fix: `toggleKompetenzen()` in `bis/index.html` definiert (außerhalb Fragment, aber in `<body>`) — Funktion nun beim Seitenload verfügbar und überlebt HTMX-Swaps

---

## Designentscheidungen (festgelegt)

- Suchergebnisse zeigen immer Berufe — Kompetenzen nur als Unterpunkte
- Kompetenzen aufklappbar per Accordion (nicht Hover)
- Default-Liste: nach BerufBereich gruppiert, alphabetisch innerhalb
- Pagination: "Mehr laden" (append), 20 Berufe pro Seite
- Detail-Panel bleibt rechts (Master-Detail Layout bleibt)

---

## Review

**Abgeschlossen:** 2026-04-15

### Was implementiert wurde

- **BIS Explorer** unter `/bis`: Master-Detail-Seite mit Sidebar + Detail-Panel
- **Suchfeld** mit HTMX (300ms Debounce, Spinner): sucht parallel in Berufen und Kompetenzen; bei Kompetenz-Treffer werden zugehörige Berufe mit bereits aufgeklappten Match-Kompetenzen angezeigt
- **Accordion-Liste** nach BerufBereich gruppiert, lazy-loaded Kompetenzen per `hx-trigger="revealed"`
- **Pagination** ("Mehr laden", HTMX append, 20 Berufe/Seite)
- **Detail-Panel** für Beruf und Kompetenz (HTMX swap)
- **Navbar + Dashboard** mit BIS-Link

### Abweichungen vom Plan

- `GET /bis/berufe` → umbenannt zu `GET /bis/berufe-liste` wegen URL-Konflikt mit bestehendem `BisController.berufeVorschlaege`
- `toggleKompetenzen()` konnte nicht im Fragment definiert werden (HTMX swapped nur Fragment-Inhalt) → in `bis/index.html` innerhalb `layout:fragment="content"` definiert

### Bugs gefunden und behoben

1. `<script>` außerhalb `layout:fragment` → vom Thymeleaf Layout Dialect stillschweigend entfernt
2. `spring.thymeleaf.cache=true` → Neustart nötig nach Template-Änderungen
3. DaisyUI `badge` mit `white-space: nowrap` → lange Kompetenz-Namen überlappten; fix: `whitespace-normal h-auto py-0.5 text-left`
