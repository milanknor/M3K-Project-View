package cz.msproject.viewer;

import org.mpxj.MPXJException;
import org.mpxj.ProjectFile;
import org.mpxj.mpp.MPPReader;
import org.mpxj.reader.UniversalProjectReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Načítání projektových souborů přes MPXJ. */
public final class ProjectLoader {

    /** Soubor je chráněn heslem pro čtení a heslo chybí nebo je špatné. */
    public static final class PasswordRequiredException extends Exception {
        private static final long serialVersionUID = 1L;

        PasswordRequiredException() {
            super("Soubor je chráněn heslem.");
        }
    }

    public static final String[] EXTENSIONS = {"mpp", "mpt", "mpx", "xml", "mpd", "mdb"};

    private ProjectLoader() {
    }

    public static boolean isMpp(Path path) {
        String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".mpp") || n.endsWith(".mpt");
    }

    public static ProjectFile load(Path path, String password) throws Exception {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Soubor neexistuje: " + path);
        }
        if (isMpp(path)) {
            MPPReader reader = new MPPReader();
            reader.setReadPresentationData(false);
            if (password != null) {
                reader.setReadPassword(password);
            }
            try {
                return reader.read(path.toFile());
            } catch (MPXJException e) {
                if (isPasswordProblem(e)) {
                    throw new PasswordRequiredException();
                }
                throw e;
            }
        }
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mpx")) {
            return loadMpx(path);
        }
        ProjectFile file = new UniversalProjectReader().read(path.toFile());
        if (file == null) {
            throw new MPXJException("Formát souboru nebyl rozpoznán. Podporovány jsou soubory MPP, MPT, MPX, XML (MSPDI) a MPD.");
        }
        return file;
    }

    /** MPX je lokalizovaný textový formát – zkusí se angličtina a pak další jazyky, které MPXJ zná. */
    private static ProjectFile loadMpx(Path path) throws Exception {
        Exception first = null;
        for (String tag : new String[]{"en", "de", "fr", "it", "pt", "sv", "zh", "es", "ru"}) {
            Locale locale = Locale.forLanguageTag(tag);
            org.mpxj.mpx.MPXReader reader = new org.mpxj.mpx.MPXReader();
            reader.setLocale(locale);
            try {
                return reader.read(path.toFile());
            } catch (MPXJException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        throw first;
    }

    private static boolean isPasswordProblem(Throwable e) {
        Throwable t = e;
        while (t != null) {
            String m = t.getMessage();
            if (m != null && m.toLowerCase(Locale.ROOT).contains("password")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    public static Throwable rootCause(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }
}
