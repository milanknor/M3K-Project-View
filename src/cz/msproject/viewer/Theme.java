package cz.msproject.viewer;

import java.awt.Color;
import java.awt.Font;

/** Barvy a rozměry sdílené tabulkou a diagramem. */
public final class Theme {
    public static final int ROW = 26;
    public static final int HEADER = 44;

    public static final Color INK = new Color(30, 41, 59);
    public static final Color MUTED = new Color(100, 116, 139);
    public static final Color GRID = new Color(226, 232, 240);
    public static final Color GRID_STRONG = new Color(203, 213, 225);
    public static final Color STRIPE = new Color(248, 250, 252);
    public static final Color SELECTION = new Color(219, 234, 254);
    public static final Color HEADER_BG = new Color(241, 245, 249);
    public static final Color WEEKEND = new Color(243, 244, 246);
    public static final Color TODAY = new Color(220, 38, 38);
    public static final Color STATUS_DATE = new Color(37, 99, 235);

    public static final Color TASK = new Color(59, 130, 246);
    public static final Color TASK_LIGHT = new Color(191, 219, 254);
    public static final Color CRITICAL = new Color(220, 38, 38);
    public static final Color CRITICAL_LIGHT = new Color(254, 202, 202);
    public static final Color SUMMARY = new Color(30, 41, 59);
    public static final Color MILESTONE = new Color(15, 23, 42);
    public static final Color BASELINE = new Color(148, 163, 184);
    public static final Color DEADLINE = new Color(22, 163, 74);
    public static final Color LINK = new Color(71, 85, 105);
    public static final Color INACTIVE = new Color(156, 163, 175);

    public static final Font BASE = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font BOLD = BASE.deriveFont(Font.BOLD);
    public static final Font SMALL = BASE.deriveFont(11.5f);
    public static final Font TITLE = BASE.deriveFont(Font.BOLD, 20f);

    private Theme() {
    }
}
