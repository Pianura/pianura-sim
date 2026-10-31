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
import io.github.raffaelegrandi.pianura.examples.scenario04.ScenarioTest04;
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

    /** Crea clock+motore, collega e avvia i robot (motore non ancora avviato). */
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
        obs.add(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.BLUE, new Position(30, 25, 10), new Dimension(7, 7)));
        obs.add(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(22, 17, 0), new Dimension(4, 4)));
        obs.add(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.RED, new Position(30, 37, 30), new Dimension(2, 15)));
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
            assertArrayEquals(a.get(i), b.get(i), 0.0, "robot " + (i + 1) + " diverso tra due esecuzioni");
        }
    }

    @Test
    void scenarioTest04OverlapsAreFound() {
        List<String> p = new CollisionDetector(new ScenarioTest04().getObstacles()).validateObstacles();
        p.forEach(System.out::println);
        assertTrue(p.stream().anyMatch(s -> s.contains("white rectangle") && s.contains("red rectangle")
            && s.contains("overlap")), p.toString());
    }
}
