package cz.msproject.viewer;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Dialogy pro otevření/uložení souboru: nativní dialog Windows (FlatLaf SystemFileChooser,
 * tj. moderní dialog Průzkumníka s navigačním panelem) a jako záloha Swingový JFileChooser.
 */
final class FileDialogs {

    record Filter(String description, String... extensions) {
    }

    private FileDialogs() {
    }

    /** Otevření souboru; první filtr je výchozí. Vrací null při zrušení. */
    static File open(Component parent, String title, String dir, List<Filter> filters) {
        try {
            com.formdev.flatlaf.util.SystemFileChooser fc = new com.formdev.flatlaf.util.SystemFileChooser();
            fc.setDialogTitle(title);
            fc.setCurrentDirectory(directory(dir));
            fc.setAcceptAllFileFilterUsed(true);
            com.formdev.flatlaf.util.SystemFileChooser.FileFilter first = null;
            for (Filter f : filters) {
                com.formdev.flatlaf.util.SystemFileChooser.FileNameExtensionFilter ff =
                        new com.formdev.flatlaf.util.SystemFileChooser.FileNameExtensionFilter(f.description(), f.extensions());
                fc.addChoosableFileFilter(ff);
                if (first == null) {
                    first = ff;
                }
            }
            if (first != null) {
                fc.setFileFilter(first);
            }
            if (fc.showOpenDialog(parent) != com.formdev.flatlaf.util.SystemFileChooser.APPROVE_OPTION) {
                return null;
            }
            return fc.getSelectedFile();
        } catch (Throwable nativeUnavailable) {
            JFileChooser fc = new JFileChooser(directory(dir));
            fc.setDialogTitle(title);
            fc.setAcceptAllFileFilterUsed(true);
            FileNameExtensionFilter first = null;
            for (Filter f : filters) {
                FileNameExtensionFilter ff = new FileNameExtensionFilter(f.description(), f.extensions());
                fc.addChoosableFileFilter(ff);
                if (first == null) {
                    first = ff;
                }
            }
            if (first != null) {
                fc.setFileFilter(first);
            }
            return fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
        }
    }

    /**
     * Uložení souboru s jedním typem; doplní příponu a (u záložního dialogu) se zeptá na přepsání –
     * nativní dialog se ptá sám. Vrací null při zrušení.
     */
    static File save(Component parent, String title, String dir, String suggestedName, Filter filter) {
        String ext = filter.extensions()[0];
        File chosen;
        boolean askOverwrite;
        try {
            com.formdev.flatlaf.util.SystemFileChooser fc = new com.formdev.flatlaf.util.SystemFileChooser();
            fc.setDialogTitle(title);
            fc.setCurrentDirectory(directory(dir));
            fc.setSelectedFile(new File(directory(dir), suggestedName));
            fc.setAcceptAllFileFilterUsed(false);
            fc.setFileFilter(new com.formdev.flatlaf.util.SystemFileChooser.FileNameExtensionFilter(filter.description(), filter.extensions()));
            fc.putPlatformProperty(com.formdev.flatlaf.util.SystemFileChooser.WINDOWS_DEFAULT_EXTENSION, ext);
            if (fc.showSaveDialog(parent) != com.formdev.flatlaf.util.SystemFileChooser.APPROVE_OPTION) {
                return null;
            }
            chosen = fc.getSelectedFile();
            askOverwrite = false;
        } catch (Throwable nativeUnavailable) {
            JFileChooser fc = new JFileChooser(directory(dir));
            fc.setDialogTitle(title);
            fc.setFileFilter(new FileNameExtensionFilter(filter.description(), filter.extensions()));
            fc.setSelectedFile(new File(suggestedName));
            if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return null;
            }
            chosen = fc.getSelectedFile();
            askOverwrite = true;
        }
        if (chosen == null) {
            return null;
        }
        if (!chosen.getName().toLowerCase(Locale.ROOT).endsWith("." + ext)) {
            chosen = new File(chosen.getPath() + "." + ext);
            askOverwrite = true; // přípona přidána až teď – nativní dialog přepsání neověřil
        }
        if (askOverwrite && chosen.exists() && JOptionPane.showConfirmDialog(parent,
                Str.t("Soubor ") + chosen.getName() + Str.t(" už existuje. Přepsat?"), title, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return null;
        }
        return chosen;
    }

    private static File directory(String dir) {
        File d = dir == null || dir.isBlank() ? null : new File(dir);
        if (d == null || !d.isDirectory()) {
            d = new File(System.getProperty("user.home"));
        }
        return d;
    }
}
