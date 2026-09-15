package cz.msproject.viewer;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

/** Vstupní bod aplikace. */
public final class App {
    private App() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory");
        System.setProperty("log4j2.simplelog.level", "OFF");
        Locale.setDefault(Fmt.CS);

        if (args.length > 0 && args[0].equals("--test")) {
            SelfTest.run(args);
            return;
        }
        setupLookAndFeel();
        if (args.length > 0 && args[0].equals("--ui-test")) {
            SelfTest.uiTest(args);
            return;
        }
        // až po testech: v testech má chyba shodit proces, ne zobrazit dialog
        ErrorLog.install();
        Path file = null;
        String badArg = null;
        if (args.length > 0 && !args[0].isBlank()) {
            try {
                file = Path.of(args[0]);
            } catch (java.nio.file.InvalidPathException ex) {
                badArg = args[0];
            }
        }
        final Path toOpen = file;
        final String invalid = badArg;
        SwingUtilities.invokeLater(() -> {
            MainWindow w = new MainWindow();
            w.setVisible(true);
            if (toOpen != null) {
                w.open(toOpen);
            } else if (invalid != null) {
                w.showError(Str.t("Soubor neexistuje nebo není přístupný:\n") + invalid, null);
            }
        });
    }

    static Preferences prefs() {
        return Preferences.userNodeForPackage(MainWindow.class);
    }

    /** Vzhled podle uložených voleb: FlatLaf světlý/tmavý (záloha: systémový vzhled), písmo podle měřítka. */
    static void setupLookAndFeel() {
        Preferences p = prefs();
        // -Dm3k.dark / -Dm3k.fontScale přebijí uložené volby (testy, zkoušení)
        boolean dark = System.getProperty("m3k.dark") != null ? Boolean.getBoolean("m3k.dark") : p.getBoolean("dark", false);
        float scale = p.getFloat("fontScale", 1f);
        try {
            if (System.getProperty("m3k.fontScale") != null) {
                scale = Float.parseFloat(System.getProperty("m3k.fontScale"));
            }
        } catch (NumberFormatException ignored) {
            scale = 1f;
        }
        Theme.init(dark, scale);
        boolean flat = false;
        try {
            if (dark) {
                flat = com.formdev.flatlaf.FlatDarkLaf.setup();
            } else {
                flat = com.formdev.flatlaf.FlatLightLaf.setup();
            }
        } catch (Throwable ignored) {
            flat = false;
        }
        if (!flat) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // výchozí vzhled
            }
        }
        FontUIResource font = new FontUIResource(Theme.BASE);
        if (flat) {
            UIManager.put("defaultFont", font);
        }
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (UIManager.get(key) instanceof FontUIResource) {
                UIManager.put(key, font);
            }
        }
        UIManager.put("ToolTip.font", font);
        UIManager.put("Table.rowHeight", Theme.ROW);
        UIManager.put("TabbedPane.showTabSeparators", Boolean.TRUE);
        UIManager.put("Component.arrowType", "chevron");
    }

    /** Uloží volby vzhledu a postaví všechna okna znovu (soubory zůstanou otevřené). */
    static void applyAppearance(boolean dark, float scale) {
        Preferences p = prefs();
        p.putBoolean("dark", dark);
        p.putFloat("fontScale", scale);
        rebuildWindows();
    }

    /**
     * Spustí novou instanci programu (zabalená aplikace: jpackage.app-path) se stejnými soubory
     * a tuto ukončí. Vrací false, když spustitelný soubor není známý (běh z vývojového prostředí).
     */
    static boolean restartApplication() {
        String exe = System.getProperty("jpackage.app-path");
        if (exe == null || exe.isBlank() || !java.nio.file.Files.isRegularFile(Path.of(exe))) {
            return false;
        }
        try {
            for (MainWindow w : new ArrayList<>(MainWindow.WINDOWS)) {
                List<String> cmd = new ArrayList<>();
                cmd.add(exe);
                if (w.currentPath != null) {
                    cmd.add(w.currentPath.toAbsolutePath().toString());
                }
                new ProcessBuilder(cmd).start();
                w.saveWindowState();
            }
        } catch (java.io.IOException ex) {
            return false;
        }
        System.exit(0);
        return true;
    }

    static void rebuildWindows() {
        List<MainWindow> old = new ArrayList<>(MainWindow.WINDOWS);
        List<Path> files = new ArrayList<>();
        List<Rectangle> bounds = new ArrayList<>();
        List<Boolean> maximized = new ArrayList<>();
        for (MainWindow w : old) {
            files.add(w.currentPath);
            bounds.add(w.getBounds());
            maximized.add((w.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) == java.awt.Frame.MAXIMIZED_BOTH);
        }
        MainWindow.restarting = true;
        try {
            for (MainWindow w : old) {
                w.saveWindowState();
                w.dispose();
            }
            MainWindow.WINDOWS.clear();
            setupLookAndFeel();
            for (int i = 0; i < files.size(); i++) {
                MainWindow n = new MainWindow();
                n.setBounds(bounds.get(i));
                if (maximized.get(i)) {
                    n.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
                }
                n.setVisible(true);
                if (files.get(i) != null) {
                    n.open(files.get(i));
                }
            }
            if (files.isEmpty()) {
                new MainWindow().setVisible(true);
            }
        } finally {
            MainWindow.restarting = false;
        }
    }
}
