// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.start;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.start.StartPanel.Section;
import io.github.raffaelegrandi.pianura.gui.start.StartStore.Item;
import io.github.raffaelegrandi.pianura.structures.SceneFile;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.utils.Position;

/** The start screen: what it remembers, its sections and its pictures (no screen needed). */
class StartScreenTest {

    private static ScenarioManager scenario() {
        ScenarioManager sc = new ScenarioManager() {};
        sc.addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.BLUE, new Position(10, 10, 0), 6, 3));
        sc.addRobot(new RobotDummy(1, new Position(20, 20, 0), 2) { @Override public void executeLoop() {} });
        return sc;
    }

    private static final List<StartPanel.Example> EXAMPLES = List.of(
        new StartPanel.Example("alpha", "The first example", "basics", StartScreenTest::scenario),
        new StartPanel.Example("beta", "The second example", "UDP", null));

    private static class Actions implements StartPanel.Actions {
        final List<String> log = new ArrayList<>();
        @Override public void openExample(String name) { log.add("example " + name); }
        @Override public void openScene(Path file) { log.add("scene " + file.getFileName()); }
        @Override public void newScene() { log.add("new"); }
    }

    private static StartPanel panel(StartStore store, Actions actions) throws Exception {
        StartPanel[] p = new StartPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            p[0] = new StartPanel(store, EXAMPLES, actions, "test");
            p[0].setSize(1000, 700);
        });
        return p[0];
    }

    @Test
    void theStoreKeepsTheMostRecentFirstAndNoDuplicates() {
        StartStore s = StartStore.inMemory();
        s.touch(Item.example("a"));
        s.touch(Item.example("b"));
        s.touch(Item.example("a"));
        assertEquals(List.of(Item.example("a"), Item.example("b")), s.recent());
        for (int i = 0; i < StartStore.MAX_RECENT + 5; i++) s.touch(Item.example("x" + i));
        assertEquals(StartStore.MAX_RECENT, s.recent().size());
        assertEquals(Item.example("x" + (StartStore.MAX_RECENT + 4)), s.recent().get(0));
    }

    @Test
    void favouritesScenesFolderAndForgetting(@TempDir Path dir) {
        StartStore s = StartStore.inMemory();
        Item scene = Item.scene(dir.resolve("my scene.json"));
        assertEquals(StartStore.Kind.SCENE, scene.kind());
        assertEquals(dir.resolve("my scene.json").toAbsolutePath().normalize(), scene.file());
        s.setFavourite(scene, true);
        s.setFavourite(Item.example("a"), true);
        s.setFavourite(scene, true);                       // twice: still once
        assertEquals(List.of(Item.example("a"), scene), s.favourites());
        assertTrue(s.isFavourite(scene));
        s.touch(scene);
        s.forget(scene);
        assertTrue(s.recent().isEmpty());
        assertEquals(List.of(Item.example("a")), s.favourites());
        assertNull(s.scenesFolder());
        s.setScenesFolder(dir);
        assertEquals(dir.toAbsolutePath().normalize(), s.scenesFolder());
    }

    @Test
    void theFirstTimeTheExamplesAreOpenThenTheRecentOnes() throws Exception {
        StartStore store = StartStore.inMemory();
        StartPanel p = panel(store, new Actions());
        assertEquals(Section.EXAMPLES, p.getOpenSection(), "nothing recent yet: show the examples");
        assertEquals(List.of("alpha", "beta"), p.cardTitles(Section.EXAMPLES));
        assertTrue(p.cardTitles(Section.RECENT).isEmpty());

        store.touch(Item.example("beta"));
        StartPanel q = panel(store, new Actions());
        assertEquals(Section.RECENT, q.getOpenSection(), "the work in progress comes first");
        assertEquals(List.of("beta"), q.cardTitles(Section.RECENT));
    }

    @Test
    void oneSectionIsOpenAtATime() throws Exception {
        StartPanel p = panel(StartStore.inMemory(), new Actions());
        for (Section s : Section.values()) {
            SwingUtilities.invokeAndWait(() -> p.open(s));
            assertEquals(s, p.getOpenSection());
            // the accordion holds the four headers and the content of the open section only
            java.awt.Container accordion = (java.awt.Container) p.getComponent(1);
            assertEquals(Section.values().length + 1, accordion.getComponentCount());
        }
    }

    @Test
    void aCardOpensItsExampleAndItsStarAddsItToTheFavourites() throws Exception {
        StartStore store = StartStore.inMemory();
        Actions actions = new Actions();
        StartPanel p = panel(store, actions);
        java.awt.Component card = find(p, "Open alpha");
        SwingUtilities.invokeAndWait(() -> card.dispatchEvent(new java.awt.event.MouseEvent(card, java.awt.event.MouseEvent.MOUSE_CLICKED,
            0, 0, 20, 20, 1, false, java.awt.event.MouseEvent.BUTTON1)));
        assertEquals(List.of("example alpha"), actions.log);
        javax.swing.JButton star = (javax.swing.JButton) ((java.awt.Container) card).getComponent(0);
        assertEquals("Add to the favourites", star.getToolTipText());
        SwingUtilities.invokeAndWait(star::doClick);
        assertEquals(List.of(Item.example("alpha")), store.favourites());
        assertEquals(List.of("alpha"), p.cardTitles(Section.FAVOURITES));
        assertEquals("Remove from the favourites", star.getToolTipText());
    }

    @Test
    void aSceneFileThatIsGoneIsForgottenWhenClicked(@TempDir Path dir) throws Exception {
        StartStore store = StartStore.inMemory();
        Path there = dir.resolve("there.json"), gone = dir.resolve("gone.json");
        ScenarioManager sc = scenario();
        sc.saveScene(there);
        store.touch(Item.scene(gone));
        store.touch(Item.scene(there));
        store.setScenesFolder(dir);
        Actions actions = new Actions();
        StartPanel p = panel(store, actions);
        assertEquals(List.of("there.json", "gone.json"), p.cardTitles(Section.RECENT));
        assertEquals(List.of("there.json"), p.cardTitles(Section.MY_SCENES), "the scene files of the folder");
        java.awt.Component goneCard = find(p, "Open gone.json");
        SwingUtilities.invokeAndWait(() -> goneCard.dispatchEvent(new java.awt.event.MouseEvent(goneCard, java.awt.event.MouseEvent.MOUSE_CLICKED,
            0, 0, 20, 20, 1, false, java.awt.event.MouseEvent.BUTTON1)));
        assertTrue(actions.log.isEmpty(), "nothing to open");
        assertEquals(List.of(Item.scene(there)), store.recent());
        assertEquals(List.of("there.json"), p.cardTitles(Section.RECENT));
    }

    @Test
    void thePicturesShowTheScene(@TempDir Path dir) throws Exception {
        ScenarioManager sc = scenario();
        BufferedImage a = Thumbnails.ofScenario(sc, 208, 112);
        assertEquals(208, a.getWidth());
        assertTrue(colours(a) > 3, "floor, obstacle, robot and the surround: " + colours(a));
        Path f = dir.resolve("s.json");
        sc.saveScene(f);
        BufferedImage b = Thumbnails.ofScene(SceneFile.read(f), 208, 112);
        Set<Integer> seen = new HashSet<>();
        for (int y = 0; y < b.getHeight(); y++) for (int x = 0; x < b.getWidth(); x++) seen.add(b.getRGB(x, y));
        assertTrue(seen.contains(Color.BLUE.getRGB()), "the obstacle in its colour");
    }

    private static int colours(BufferedImage img) {
        Set<Integer> seen = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y += 2) for (int x = 0; x < img.getWidth(); x += 2) seen.add(img.getRGB(x, y));
        return seen.size();
    }

    private static java.awt.Component find(java.awt.Container c, String tooltip) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof javax.swing.JComponent j && tooltip.equals(j.getToolTipText()) && k.getClass().getSimpleName().equals("Card")) return k;
            if (k instanceof java.awt.Container cc) { java.awt.Component r = find(cc, tooltip); if (r != null) return r; }
        }
        return null;
    }
}
