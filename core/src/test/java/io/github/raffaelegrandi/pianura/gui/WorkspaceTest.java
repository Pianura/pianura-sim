// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.view.StateTimeline;
import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/** The window's views, its icon buttons and the tools that build the scene (no screen needed). */
class WorkspaceTest {

    private static class Fixture {
        final ScenarioManager scenario = new ScenarioManager() {};
        final SimEngine engine;
        final SimPanel panel;

        Fixture() throws Exception {
            scenario.addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.BLUE, new Position(10, 10, 0), 4, 2));
            scenario.addRobot(new RobotDummy(1, new Position(20, 20, 0), 2) { @Override public void executeLoop() {} });
            SimClock clock = new SimClock(Const.physicsStep);
            for (RobotDummy r : scenario.getRobots()) r.attachClock(clock);
            engine = new SimEngine(clock, new CollisionDetector(scenario.getObstacles(), scenario.getArenaWidthPx(), scenario.getArenaHeightPx()),
                scenario.getRobots());
            engine.setFloor(scenario.getFloor());
            SimPanel[] p = new SimPanel[1];
            SwingUtilities.invokeAndWait(() -> {
                p[0] = new SimPanel("", scenario, engine);
                p[0].setSize(1100, 800);
                p[0].doLayout();
            });
            panel = p[0];
        }

        void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
    }

    private static List<AbstractButton> buttons(Container c, List<AbstractButton> out) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton b) out.add(b);
            if (k instanceof Container cc) buttons(cc, out);
        }
        return out;
    }

    @Test
    void theFocusViewLeavesOnlyTheArenaAndComesBack() throws Exception {
        Fixture f = new Fixture();
        assertEquals(WorkspaceView.STUDIO, f.panel.getView());
        assertTrue(f.panel.getControlBar().isVisible());
        f.edt(() -> f.panel.setView(WorkspaceView.FOCUS));
        assertEquals(WorkspaceView.FOCUS, f.panel.getView());
        assertFalse(f.panel.getControlBar().isVisible(), "no toolbar in the Focus view");
        for (Component c : f.panel.getComponents())
            assertEquals(c == f.panel.getArenaView(), c.isVisible(), "only the arena is shown: " + c.getClass().getSimpleName());
        long floating = java.util.Arrays.stream(f.panel.getArenaView().getComponents()).filter(Component::isVisible).count();
        assertTrue(floating >= 3, "run, time and layers float over the arena: " + floating);
        f.edt(() -> f.panel.setView(WorkspaceView.STUDIO));
        assertTrue(f.panel.getControlBar().isVisible());
        assertEquals(0, java.util.Arrays.stream(f.panel.getArenaView().getComponents()).filter(Component::isVisible).count());
    }

    @Test
    void everyIconButtonSaysWhatItDoes() throws Exception {
        Fixture f = new Fixture();
        List<AbstractButton> bs = buttons(f.panel, new ArrayList<>());
        assertTrue(bs.size() >= 15, "toolbar, tool rail and floating controls: " + bs.size());
        for (AbstractButton b : bs) {
            if (b.getText() != null && !b.getText().isEmpty()) continue;       // a button with its name written on it
            if (b.getClass().getName().contains("plaf") || b.getIcon() == null && b.getToolTipText() == null) continue;   // arrows of combos and scroll bars
            assertNotNull(b.getToolTipText(), "an icon needs a tooltip");
            assertFalse(b.getToolTipText().isBlank());
        }
    }

    @Test
    void everyIconDrawsSomething() {
        for (Icons.Kind k : Icons.Kind.values()) {
            BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
            Icons.of(k, 24, Color.BLACK).paintIcon(null, img.getGraphics(), 0, 0);
            int painted = 0;
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) if ((img.getRGB(x, y) >>> 24) > 0) painted++;
            assertTrue(painted > 20, k + " paints " + painted + " pixels");
        }
    }

    @Test
    void obstaclesAreAddedAndDeletedOnlyWhileEditable() throws Exception {
        Fixture f = new Fixture();
        f.edt(() -> f.panel.addObstacle(ArenaObjectType.OBSTACLE_OVAL));
        assertEquals(2, f.scenario.getObstacles().size());
        assertEquals(1, f.panel.getArenaView().getSelectedObstacle(), "the new obstacle is selected, ready to be moved");
        Obstacle added = f.scenario.getObstacles().get(1);
        assertEquals(ArenaObjectType.OBSTACLE_OVAL, added.getID().getType());
        f.edt(f.panel::deleteSelectedObstacle);
        assertEquals(1, f.scenario.getObstacles().size());
        assertFalse(f.scenario.getObstacles().contains(added));

        f.engine.start();
        f.engine.resume();
        try {
            f.edt(() -> f.panel.addObstacle(ArenaObjectType.OBSTACLE_RECT));
            assertEquals(1, f.scenario.getObstacles().size(), "not while the simulation runs");
        } finally {
            f.engine.stop();
        }
    }

    @Test
    void theTimelineGivesALaneToEveryRobotWithAState() throws Exception {
        Fixture f = new Fixture();
        RobotDummy r = f.scenario.getRobots().get(0);
        StateTimeline tl = new StateTimeline(f.scenario.getRobots(), () -> 10, x -> {});
        tl.refresh();
        assertEquals(0, tl.laneCount());
        r.inspector().state("searching").state("following").state("searching");
        tl.refresh();
        assertEquals(1, tl.laneCount());
        assertEquals(List.of("searching", "following", "searching"), r.inspector().states().stream().map(e -> e.text()).toList());
        assertNotNull(tl.colorOf("searching"));
        assertNotEquals(tl.colorOf("searching"), tl.colorOf("following"));
        assertNull(tl.colorOf("arrived"));
    }
}
