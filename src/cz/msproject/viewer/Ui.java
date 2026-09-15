package cz.msproject.viewer;

import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.event.ActionEvent;

/** Drobné sdílené pomůcky pro stavbu uživatelského rozhraní (popisky, tabulky, položky nabídek). */
final class Ui {
    private Ui() {
    }

    /** Tlumený popisek před ovládacím prvkem („Filtr:“). */
    static JLabel label(String text) {
        JLabel l = new JLabel(text + " ");
        l.setFont(Theme.BASE);
        l.setForeground(Theme.MUTED);
        return l;
    }

    /** Nadpis sekce uvnitř panelu. */
    static JLabel section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.BOLD);
        l.setForeground(Theme.MUTED);
        l.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 12, 4, 12));
        return l;
    }

    /** Model tabulky jen pro čtení se zadanými sloupci. */
    static DefaultTableModel readOnlyModel(String... cols) {
        return new DefaultTableModel(cols, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /** Jednotný vzhled pomocných tabulek (detail, zdroje, kalendáře). */
    static void styleTable(JTable t) {
        t.setFont(Theme.BASE);
        t.setRowHeight(Math.round(24 * Theme.SCALE));
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.getTableHeader().setReorderingAllowed(false);
        t.setShowVerticalLines(false);
        t.setGridColor(Theme.GRID);
        t.setFillsViewportHeight(true);
    }

    static JMenuItem item(String label, KeyStroke key, Runnable action) {
        JMenuItem mi = new JMenuItem(label);
        if (key != null) {
            mi.setAccelerator(key);
        }
        mi.addActionListener(e -> action.run());
        return mi;
    }

    /** Položka nabídky svázaná se zaškrtávacím políčkem v liště (oba stavy se drží společně). */
    static JCheckBoxMenuItem check(String label, JCheckBox box) {
        JCheckBoxMenuItem mi = new JCheckBoxMenuItem(label, box.isSelected());
        mi.addActionListener(e -> box.setSelected(mi.isSelected()));
        box.addActionListener(e -> mi.setSelected(box.isSelected()));
        return mi;
    }

    static void bindKey(JComponent c, KeyStroke key, String name, Runnable action) {
        c.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        c.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
