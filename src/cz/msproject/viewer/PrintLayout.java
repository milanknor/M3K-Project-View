package cz.msproject.viewer;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.time.LocalDateTime;

/**
 * Stránkování vykresleného diagramu (tabulka + Gantt jako jeden obrázek) pro tisk a PDF.
 * Hlavička (názvy sloupců + časová osa) se opakuje na každé stránce, řádky se dělí
 * na hranicích řádků, dole je zápatí s názvem souboru, datem a číslem stránky.
 * Souřadnice stránky jsou v bodech (1/72 palce) jako u {@link Printable}.
 */
public final class PrintLayout implements Printable {
    private static final int FOOTER_PT = 18;
    private static final int TITLE_PT = 16;

    private final BufferedImage image;
    /** Měřítko, ve kterém byl obrázek vykreslen (1 = pixel obrazovky). */
    private final double imageScale;
    private final int rows;
    private final boolean fitWidth;
    private final String title;
    private final String footerLeft;
    private PageFormat format;

    public PrintLayout(BufferedImage image, double imageScale, int rows, boolean fitWidth, String title) {
        this.image = image;
        this.imageScale = imageScale;
        this.rows = Math.max(0, rows);
        this.fitWidth = fitWidth;
        this.title = title == null ? "" : title;
        this.footerLeft = MainWindow.APP_NAME + " · " + Fmt.dateTime(LocalDateTime.now());
    }

    public void setPageFormat(PageFormat f) {
        format = f;
    }

    // --- geometrie ------------------------------------------------------------------

    private double headerSrc() {
        return Theme.HEADER * imageScale;
    }

    private double rowSrc() {
        return Theme.ROW * imageScale;
    }

    /** Poměr bodů stránky na pixel obrázku. */
    private double factor(double pageW) {
        double natural = 72.0 / 96.0 / imageScale; // 96 px obrazovky ≈ 1 palec
        if (!fitWidth) {
            return natural;
        }
        return Math.min(natural, pageW / image.getWidth());
    }

    private int rowsPerPage(double pageW, double pageH) {
        double f = factor(pageW);
        double avail = pageH - TITLE_PT - FOOTER_PT - headerSrc() * f;
        return Math.max(1, (int) Math.floor(avail / (rowSrc() * f)));
    }

    private int pagesAcross(double pageW) {
        double f = factor(pageW);
        return Math.max(1, (int) Math.ceil(image.getWidth() * f / pageW - 1e-6));
    }

    private int pagesDown(double pageW, double pageH) {
        return Math.max(1, (int) Math.ceil(rows / (double) rowsPerPage(pageW, pageH)));
    }

    public int pageCount(double pageW, double pageH) {
        return pagesAcross(pageW) * pagesDown(pageW, pageH);
    }

    // --- kreslení -----------------------------------------------------------------

    /** Vykreslí stránku {@code page} (od 0) do plochy {@code pageW × pageH} bodů s počátkem v (0,0). */
    public void paintPage(Graphics2D g, int page, double pageW, double pageH) {
        int across = pagesAcross(pageW);
        int down = pagesDown(pageW, pageH);
        int total = across * down;
        if (page < 0 || page >= total) {
            return;
        }
        int col = page % across;
        int rowPage = page / across;
        double f = factor(pageW);
        int perPage = rowsPerPage(pageW, pageH);
        int firstRow = rowPage * perPage;
        int lastRow = Math.min(rows, firstRow + perPage);

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(shorten(title, fm, pageW), 0, fm.getAscent());

        double srcX0 = col * pageW / f;
        double srcX1 = Math.min(image.getWidth(), srcX0 + pageW / f);
        double top = TITLE_PT;
        AffineTransform saved = g.getTransform();
        java.awt.Shape clip = g.getClip();
        g.clip(new java.awt.geom.Rectangle2D.Double(0, top, pageW, pageH - top - FOOTER_PT));
        g.translate(0, top);
        g.scale(f, f);
        g.translate(-srcX0, 0);
        // hlavička
        int hx0 = (int) Math.floor(srcX0);
        int hx1 = (int) Math.ceil(srcX1);
        int hh = (int) Math.round(headerSrc());
        g.drawImage(image, hx0, 0, hx1, hh, hx0, 0, hx1, hh, null);
        // řádky
        int sy0 = hh + (int) Math.round(firstRow * rowSrc());
        int sy1 = Math.min(image.getHeight(), hh + (int) Math.round(lastRow * rowSrc()));
        if (sy1 > sy0) {
            g.drawImage(image, hx0, hh, hx1, hh + (sy1 - sy0), hx0, sy0, hx1, sy1, null);
        }
        g.setTransform(saved);
        g.setClip(clip);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 8));
        fm = g.getFontMetrics();
        g.setColor(new Color(100, 116, 139));
        double baseline = pageH - FOOTER_PT + fm.getAscent() + 4;
        g.drawString(footerLeft, 0, (float) baseline);
        String right = "Strana " + (page + 1) + " / " + total
                + (across > 1 ? "  (řádky " + (firstRow + 1) + "–" + lastRow + ", část " + (col + 1) + "/" + across + ")" : "  (řádky " + (firstRow + 1) + "–" + lastRow + ")");
        g.drawString(right, (float) (pageW - fm.stringWidth(right)), (float) baseline);
    }

    private static String shorten(String s, FontMetrics fm, double width) {
        String t = s;
        while (t.length() > 4 && fm.stringWidth(t) > width) {
            t = t.substring(0, t.length() - 2) + "…";
        }
        return t;
    }

    // --- Printable ------------------------------------------------------------------

    @Override
    public int print(Graphics graphics, PageFormat pf, int pageIndex) {
        double w = pf.getImageableWidth();
        double h = pf.getImageableHeight();
        if (pageIndex >= pageCount(w, h)) {
            return NO_SUCH_PAGE;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(pf.getImageableX(), pf.getImageableY());
        paintPage(g, pageIndex, w, h);
        g.dispose();
        return PAGE_EXISTS;
    }

    public PageFormat pageFormat() {
        return format;
    }

    /** Vykreslí stránku do bitmapy v daném DPI (pro PDF). */
    public BufferedImage renderPage(int page, double pageW, double pageH, int dpi) {
        int w = (int) Math.round(pageW * dpi / 72.0);
        int h = (int) Math.round(pageH * dpi / 72.0);
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.scale(dpi / 72.0, dpi / 72.0);
        paintPage(g, page, pageW, pageH);
        g.dispose();
        return img;
    }
}
