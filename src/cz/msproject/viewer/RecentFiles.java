package cz.msproject.viewer;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** Seznam nedávných souborů uložený v Preferences (recent0…recentN); neplatné položky se tiše přeskočí. */
final class RecentFiles {
    static final int MAX = 8;

    private final Preferences prefs;

    RecentFiles(Preferences prefs) {
        this.prefs = prefs;
    }

    List<Path> list() {
        List<Path> list = new ArrayList<>();
        for (int i = 0; i < MAX; i++) {
            String v = prefs.get("recent" + i, null);
            if (v == null || v.isBlank()) {
                continue;
            }
            try {
                list.add(Path.of(v));
            } catch (InvalidPathException ignored) {
                // poškozená položka – při dalším uložení zmizí
            }
        }
        return list;
    }

    void add(Path path) {
        List<Path> list = list();
        Path abs = absolute(path);
        list.removeIf(p -> absolute(p).equals(abs));
        list.add(0, abs);
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
        store(list);
    }

    void remove(Path path) {
        List<Path> list = list();
        Path abs = absolute(path);
        list.removeIf(p -> absolute(p).equals(abs));
        store(list);
    }

    void clear() {
        store(new ArrayList<>());
    }

    private static Path absolute(Path p) {
        try {
            return p.toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return p;
        }
    }

    private void store(List<Path> list) {
        for (int i = 0; i < MAX; i++) {
            if (i < list.size()) {
                prefs.put("recent" + i, absolute(list.get(i)).toString());
            } else {
                prefs.remove("recent" + i);
            }
        }
    }
}
