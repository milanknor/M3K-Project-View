package cz.msproject.viewer;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Zachytávání neošetřených výjimek (vlákno událostí i ostatní vlákna): zápis do
 * souboru {@code %LOCALAPPDATA%\M3K Project View\error.log} a jedno upozornění
 * uživateli (další chyby stejného typu v rychlém sledu se jen logují, aby nevznikla
 * lavina dialogů). Program po chybě běží dál.
 */
public final class ErrorLog implements Thread.UncaughtExceptionHandler {
    private static final long MAX_LOG_BYTES = 512 * 1024;
    private static final long DIALOG_INTERVAL_MS = 5000;
    private static volatile long lastDialog;
    private static volatile String lastKey = "";

    private ErrorLog() {
    }

    public static void install() {
        ErrorLog handler = new ErrorLog();
        Thread.setDefaultUncaughtExceptionHandler(handler);
        System.setProperty("sun.awt.exception.handler", ErrorLog.class.getName());
    }

    /** Cesta k logu; null, když ji nelze vytvořit. */
    public static Path logFile() {
        try {
            String base = System.getenv("LOCALAPPDATA");
            Path dir = base == null || base.isBlank() ? Path.of(System.getProperty("user.home"), ".m3kprojectview") : Path.of(base, "M3K Project View");
            Files.createDirectories(dir);
            return dir.resolve("error.log");
        } catch (RuntimeException | IOException ex) {
            return null;
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        handle(t == null ? "?" : t.getName(), e);
    }

    /** Pro sun.awt.exception.handler (starší mechanismus, volá se reflexí bez instance). */
    public void handle(Throwable e) {
        handle("AWT-EventQueue", e);
    }

    public static void handle(String where, Throwable e) {
        if (e == null) {
            return;
        }
        String text = describe(where, e);
        write(text);
        System.err.print(text);
        boolean oom = isOutOfMemory(e);
        String key = e.getClass().getName();
        long now = System.currentTimeMillis();
        if (!oom && key.equals(lastKey) && now - lastDialog < DIALOG_INTERVAL_MS) {
            return;
        }
        lastKey = key;
        lastDialog = now;
        Runnable show = () -> {
            try {
                Path log = logFile();
                String msg = oom
                        ? Str.t("Došla paměť. Zavřete jiná okna nebo otevřete menší soubor; pro velmi velké projekty pomůže sbalit osnovu nebo zmenšit měřítko.")
                        : Str.t("V programu nastala neočekávaná chyba. Program běží dál, ale zkontrolujte poslední akci.");
                Throwable root = ProjectLoader.rootCause(e);
                JOptionPane.showMessageDialog(null, msg + "\n\n" + root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + shorten(root.getMessage()))
                        + (log == null ? "" : "\n" + Str.t("Podrobnosti: ") + log), MainWindow.APP_NAME, JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ignored) {
                // dialog se nepodařilo zobrazit – chyba je v logu
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            SwingUtilities.invokeLater(show);
        }
    }

    public static boolean isOutOfMemory(Throwable e) {
        Throwable t = e;
        int guard = 0;
        while (t != null && guard++ < 20) {
            if (t instanceof OutOfMemoryError) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    static String describe(String where, Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()) + "  " + MainWindow.APP_NAME + " " + MainWindow.VERSION
                + "  Java " + System.getProperty("java.version") + "  vlákno: " + where);
        e.printStackTrace(pw);
        pw.println();
        pw.flush();
        return sw.toString();
    }

    /** Zápis do logu s rotací (po překročení limitu se starý log přejmenuje na error.log.old). */
    static synchronized void write(String text) {
        Path log = logFile();
        if (log == null) {
            return;
        }
        try {
            if (Files.exists(log) && Files.size(log) > MAX_LOG_BYTES) {
                Files.move(log, log.resolveSibling("error.log.old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(log, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            // log nelze zapsat – nic dalšího nedělat
        }
    }

    private static String shorten(String s) {
        String v = s.replace('\r', ' ').replace('\n', ' ');
        return v.length() > 300 ? v.substring(0, 297) + "…" : v;
    }
}
