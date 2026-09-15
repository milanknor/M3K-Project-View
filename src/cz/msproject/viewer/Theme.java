package cz.msproject.viewer;

import java.awt.Color;
import java.awt.Font;

/**
 * Barvy, písma a rozměry sdílené celou aplikací. Nastavují se jednou při startu
 * ({@link #init}) podle uložených voleb (světlý/tmavý režim, velikost písma);
 * po změně se okna postaví znovu (viz App.rebuildWindows).
 */
public final class Theme {
    public static boolean DARK;
    public static float SCALE = 1f;

    public static int ROW = 26;
    public static int HEADER = 44;

    public static Color BG;
    public static Color INK;
    public static Color MUTED;
    public static Color GRID;
    public static Color GRID_STRONG;
    public static Color STRIPE;
    public static Color STRIPE_ALPHA;
    public static Color SELECTION;
    public static Color HEADER_BG;
    public static Color WEEKEND;
    public static Color TODAY;
    public static Color STATUS_DATE;
    public static Color ACCENT;

    public static Color TASK;
    public static Color TASK_LIGHT;
    public static Color CRITICAL;
    public static Color CRITICAL_LIGHT;
    public static Color SUMMARY;
    public static Color MILESTONE;
    public static Color BASELINE;
    public static Color DEADLINE;
    public static Color LINK;
    public static Color INACTIVE;
    /** Zvýraznění vazeb vybraného úkolu (oranžová značky M3K). */
    public static final Color HIGHLIGHT = new Color(0xFF, 0x79, 0x0A);
    public static Color CONSTRAINT_FLEXIBLE;
    public static Color CONSTRAINT_FIXED;
    public static Color OVERALLOCATED;
    public static Color OVERALLOCATED_INK;
    public static Color WORK_LIGHT;

    /** Hex barvy a styly pro HTML popisky (JEditorPane, JLabel). */
    public static String INK_HEX;
    public static String MUTED_HEX;
    public static String ACCENT_HEX;
    public static String HTML_BODY;
    public static String HTML_MUTED;

    public static Font BASE;
    public static Font BOLD;
    public static Font SMALL;
    public static Font TITLE;

    static {
        init(false, 1f);
    }

    private Theme() {
    }

    public static void init(boolean dark, float scale) {
        DARK = dark;
        SCALE = Math.max(0.7f, Math.min(2f, scale));
        ROW = Math.round(26 * SCALE);
        HEADER = Math.round(44 * SCALE);
        BASE = new Font("Segoe UI", Font.PLAIN, 13).deriveFont(13f * SCALE);
        BOLD = BASE.deriveFont(Font.BOLD);
        SMALL = BASE.deriveFont(11.5f * SCALE);
        TITLE = BASE.deriveFont(Font.BOLD, 20f * SCALE);
        if (dark) {
            BG = new Color(0x2b, 0x2d, 0x30);
            INK = new Color(0xe6, 0xe6, 0xe6);
            MUTED = new Color(0x9a, 0xa0, 0xa6);
            GRID = new Color(0x3f, 0x43, 0x47);
            GRID_STRONG = new Color(0x55, 0x59, 0x5e);
            STRIPE = new Color(0x30, 0x32, 0x36);
            STRIPE_ALPHA = new Color(255, 255, 255, 12);
            SELECTION = new Color(0x2f, 0x4a, 0x6e);
            HEADER_BG = new Color(0x35, 0x37, 0x3b);
            WEEKEND = new Color(0x34, 0x36, 0x3a);
            TODAY = new Color(0xf8, 0x71, 0x71);
            STATUS_DATE = new Color(0x8a, 0xb4, 0xf8);
            ACCENT = new Color(0x8a, 0xb4, 0xf8);
            TASK = new Color(0x5b, 0x9c, 0xf6);
            TASK_LIGHT = new Color(0x3a, 0x53, 0x78);
            CRITICAL = new Color(0xf0, 0x52, 0x52);
            CRITICAL_LIGHT = new Color(0x6b, 0x2f, 0x2f);
            SUMMARY = new Color(0xd0, 0xd4, 0xda);
            MILESTONE = new Color(0xe6, 0xe6, 0xe6);
            BASELINE = new Color(0x7b, 0x83, 0x90);
            DEADLINE = new Color(0x4a, 0xde, 0x80);
            LINK = new Color(0xa0, 0xa8, 0xb3);
            INACTIVE = new Color(0x6b, 0x72, 0x80);
            CONSTRAINT_FLEXIBLE = new Color(0x8a, 0xb4, 0xf8);
            CONSTRAINT_FIXED = new Color(0xf4, 0x72, 0xb6);
            OVERALLOCATED = new Color(0x5a, 0x26, 0x26);
            OVERALLOCATED_INK = new Color(0xff, 0xb4, 0xb4);
            WORK_LIGHT = new Color(0x4a, 0x3a, 0x25);
        } else {
            BG = Color.WHITE;
            INK = new Color(30, 41, 59);
            MUTED = new Color(100, 116, 139);
            GRID = new Color(226, 232, 240);
            GRID_STRONG = new Color(203, 213, 225);
            STRIPE = new Color(248, 250, 252);
            STRIPE_ALPHA = new Color(248, 250, 252, 160);
            SELECTION = new Color(219, 234, 254);
            HEADER_BG = new Color(241, 245, 249);
            WEEKEND = new Color(243, 244, 246);
            TODAY = new Color(220, 38, 38);
            STATUS_DATE = new Color(37, 99, 235);
            ACCENT = new Color(31, 78, 140);
            TASK = new Color(59, 130, 246);
            TASK_LIGHT = new Color(191, 219, 254);
            CRITICAL = new Color(220, 38, 38);
            CRITICAL_LIGHT = new Color(254, 202, 202);
            SUMMARY = new Color(30, 41, 59);
            MILESTONE = new Color(15, 23, 42);
            BASELINE = new Color(148, 163, 184);
            DEADLINE = new Color(22, 163, 74);
            LINK = new Color(71, 85, 105);
            INACTIVE = new Color(156, 163, 175);
            CONSTRAINT_FLEXIBLE = new Color(37, 99, 235);
            CONSTRAINT_FIXED = new Color(190, 24, 93);
            OVERALLOCATED = new Color(254, 202, 202);
            OVERALLOCATED_INK = new Color(153, 27, 27);
            WORK_LIGHT = new Color(255, 237, 213);
        }
        INK_HEX = hex(INK);
        MUTED_HEX = hex(MUTED);
        ACCENT_HEX = hex(ACCENT);
        int pt = Math.round(10 * SCALE);
        HTML_BODY = "font-family:Segoe UI;font-size:" + pt + "pt;color:" + INK_HEX;
        HTML_MUTED = "font-family:Segoe UI;font-size:" + pt + "pt;color:" + MUTED_HEX;
    }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
