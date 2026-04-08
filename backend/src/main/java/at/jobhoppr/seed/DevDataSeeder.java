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
 * 5 Berufscluster × ~40 Persons + ~16 Stellen = 200 Persons / 80 Stellen.
 * At least 5 showcase persons have strong (>85%) matches.
 *
 * Cluster mapping (beruf_id → kompetenz_ids):
 *   IKT:      beruf 1-5,  kompetenzen 1-12
 *   Finanzen: beruf 6-10, kompetenzen 13-17
 *   Gesundheit: beruf 11-15, kompetenzen 18-21
 *   Bildung:  beruf 16-20, kompetenzen 22-24
 *   Technik:  beruf 21-25, kompetenzen 25-28
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

    // Cluster definitions: {clusterName, berufIds[], kompetenzIds[]}
    private static final int[] IKT_BERUFE         = {1, 2, 3, 4, 5};
    private static final int[] IKT_KOMPS          = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

    private static final int[] FINANZEN_BERUFE    = {6, 7, 8, 9, 10};
    private static final int[] FINANZEN_KOMPS     = {13, 14, 15, 16, 17};

    private static final int[] GESUNDHEIT_BERUFE  = {11, 12, 13, 14, 15};
    private static final int[] GESUNDHEIT_KOMPS   = {18, 19, 20, 21};

    private static final int[] BILDUNG_BERUFE     = {16, 17, 18, 19, 20};
    private static final int[] BILDUNG_KOMPS      = {22, 23, 24};

    private static final int[] TECHNIK_BERUFE     = {21, 22, 23, 24, 25};
    private static final int[] TECHNIK_KOMPS      = {25, 26, 27, 28};

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

        log.info("Generiere Dev-Testdaten (200 Personen, 80 Stellen)...");

        int[][] clusterBerufe = {IKT_BERUFE, FINANZEN_BERUFE, GESUNDHEIT_BERUFE, BILDUNG_BERUFE, TECHNIK_BERUFE};
        int[][] clusterKomps  = {IKT_KOMPS,  FINANZEN_KOMPS,  GESUNDHEIT_KOMPS,  BILDUNG_KOMPS,  TECHNIK_KOMPS};

        int personIdx = 0;
        int stadtIdx  = 0;

        // 5 clusters × 40 persons = 200
        for (int c = 0; c < 5; c++) {
            int[] berufe = clusterBerufe[c];
            int[] komps  = clusterKomps[c];

            for (int p = 0; p < 40; p++) {
                // Use personIdx (0..199) to pick unique name combinations
                String[] flatVornamen = Stream.concat(
                        java.util.Arrays.stream(VORNAMEN[0]),
                        java.util.Arrays.stream(VORNAMEN[1])).toArray(String[]::new);
                String vorname  = flatVornamen[personIdx % flatVornamen.length];
                String nachname = NACHNAMEN[(personIdx / flatVornamen.length + personIdx) % NACHNAMEN.length];
                int berufId     = berufe[p % berufe.length];
                double umkreis  = 15 + (p % 6) * 10;  // 15,25,35,45,55,65 km cycling

                Object[] stadt = STAEDTE[stadtIdx % STAEDTE.length];
                double lat = (double) stadt[1] + (personIdx % 5) * 0.005;
                double lon = (double) stadt[2] + (personIdx % 5) * 0.005;

                UUID personId = insertPerson(vorname, nachname, berufId);

                // Wohnort
                insertPersonOrt(personId, "WOHNORT", "GENAU",
                        (String) stadt[0], lat, lon, umkreis);

                // Assign kompetenzen: showcase persons (first 5 per cluster) get all cluster komps
                // others get a subset
                int kompCount = (p < 5) ? komps.length : Math.max(2, komps.length - (p % 3) - 1);
                for (int k = 0; k < kompCount && k < komps.length; k++) {
                    String niveau = k == 0 ? "EXPERTE" : (k < 3 ? "FORTGESCHRITTEN" : "GRUNDKENNTNISSE");
                    insertPersonKompetenz(personId, komps[k], niveau);
                }
                // All persons get cross-cluster soft skills
                insertPersonKompetenz(personId, 30, "FORTGESCHRITTEN"); // Teamarbeit
                insertPersonKompetenz(personId, 31, "FORTGESCHRITTEN"); // Kommunikation

                personIdx++;
                stadtIdx++;
            }
        }

        log.info("Personen angelegt: {}", jdbc.queryForObject("SELECT COUNT(*) FROM person", Integer.class));

        // ── Stellen: 5 clusters × 16 = 80 ────────────────────────────────────
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
                int berufId = berufe[s % berufe.length];
                String unt = unternehmen[(c * 4 + s % 4) % unternehmen.length];

                UUID stelleId = insertStelle(titel[s], unt, (String) stadt[0], lat, lon, berufId);

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
                // Cross-skill: Teamarbeit optional
                insertStelleKompetenz(stelleId, 30, false);

                stadtIdx++;
            }
        }

        log.info("Stellen angelegt: {}", jdbc.queryForObject("SELECT COUNT(*) FROM stelle", Integer.class));
        log.info("Dev-Testdaten vollständig generiert.");
    }

    private UUID insertPerson(String vorname, String nachname, int berufId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO person (id, vorname, nachname, email, beruf_id) VALUES (?::uuid, ?, ?, ?, ?)",
            id.toString(), vorname, nachname,
            vorname.toLowerCase() + "." + nachname.toLowerCase() + "@example.at",
            berufId);
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

    private UUID insertStelle(String titel, String unternehmen, String ortBez,
                               double lat, double lon, int berufId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO stelle (id, titel, unternehmen, ort_bezeichnung, ort_lat, ort_lon, beruf_id) " +
            "VALUES (?::uuid, ?, ?, ?, ?, ?, ?)",
            id.toString(), titel, unternehmen, ortBez, lat, lon, berufId);
        return id;
    }

    private void insertStelleKompetenz(UUID stelleId, int kompetenzId, boolean pflicht) {
        jdbc.update(
            "INSERT INTO stelle_kompetenz (stelle_id, kompetenz_id, pflicht) VALUES (?::uuid, ?, ?) ON CONFLICT DO NOTHING",
            stelleId.toString(), kompetenzId, pflicht);
    }
}
