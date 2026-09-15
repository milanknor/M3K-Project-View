package cz.msproject.viewer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Minimální zapisovač PDF: každá stránka je jeden bezztrátově (Flate) komprimovaný RGB
 * obrázek roztažený na celou stránku. Bez externích knihoven; výsledek otevře
 * Acrobat, Edge, Chrome i Sumatra.
 */
public final class PdfWriter {
    private PdfWriter() {
    }

    /**
     * @param pages     počet stránek
     * @param render    vykreslí stránku i jako bitmapu
     * @param pageWpt   šířka stránky v bodech
     * @param pageHpt   výška stránky v bodech
     */
    public static void write(Path target, int pages, IntFunction<BufferedImage> render, double pageWpt, double pageHpt, String title) throws IOException {
        List<Long> offsets = new ArrayList<>();
        try (OutputStream raw = Files.newOutputStream(target); CountingStream out = new CountingStream(raw)) {
            out.write("%PDF-1.4\n%âãÏÓ\n");
            // objekty: 1 catalog, 2 pages, 3 info, pak pro každou stránku: page, content, image
            int first = 4;
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pages; i++) {
                kids.append(first + i * 3).append(" 0 R ");
            }
            offsets.add(out.count);
            out.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
            offsets.add(out.count);
            out.write("2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + pages + " >>\nendobj\n");
            offsets.add(out.count);
            out.write("3 0 obj\n<< /Producer (" + escape(MainWindow.APP_NAME + " " + MainWindow.VERSION) + ") /Title (" + escape(title) + ") >>\nendobj\n");
            String w = fmt(pageWpt);
            String h = fmt(pageHpt);
            for (int i = 0; i < pages; i++) {
                int pageObj = first + i * 3;
                int contentObj = pageObj + 1;
                int imageObj = pageObj + 2;
                BufferedImage img = render.apply(i);
                offsets.add(out.count);
                out.write(pageObj + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + w + " " + h + "] /Contents " + contentObj
                        + " 0 R /Resources << /XObject << /Im" + i + " " + imageObj + " 0 R >> >> >>\nendobj\n");
                String content = "q " + w + " 0 0 " + h + " 0 0 cm /Im" + i + " Do Q\n";
                byte[] cb = content.getBytes(StandardCharsets.US_ASCII);
                offsets.add(out.count);
                out.write(contentObj + " 0 obj\n<< /Length " + cb.length + " >>\nstream\n");
                out.write(cb);
                out.write("\nendstream\nendobj\n");
                byte[] data = flate(img);
                offsets.add(out.count);
                out.write(imageObj + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + img.getWidth() + " /Height " + img.getHeight()
                        + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length " + data.length + " >>\nstream\n");
                out.write(data);
                out.write("\nendstream\nendobj\n");
            }
            long xref = out.count;
            out.write("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
            for (long o : offsets) {
                out.write(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", o));
            }
            out.write("trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R /Info 3 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        }
    }

    private static byte[] flate(BufferedImage img) throws IOException {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(w * h / 4);
        Deflater def = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(bos, def, 65536)) {
            int[] row = new int[w];
            byte[] line = new byte[w * 3];
            for (int y = 0; y < h; y++) {
                img.getRGB(0, y, w, 1, row, 0, w);
                for (int x = 0; x < w; x++) {
                    int p = row[x];
                    line[x * 3] = (byte) (p >> 16);
                    line[x * 3 + 1] = (byte) (p >> 8);
                    line[x * 3 + 2] = (byte) p;
                }
                dos.write(line);
            }
        } finally {
            def.end();
        }
        return bos.toByteArray();
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : (s == null ? "" : s).toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 128) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static final class CountingStream extends OutputStream {
        private final OutputStream out;
        long count;

        CountingStream(OutputStream out) {
            this.out = out;
        }

        void write(String s) throws IOException {
            write(s.getBytes(StandardCharsets.ISO_8859_1));
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }
}
