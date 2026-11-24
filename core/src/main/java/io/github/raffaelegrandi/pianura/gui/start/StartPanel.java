// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.start;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;

import io.github.raffaelegrandi.pianura.gui.Icons;
import io.github.raffaelegrandi.pianura.gui.LookAndFeelSetup;
import io.github.raffaelegrandi.pianura.structures.SceneFile;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;

/**
 * The start screen: what to open. On the left, a new scene or a scene file; on the right four
 * sections that open one at a time, like the drawers of a cabinet: Recent (what was opened
 * last, so the work in progress is one click away), Favourites, Examples, My scenes (the scene
 * files of a folder). A card shows a small picture, a name and a line of text; its star adds it
 * to the favourites.
 */
@SuppressWarnings("serial")
public class StartPanel extends JPanel {

    /** An example of the program: its name, a line about it, a short tag, and how to build it for the picture. */
    public record Example(String name, String description, String tag, Supplier<ScenarioManager> preview) {}

    /** What the screen asks the program to do. */
    public interface Actions {
        void openExample(String name);

        void openScene(Path file);

        void newScene();
    }

    /** The sections, in the order they are shown. */
    public enum Section {
        RECENT("Recent"), FAVOURITES("Favourites"), EXAMPLES("Examples"), MY_SCENES("My scenes");

        final String title;

        Section(String title) { this.title = title; }
    }

    private static final int CARD_W = 208, THUMB_H = 112, CARD_H = THUMB_H + 66;

    private final StartStore store;
    private final List<Example> examples;
    private final Actions actions;
    private final Map<Section, JButton> headers = new EnumMap<>(Section.class);
    private final Map<Section, JComponent> contents = new EnumMap<>(Section.class);
    private final JPanel accordion = new JPanel(new GridBagLayout());
    private final Map<StartStore.Item, BufferedImage> thumbs = new HashMap<>();
    private final Map<StartStore.Item, String> sceneTexts = new HashMap<>();
    private final ArrayDeque<Card> pending = new ArrayDeque<>();
    private final Timer painter = new Timer(30, e -> renderNext());
    private Section open;

    public StartPanel(StartStore store, List<Example> examples, Actions actions, String version) {
        this.store = store;
        this.examples = List.copyOf(examples);
        this.actions = actions;
        setLayout(new BorderLayout());
        add(buildSide(version), BorderLayout.WEST);
        accordion.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        add(accordion, BorderLayout.CENTER);
        for (Section s : Section.values()) {
            JButton h = new JButton();
            h.setHorizontalAlignment(SwingConstants.LEFT);
            h.setFocusable(false);
            h.setIconTextGap(10);
            h.setMargin(new Insets(8, 10, 8, 10));
            h.setFont(h.getFont().deriveFont(15f));
            h.addActionListener(e -> open(s));
            headers.put(s, h);
        }
        reload();
    }

