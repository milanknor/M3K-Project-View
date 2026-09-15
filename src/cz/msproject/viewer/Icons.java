package cz.msproject.viewer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Programově vykreslená ikona M3K Project View (bez externích souborů):
 * antracitový zaoblený čtverec, dva oranžové pruhy harmonogramu, diamant milníku
 * a oblouk odkazující na značku M3K. Stejnou kresbu generuje tools/make_icon.py
 * do ICO pro jpackage – při změně tvarů upravit obojí.
 */
public final class Icons {
    public static final Color ORANGE = new Color(0xFF, 0x79, 0x0A);
    public static final Color DARK = new Color(0x11, 0x11, 0x15);

    private Icons() {
    }

    /** Ikona s pozadím (okno, hlavní panel, instalátor). */
    public static BufferedImage app(int size) {
        return render(size, true);
    }

    /** Jen oranžový symbol bez pozadí (logo v hlavičce a dialogu O aplikaci). */
    public static BufferedImage symbol(int size) {
        return render(size, false);
    }

    private static BufferedImage render(int size, boolean background) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double s = size / 64.0;
        g.scale(s, s);
        if (background) {
            g.setColor(DARK);
            g.fill(new RoundRectangle2D.Double(0, 0, 64, 64, 28, 28));
        }
        g.setColor(ORANGE);
        // pruhy harmonogramu
        g.fill(new RoundRectangle2D.Double(9, 20, 22, 8, 4, 4));
        g.fill(new RoundRectangle2D.Double(15, 31, 24, 8, 4, 4));
        // diamant milníku
        Path2D.Double diamond = new Path2D.Double();
        diamond.moveTo(41, 41.5);
        diamond.lineTo(46.5, 47);
        diamond.lineTo(41, 52.5);
        diamond.lineTo(35.5, 47);
        diamond.closePath();
        g.fill(diamond);
        // oblouk (pravá část kružnice se středem vlevo)
        g.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(new Arc2D.Double(24 - 28, 32 - 28, 56, 56, -62, 124, Arc2D.OPEN));
        g.dispose();
        return img;
    }

    public static List<java.awt.Image> appImages() {
        List<java.awt.Image> list = new ArrayList<>();
        for (int s : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            list.add(app(s));
        }
        return list;
    }
}
