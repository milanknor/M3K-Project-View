package cz.msproject.viewer;

import org.mpxj.MPXJException;
import org.mpxj.ProjectFile;
import org.mpxj.mpd.MPDFileReader;
import org.mpxj.mpp.MPPReader;
import org.mpxj.mspdi.MSPDIReader;
import org.mpxj.mpx.MPXReader;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Načítání projektových souborů přes MPXJ – záměrně jen konkrétními čtečkami podle
 * přípony a po kontrole signatury souboru (žádné hádání formátu, žádné další parsery
 * ani nativní knihovny). XML parser běží s pevnými limity (bez DOCTYPE, bez externích
 * entit, omezená expanze), viz statická inicializace.
 */
public final class ProjectLoader {

    /** Soubor je chráněn heslem pro čtení a heslo chybí nebo je špatné. */
    public static final class PasswordRequiredException extends Exception {
        private static final long serialVersionUID = 1L;

        PasswordRequiredException() {
            super("Soubor je chráněn heslem.");
        }
    }

    public static final String[] EXTENSIONS = {"mpp", "mpt", "mpx", "xml", "mpd", "mdb"};
    /** Větší soubory se odmítnou (skutečné projekty mají jednotky až desítky MB). */
    public static final long MAX_FILE_BYTES = 1024L * 1024 * 1024;

    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] JET = "Standard Jet DB".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ACE = "Standard ACE DB".getBytes(StandardCharsets.US_ASCII);

    static {
        hardenXml();
    }

    private ProjectLoader() {
    }

    /** Limity JAXP pro všechny XML parsery v procesu (druhá vrstva za MPXJ, které DOCTYPE zakazuje). */
    static void hardenXml() {
        setIfAbsent("javax.xml.accessExternalDTD", "");
        setIfAbsent("javax.xml.accessExternalSchema", "");
        setIfAbsent("javax.xml.accessExternalStylesheet", "");
        setIfAbsent("jdk.xml.entityExpansionLimit", "1000");
        setIfAbsent("jdk.xml.totalEntitySizeLimit", "200000");
        setIfAbsent("jdk.xml.maxGeneralEntitySizeLimit", "20000");
        setIfAbsent("jdk.xml.maxParameterEntitySizeLimit", "20000");
        setIfAbsent("jdk.xml.elementAttributeLimit", "1000");
        setIfAbsent("jdk.xml.maxElementDepth", "10000");
        setIfAbsent("jdk.xml.maxXMLNameLimit", "5000");
    }

    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    public enum Kind { MPP, XML, MPX, MPD }

    public static boolean isMpp(Path path) {
        return kindByExtension(path) == Kind.MPP;
    }

    /** Druh souboru podle přípony (null = nepodporovaná přípona). */
    public static Kind kindByExtension(Path path) {
        String n = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        String ext = dot < 0 ? "" : n.substring(dot + 1);
        switch (ext) {
            case "mpp": case "mpt": return Kind.MPP;
            case "xml": return Kind.XML;
            case "mpx": return Kind.MPX;
            case "mpd": case "mdb": return Kind.MPD;
            default: return null;
        }
    }

    /** Ověří, že obsah odpovídá formátu deklarovanému příponou (signatura v prvních bajtech). */
    static boolean signatureMatches(Kind kind, byte[] head) {
        switch (kind) {
            case MPP:
                return startsWith(head, 0, OLE2);
            case MPD:
                return startsWith(head, 4, JET) || startsWith(head, 4, ACE);
            case MPX:
                return startsWith(head, 0, "MPX".getBytes(StandardCharsets.US_ASCII));
            case XML: {
                int i = 0;
                if (head.length >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
                    i = 3;
                }
                // UTF-16 s BOM: FF FE 3C 00 / FE FF 00 3C
                if (head.length >= 4 && ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE && head[2] == '<'
                        || (head[0] & 0xFF) == 0xFE && (head[1] & 0xFF) == 0xFF && head[3] == '<')) {
                    return true;
                }
                while (i < head.length && (head[i] == ' ' || head[i] == '\t' || head[i] == '\r' || head[i] == '\n')) {
                    i++;
                }
                return i < head.length && head[i] == '<';
            }
            default:
                return false;
        }
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(data, offset, offset + prefix.length), prefix);
    }

    private static byte[] head(Path path) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(path.toFile()))) {
            byte[] buf = new byte[64];
            int n = in.readNBytes(buf, 0, buf.length);
            return Arrays.copyOf(buf, Math.max(0, n));
        }
    }

    public static ProjectFile load(Path path, String password) throws Exception {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Soubor neexistuje: " + path);
        }
        Kind kind = kindByExtension(path);
        if (kind == null) {
            throw new MPXJException("Nepodporovaná přípona. Podporovány jsou soubory MPP, MPT, MPX, XML (MSPDI) a MPD.");
        }
        long size = Files.size(path);
        if (size == 0) {
            throw new MPXJException("Soubor je prázdný.");
        }
        if (size > MAX_FILE_BYTES) {
            throw new MPXJException("Soubor je příliš velký (" + (size / (1024 * 1024)) + " MB); limit je " + (MAX_FILE_BYTES / (1024 * 1024)) + " MB.");
        }
        if (!signatureMatches(kind, head(path))) {
            throw new MPXJException("Obsah souboru neodpovídá příponě (" + kind + ") – soubor je poškozený, přejmenovaný, nebo jde o jiný formát.");
        }
        switch (kind) {
            case MPP: {
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
            case XML: {
                MSPDIReader reader = new MSPDIReader();
                try (InputStream in = new BufferedInputStream(new FileInputStream(path.toFile()), 1 << 16)) {
                    ProjectFile file = reader.read(in);
                    if (file == null) {
                        throw new MPXJException("Soubor XML není projekt Microsoft Project (MSPDI).");
                    }
                    return file;
                }
            }
            case MPX:
                return loadMpx(path);
            default: {
                MPDFileReader reader = new MPDFileReader();
                try {
                    return reader.read(path.toFile());
                } catch (MPXJException e) {
                    // databáze s více projekty: vezme se první
                    List<ProjectFile> all = reader.readAll(path.toFile());
                    if (all == null || all.isEmpty()) {
                        throw e;
                    }
                    return all.get(0);
                }
            }
        }
    }

    /** MPX je lokalizovaný textový formát – zkusí se angličtina a pak další jazyky, které MPXJ zná. */
    private static ProjectFile loadMpx(Path path) throws Exception {
        Exception first = null;
        for (String tag : new String[]{"en", "de", "fr", "it", "pt", "sv", "zh", "es", "ru"}) {
            Locale locale = Locale.forLanguageTag(tag);
            MPXReader reader = new MPXReader();
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
