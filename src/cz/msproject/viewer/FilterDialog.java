package cz.msproject.viewer;

import org.mpxj.ProjectFile;
import org.mpxj.Resource;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** Dialog rozšířeného filtru s možností uložit filtr pod názvem. */
final class FilterDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final String PREF_KEY = "savedFilters";
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("d. M. yyyy"), DateTimeFormatter.ofPattern("d.M.yyyy"), DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d. M. yy"), DateTimeFormatter.ofPattern("d.M.yy")};

    private final JComboBox<String> savedBox = new JComboBox<>();
    private final JComboBox<String> resourceBox = new JComboBox<>();
    private final JTextField fromField = new JTextField(10);
    private final JTextField toField = new JTextField(10);
    private final JTextField pctMin = new JTextField(4);
    private final JTextField pctMax = new JTextField(4);
    private final JComboBox<Columns.Column> columnBox = new JComboBox<>();
    private final JTextField textField = new JTextField(18);
    private final JCheckBox leafOnly = new JCheckBox("Jen běžné úkoly a milníky (bez souhrnných)");
    private final Preferences prefs;
    private final List<TaskFilter> saved = new ArrayList<>();
    private TaskFilter result;
    private boolean applied;

    FilterDialog(Window owner, Preferences prefs, ProjectFile project, Columns columns, TaskFilter current) {
        super(owner, "Rozšířený filtr", ModalityType.APPLICATION_MODAL);
        this.prefs = prefs;
        resourceBox.setEditable(true);
        resourceBox.addItem("");
        if (project != null) {
            for (Resource r : project.getResources()) {
                if (r.getName() != null && !r.getName().isBlank()) {
                    resourceBox.addItem(r.getName());
                }
            }
        }
        columnBox.addItem(null);
        for (Columns.Column c : columns.all()) {
            columnBox.addItem(c);
        }
        columnBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l, Object v, int i, boolean s, boolean f) {
                return super.getListCellRendererComponent(l, v == null ? "název, poznámky, WBS" : ((Columns.Column) v).title, i, s, f);
            }
        });
        loadSaved();
        savedBox.addActionListener(e -> {
            int i = savedBox.getSelectedIndex() - 1;
            if (i >= 0 && i < saved.size()) {
                fill(saved.get(i));
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        int row = 0;
        add(form, gc, row++, "Uložený filtr:", savedBox, savedButtons());
        add(form, gc, row++, "Zdroj (část názvu):", resourceBox, null);
        JPanel dates = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dates.add(fromField);
        dates.add(new JLabel(" – "));
        dates.add(toField);
        dates.add(muted("(d. M. yyyy; úkol zasahuje do období)"));
        add(form, gc, row++, "Období:", dates, null);
        JPanel pct = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pct.add(pctMin);
        pct.add(new JLabel(" – "));
        pct.add(pctMax);
        pct.add(muted("%"));
        add(form, gc, row++, "Dokončeno:", pct, null);
        JPanel text = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        text.add(columnBox);
        text.add(new JLabel(" obsahuje "));
        text.add(textField);
        add(form, gc, row++, "Text ve sloupci:", text, null);
        add(form, gc, row++, "", leafOnly, null);
        for (JComponent c : new JComponent[]{savedBox, resourceBox, fromField, toField, pctMin, pctMax, columnBox, textField, leafOnly}) {
            c.setFont(Theme.BASE);
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton clear = new JButton("Vymazat");
        clear.addActionListener(e -> fill(TaskFilter.EMPTY));
        JButton cancel = new JButton("Zrušit");
        cancel.addActionListener(e -> dispose());
        JButton ok = new JButton("Použít");
        ok.addActionListener(e -> {
            TaskFilter f = read(false);
            if (f != null) {
                result = f;
                applied = true;
                dispose();
            }
        });
        buttons.add(clear);
        buttons.add(cancel);
        buttons.add(ok);
        getRootPane().setDefaultButton(ok);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        getRootPane().getActionMap().put("esc", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });
        JPanel root = new JPanel(new BorderLayout());
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        fill(current == null ? TaskFilter.EMPTY : current);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel savedButtons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton save = new JButton("Uložit jako…");
        save.addActionListener(e -> {
            TaskFilter f = read(true);
            if (f == null) {
                return;
            }
            String name = JOptionPane.showInputDialog(this, "Název filtru:", f.name() == null || f.name().isBlank() ? f.describe() : f.name());
            if (name == null || name.isBlank()) {
                return;
            }
            TaskFilter named = new TaskFilter(name.strip(), f.resource(), f.from(), f.to(), f.pctMin(), f.pctMax(), f.columnKey(), f.text(), f.leafOnly());
            saved.removeIf(s -> s.name().equalsIgnoreCase(named.name()));
            saved.add(named);
            storeSaved();
            savedBox.setSelectedIndex(saved.indexOf(named) + 1);
        });
        JButton delete = new JButton("Smazat");
        delete.addActionListener(e -> {
            int i = savedBox.getSelectedIndex() - 1;
            if (i >= 0 && i < saved.size()) {
                saved.remove(i);
                storeSaved();
            }
        });
        p.add(save);
        p.add(delete);
        return p;
    }

    private static JLabel muted(String s) {
        JLabel l = new JLabel(s);
        l.setFont(Theme.SMALL);
        l.setForeground(Theme.MUTED);
        return l;
    }

    private static void add(JPanel form, GridBagConstraints gc, int row, String label, JComponent field, JComponent extra) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        JLabel l = new JLabel(label);
        l.setFont(Theme.BASE);
        l.setForeground(Theme.MUTED);
        form.add(l, gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, gc);
        gc.fill = GridBagConstraints.NONE;
        if (extra != null) {
            gc.gridx = 2;
            gc.weightx = 0;
            form.add(extra, gc);
        }
    }

    private void fill(TaskFilter f) {
        resourceBox.setSelectedItem(f.resource() == null ? "" : f.resource());
        fromField.setText(f.from() == null ? "" : Fmt.DATE.format(f.from()));
        toField.setText(f.to() == null ? "" : Fmt.DATE.format(f.to()));
        pctMin.setText(f.pctMin() == null ? "" : String.valueOf(f.pctMin()));
        pctMax.setText(f.pctMax() == null ? "" : String.valueOf(f.pctMax()));
        Columns.Column col = null;
        for (int i = 1; i < columnBox.getItemCount(); i++) {
            if (columnBox.getItemAt(i).key.equals(f.columnKey())) {
                col = columnBox.getItemAt(i);
            }
        }
        columnBox.setSelectedItem(col);
        textField.setText(f.text() == null ? "" : f.text());
        leafOnly.setSelected(f.leafOnly());
    }

    /** Přečte formulář; při chybě zobrazí hlášku a vrátí null. */
    private TaskFilter read(boolean quiet) {
        try {
            LocalDate from = parseDate(fromField.getText());
            LocalDate to = parseDate(toField.getText());
            if (from != null && to != null && to.isBefore(from)) {
                throw new IllegalArgumentException("Konec období je před začátkem.");
            }
            Integer min = parseInt(pctMin.getText());
            Integer max = parseInt(pctMax.getText());
            Object res = resourceBox.getEditor().getItem();
            Columns.Column col = (Columns.Column) columnBox.getSelectedItem();
            int sel = savedBox.getSelectedIndex() - 1;
            String name = sel >= 0 && sel < saved.size() ? saved.get(sel).name() : "";
            return new TaskFilter(name, res == null ? "" : res.toString().strip(), from, to, min, max, col == null ? "" : col.key,
                    textField.getText().strip(), leafOnly.isSelected());
        } catch (IllegalArgumentException ex) {
            if (!quiet) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Rozšířený filtr", JOptionPane.WARNING_MESSAGE);
            }
            return null;
        }
    }

    static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.strip();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignored) {
                // další formát
            }
        }
        throw new IllegalArgumentException("Nerozumím datu „" + v + "“ – zadejte např. 15. 9. 2026.");
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            int v = Integer.parseInt(s.strip().replace("%", "").strip());
            if (v < 0 || v > 100) {
                throw new IllegalArgumentException("Procenta musí být 0–100.");
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Procenta musí být celé číslo 0–100.");
        }
    }

    private void loadSaved() {
        saved.clear();
        String v = prefs.get(PREF_KEY, "");
        if (!v.isBlank()) {
            for (String line : v.split("\n")) {
                TaskFilter f = TaskFilter.deserialize(line);
                if (f != null && !f.name().isBlank()) {
                    saved.add(f);
                }
            }
        }
        refreshSavedBox();
    }

    private void storeSaved() {
        StringBuilder sb = new StringBuilder();
        for (TaskFilter f : saved) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(f.serialize());
        }
        prefs.put(PREF_KEY, sb.toString());
        refreshSavedBox();
    }

    private void refreshSavedBox() {
        savedBox.removeAllItems();
        savedBox.addItem("(nový filtr)");
        for (TaskFilter f : saved) {
            savedBox.addItem(f.name());
        }
    }

    TaskFilter result() {
        return applied ? result : null;
    }

    /** Uložené filtry (pro nabídku Zobrazit). */
    static List<TaskFilter> savedFilters(Preferences prefs) {
        List<TaskFilter> out = new ArrayList<>();
        String v = prefs.get(PREF_KEY, "");
        if (!v.isBlank()) {
            for (String line : v.split("\n")) {
                TaskFilter f = TaskFilter.deserialize(line);
                if (f != null && !f.name().isBlank()) {
                    out.add(f);
                }
            }
        }
        return out;
    }
}
