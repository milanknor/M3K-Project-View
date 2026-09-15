package cz.msproject.viewer;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tabulka úkolů (levá část Ganttova diagramu) se zvolenou sadou sloupců. */
public final class TaskTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    private final TaskTree tree;
    private final Columns columns;
    private final List<Columns.Column> shown = new ArrayList<>();

    public TaskTableModel(TaskTree tree, Columns columns) {
        this.tree = tree;
        this.columns = columns;
        for (String k : Columns.DEFAULT_KEYS) {
            shown.add(columns.byKey(k));
        }
    }

    public Columns columns() {
        return columns;
    }

    public List<Columns.Column> shown() {
        return Collections.unmodifiableList(shown);
    }

    /** Nastaví zobrazené sloupce (v pořadí modelu) a oznámí změnu struktury tabulky. */
    public void setShown(List<Columns.Column> list) {
        shown.clear();
        for (Columns.Column c : list) {
            if (c != null && !shown.contains(c)) {
                shown.add(c);
            }
        }
        if (shown.isEmpty()) {
            shown.add(Columns.NAME);
        }
        fireTableStructureChanged();
    }

    public Columns.Column columnAt(int modelIndex) {
        return modelIndex >= 0 && modelIndex < shown.size() ? shown.get(modelIndex) : null;
    }

    public int indexOf(Columns.Column c) {
        return shown.indexOf(c);
    }

    @Override
    public int getRowCount() {
        return tree.visible().size();
    }

    @Override
    public int getColumnCount() {
        return shown.size();
    }

    @Override
    public String getColumnName(int column) {
        return shown.get(column).title;
    }

    public TaskTree.Row row(int index) {
        return tree.visible().get(index);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= tree.visible().size() || columnIndex < 0 || columnIndex >= shown.size()) {
            return "";
        }
        return columns.text(shown.get(columnIndex), row(rowIndex));
    }
}
