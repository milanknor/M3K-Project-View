package cz.msproject.viewer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Obarvení pruhů v Ganttově diagramu podle hodnoty zvoleného sloupce – typicky
 * zdroje (kdo úkol dělá), střediska nebo vlastního pole ze souboru. Nejčastější
 * hodnoty dostanou barvu z palety, méně časté jednu společnou; úkoly s prázdnou
 * hodnotou i souhrnné úkoly zůstávají ve výchozích barvách.
 */
public final class BarColors {

    /** Paleta odlišitelná ve světlém i tmavém režimu; začíná firemní oranžovou. */
    private static final Color[] PALETTE = {
            new Color(0xFF790A), new Color(0x2E7DD7), new Color(0x2FA34F), new Color(0x8E5BD0),
            new Color(0xD2487F), new Color(0x0E9A9A), new Color(0xB07B12), new Color(0x5C7896),
            new Color(0xC1533C), new Color(0x4F7A28), new Color(0x7A4FBF), new Color(0x1F6F8B),
    };
    private static final Color OTHERS = new Color(0x8A8A96);

    private final Columns.Column column;
    private final Columns columns;
    private final Map<String, Color> colors = new LinkedHashMap<>();
    private boolean others;

    private BarColors(Columns.Column column, Columns columns) {
        this.column = column;
        this.columns = columns;
    }

    /** Sestaví obarvení z hodnot v zadaných řádcích (nejčastější hodnota dostane první barvu). */
    public static BarColors of(Columns.Column column, Columns columns, List<TaskTree.Row> rows) {
        BarColors b = new BarColors(column, columns);
        Map<String, Integer> counts = new HashMap<>();
        for (TaskTree.Row r : rows) {
            if (r.isSummary()) {
                continue;
            }
            String v = b.value(r);
            if (!v.isEmpty()) {
                counts.merge(v, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        for (int i = 0; i < sorted.size(); i++) {
            if (i < PALETTE.length) {
                b.colors.put(sorted.get(i).getKey(), PALETTE[i]);
            } else {
                b.others = true;
                break;
            }
        }
        return b;
    }

    /**
     * Hodnota, podle které se barví. U více přiřazených zdrojů rozhoduje první
     * z nich, aby úkol dostal jednu barvu a legenda nenarostla do nesmyslů.
     */
    String value(TaskTree.Row row) {
        String s = columns.text(column, row);
        if (s == null) {
            return "";
        }
        int cut = s.length();
        for (char c : new char[]{',', ';'}) {
            int i = s.indexOf(c);
            if (i >= 0) {
                cut = Math.min(cut, i);
            }
        }
        String v = s.substring(0, cut).trim();
        // u zdrojů odřízne jednotky v hranatých závorkách, ať má „Zedník [50 %]“
        // a „Zedník [100 %]“ jednu barvu
        int bracket = v.indexOf('[');
        return bracket > 0 ? v.substring(0, bracket).trim() : v;
    }

    /** Barva pruhu, nebo null když se má použít výchozí (souhrn, prázdná hodnota). */
    public Color color(TaskTree.Row row) {
        if (row.isSummary()) {
            return null;
        }
        String v = value(row);
        if (v.isEmpty()) {
            return null;
        }
        Color c = colors.get(v);
        return c != null ? c : OTHERS;
    }

    /** Dvojice hodnota–barva pro legendu, v pořadí od nejčastější hodnoty. */
    public List<Map.Entry<String, Color>> legend() {
        List<Map.Entry<String, Color>> out = new ArrayList<>(colors.entrySet());
        if (others) {
            out.add(Map.entry(Str.t("ostatní"), OTHERS));
        }
        return out;
    }

    public Columns.Column column() {
        return column;
    }

    public boolean isEmpty() {
        return colors.isEmpty();
    }

    /** Světlejší odstín pro nedokončenou část pruhu. */
    public static Color lighter(Color c) {
        return Theme.DARK
                ? new Color(c.getRed(), c.getGreen(), c.getBlue(), 90)
                : new Color(mix(c.getRed()), mix(c.getGreen()), mix(c.getBlue()));
    }

    private static int mix(int v) {
        return Math.min(255, (int) Math.round(v + (255 - v) * 0.68));
    }
}
