package cz.msproject.viewer;

import org.mpxj.Task;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Výsledek kontroly kvality plánu: přehled kontrol, detail nálezu a přechod na dotčené úkoly. */
final class ScheduleCheckDialog extends JDialog {

    private final MainWindow owner;
    private final List<ScheduleCheck.Finding> findings;
    private final JTable table;
    private final JTextArea detail = new JTextArea(5, 60);

    ScheduleCheckDialog(MainWindow owner, List<ScheduleCheck.Finding> findings) {
        super(owner, Str.t("Kontrola kvality plánu"), false);
        this.owner = owner;
        this.findings = findings;

        DefaultTableModel model = Ui.readOnlyModel(Str.t("Kontrola"), Str.t("Nález"), Str.t("Podíl"), Str.t("Závažnost"));
        for (ScheduleCheck.Finding f : findings) {
            model.addRow(new Object[]{f.rule(), f.count() == 0 ? Str.t("nic") : String.valueOf(f.count()),
                    f.count() == 0 ? "" : Fmt.number(Math.round(f.share() * 10) / 10.0) + " %", f.level().label});
        }
        table = new JTable(model);
        Ui.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(260);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(70);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                ScheduleCheck.Finding f = findings.get(t.convertRowIndexToModel(r));
                if (!sel) {
                    comp.setForeground(switch (f.level()) {
                        case WARNING -> Theme.CRITICAL;
                        case NOTE -> Theme.HIGHLIGHT;
                        case OK -> Theme.MUTED;
                    });
                }
                return comp;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetail();
            }
        });

        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setFont(Theme.BASE);
        detail.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel head = new JLabel(ScheduleCheck.summary(findings));
        head.setFont(Theme.BOLD);
        content.add(head, BorderLayout.NORTH);
        JScrollPane list = new JScrollPane(table);
        list.setPreferredSize(new Dimension(760, 320));
        JScrollPane det = new JScrollPane(detail);
        det.setPreferredSize(new Dimension(760, 150));
        JPanel middle = new JPanel(new BorderLayout(0, 10));
        middle.add(list, BorderLayout.CENTER);
        middle.add(det, BorderLayout.SOUTH);
        content.add(middle, BorderLayout.CENTER);
        content.add(buttons(), BorderLayout.SOUTH);
        setContentPane(content);
        if (!findings.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        pack();
        setLocationRelativeTo(owner);
        Ui.bindKey(content, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "close", this::dispose);
    }

    private JComponent buttons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton show = new JButton(Str.t("Vybrat úkoly v tabulce"));
        show.addActionListener(e -> {
            ScheduleCheck.Finding f = selected();
            if (f != null && !f.tasks().isEmpty()) {
                owner.selectTasks(f.tasks());
            }
        });
        JButton copy = new JButton(Str.t("Kopírovat přehled"));
        copy.addActionListener(e -> {
            try {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(report()), null);
            } catch (RuntimeException ignored) {
                // schránka může být obsazená jinou aplikací – uživatel může použít uložení do CSV
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

    private ScheduleCheck.Finding selected() {
        int r = table.getSelectedRow();
        return r < 0 || r >= findings.size() ? null : findings.get(table.convertRowIndexToModel(r));
    }

    private void showDetail() {
        ScheduleCheck.Finding f = selected();
        if (f == null) {
            detail.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder(f.detail());
        if (f.count() > 0) {
            sb.append("\n\n").append(Str.t("Dotčené úkoly (")).append(f.count()).append("):");
            int shown = 0;
            for (Task t : f.tasks()) {
                if (shown++ == 30) {
                    sb.append("\n…");
                    break;
                }
                sb.append("\n").append(Fmt.text(t.getID())).append(" · ").append(Fmt.plain(Fmt.text(t.getName())));
            }
        }
        detail.setText(sb.toString());
        detail.setCaretPosition(0);
    }

    /** Přehled kontrol jako text (pro schránku). */
    private String report() {
        StringBuilder sb = new StringBuilder(Str.t("Kontrola kvality plánu") + " – " + ScheduleCheck.summary(findings) + "\n\n");
        for (ScheduleCheck.Finding f : findings) {
            sb.append(f.rule()).append('\t').append(f.count()).append('\t')
                    .append(f.count() == 0 ? "" : Fmt.number(Math.round(f.share() * 10) / 10.0) + " %")
                    .append('\t').append(f.level().label).append('\n');
        }
        return sb.toString();
    }

    private void saveCsv() {
        File f = FileDialogs.save(this, Str.t("Uložit kontrolu jako CSV"),
                owner.prefs.get("lastExportDir", System.getProperty("user.home")),
                "kontrola-planu.csv", new FileDialogs.Filter("CSV (*.csv)", "csv"));
        if (f == null) {
            return;
        }
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(Str.t("Kontrola")).append(';').append(Str.t("Nález")).append(';').append(Str.t("Podíl %"))
                .append(';').append(Str.t("Závažnost")).append(';').append(Str.t("Úkoly")).append("\r\n");
        for (ScheduleCheck.Finding fi : findings) {
            StringBuilder ids = new StringBuilder();
            for (Task t : fi.tasks()) {
                if (ids.length() > 0) {
                    ids.append(' ');
                }
                ids.append(Fmt.text(t.getID()));
            }
            sb.append(csv(fi.rule())).append(';').append(fi.count()).append(';')
                    .append(csv(Fmt.number(Math.round(fi.share() * 10) / 10.0))).append(';')
                    .append(csv(fi.level().label)).append(';').append(csv(ids.toString())).append("\r\n");
        }
        try {
            Files.writeString(f.toPath(), sb.toString(), StandardCharsets.UTF_8);
            owner.status.setText(Str.t("Kontrola uložena do ") + f.getAbsolutePath());
        } catch (IOException ex) {
            owner.showError(Str.t("Uložení se nezdařilo."), ex);
        }
    }

    private static String csv(String s) {
        String v = s == null ? "" : s;
        return v.contains(";") || v.contains("\"") ? '"' + v.replace("\"", "\"\"") + '"' : v;
    }
}
