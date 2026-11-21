// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.start;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * What the start screen remembers between runs: the examples and scene files opened last, the
 * favourites, and the folder of "my scenes". Kept in the user's preferences (nothing is written
 * next to the program); a store in memory serves the tests.
 */
public final class StartStore {

    /** An example of the program, or a scene file on disk. */
    public enum Kind { EXAMPLE, SCENE }

    /** Something that can be opened: an example by name, a scene by its absolute path. */
    public record Item(Kind kind, String id) {
        public static Item example(String name) { return new Item(Kind.EXAMPLE, name); }

        public static Item scene(Path file) { return new Item(Kind.SCENE, file.toAbsolutePath().normalize().toString()); }

        /** The scene's file (only for scenes). */
        public Path file() { return Path.of(id); }
    }

    /** Where the values are kept. */
    public interface Backend {
        String get(String key);

        void put(String key, String value);
    }

    /** Most recent items kept. */
    public static final int MAX_RECENT = 12;
    /** Most favourites kept. */
    public static final int MAX_FAVOURITES = 40;

    private static StartStore user;
    private final Backend backend;

    public StartStore(Backend backend) { this.backend = backend; }

    /** A store that lives in memory only. */
    public static StartStore inMemory() {
        Map<String, String> m = new HashMap<>();
        return new StartStore(new Backend() {
            @Override public String get(String key) { return m.get(key); }
            @Override public void put(String key, String value) { m.put(key, value); }
        });
    }

    /** The user's store, in the Java preferences. If they cannot be used, it lasts for this run only. */
    public static synchronized StartStore user() {
        if (user == null) {
            StartStore s;
            try {
                Preferences p = Preferences.userRoot().node("io/github/raffaelegrandi/pianura/start");
                s = new StartStore(new Backend() {
                    @Override public String get(String key) { return p.get(key, null); }
                    @Override public void put(String key, String value) {
                        try {
                            if (value == null) p.remove(key); else p.put(key, value);
                            p.flush();
                        } catch (Exception e) { /* preferences not writable: keep going without them */ }
                    }
                });
            } catch (Exception | Error e) {
                s = inMemory();
            }
            user = s;
        }
        return user;
    }

    // ------------------------------------------------------------------ recent

    /** The items opened last, most recent first. */
    public synchronized List<Item> recent() { return read("recent"); }

    /** Records that the item was opened now: it goes to the top of the recent ones. */
    public synchronized void touch(Item item) {
        List<Item> l = read("recent");
        l.remove(item);
        l.add(0, item);
        while (l.size() > MAX_RECENT) l.remove(l.size() - 1);
        write("recent", l);
    }

    /** Forgets an item (e.g. a scene file that no longer exists). */
    public synchronized void forget(Item item) {
        List<Item> l = read("recent");
        if (l.remove(item)) write("recent", l);
        l = read("favourites");
        if (l.remove(item)) write("favourites", l);
    }

    // ------------------------------------------------------------------ favourites

    /** The favourites, in the order they were chosen. */
    public synchronized List<Item> favourites() { return read("favourites"); }

    public synchronized boolean isFavourite(Item item) { return read("favourites").contains(item); }

    public synchronized void setFavourite(Item item, boolean on) {
        List<Item> l = read("favourites");
        l.remove(item);
        if (on) l.add(item);
        while (l.size() > MAX_FAVOURITES) l.remove(0);
        write("favourites", l);
    }

    // ------------------------------------------------------------------ my scenes

    /** The folder shown under "My scenes", or null if none was chosen yet. */
    public synchronized Path scenesFolder() {
        String s = backend.get("scenesFolder");
        return s == null || s.isBlank() ? null : Path.of(s);
    }

    public synchronized void setScenesFolder(Path folder) {
        backend.put("scenesFolder", folder == null ? null : folder.toAbsolutePath().normalize().toString());
    }

    // ------------------------------------------------------------------ storage: one item per line, "E name" or "S path"

    private List<Item> read(String key) {
        List<Item> out = new ArrayList<>();
        String s = backend.get(key);
        if (s == null) return out;
        for (String line : s.split("\n")) {
            if (line.length() < 3) continue;
            Kind k = line.charAt(0) == 'E' ? Kind.EXAMPLE : line.charAt(0) == 'S' ? Kind.SCENE : null;
            if (k != null) out.add(new Item(k, line.substring(2)));
        }
        return out;
    }

    private void write(String key, List<Item> items) {
        StringBuilder b = new StringBuilder();
        for (Item i : items) {
            String line = (i.kind() == Kind.EXAMPLE ? "E " : "S ") + i.id().replace("\n", " ");
            if (b.length() + line.length() + 1 > 8000) break;        // a preference value holds 8192 characters
            if (b.length() > 0) b.append('\n');
            b.append(line);
        }
        backend.put(key, b.toString());
    }
}
