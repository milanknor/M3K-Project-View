package cz.msproject.viewer;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;

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
        Path file = args.length > 0 ? Path.of(args[0]) : null;
        SwingUtilities.invokeLater(() -> {
            MainWindow w = new MainWindow();
            w.setVisible(true);
            if (file != null) {
                w.open(file);
            }
        });
    }

    static void setupLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // výchozí vzhled
        }
        FontUIResource font = new FontUIResource(Theme.BASE);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (UIManager.get(key) instanceof FontUIResource) {
                UIManager.put(key, font);
            }
        }
        UIManager.put("ToolTip.font", font);
    }
}
