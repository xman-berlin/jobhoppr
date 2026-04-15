# Proposal: BIS Exploration View

## Option A: Tab-basierte Explore-Seite

```
┌─────────────────────────────────────────────────────────────────┐
│ JobHoppr                    Personen  Stellen  Match-Modell  BIS │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ 🔍 Beruf suchen...                              [Suche]    │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐               │
│  │ Berufe      │ │ Kompetenzen │ │ Verknüpfungen              │  │
│  │    847      │ │   8.432     │ │   5.700     │               │
│  └─────────────┘ └─────────────┘ └─────────────┘               │
│                                                                  │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  ┌─ Berufe Tab ────────────────────────────────────────────────┐ │
│  │                                                             │ │
│  │ Filter: [Alle ▼] [Berufsfeld ▼] [Suche...]                 │ │
│  │                                                             │ │
│  │ ┌─────────────────────────────────────────────────────────┐│ │
│  │ │ 71102 - Sozialpädagoge/in                               ││ │
│  │ │ Berufsfeld: Soziales, Gesundheit, Bildung               ││ │
│  │ │ ────────────────────────────────────                    ││ │
│  │ │ Basis-Kompetenzen: Kommunikationsstärke, Teamfähigkeit  ││ │
│  │ │ Fach-Kompetenzen: Didaktikkenntnisse, Pädagogikkenntnisse││
│  │ └─────────────────────────────────────────────────────────┘│ │
│  │                                                             │ │
│  │ ┌─────────────────────────────────────────────────────────┐│ │
│  │ │ 71101 - Kindergartenpädagoge/in                         ││ │
│  │ │ ...                                                     ││ │
│  │ └─────────────────────────────────────────────────────────┘│ │
│  │                                                             │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Features:**
- 3 Tabs: Berufe | Kompetenzen | Verknüpfungen
- Suche in jedem Tab (HTMX lazy load)
- Klick auf Beruf → zeigt zugehörige Kompetenzen
- Klick auf Kompetenz → zeigt Berufe die diese benötigen

---

## Option B: Einzige Explore-Seite mit Master-Detail

```
┌─────────────────────────────────────────────────────────────────┐
│                        BIS-Explorer                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────┬────────────────────────────────────────┐│
│  │ SUCHE               │                                        ││
│  │ ┌─────────────────┐ │  DETAIL                                ││
│  │ │ Beruf: [____]   │ │  ────────────────────────────────────  ││
│  │ │ Kompetenz:[____]│ │                                        ││
│  │ └─────────────────┘ │  Gewählter: Sozialpädagoge/in        ││
│  │                     │  ID: 71102                            ││
│  │ ALLE ANZEIGEN      │  Berufsfeld: Soziales, Gesundheit...  ││
│  │ ─────────────────  │                                        ││
│  │ 🔍 Berufe (847)    │  ────────────────────────────────────  ││
│  │ 🔍 Kompetenzen(8.432│  VERKNÜPFTE KOMPETENZEN                ││
│  │                     │  ├─ Basis (5)                         ││
│  │                     │  │  ├─ Kommunikationsstärke           ││
│  │                     │  │  ├─ Teamfähigkeit                  ││
│  │                     │  │  └─ ...                           ││
│  │                     │  └─ Fach (12)                         ││
│  │                     │     ├─ Didaktikkenntnisse             ││
│  │                     │     └─ Pädagogikkenntnisse            ││
│  │                     │                                        ││
│  └─────────────────────┴────────────────────────────────────────┘│
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**HTMX-Interaction:**
- Sidebar: Liste wird via `/bis/berufe?q=` gefiltert
- Main: Klick auf Item lädt `/bis/beruf/{id}/detail` in Detail-Bereich
- Verknüpfungen: werden lazy geladen

---

## Option C: Dashboard-Karte erweitern (minimal)

Die bestehenden Karten auf `/` werden verlinkt und führen zu dedizierten Seiten:

```
/bis/berufe    → Liste aller Berufe mit Suchfeld
/bis/kompetenzen → Liste aller Kompetenzen  
/bis/verknuepfungen → Matrix: Beruf ↔ Kompetenz
```

Jede Seite hat dasselbe Layout: Suchfeld + gefilterte Liste.

---

## Empfehlung

**Option B** (Master-Detail) bietet die beste UX:
- Zentrale Seite für alles
- Suche in beiden Dimensionen (Beruf → Kompetenzen, Kompetenz → Berufe)
- Detail-Bereich zeigt Zusammenhänge

Soll ich mit der Implementierung beginnen?
