package cz.msproject.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Uložený pohled: sada sloupců se šířkami, rychlý i rozšířený filtr, úroveň osnovy
 * a měřítko diagramu pod jedním názvem. Ukládá se do nastavení jako jeden řádek.
 */
record SavedView(String name, String columns, String filter, String extra, int level, String zoom) {

    private static final String PREF_KEY = "savedViews";
    private static final String SEP = "";

    String serialize() {
        return String.join(SEP, clean(name), clean(columns), clean(filter), clean(extra), String.valueOf(level), clean(zoom));
    }

    static SavedView deserialize(String line) {
        String[] p = line.split(SEP, -1);
        if (p.length < 6 || p[0].isBlank()) {
            return null;
        }
        int level;
        try {
            level = Integer.parseInt(p[4]);
        } catch (NumberFormatException ex) {
            level = 0;
        }
        return new SavedView(p[0], p[1], p[2], p[3], level, p[5]);
    }

    /** Všechny uložené pohledy v pořadí uložení. */
    static List<SavedView> load(Preferences prefs) {
        List<SavedView> out = new ArrayList<>();
        String v = prefs.get(PREF_KEY, "");
        if (v.isBlank()) {
            return out;
        }
        for (String line : v.split("\n")) {
            SavedView view = deserialize(line);
            if (view != null) {
                out.add(view);
            }
        }
        return out;
    }

    /** Uloží pohled (stejný název přepíše). Nejstarší se zahodí, kdyby se nastavení nevešlo. */
    static void save(Preferences prefs, SavedView view) {
        List<SavedView> list = new ArrayList<>(load(prefs));
        list.removeIf(v -> v.name().equalsIgnoreCase(view.name()));
        list.add(view);
        store(prefs, list);
    }

    static void remove(Preferences prefs, String name) {
        List<SavedView> list = new ArrayList<>(load(prefs));
        list.removeIf(v -> v.name().equalsIgnoreCase(name));
        store(prefs, list);
    }

    private static void store(Preferences prefs, List<SavedView> list) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            sb.setLength(0);
            for (SavedView v : list) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(v.serialize());
            }
            // Preferences neuloží hodnotu delší než 8192 znaků – raději zahodíme nejstarší pohled
            if (sb.length() <= Preferences.MAX_VALUE_LENGTH || list.isEmpty()) {
                break;
            }
            list.remove(0);
        }
        prefs.put(PREF_KEY, sb.toString());
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace(SEP, " ").replace('\n', ' ').replace('\r', ' ');
    }
}
