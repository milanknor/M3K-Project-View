package cz.msproject.viewer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Kontrola nové verze: stáhne textový soubor (1. řádek = číslo verze, 2. řádek = adresa
 * ke stažení) a porovná s {@link MainWindow#VERSION}. Nic jiného se neodesílá ani nestahuje.
 */
public final class UpdateCheck {
    /** Soubor s aktuální verzí (repozitář musí být veřejný, jinak kontrola skončí chybou 404). */
    public static final String URL = "https://raw.githubusercontent.com/milanknor/M3K-Project-View/main/version.txt";
    public static final String RELEASES = "https://github.com/milanknor/M3K-Project-View/releases";

    public record Result(String latest, String downloadUrl, boolean newer) {
    }

    private UpdateCheck() {
    }

    public static Result check() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(10))
                .header("User-Agent", MainWindow.APP_NAME + "/" + MainWindow.VERSION).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode());
        }
        return parse(resp.body());
    }

    static Result parse(String body) throws IOException {
        String[] lines = body.strip().split("\\R");
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new IOException("Prázdná odpověď.");
        }
        String latest = lines[0].strip();
        String url = lines.length > 1 && !lines[1].isBlank() ? lines[1].strip() : RELEASES;
        return new Result(latest, url, compare(latest, MainWindow.VERSION) > 0);
    }

    /** Porovnání verzí „a.b.c“ po číslech; nečíselné části se ignorují. */
    static int compare(String a, String b) {
        String[] pa = a.split("[.\\-]");
        String[] pb = b.split("[.\\-]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length ? num(pa[i]) : 0;
            int y = i < pb.length ? num(pb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
