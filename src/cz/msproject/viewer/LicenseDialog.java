package cz.msproject.viewer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dialog Nápověda → Licence: vlevo seznam (licence programu, přehled třetích stran,
 * licence a NOTICE jednotlivých knihoven), vpravo text. Texty jsou zdroje v jaru
 * ve složce licenses/ (naplní ji build.ps1 z tools\collect_licenses.py), seznam
 * je v licenses/index.txt ve tvaru „titulek|cesta“.
 */
final class LicenseDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final String DIR = "licenses/";

    record Entry(String title, String resource) {
        @Override
        public String toString() {
            return title;
        }
    }

    private final JTextArea text = new JTextArea();

    LicenseDialog(Window owner) {
        super(owner, "Licence", ModalityType.APPLICATION_MODAL);
        List<Entry> entries = entries();
        JList<Entry> list = new JList<>(entries.toArray(new Entry[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(Theme.BASE);
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, value, index, selected, focus);
                c.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
                if (index < 2) {
                    c.setFont(Theme.BOLD);
                }
                return c;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                show(list.getSelectedValue());
            }
        });

        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        text.setMargin(new java.awt.Insets(10, 12, 10, 12));

        JScrollPane left = new JScrollPane(list);
        left.setMinimumSize(new Dimension(260, 100));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, new JScrollPane(text));
        split.setDividerLocation(330);
        split.setBorder(null);

        JPanel root = new JPanel(new BorderLayout());
        JLabel intro = new JLabel("<html>" + MainWindow.APP_NAME_HTML + " je freeware – zdarma k použití i k dalšímu šíření v nezměněné podobě.<br>"
                + "Knihovny třetích stran a běhové prostředí Java mají vlastní licence; "
                + "stejné texty jsou i ve složce <b>licenses</b> vedle programu.</html>");
        intro.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        intro.setFont(Theme.BASE);
        root.add(intro, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        root.getActionMap().put("close", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });
        setContentPane(root);
        setSize(980, 640);
        setMinimumSize(new Dimension(640, 400));
        setLocationRelativeTo(owner);
        if (!entries.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            text.setText("Licenční texty nejsou v tomto sestavení k dispozici (chybí zdroje licenses/).");
        }
    }

    private void show(Entry entry) {
        text.setText(text(entry.resource()));
        text.setCaretPosition(0);
    }

    /** Aktuálně zobrazený text (pro testy). */
    String currentText() {
        return text.getText();
    }

    /** Položky z licenses/index.txt; prázdný seznam, když zdroje chybí. */
    static List<Entry> entries() {
        String index = text("index.txt");
        if (index.isEmpty()) {
            return Collections.emptyList();
        }
        List<Entry> out = new ArrayList<>();
        for (String line : index.split("\n")) {
            int sep = line.indexOf('|');
            if (sep > 0) {
                out.add(new Entry(line.substring(0, sep).trim(), line.substring(sep + 1).trim()));
            }
        }
        return out;
    }

    /** Text zdroje licenses/&lt;resource&gt; (UTF-8); prázdný řetězec, když chybí. */
    static String text(String resource) {
        try (InputStream in = LicenseDialog.class.getResourceAsStream(DIR + resource)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            return "";
        }
    }
}
