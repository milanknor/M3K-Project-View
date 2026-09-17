package cz.msproject.viewer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Porovnání otevřeného harmonogramu se starší verzí: co přibylo, zmizelo a posunulo se. */
final class CompareDialog extends JDialog {

    private final MainWindow owner;
    private final List<ScheduleDiff.Row> all;
    private final Path olderPath;
    private final JTable table = new JTable();
    private final JCheckBox showSame = new JCheckBox(Str.t("Zobrazit i úkoly beze změny"));
    private final JLabel head = new JLabel();
    private List<ScheduleDiff.Row> shown = new ArrayList<>();

    CompareDialog(MainWindow owner, Path olderPath, List<ScheduleDiff.Row> rows) {
        super(owner, Str.t("Porovnání verzí"), false);
        this.owner = owner;
        this.olderPath = olderPath;
        this.all = rows;

        head.setFont(Theme.BOLD);
        Ui.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                if (!sel && r >= 0 && r < shown.size()) {
                    comp.setForeground(switch (shown.get(r).kind()) {
                        case ADDED -> Theme.TASK;
                        case REMOVED -> Theme.CRITICAL;
                        case CHANGED -> Theme.HIGHLIGHT;
                        case SAME -> Theme.MUTED;
                    });
                }
                return comp;
            }
        });
        showSame.setFont(Theme.BASE);
        showSame.setOpaque(false);
        showSame.addActionListener(e -> fill());

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel top = new JPanel(new BorderLayout());
        top.add(head, BorderLayout.NORTH);
        JLabel files = new JLabel(Str.t("Starší verze: ") + olderPath.getFileName()
                + "      " + Str.t("Otevřený soubor: ") + (owner.currentPath == null ? "" : owner.currentPath.getFileName()));
        files.setFont(Theme.SMALL);
        files.setForeground(Theme.MUTED);
        top.add(files, BorderLayout.CENTER);
        top.add(showSame, BorderLayout.SOUTH);
        content.add(top, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(1000, 420));
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons(), BorderLayout.SOUTH);
        setContentPane(content);
        fill();
        pack();
        setLocationRelativeTo(owner);
        Ui.bindKey(content, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "close", this::dispose);
    }

    private JComponent buttons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton show = new JButton(Str.t("Vybrat úkol v tabulce"));
        show.addActionListener(e -> {
            ScheduleDiff.Row r = selected();
            if (r != null && r.newTask() != null) {
                owner.selectTasks(List.of(r.newTask()));
            }
        });
        JButton copy = new JButton(Str.t("Kopírovat přehled"));
        copy.addActionListener(e -> {
            try {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(text('\t')), null);
            } catch (RuntimeException ignored) {
                // schránku může držet jiná aplikace – zbývá uložení do CSV
            }
        });
        JButton csv = new JButton(Str.t("Uložit CSV…"));
        csv.addActionListener(e -> saveCsv());
        JButton close = new JButton(Str.t("Zavřít"));
        close.addActionListener(e -> dispose());
        p.add(show);
        p.add(copy);
        p.add(csv);
        p.add(Box.createHorizontalStrut(16));
        p.add(close);
        return p;
    }

    private ScheduleDiff.Row selected() {
        int r = table.getSelectedRow();
        return r < 0 || r >= shown.size() ? null : shown.get(r);
    }

    private void fill() {
        shown = new ArrayList<>();
        for (ScheduleDiff.Row r : all) {
            if (showSame.isSelected() || r.kind() != ScheduleDiff.Kind.SAME) {
                shown.add(r);
            }
        }
        DefaultTableModel model = Ui.readOnlyModel(Str.t("Stav"), Str.t("WBS"), Str.t("Úkol"),
                Str.t("Zahájení dřív"), Str.t("Zahájení teď"), Str.t("Posun"),
                Str.t("Dokončení dřív"), Str.t("Dokončení teď"), Str.t("Posun"), Str.t("Změny"));
        for (ScheduleDiff.Row r : shown) {
            model.addRow(new Object[]{r.kind().label, r.wbs(), r.name(),
                    Fmt.date(r.oldStart()), Fmt.date(r.newStart()), shiftText(r.startShift()),
                    Fmt.date(r.oldFinish()), Fmt.date(r.newFinish()), shiftText(r.finishShift()),
                    r.changesText()});
        }
        table.setModel(model);
        int[] widths = {90, 80, 300, 100, 100, 60, 100, 100, 60, 320};
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        head.setText(ScheduleDiff.summary(all) + Str.t("  •  zobrazeno ") + shown.size() + Str.t(" řádků"));
    }

    private static String shiftText(long days) {
        return days == 0 ? "" : (days > 0 ? "+" : "") + days;
    }

    private String text(char sep) {
        StringBuilder sb = new StringBuilder();
        sb.append(Str.t("Porovnání verzí")).append(sep).append(olderPath).append(sep)
                .append(owner.currentPath == null ? "" : owner.currentPath.toString()).append('\n');
        sb.append(String.join(String.valueOf(sep), Str.t("Stav"), Str.t("WBS"), Str.t("Úkol"),
                Str.t("Zahájení dřív"), Str.t("Zahájení teď"), Str.t("Posun"),
                Str.t("Dokončení dřív"), Str.t("Dokončení teď"), Str.t("Posun"), Str.t("Změny"))).append('\n');
        for (ScheduleDiff.Row r : shown) {
            sb.append(String.join(String.valueOf(sep), r.kind().label, r.wbs(), r.name(),
                    Fmt.date(r.oldStart()), Fmt.date(r.newStart()), shiftText(r.startShift()),
                    Fmt.date(r.oldFinish()), Fmt.date(r.newFinish()), shiftText(r.finishShift()),
                    r.changesText())).append('\n');
        }
        return sb.toString();
    }

    private void saveCsv() {
        File f = FileDialogs.save(this, Str.t("Uložit porovnání jako CSV"),
                owner.prefs.get("lastExportDir", System.getProperty("user.home")),
                "porovnani-verzi.csv", new FileDialogs.Filter("CSV (*.csv)", "csv"));
        if (f == null) {
            return;
        }
        try {
            Files.writeString(f.toPath(), "﻿" + text(';').replace("\n", "\r\n"), StandardCharsets.UTF_8);
            owner.status.setText(Str.t("Porovnání uloženo do ") + f.getAbsolutePath());
        } catch (IOException ex) {
            owner.showError(Str.t("Uložení se nezdařilo."), ex);
        }
    }
}
