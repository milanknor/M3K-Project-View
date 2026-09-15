package cz.msproject.viewer;

import org.mpxj.Task;

import javax.swing.table.AbstractTableModel;

/** Tabulka úkolů (levá část Ganttova diagramu). */
public final class TaskTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    public enum Col {
        ID("ID", 46),
        NAME("Název úkolu", 330),
        DURATION("Trvání", 92),
        START("Zahájení", 96),
        FINISH("Dokončení", 96),
        PERCENT("% dokonč.", 78),
        PREDECESSORS("Předchůdci", 110),
        RESOURCES("Názvy zdrojů", 190),
        WORK("Práce", 90),
        COST("Náklady", 100),
        SLACK("Celková rezerva", 110),
        WBS("WBS", 70);

        public final String title;
        public final int width;

        Col(String title, int width) {
            this.title = title;
            this.width = width;
        }
    }

    private final TaskTree tree;
    private String currency = "";

    public TaskTableModel(TaskTree tree) {
        this.tree = tree;
    }

    public void setCurrency(String currency) {
        this.currency = currency == null ? "" : currency;
    }

    @Override
    public int getRowCount() {
        return tree.visible().size();
    }

    @Override
    public int getColumnCount() {
        return Col.values().length;
    }

    @Override
    public String getColumnName(int column) {
        return Col.values()[column].title;
    }

    public TaskTree.Row row(int index) {
        return tree.visible().get(index);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= tree.visible().size()) {
            return "";
        }
        Task t = row(rowIndex).task;
        switch (Col.values()[columnIndex]) {
            case ID: return t.getID() == null ? "" : String.valueOf(t.getID());
            case NAME: return Fmt.text(t.getName());
            case DURATION: return Fmt.duration(t.getDuration());
            case START: return Fmt.date(t.getStart());
            case FINISH: return Fmt.date(t.getFinish());
            case PERCENT: return Fmt.percent(t.getPercentageComplete());
            case PREDECESSORS: return Fmt.predecessors(t);
            case RESOURCES: return Fmt.resources(t);
            case WORK: return Fmt.duration(t.getWork());
            case COST: return Fmt.money(t.getCost(), currency);
            case SLACK: return Fmt.duration(t.getTotalSlack());
            case WBS: return Fmt.text(t.getWBS());
            default: return "";
        }
    }
}
