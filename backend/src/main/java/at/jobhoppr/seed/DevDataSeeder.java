package at.jobhoppr.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Generates realistic Austrian test data for dev/demo purposes.
 * 5 Berufscluster × ~40 Persons + ~16 Stellen = 200 Persons / 80 Stellen standard + 10 Lehrstellen.
 *
 * Uses real BIS IDs from beruf_spezialisierung (seeded by BerufHierarchieSeedRunner).
 *
 * Cluster mapping (beruf_spezialisierung_id → kompetenz_ids):
 *   IKT:        spezialisierungen aus BF 303 (Softwaretechnik, Programmierung)
 *   Finanzen:   spezialisierungen aus BF 282 (Bank-, Finanz- und Versicherungswesen)
 *   Gesundheit: spezialisierungen aus BF 335 (Gesundheits- und Krankenpflege)
 *   Bildung:    spezialisierungen aus BF 360 (Schule, Weiterbildung, Hochschule)
 *   Technik:    spezialisierungen aus BF 319 (Maschinen- und Anlagenbau)
 */
@Component
@Profile("dev")
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    // ── Austrian city sample data: {name, lat, lon} ──────────────────────────
    private static final Object[][] STAEDTE = {
        {"Wien, 1010",         48.2082, 16.3738},
        {"Wien, 1020",         48.2145, 16.3908},
        {"Wien, 1100",         48.1741, 16.3676},
        {"Wien, 1140",         48.2003, 16.2977},
        {"Wien, 1210",         48.2630, 16.3930},
        {"Graz",               47.0707, 15.4395},
        {"Graz-Liebenau",      46.9910, 15.4612},
        {"Linz",               48.3069, 14.2858},
        {"Wels",               48.1566, 14.0297},
        {"Salzburg",           47.8095, 13.0550},
        {"Hallein",            47.6833, 13.0933},
        {"Innsbruck",          47.2692, 11.4041},
        {"Hall in Tirol",      47.2813, 11.5077},
        {"Klagenfurt",         46.6228, 14.3050},
        {"Villach",            46.6167, 13.8500},
        {"St. Pölten",         48.2044, 15.6229},
        {"Krems",              48.4098, 15.5964},
        {"Eisenstadt",         47.8459, 16.5216},
        {"Bregenz",            47.5031, 9.7471},
        {"Dornbirn",           47.4128, 9.7417},
        {"Baden bei Wien",     48.0053, 16.2319},
        {"Mödling",            48.0833, 16.2833},
        {"Wiener Neustadt",    47.8073, 16.2504},
        {"Klosterneuburg",     48.3043, 16.3266},
        {"Schwechat",          48.1378, 16.4708},
    };

    // ── Cluster: beruf_spezialisierung IDs (BIS real IDs) ────────────────────
    // IKT: BF 303 Softwaretechnik, Programmierung
    private static final int[] IKT_BERUFE = {425, 636, 622, 1268, 581};
    // Kompetenzen: Informationstechnologie (parent=10015) and children
    private static final int[] IKT_KOMPS  = {
        20119, // Programmiersprachen-Kenntnisse
        20117, // Softwareentwicklungskenntnisse
        20129, // Datenbankkenntnisse
        20130, // Betriebssystemkenntnisse
        20127, // Datensicherheitskenntnisse
        20116, // Internetentwicklungs- und Administrationskenntnisse
        24633, // IT-Projektmanagement- und Consultingkenntnisse
        24640, // IT-Support
        20120, // Netzwerktechnik-Kenntnisse
        27382, // Artificial Intelligence
        24628, // SAP-Kenntnisse
        20111  // Betriebswirtschaftliche Anwendungssoftware-Kenntnisse
    };

    // Finanzen: BF 282 Bank-, Finanz- und Versicherungswesen
    private static final int[] FINANZEN_BERUFE = {1188, 245, 1241, 400, 533};
    private static final int[] FINANZEN_KOMPS  = {
        27066, // Bank- und Finanzwesen-Kenntnisse
        20103, // Betriebswirtschaftskenntnisse
        20105, // E-Commerce-Kenntnisse
        24637, // Einkaufskenntnisse
        20092  // Managementkenntnisse
    };

    // Gesundheit: BF 335 Gesundheits- und Krankenpflege, Hebammen
    private static final int[] GESUNDHEIT_BERUFE = {348, 345, 674, 354, 226};
    private static final int[] GESUNDHEIT_KOMPS  = {
        20155, // Gesundheits- und Krankenpflege
        20158, // Gesundheitsförderung
        20152, // Ergotherapiekenntnisse
        20159  // Medizinische Assistenzdienste
    };

    // Bildung: BF 360 Schule, Weiterbildung, Hochschule
    private static final int[] BILDUNG_BERUFE = {506, 513, 676, 508, 692};
    private static final int[] BILDUNG_KOMPS  = {
        20167, // Didaktikkenntnisse
        20170, // Pädagogikkenntnisse
        20169  // Kenntnisse in Sozialarbeit und -pädagogik
    };

    // Technik: BF 319 Maschinen- und Anlagenbau + BF 367
    private static final int[] TECHNIK_BERUFE = {1209, 446, 133, 722, 89};
    private static final int[] TECHNIK_KOMPS  = {
        20165, // Maschinenbaukenntnisse
        20166, // Metallbearbeitungskenntnisse
        20163, // Schweißkenntnisse
        20164  // Feinwerktechnik-Kenntnisse
    };

    // ── Cross-cluster soft-skill kompetenz IDs ───────────────────────────────
    private static final int KOMPETENZ_TEAMFAEHIGKEIT     = 20044; // Teamfähigkeit (parent=10006)
    private static final int KOMPETENZ_KOMMUNIKATION      = 20041; // Kommunikationsstärke (parent=10006)

    private static final String[][] VORNAMEN = {
        {"Max", "Anna", "Lukas", "Maria", "Tobias", "Lena", "Stefan", "Sandra",
         "Michael", "Julia", "Thomas", "Laura", "David", "Lisa", "Christian",
         "Eva", "Patrick", "Sabine", "Andreas", "Claudia"},
        {"Martin", "Petra", "Robert", "Monika", "Klaus", "Brigitte", "Franz", "Renate",
         "Johann", "Ursula", "Gerhard", "Ingrid", "Walter", "Helga", "Herbert",
         "Christine", "Wolfgang", "Elisabeth", "Manfred", "Silvia"}
    };

    private static final String[] NACHNAMEN = {
        "Müller", "Gruber", "Huber", "Wagner", "Bauer", "Pichler", "Maier", "Schneider",
        "Steiner", "Moser", "Leitner", "Fischer", "Hofer", "Winkler", "Schwarz",
        "Zimmermann", "Berger", "Kraus", "Brunner", "Wimmer", "Wolf", "Schmid",
        "Hofmann", "Mayer", "Lang", "Eder", "Lehner", "Schuster", "Riedl", "Reiter",
        "Danner", "Fuchs", "Kern", "Hahn", "Kaiser", "König", "Roth", "Frank",
        "Weber", "Koch"
    };

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Integer personCount = jdbc.queryForObject("SELECT COUNT(*) FROM person", Integer.class);
        if (personCount != null && personCount > 0) {
            log.debug("Dev-Daten bereits vorhanden, überspringe DevDataSeeder.");
            return;
        }

        log.info("Generiere Dev-Testdaten (200 Personen, 80 Stellen + 10 Lehrstellen)...");

        int[][] clusterBerufe = {IKT_BERUFE, FINANZEN_BERUFE, GESUNDHEIT_BERUFE, BILDUNG_BERUFE, TECHNIK_BERUFE};
        int[][] clusterKomps  = {IKT_KOMPS,  FINANZEN_KOMPS,  GESUNDHEIT_KOMPS,  BILDUNG_KOMPS,  TECHNIK_KOMPS};

        int personIdx = 0;
        int stadtIdx  = 0;

        // 5 clusters × 40 persons = 200
        for (int c = 0; c < 5; c++) {
            int[] berufe = clusterBerufe[c];
            int[] komps  = clusterKomps[c];

            for (int p = 0; p < 40; p++) {
                String[] flatVornamen = Stream.concat(
                        java.util.Arrays.stream(VORNAMEN[0]),
                        java.util.Arrays.stream(VORNAMEN[1])).toArray(String[]::new);
                String vorname  = flatVornamen[personIdx % flatVornamen.length];
                String nachname = NACHNAMEN[(personIdx / flatVornamen.length + personIdx) % NACHNAMEN.length];
                int berufSpezialisierungId = berufe[p % berufe.length];
                double umkreis = 15 + (p % 6) * 10;  // 15,25,35,45,55,65 km cycling

                Object[] stadt = STAEDTE[stadtIdx % STAEDTE.length];
                double lat = (double) stadt[1] + (personIdx % 5) * 0.005;
                double lon = (double) stadt[2] + (personIdx % 5) * 0.005;

                UUID personId = insertPerson(vorname, nachname, berufSpezialisierungId);

                // Wohnort
                insertPersonOrt(personId, "WOHNORT", "GENAU",
                        (String) stadt[0], lat, lon, umkreis);

                // Assign kompetenzen: showcase persons (first 5 per cluster) get all cluster komps
                int kompCount = (p < 5) ? komps.length : Math.max(2, komps.length - (p % 3) - 1);
                for (int k = 0; k < kompCount && k < komps.length; k++) {
                    String niveau = k == 0 ? "EXPERTE" : (k < 3 ? "FORTGESCHRITTEN" : "GRUNDKENNTNISSE");
                    insertPersonKompetenz(personId, komps[k], niveau);
                }
                // All persons get cross-cluster soft skills
                insertPersonKompetenz(personId, KOMPETENZ_TEAMFAEHIGKEIT, "FORTGESCHRITTEN");
                insertPersonKompetenz(personId, KOMPETENZ_KOMMUNIKATION,  "FORTGESCHRITTEN");

                personIdx++;
                stadtIdx++;
            }
        }

        log.info("Personen angelegt: {}", jdbc.queryForObject("SELECT COUNT(*) FROM person", Integer.class));

        // ── Stellen: 5 clusters × 16 = 80 STANDARD ───────────────────────────
        String[][] stellenTitel = {
            // IKT
            {"Senior Java Developer", "Frontend-Entwickler/in (Angular)", "DevOps Engineer",
             "Data Scientist", "IT-Projektleiter/in", "Fullstack-Entwickler/in (Spring/React)",
             "Cloud-Architekt/in", "Scrum Master", "UX Designer/in", "Mobile Developer (Android)",
             "Backend-Entwickler/in (Python)", "Cybersecurity Analyst", "BI Developer",
             "Tech Lead", "Platform Engineer", "Machine Learning Engineer"},
            // Finanzen
            {"Buchhalter/in", "Steuerberater/in", "Finanzanalyst/in", "Bilanzbuchhalter/in",
             "Controller/in", "Kreditanalyst/in", "Wirtschaftsprüfer/in (Junior)", "Lohnverrechner/in",
             "Treasury Manager/in", "Risk Analyst/in", "Compliance Officer/in", "CFO Assistent/in",
             "Payroll Specialist", "Fond Manager/in", "Audit Manager/in", "Investmentanalyst/in"},
            // Gesundheit
            {"Diplomkrankenpfleger/in", "Allgemeinmediziner/in", "Physiotherapeut/in",
             "MTA (medizinisch-technische/r Assistent/in)", "Pflegeassistent/in", "Radiologie-Assistent/in",
             "Hebamme", "Ergotherapeut/in", "Rettungssanitäter/in", "Labortechniker/in",
             "Stationsleitung Pflege", "Onkologie-Pflegefachkraft", "Intensivpflege",
             "Notarzt/Notärztin", "Psychiatrie-Pflege", "Palliativpflege-Koordinator/in"},
            // Bildung
            {"AHS-Lehrer/in (Mathematik)", "Volksschullehrer/in", "Kindergartenpädagog/in",
             "Berufsschullehrer/in", "Sonderpädagog/in", "Nachhilfelehrer/in", "Ausbildner/in Betrieb",
             "Sozialarbeiter/in", "Schulpsycholog/in", "E-Learning Entwickler/in",
             "Trainer/in Erwachsenenbildung", "Lernbegleiter/in", "Integrationslehrer/in",
             "Hochschullehrender/r", "Bildungsberater/in", "BFI Kursleiter/in"},
            // Technik
            {"Elektrotechniker/in", "Maschinenbautechniker/in", "Bauingenieur/in",
             "Mechatroniker/in", "Qualitätssicherungstechniker/in", "Anlagentechniker/in",
             "Automatisierungstechniker/in", "Schweißfachmann/-frau", "HKLS-Techniker/in",
             "Stahlbauer/in", "Kfz-Techniker/in", "CNC-Facharbeiter/in",
             "Produktionsleiter/in", "Facility Manager/in", "Instandhaltungstechniker/in",
             "Umwelttechniker/in"}
        };

        String[] unternehmen = {
            "TechCorp GmbH", "DataSoft AG", "InnoSystems GmbH", "IT-Lösungen Austria",
            "Raiffeisen Bank", "Erste Group", "UNIQA Insurance", "Wien Energie",
            "AKH Wien", "LKH Graz", "Sanatorium Hera", "ÖGK",
            "Wiener Volksschulen", "BFI Wien", "WAFF Wien", "Bildungszentrum Plus",
            "AVL List GmbH", "Anton Paar GmbH", "Palfinger AG", "Kapsch TrafficCom",
            "Frequentis AG", "Atomic Austria", "Blum GmbH", "Doka GmbH",
            "Knorr-Bremse GmbH", "Engel Austria GmbH", "Andritz AG", "Voestalpine AG"
        };

        stadtIdx = 0;
        for (int c = 0; c < 5; c++) {
            int[] berufe = clusterBerufe[c];
            int[] komps  = clusterKomps[c];
            String[] titel = stellenTitel[c];

            for (int s = 0; s < 16; s++) {
                Object[] stadt = STAEDTE[stadtIdx % STAEDTE.length];
                double lat = (double) stadt[1];
                double lon = (double) stadt[2];
                int berufSpezialisierungId = berufe[s % berufe.length];
                String unt = unternehmen[(c * 4 + s % 4) % unternehmen.length];

                UUID stelleId = insertStelle(titel[s], unt, (String) stadt[0], lat, lon,
                        berufSpezialisierungId, "STANDARD");

                // Pflicht-Kompetenzen: first 2 of cluster
                int pflichtCount = Math.min(2, komps.length);
                for (int k = 0; k < pflichtCount; k++) {
                    insertStelleKompetenz(stelleId, komps[k], true);
                }
                // Optional: next 1-3
                int optCount = Math.min(3, komps.length - pflichtCount);
                for (int k = pflichtCount; k < pflichtCount + optCount; k++) {
                    insertStelleKompetenz(stelleId, komps[k], false);
                }
                // Cross-skill: Teamfähigkeit optional
                insertStelleKompetenz(stelleId, KOMPETENZ_TEAMFAEHIGKEIT, false);

                stadtIdx++;
            }
        }

        log.info("Standard-Stellen angelegt: {}", jdbc.queryForObject("SELECT COUNT(*) FROM stelle", Integer.class));

        // ── 10 Lehrstellen ────────────────────────────────────────────────────
        // Lehrstellen use StelleTyp=LEHRSTELLE, have interests + prerequisites, no Kompetenzen.
        // Interests from bis_interessensgebiet, prerequisites from bis_voraussetzung.
        // beruf_spezialisierung IDs from apprenticeship-relevant berufsfelder.

        // Lehrstellen: {titel, unternehmen, ort, lat, lon, berufSpezialisierungId, interessen[], voraussetzungen[]}
        Object[][] lehrstellen = {
            // IKT Lehrstelle (BF 302 IT-Support/Schulung, stammberuf 581=AnwendungsbetreuerIn)
            {"Lehre IT-Techniker/in", "TechCorp GmbH", "Wien, 1010", 48.2082, 16.3738, 581,
             new int[]{129, 137},           // Computer IT EDV; Elektro
             new int[]{70, 48, 59, 88}},    // Begeisterung Computer; Gerne am Computer; Grundkenntnisse EDV; Logisches Denken

            // IKT Lehrstelle 2
            {"Lehre Softwareentwicklung", "DataSoft AG", "Graz", 47.0707, 15.4395, 622,
             new int[]{129},
             new int[]{70, 88, 60}},        // Computer; logisches Denken; Mathe

            // Technik Lehrstelle (BF 298 Elektromechanik, stammberuf 1126=ElektromechanikerIn)
            {"Lehre Elektromechaniker/in", "AVL List GmbH", "Graz", 47.0707, 15.4395, 1126,
             new int[]{137, 159},           // Elektro; Maschinen Werkstatt
             new int[]{71, 76, 55, 65}},    // Begeisterung Elektrotechnik; Maschinenbau; Maschinen; Handwerklich

            // Technik Lehrstelle 2 (BF 319, stammberuf 1209=AnlagentechnikerIn)
            {"Lehre Anlagentechniker/in", "Anton Paar GmbH", "Graz-Liebenau", 46.9910, 15.4612, 1209,
             new int[]{137, 159},
             new int[]{71, 80, 55, 64}},    // Elektrotechnik; Technik; Maschinen; räumliches Denken

            // Bau Lehrstelle (BF 272, stammberuf 856=BautechnikerIn)
            {"Lehre Bautechniker/in", "Doka GmbH", "Linz", 48.3069, 14.2858, 856,
             new int[]{160, 159},           // Bau Holz; Maschinen Werkstatt
             new int[]{67, 41, 65, 89}},    // Bau; Basteln; Handwerk; Schwindelfrei

            // Handel Lehrstelle (BF 308, stammberuf 55=DrogistIn)
            {"Lehre Einzelhandelskaufmann/-frau", "Raiffeisen Bank", "Wien, 1020", 48.2145, 16.3908, 55,
             new int[]{135, 131},           // Handel Verkauf; Büro
             new int[]{84, 44, 46, 90}},    // Begeisterung Verkauf; Kontakt Menschen; freundlich; serviceorientiert

            // Gastgewerbe Lehrstelle (BF 350, stammberuf 287=BarkeeperIn)
            {"Lehre Köchin/Koch", "Hotel Sacher Wien", "Wien, 1010", 48.2082, 16.3738, 287,
             new int[]{153, 154},           // Gastgewerbe; Lebensmittel
             new int[]{43, 95, 62, 46}},    // Freude Kochen; Sauberkeit; Geruchssinn; freundlich

            // Gesundheit Lehrstelle (BF 339, stammberuf 64=GewerblicheR MasseurIn)
            {"Lehre Masseur/in", "Sanatorium Hera", "Wien, 1140", 48.2003, 16.2977, 64,
             new int[]{149, 134},           // Gesundheit; Menschen Kinder Kommunikation
             new int[]{74, 58, 86, 44}},    // Gesundheit; Fingergeschicklichkeit; körperlich fit; Kontakt

            // Medien/Grafik Lehrstelle (BF 326, stammberuf 631=GrafikerIn)
            {"Lehre Grafik- und Kommunikationsdesign", "Frequentis AG", "Wien, 1210", 48.2630, 16.3930, 631,
             new int[]{127, 129},           // Kreatives Design; Computer IT
             new int[]{51, 75, 91, 92}},    // Kreativ; Grafik Design; Farben; Formen

            // Büro/Kaufmänn. Lehrstelle (BF 289, stammberuf 534=Bürokaufmann/-frau)
            {"Lehre Bürokaufmann/-frau", "Erste Group", "Wien, 1100", 48.1741, 16.3676, 534,
             new int[]{131, 135},           // Büro; Handel Verkauf
             new int[]{50, 48, 85, 44}},    // Büro; Computer; wirtschaftliche Themen; Kontakt
        };

        // Also seed 10 persons who suchen Lehrstellen with matching interests/prerequisites
        Object[][] lehrlingPersonen = {
            // {vorname, nachname, berufSpezialisierungId, stadt, lat, lon, interessen[], voraussetzungen[]}
            {"Leon",    "Bauer",     581, "Wien, 1010", 48.2090, 16.3745, new int[]{129, 137}, new int[]{70, 48, 59}},
            {"Sophie",  "Gruber",    622, "Graz",       47.0720, 15.4400, new int[]{129},      new int[]{70, 88}},
            {"Nico",    "Steiner",  1126, "Graz",       47.0730, 15.4410, new int[]{137, 159}, new int[]{71, 76}},
            {"Laura",   "Hofer",   1209, "Graz-Liebenau", 46.9920, 15.4620, new int[]{137, 159}, new int[]{71, 80}},
            {"Simon",   "Wagner",   856, "Linz",        48.3075, 14.2865, new int[]{160, 159}, new int[]{67, 65}},
            {"Emma",    "Müller",    55, "Wien, 1020",  48.2150, 16.3915, new int[]{135, 131}, new int[]{84, 44}},
            {"Paul",    "Fischer",  287, "Wien, 1010",  48.2088, 16.3742, new int[]{153, 154}, new int[]{43, 95}},
            {"Jana",    "Reiter",    64, "Wien, 1140",  48.2010, 16.2980, new int[]{149, 134}, new int[]{74, 58}},
            {"Ben",     "Wolf",     631, "Wien, 1210",  48.2635, 16.3935, new int[]{127, 129}, new int[]{51, 75}},
            {"Mia",     "Leitner",  534, "Wien, 1100",  48.1745, 16.3680, new int[]{131, 135}, new int[]{50, 48}},
        };

        for (Object[] lp : lehrlingPersonen) {
            String vorname  = (String) lp[0];
            String nachname = (String) lp[1];
            int berufId     = (int) lp[2];
            String ortName  = (String) lp[3];
            double lat      = (double) lp[4];
            double lon      = (double) lp[5];
            int[] interessen     = (int[]) lp[6];
            int[] voraussetzungen = (int[]) lp[7];

            UUID personId = insertPersonMitLehrstelle(vorname, nachname, berufId);
            insertPersonOrt(personId, "WOHNORT", "GENAU", ortName, lat, lon, 25.0);
            for (int ig : interessen) {
                insertPersonInteresse(personId, ig);
            }
            for (int vv : voraussetzungen) {
                insertPersonVoraussetzung(personId, vv);
            }
        }

        for (int i = 0; i < lehrstellen.length; i++) {
            Object[] ls    = lehrstellen[i];
            String titel   = (String) ls[0];
            String unt     = (String) ls[1];
            String ortBez  = (String) ls[2];
            double lat     = (double) ls[3];
            double lon     = (double) ls[4];
            int berufId    = (int) ls[5];
            int[] interessen      = (int[]) ls[6];
            int[] voraussetzungen = (int[]) ls[7];

            UUID stelleId = insertStelle(titel, unt, ortBez, lat, lon, berufId, "LEHRSTELLE");
            for (int ig : interessen) {
                insertStelleInteresse(stelleId, ig);
            }
            for (int vv : voraussetzungen) {
                insertStelleVoraussetzung(stelleId, vv);
            }
        }

        log.info("Stellen gesamt: {}", jdbc.queryForObject("SELECT COUNT(*) FROM stelle", Integer.class));
        log.info("Davon Lehrstellen: {}",
            jdbc.queryForObject("SELECT COUNT(*) FROM stelle WHERE typ = 'LEHRSTELLE'", Integer.class));
        log.info("Dev-Testdaten vollständig generiert.");
    }

    // ── Insert helpers ───────────────────────────────────────────────────────

    private UUID insertPerson(String vorname, String nachname, int berufSpezialisierungId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO person (id, vorname, nachname, email, beruf_spezialisierung_id, vermittlungspost, max_bewegungen, sucht_lehrstelle) " +
            "VALUES (?::uuid, ?, ?, ?, ?, TRUE, 999, FALSE)",
            id.toString(), vorname, nachname,
            vorname.toLowerCase() + "." + nachname.toLowerCase() + "@example.at",
            berufSpezialisierungId);
        return id;
    }

    private UUID insertPersonMitLehrstelle(String vorname, String nachname, int berufSpezialisierungId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO person (id, vorname, nachname, email, beruf_spezialisierung_id, vermittlungspost, max_bewegungen, sucht_lehrstelle) " +
            "VALUES (?::uuid, ?, ?, ?, ?, TRUE, 999, TRUE)",
            id.toString(), vorname, nachname,
            vorname.toLowerCase() + "." + nachname.toLowerCase() + ".lehrling@example.at",
            berufSpezialisierungId);
        return id;
    }

    private void insertPersonOrt(UUID personId, String rolle, String typ,
                                  String bezeichnung, double lat, double lon, double umkreis) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO person_ort (id, person_id, ort_rolle, ort_typ, bezeichnung, lat, lon, umkreis_km) " +
            "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)",
            id.toString(), personId.toString(), rolle, typ, bezeichnung, lat, lon, umkreis);
    }

    private void insertPersonKompetenz(UUID personId, int kompetenzId, String niveau) {
        jdbc.update(
            "INSERT INTO person_kompetenz (person_id, kompetenz_id, niveau) VALUES (?::uuid, ?, ?) ON CONFLICT DO NOTHING",
            personId.toString(), kompetenzId, niveau);
    }

    private void insertPersonInteresse(UUID personId, int interessensgebietId) {
        jdbc.update(
            "INSERT INTO person_interesse (person_id, interessensgebiet_id) VALUES (?::uuid, ?) ON CONFLICT DO NOTHING",
            personId.toString(), interessensgebietId);
    }

    private void insertPersonVoraussetzung(UUID personId, int voraussetzungId) {
        jdbc.update(
            "INSERT INTO person_voraussetzung (person_id, voraussetzung_id) VALUES (?::uuid, ?) ON CONFLICT DO NOTHING",
            personId.toString(), voraussetzungId);
    }

    private UUID insertStelle(String titel, String unternehmen, String ortBez,
                               double lat, double lon, int berufSpezialisierungId, String typ) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO stelle (id, titel, unternehmen, ort_bezeichnung, ort_lat, ort_lon, beruf_spezialisierung_id, typ) " +
            "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?)",
            id.toString(), titel, unternehmen, ortBez, lat, lon, berufSpezialisierungId, typ);
        return id;
    }

    private void insertStelleKompetenz(UUID stelleId, int kompetenzId, boolean pflicht) {
        jdbc.update(
            "INSERT INTO stelle_kompetenz (stelle_id, kompetenz_id, pflicht) VALUES (?::uuid, ?, ?) ON CONFLICT DO NOTHING",
            stelleId.toString(), kompetenzId, pflicht);
    }

    private void insertStelleInteresse(UUID stelleId, int interessensgebietId) {
        jdbc.update(
            "INSERT INTO stelle_interesse (stelle_id, interessensgebiet_id) VALUES (?::uuid, ?) ON CONFLICT DO NOTHING",
            stelleId.toString(), interessensgebietId);
    }

    private void insertStelleVoraussetzung(UUID stelleId, int voraussetzungId) {
        jdbc.update(
            "INSERT INTO stelle_voraussetzung (stelle_id, voraussetzung_id) VALUES (?::uuid, ?) ON CONFLICT DO NOTHING",
            stelleId.toString(), voraussetzungId);
    }
}
