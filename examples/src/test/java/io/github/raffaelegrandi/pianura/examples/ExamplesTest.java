package io.github.raffaelegrandi.pianura.examples;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.examples.braitenberg.BraitenbergScenario;
import io.github.raffaelegrandi.pianura.examples.obstacles.ObstaclesScenario;
import io.github.raffaelegrandi.pianura.algorithms.iravoidance.IrAvoider;
import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Tests that run the example code on the simulator without GUI (moved here from the core
 * tests when the examples became a module of their own).
 */
class ExamplesTest {

    /** Creates clock + engine, connects and starts the robots (engine not started yet). */
    private static SimEngine setup(List<RobotDummy> robots, List<Obstacle> obstacles) {
        SimClock clock = new SimClock(Const.physicsStep);
        SimEngine engine = new SimEngine(clock, new CollisionDetector(obstacles), robots);
        engine.setLogger(m -> {});
        for (RobotDummy r : robots) r.attachClock(clock);
        return engine;
    }

    private static void startRobots(List<RobotDummy> robots) {
        for (RobotDummy r : robots) r.start();
    }

    private static List<Obstacle> someObstacles() {
        List<Obstacle> obs = new ArrayList<>();
        obs.add(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.BLUE, new Position(30, 25, Math.toRadians(10)), new Dimension(7, 7)));
        obs.add(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(22, 17, 0), new Dimension(4, 4)));
        obs.add(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.RED, new Position(30, 37, Math.toRadians(30)), new Dimension(2, 15)));
        return obs;
    }

    private static List<double[]> runSwarm(double seconds) {
        List<RobotDummy> robots = new ArrayList<>();
        robots.add(new IrAvoider(1, new Position(7, 17, Math.toRadians(90)), 2));
        robots.add(new IrAvoider(2, new Position(10, 20, 0), 2));
        robots.add(new IrAvoider(3, new Position(17, 19, 0), 2));
        robots.add(new IrAvoider(4, new Position(30, 18, 0), 2));
        robots.add(new IrAvoider(5, new Position(21, 30, 0), 2));
        SimEngine engine = setup(robots, someObstacles());
        startRobots(robots);
        engine.runUntil(seconds);
        engine.stop();
        List<double[]> poses = new ArrayList<>();
        for (RobotDummy r : robots) {
            Position p = r.getPosition();
            poses.add(new double[] { p.x, p.y, p.theta });
        }
        return poses;
    }

    @Test
    @Timeout(60)
    void sameScenarioGivesSameResult() {
        List<double[]> a = runSwarm(30);
        List<double[]> b = runSwarm(30);
        for (int i = 0; i < a.size(); i++) {
            assertArrayEquals(a.get(i), b.get(i), 0.0, "robot " + (i + 1) + " differs between two runs");
        }
    }

    @Test
    void scenarioTest04OverlapsAreFound() {
        List<String> p = new CollisionDetector(new ObstaclesScenario().getObstacles()).validateObstacles();
        p.forEach(System.out::println);
        // the scenario keeps its original mistakes on purpose, to show the warnings
        assertTrue(p.stream().anyMatch(s -> s.contains("white oval") && s.contains("overlap")), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.contains("yellow rectangle") && s.contains("outside the arena")), p.toString());
    }

    @Test
    @Timeout(60)
    void braitenbergVehiclesMoveAndRarelyTouch() {
        BraitenbergScenario sc = new BraitenbergScenario();
        List<RobotDummy> robots = sc.getRobots();
        SimClock clock = new SimClock(Const.physicsStep);
        SimEngine engine = new SimEngine(clock,
            new CollisionDetector(sc.getObstacles(), sc.getArenaWidthPx(), sc.getArenaHeightPx()), robots);
        engine.setLogger(m -> {});
        for (RobotDummy r : robots) r.attachClock(clock);
        startRobots(robots);
        int n = robots.size(), samples = 0;
        int[] contact = new int[n];
        double[] path = new double[n];
        Position[] last = new Position[n];
        for (int i = 0; i < n; i++) last[i] = robots.get(i).getPosition();
        for (int k = 1; k <= 600; k++) {                 // 60 s, sampled every 0.1 s
            engine.runUntil(k * 0.1);
            samples++;
            for (int i = 0; i < n; i++) {
                RobotDummy r = robots.get(i);
                if (r.hasCollision()) contact[i]++;
                Position p = r.getPosition();
                path[i] += Math.hypot(p.x - last[i].x, p.y - last[i].y);
                last[i] = p;
            }
        }
        engine.stop();
        for (int i = 0; i < n; i++) {
            assertTrue(path[i] > 40, "vehicle " + (i + 1) + " moved only " + path[i] + " cells");
            assertTrue(contact[i] < samples * 0.05, "vehicle " + (i + 1) + " in contact " + contact[i] + "/" + samples);
        }
    }


    @Test
    void theExamplesShipTheModernLook() {
        assertTrue(io.github.raffaelegrandi.pianura.gui.LookAndFeelSetup.flatLafAvailable(),
            "FlatLaf is a runtime dependency of the examples (optional for core)");
    }

    @Test
    @Timeout(60)
    void theRrtPlannerFindsAPathAndReachesTheGoalWithoutContacts() {
        io.github.raffaelegrandi.pianura.examples.planning.PlanningScenario sc = new io.github.raffaelegrandi.pianura.examples.planning.PlanningScenario();
        SimClock clock = new SimClock(Const.physicsStep);
        SimEngine e = new SimEngine(clock, new CollisionDetector(sc.getObstacles(), sc.getArenaWidthPx(), sc.getArenaHeightPx()), sc.getRobots());
        e.setLogger(m -> {});
        RobotDummy r = sc.getRobots().get(0);
        r.attachClock(clock);
        r.start();
        int contacts = 0;
        double t = 0;
        while (!sc.getPlanner().arrived() && t < 90) {
            t += 0.1;
            e.runUntil(t);
            if (r.hasCollision()) contacts++;
        }
        e.stop();
        assertTrue(sc.getPlanner().arrived(), "goal not reached in 90 s");
        assertEquals(0, contacts, "the planned path keeps clear of the obstacles");
        assertEquals(io.github.raffaelegrandi.pianura.examples.planning.PlanningScenario.GOAL_X, r.getPosition().x, 0.35);
        assertEquals(io.github.raffaelegrandi.pianura.examples.planning.PlanningScenario.GOAL_Y, r.getPosition().y, 0.35);
        var snap = r.inspector().snapshot();
        assertEquals("arrived", snap.state());
        assertTrue(snap.log().stream().anyMatch(en -> en.text().startsWith("path found after")));
        assertTrue(snap.series().stream().anyMatch(se -> se.name().equals("heading error (rad)")));
        assertFalse(r.getDrawings().get("tree").isEmpty());
        assertEquals(1, r.getDrawings().get("path").size());
    }
}