    private JComponent buildSide(String version) {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY), BorderFactory.createEmptyBorder(22, 18, 18, 18)));
        JLabel title = new JLabel("Pianura Sim");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        JLabel ver = new JLabel(version == null ? " " : version);
        ver.setForeground(muted());
        JButton fresh = new JButton("New empty scene", Icons.of(Icons.Kind.NEW, 18));
        JButton file = new JButton("Open a scene file…", Icons.of(Icons.Kind.OPEN, 18));
        fresh.setToolTipText("An empty arena with one robot: add obstacles, save it, come back to it");
        file.setToolTipText("A scene saved with Save: obstacles and robot positions");
        fresh.addActionListener(e -> actions.newScene());
        file.addActionListener(e -> chooseSceneFile());
        JLabel hint = new JLabel("<html><div style='width:128px'>Robots can also be driven from outside, "
            + "by Python, MATLAB or any program, over UDP: see the examples remote and brains.</div></html>");
        hint.setForeground(muted());
        for (JComponent c : List.of(title, ver, fresh, file, hint)) c.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JButton b : List.of(fresh, file)) {
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setFocusable(false);
            b.setMargin(new Insets(8, 10, 8, 10));
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height + 4));
        }
        side.add(title);
        side.add(ver);
        side.add(Box.createVerticalStrut(22));
        side.add(fresh);
        side.add(Box.createVerticalStrut(8));
        side.add(file);
        side.add(Box.createVerticalGlue());
        side.add(hint);
        side.setPreferredSize(new Dimension(212, 400));
        return side;
    }

    private void chooseSceneFile() {
        Path dir = store.scenesFolder();
        JFileChooser fc = new JFileChooser(dir == null ? null : dir.toFile());
        fc.setDialogTitle("Open scene");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Pianura scenes (*.json)", "json"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path f = fc.getSelectedFile().toPath();
        if (store.scenesFolder() == null && f.getParent() != null) store.setScenesFolder(f.getParent());
        actions.openScene(f);
    }

    // ------------------------------------------------------------------ sections

    /** Reads the store again and rebuilds the sections (call it when the screen comes back). */
    public final void reload() {
        pending.clear();
        List<StartStore.Item> recent = store.recent(), favourites = store.favourites();
        contents.put(Section.RECENT, cards(recent, "Nothing opened yet: pick an example, or start a new scene."));
        contents.put(Section.FAVOURITES, cards(favourites, "Click the star of an example or of a scene to keep it here."));
        List<StartStore.Item> ex = new ArrayList<>();
        for (Example e : examples) ex.add(StartStore.Item.example(e.name()));
        contents.put(Section.EXAMPLES, cards(ex, "No examples."));
        contents.put(Section.MY_SCENES, myScenes());
        setCount(Section.RECENT, recent.size());
        setCount(Section.FAVOURITES, favourites.size());
        setCount(Section.EXAMPLES, ex.size());
        if (open == null) open = !recent.isEmpty() ? Section.RECENT : !favourites.isEmpty() ? Section.FAVOURITES : Section.EXAMPLES;
        layoutAccordion();
        if (!pending.isEmpty()) painter.start();
    }

    private void setCount(Section s, int n) {
        headers.get(s).putClientProperty("count", n);
    }

    /** Opens a section and closes the others. */
    public void open(Section s) {
        open = s;
        layoutAccordion();
    }

    public Section getOpenSection() { return open; }

    /** The cards of a section, in order (for the tests and for keyboard users). */
    public List<String> cardTitles(Section s) {
        List<String> out = new ArrayList<>();
        collect(contents.get(s), out);
        return out;
    }

    private static void collect(Component c, List<String> out) {
        if (c instanceof Card k) out.add(k.title);
        if (c instanceof Container cc) for (Component x : cc.getComponents()) collect(x, out);
    }

    private void layoutAccordion() {
        accordion.removeAll();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.fill = GridBagConstraints.BOTH;
        g.weightx = 1;
        int row = 0;
        for (Section s : Section.values()) {
            JButton h = headers.get(s);
            boolean isOpen = s == open;
            Object n = h.getClientProperty("count");
            h.setIcon(Icons.of(isOpen ? Icons.Kind.CHEVRON_DOWN : Icons.Kind.CHEVRON_RIGHT, 16));
            h.setText(s.title + (n instanceof Integer i && i > 0 ? "   " + i : ""));
            h.setFont(h.getFont().deriveFont(isOpen ? Font.BOLD : Font.PLAIN));
            h.getAccessibleContext().setAccessibleDescription(isOpen ? "open" : "closed");
            g.gridy = row++;
            g.weighty = 0;
            g.insets = new Insets(row == 1 ? 0 : 6, 0, 0, 0);
            accordion.add(h, g);
            if (isOpen) {
                g.gridy = row++;
                g.weighty = 1;
                g.insets = new Insets(6, 0, 4, 0);
                accordion.add(contents.get(s), g);
            }
        }
        accordion.revalidate();
        accordion.repaint();
    }

    private JComponent cards(List<StartStore.Item> items, String empty) {
        if (items.isEmpty()) return emptyNote(empty);
        WrapPanel p = new WrapPanel();
        for (StartStore.Item it : items) {
            Card c = card(it);
            if (c != null) p.add(c);
        }
        return scroll(p);
    }

    private JComponent emptyNote(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(muted());
        l.setVerticalAlignment(SwingConstants.TOP);
        l.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        return l;
    }

    private static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        return sp;
    }

    private JComponent myScenes() {
        Path dir = store.scenesFolder();
        JPanel p = new JPanel(new BorderLayout(0, 6));
        JPanel top = new JPanel(new BorderLayout(10, 0));
        JLabel where = new JLabel(dir == null ? "Choose the folder where you keep your scenes." : dir.toString());
        where.setForeground(muted());
        JButton choose = new JButton(dir == null ? "Choose a folder…" : "Change folder…");
        choose.setFocusable(false);
        choose.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(dir == null ? null : dir.toFile());
            fc.setDialogTitle("Folder of my scenes");
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            store.setScenesFolder(fc.getSelectedFile().toPath());
            reload();
        });
        top.add(where, BorderLayout.CENTER);
        top.add(choose, BorderLayout.EAST);
        top.setBorder(BorderFactory.createEmptyBorder(2, 12, 2, 2));
        p.add(top, BorderLayout.NORTH);
        List<StartStore.Item> files = new ArrayList<>();
        if (dir != null && Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted((a, b) -> Long.compare(modified(b), modified(a)))
                    .limit(60).forEach(f -> files.add(StartStore.Item.scene(f)));
            } catch (java.io.IOException e) { /* unreadable folder: shown as empty */ }
        }
        setCount(Section.MY_SCENES, files.size());
        p.add(dir == null ? emptyNote(" ") : cards(files, "No scene files (*.json) in this folder yet: Save one from the toolbar."), BorderLayout.CENTER);
        return p;
    }

    private static long modified(Path f) {
        try { return Files.getLastModifiedTime(f).toMillis(); } catch (java.io.IOException e) { return 0; }
    }

    // ------------------------------------------------------------------ cards

    private Card card(StartStore.Item it) {
        if (it.kind() == StartStore.Kind.EXAMPLE) {
            Example ex = null;
            for (Example e : examples) if (e.name().equals(it.id())) ex = e;
            if (ex == null) return null;        // an example of another version of the program
            Example e = ex;
            Card c = new Card(it, e.name(), e.description(), e.tag(), () -> actions.openExample(e.name()));
            c.render = e.preview() == null ? null : () -> Thumbnails.ofScenario(e.preview().get(), CARD_W, THUMB_H);
            enqueue(c);
            return c;
        }
        Path f = it.file();
        boolean there = Files.isRegularFile(f);
        String folder = f.getParent() == null ? "" : f.getParent().toString();
        Card c = new Card(it, f.getFileName().toString(), there ? sceneTexts.getOrDefault(it, folder) : "File not found: click to forget it",
            "scene", there ? () -> actions.openScene(f) : () -> { store.forget(it); reload(); });
        c.available = there;
        if (there) {
            c.render = () -> {
                try {
                    SceneFile.Scene s = SceneFile.read(f);
                    sceneTexts.put(it, String.format(Locale.ROOT, "%s × %s cells · %d obstacles · %d robots",
                        num(s.arenaWidth()), num(s.arenaHeight()), s.obstacles().size(), s.robots().size()));
                    return Thumbnails.ofScene(s, CARD_W, THUMB_H);
                } catch (Exception ex) {
                    sceneTexts.put(it, "Not a Pianura scene");
                    return null;
                }
            };
            enqueue(c);
        }
        return c;
    }

    private static String num(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    private void enqueue(Card c) {
        BufferedImage img = thumbs.get(c.item);
        if (img != null) { c.thumb = img; return; }
        if (c.render != null) pending.add(c);
    }

    /** Draws one picture per tick, so the screen appears at once and fills in. */
    private void renderNext() {
        Card c = pending.poll();
        if (c == null) { painter.stop(); return; }
        try {
            BufferedImage img = thumbs.get(c.item);
            if (img == null) {
                img = c.render.get();
                if (img != null) thumbs.put(c.item, img);
            }
            c.thumb = img;
            if (c.item.kind() == StartStore.Kind.SCENE && sceneTexts.containsKey(c.item)) c.text = sceneTexts.get(c.item);
        } catch (RuntimeException ex) {
            c.thumb = null;      // no picture: the card still works
        }
        c.repaint();
    }

    private Color muted() {
        Color fg = UIManager.getColor("Label.foreground"), bg = UIManager.getColor("Panel.background");
        if (fg == null) fg = Color.DARK_GRAY;
        if (bg == null) bg = Color.WHITE;
        return new Color((fg.getRed() * 3 + bg.getRed() * 2) / 5, (fg.getGreen() * 3 + bg.getGreen() * 2) / 5, (fg.getBlue() * 3 + bg.getBlue() * 2) / 5);
    }

    /** One thing to open: picture, name, a line of text, a tag, and its star. */
    private final class Card extends JComponent {
        final StartStore.Item item;
        final String title, tag;
        final Runnable action;
        String text;
        BufferedImage thumb;
        Supplier<BufferedImage> render;
        boolean available = true, hover;
        private final JButton star = new JButton();

        Card(StartStore.Item item, String title, String text, String tag, Runnable action) {
            this.item = item;
            this.title = title;
            this.text = text;
            this.tag = tag;
            this.action = action;
            setPreferredSize(new Dimension(CARD_W, CARD_H));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Open " + title);
            setFocusable(true);
            getAccessibleContext().setAccessibleName(title);
            setLayout(null);
            star.setFocusable(false);
            star.setContentAreaFilled(false);
            star.setBorderPainted(false);
            star.setMargin(new Insets(0, 0, 0, 0));
            star.setBounds(CARD_W - 30, THUMB_H + 6, 24, 24);
            star.addActionListener(e -> {
                store.setFavourite(item, !store.isFavourite(item));
                syncStar();
                contents.put(Section.FAVOURITES, cards(store.favourites(), "Click the star of an example or of a scene to keep it here."));
                setCount(Section.FAVOURITES, store.favourites().size());
                layoutAccordion();
                if (!pending.isEmpty()) painter.start();
            });
            add(star);
            syncStar();
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { if (e.getButton() == MouseEvent.BUTTON1) action.run(); }
            });
            addKeyListener(new java.awt.event.KeyAdapter() {
                @Override public void keyPressed(java.awt.event.KeyEvent e) {
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER || e.getKeyCode() == java.awt.event.KeyEvent.VK_SPACE) action.run();
                }
            });
        }

        @Override
        public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() {
                    @Override public javax.accessibility.AccessibleRole getAccessibleRole() { return javax.accessibility.AccessibleRole.PUSH_BUTTON; }
                };
            }
            return accessibleContext;
        }

        private void syncStar() {
            boolean fav = store.isFavourite(item);
            star.setIcon(Icons.of(fav ? Icons.Kind.STAR_FILLED : Icons.Kind.STAR, 18, fav ? new Color(0xD99A00) : muted()));
            star.setToolTipText(fav ? "Remove from the favourites" : "Add to the favourites");
            star.getAccessibleContext().setAccessibleName(star.getToolTipText());
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.foreground"), bg = UIManager.getColor("TextField.background");
            if (fg == null) fg = Color.DARK_GRAY;
            if (bg == null) bg = Color.WHITE;
            int w = getWidth(), h = getHeight();
            RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 12, 12);
            g.setColor(bg);
            g.fill(box);
            Graphics2D gi = (Graphics2D) g.create();
            gi.clip(box);
            if (thumb != null) {
                gi.drawImage(thumb, 0, 0, w, THUMB_H, null);
            } else {
                gi.setColor(new Color(0x3A3F47));
                gi.fillRect(0, 0, w, THUMB_H);
            }
            gi.dispose();
            if (tag != null && !tag.isEmpty()) {
                Font tf = getFont().deriveFont(11f);
                g.setFont(tf);
                FontMetrics fm = g.getFontMetrics();
                int tw = fm.stringWidth(tag) + 14;
                g.setColor(new Color(255, 255, 255, 235));
                g.fill(new RoundRectangle2D.Double(w - tw - 6, 6, tw, 18, 18, 18));
                g.setColor(new Color(0x30363D));
                g.drawString(tag, w - tw - 6 + 7, 6 + 13);
            }
            g.setColor(hover || isFocusOwner() ? LookAndFeelSetup.accentColor() : new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 70));
            g.setStroke(new BasicStroke(hover || isFocusOwner() ? 2f : 1f));
            g.draw(box);

            g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g.setColor(available ? fg : muted());
            g.drawString(clip(title, g.getFontMetrics(), w - 44), 10, THUMB_H + 22);
            g.setFont(getFont().deriveFont(11.5f));
            g.setColor(muted());
            FontMetrics fm = g.getFontMetrics();
            int y = THUMB_H + 40;
            for (String line : wrap(text == null ? "" : text, fm, w - 20, 2)) {
                g.drawString(line, 10, y);
                y += fm.getHeight();
            }
            g.dispose();
        }
    }

    private static String clip(String s, FontMetrics fm, int w) {
        if (fm.stringWidth(s) <= w) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** Breaks a text at the spaces into at most {@code max} lines; the last one is clipped. */
    static List<String> wrap(String text, FontMetrics fm, int w, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = text.split(" ");
        for (int i = 0; i < words.length; i++) {
            String next = line.length() == 0 ? words[i] : line + " " + words[i];
            if (fm.stringWidth(next) <= w || line.length() == 0) {
                line.setLength(0);
                line.append(next);
            } else if (out.size() == max - 1) {
                StringBuilder rest = new StringBuilder(line);
                for (int k = i; k < words.length; k++) rest.append(' ').append(words[k]);
                out.add(clip(rest.toString(), fm, w));
                return out;
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(words[i]);
            }
        }
        if (line.length() > 0) out.add(out.size() == max - 1 ? clip(line.toString(), fm, w) : line.toString());
        return out;
    }

    /** Cards from left to right, going to a new row at the end of the width; as wide as its scroll pane. */
    private static final class WrapPanel extends JPanel implements Scrollable {
        private static final int GAP = 14;

        WrapPanel() {
            setLayout(new LayoutManager() {
                @Override public void addLayoutComponent(String name, Component comp) {}
                @Override public void removeLayoutComponent(Component comp) {}
                @Override public Dimension preferredLayoutSize(Container parent) { return arrange(parent, false); }
                @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(CARD_W + 2 * GAP, CARD_H + 2 * GAP); }
                @Override public void layoutContainer(Container parent) { arrange(parent, true); }
            });
        }

        private Dimension arrange(Container parent, boolean place) {
            int width = parent.getWidth() > 0 ? parent.getWidth() : 3 * (CARD_W + GAP) + GAP;
            int x = GAP, y = GAP, rowH = 0;
            for (Component c : parent.getComponents()) {
                Dimension d = c.getPreferredSize();
                if (x > GAP && x + d.width + GAP > width) { x = GAP; y += rowH + GAP; rowH = 0; }
                if (place) c.setBounds(x, y, d.width, d.height);
                x += d.width + GAP;
                rowH = Math.max(rowH, d.height);
            }
            return new Dimension(width, y + rowH + GAP);
        }

        @Override
        public void setBounds(int x, int y, int w, int h) {
            boolean widthChanged = w != getWidth();
            super.setBounds(x, y, w, h);
            if (widthChanged) revalidate();          // the rows depend on the width
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return CARD_H + GAP; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
