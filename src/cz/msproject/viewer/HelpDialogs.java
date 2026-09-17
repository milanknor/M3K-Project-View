package cz.msproject.viewer;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import java.awt.Component;

/** Dialogy Nápověda → Ovládání a O aplikaci. */
final class HelpDialogs {
    private HelpDialogs() {
    }

    static void showHelp(Component parent) {
        String text = "<html><body style='" + Theme.HTML_BODY + ";width:520px'>"
                + Str.t("<b>Otevření souboru</b><br>Ctrl+O, tlačítko Otevřít… nebo přetažení souboru do okna. ")
                + Str.t("Podporované formáty: MPP/MPT (Project 98 – 2021/365), XML (MSPDI), MPX, MPD.<br><br>")
                + Str.t("<b>Osnova</b><br>Kliknutí na šipku u souhrnného úkolu, dvojklik nebo mezerník sbalí/rozbalí podúkoly. ")
                + Str.t("Klávesy + a − rozbalí/sbalí vybraný souhrn. Ctrl+Shift+E rozbalí vše, Ctrl+Shift+C sbalí vše. ")
                + Str.t("Pole Osnova rozbalí hierarchii do zvolené úrovně.<br><br>")
                + Str.t("<b>Hledání a filtry</b><br>Ctrl+F přejde do pole Hledat (název, ID, WBS, zdroje, poznámky). ")
                + Str.t("Filtr omezí seznam např. na kritické, zpožděné nebo nedokončené úkoly; nadřazené souhrny zůstávají vidět.<br><br>")
                + Str.t("<b>Diagram</b><br>Měřítko: Dny/Týdny/Měsíce/Čtvrtletí/Roky/Přizpůsobit, Ctrl+kolečko myši přibližuje kolem kurzoru, ")
                + Str.t("Ctrl+0 zobrazí celý projekt, Ctrl+T posune na dnešek, Ctrl+G na vybraný úkol. ")
                + Str.t("Modrá = úkol, červená = kritický úkol, černý pruh = souhrn, kosočtverec = milník, zelená šipka = termín, ")
                + Str.t("šedý pruh = základní plán, červená svislá čára = dnešek, modrá = datum stavu. ")
                + Str.t("Zobrazit → Obarvit pruhy podle obarví úkoly podle zdroje, úrovně osnovy nebo vlastního pole ")
                + Str.t("a pod diagram doplní legendu (kritické úkoly si nechají červený obrys).<br><br>")
                + Str.t("<b>Export</b><br>Ctrl+E uloží zobrazené úkoly do CSV (otevře se v Excelu), Ctrl+Shift+P uloží diagram jako PNG.<br><br>")
                + Str.t("Aplikace soubory pouze čte a nikam je neodesílá.")
                + "</body></html>";
        JOptionPane.showMessageDialog(parent, text, Str.t("Ovládání"), JOptionPane.PLAIN_MESSAGE);
    }

    static void showAbout(Component parent) {
        String text = "<html><body style='" + Theme.HTML_BODY + ";width:420px'>"
                + "<div style='font-size:14pt'>" + MainWindow.APP_NAME_HTML + " " + MainWindow.VERSION + "</div>"
                + Str.t("Prohlížeč souborů Microsoft Project (pouze pro čtení).<br>")
                + Str.t("Autor: ") + MainWindow.AUTHOR + Str.t(". Freeware – zdarma k použití i šíření v nezměněné podobě (Nápověda → Licence…).<br><br>")
                + Str.t("Čtení souborů zajišťuje knihovna <b>MPXJ</b> (https://www.mpxj.org, licence LGPL 2.1+). ")
                + Str.t("Licence všech knihoven třetích stran: Nápověda → Licence… (a složka <b>licenses</b> vedle programu).<br>")
                + Str.t("Běhové prostředí: Java ") + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ").<br><br>"
                + Str.t("Tato aplikace není produktem společnosti Microsoft. Microsoft Project je ochranná známka Microsoft Corporation.")
                + "</body></html>";
        JOptionPane.showMessageDialog(parent, text, Str.t("O aplikaci"), JOptionPane.PLAIN_MESSAGE, new ImageIcon(Icons.app(64)));
    }
}
