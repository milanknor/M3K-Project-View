package cz.msproject.viewer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Úvodní obrazovka bez otevřeného souboru: logo, tlačítko Otevřít a nedávné soubory. */
final class StartPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JPanel recentBox = new JPanel();
    private final Consumer<Path> opener;
    private final Runnable chooser;

    StartPanel(Consumer<Path> opener, Runnable chooser) {
        super(new GridBagLayout());
        this.opener = opener;
        this.chooser = chooser;
        setBackground(Theme.BG);
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));

        JLabel logo = new JLabel("<html><span style='font-size:22pt'>" + MainWindow.APP_NAME_HTML + "</span></html>", new ImageIcon(Icons.app(64)), SwingConstants.LEFT);
        logo.setIconTextGap(16);
        logo.setForeground(Icons.DARK);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(logo);
        JLabel sub = new JLabel(Str.t("Prohlížeč dokumentů MS Project – MPP, MPT, XML, MPX, MPD. Pouze pro čtení."));
        sub.setFont(Theme.BASE);
        sub.setForeground(Theme.MUTED);
        sub.setBorder(BorderFactory.createEmptyBorder(6, 0, 18, 0));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(sub);

        JButton open = new JButton(Str.t("Otevřít soubor…   (Ctrl+O)"));
        open.setFont(Theme.BOLD);
        open.setAlignmentX(Component.LEFT_ALIGNMENT);
        open.addActionListener(e -> chooser.run());
        card.add(open);
        JLabel drop = new JLabel(Str.t("nebo přetáhněte soubor kamkoli do tohoto okna"));
        drop.setFont(Theme.SMALL);
        drop.setForeground(Theme.MUTED);
        drop.setBorder(BorderFactory.createEmptyBorder(6, 2, 20, 0));
        drop.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(drop);

        JLabel recentTitle = new JLabel(Str.t("Nedávné soubory"));
        recentTitle.setFont(Theme.BOLD);
        recentTitle.setForeground(Theme.INK);
        recentTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(recentTitle);
        recentBox.setLayout(new BoxLayout(recentBox, BoxLayout.Y_AXIS));
        recentBox.setOpaque(false);
        recentBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        recentBox.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        card.add(recentBox);
        card.add(Box.createVerticalGlue());

        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = new Insets(30, 40, 30, 40);
        gc.weightx = 1;
        gc.weighty = 1;
        add(card, gc);
    }

    void setRecent(List<Path> files) {
        recentBox.removeAll();
        if (files.isEmpty()) {
            JLabel none = new JLabel(Str.t("(zatím žádné)"));
            none.setFont(Theme.BASE);
            none.setForeground(Theme.MUTED);
            recentBox.add(none);
        }
        for (Path p : files) {
            boolean exists = Files.isRegularFile(p);
            JLabel link = new JLabel("<html><b>" + Fmt.html(p.getFileName().toString()) + "</b> &nbsp;<span style='color:" + Theme.MUTED_HEX + "'>"
                    + Fmt.html(String.valueOf(p.getParent())) + (exists ? "" : Str.t(" – soubor nenalezen")) + "</span></html>");
            link.setFont(Theme.BASE);
            link.setForeground(exists ? Theme.STATUS_DATE : Theme.INACTIVE);
            link.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
            link.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (exists) {
                link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                link.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        opener.accept(p);
                    }
                });
            }
            recentBox.add(link);
        }
        recentBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, recentBox.getPreferredSize().height));
        recentBox.revalidate();
        recentBox.repaint();
    }
}
