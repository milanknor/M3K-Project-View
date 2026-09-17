package cz.msproject.viewer;

import org.mpxj.ProjectFile;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/** Řízené snímky okna pro webovou stránku (spouští se ze scratchpadu, není součástí aplikace). */
public final class Shots {

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        String outDir = args[1];
        App.setupLookAndFeel();
        ProjectFile project = ProjectLoader.load(file, null);
        SwingUtilities.invokeAndWait(() -> {
            try {
                MainWindow w = new MainWindow();
                w.setSize(1400, 880);
                w.addNotify();
                w.apply(file, project);
                w.validate();
                // Ganttův diagram: celý projekt, výběr na zajímavém úkolu, tabulka nahoře
                w.gantt.setZoom(GanttPanel.Zoom.FIT);
                w.baselineBox.setSelected(true);
                w.gantt.setShowBaseline(true);
                // šířky sloupců si program přizpůsobí sám (od verze 0.3.0),
                // stačí posunout dělicí příčku, ať je na názvy vidět
                w.mainSplit.setDividerLocation(820);
                int row = Math.min(9, w.tree.visible().size() - 1);
                w.table.setRowSelectionInterval(row, row);
                w.table.scrollRectToVisible(w.table.getCellRect(0, 0, true));
                w.details.tabs().setSelectedIndex(0);
                w.validate();
                shot(w, outDir + "/gantt.png");

                w.tabs.setSelectedIndex(1);   // Časová osa
                w.timeline.rebuild();
                w.validate();
                shot(w, outDir + "/timeline.png");

                w.tabs.setSelectedIndex(3);   // Vytížení
                w.usage.setPeriod(ResourceUsagePanel.Period.WEEKS);
                w.usage.waitForCompute();
                // zdroj s více úkoly, aby spodní panel nebyl skoro prázdný
                int res = Math.min(1, w.usage.table().getRowCount() - 1);
                if (res >= 0) {
                    w.usage.table().setRowSelectionInterval(res, res);
                }
                w.validate();
                shot(w, outDir + "/usage.png");

                w.tabs.setSelectedIndex(4);   // Kalendáře
                w.validate();
                shot(w, outDir + "/calendar.png");

                w.tabs.setSelectedIndex(0);
                w.validate();

                // dialog kontroly kvality plánu nad oknem
                java.util.List<ScheduleCheck.Finding> findings = ScheduleCheck.run(project);
                ScheduleCheckDialog dialog = new ScheduleCheckDialog(w, findings);
                dialog.pack();
                dialog.addNotify();
                dialog.validate();
                shotWithDialog(w, dialog, outDir + "/check.png");
                dialog.dispose();

                // samotný diagram v plné velikosti (pro výřez do hlavičky stránky)
                BufferedImage full = w.renderImage();
                ImageIO.write(full, "png", new File(outDir + "/gantt-full.png"));
                System.out.println("hotovo, diagram " + full.getWidth() + "×" + full.getHeight());
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(2);
            }
        });
        System.exit(0);
    }

    /** Okno se ztmavením a dialogem uprostřed – jako když dialog opravdu vyskočí. */
    private static void shotWithDialog(MainWindow w, javax.swing.JDialog dialog, String path) throws Exception {
        BufferedImage img = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        w.getRootPane().printAll(g);
        g.setColor(new java.awt.Color(0, 0, 0, 90));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        BufferedImage d = new BufferedImage(dialog.getWidth(), dialog.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D dg = d.createGraphics();
        dialog.getRootPane().printAll(dg);
        dg.dispose();

        int x = (img.getWidth() - d.getWidth()) / 2;
        int y = (img.getHeight() - d.getHeight()) / 2;
        g.setColor(new java.awt.Color(0, 0, 0, 60));
        g.fillRoundRect(x - 6, y - 4, d.getWidth() + 12, d.getHeight() + 14, 12, 12);
        g.drawImage(d, x, y, null);
        g.setColor(new java.awt.Color(0, 0, 0, 120));
        g.drawRect(x - 1, y - 1, d.getWidth() + 1, d.getHeight() + 1);
        g.dispose();
        ImageIO.write(img, "png", new File(path));
    }

    private static void shot(MainWindow w, String path) throws Exception {
        BufferedImage img = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        w.getRootPane().printAll(g);
        g.dispose();
        ImageIO.write(img, "png", new File(path));
    }
}
