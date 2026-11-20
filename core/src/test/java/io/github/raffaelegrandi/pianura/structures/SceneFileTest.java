// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.structures;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.units.ControllerRobot;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

class SceneFileTest {

    private static RobotDummy robot(int id, double x, double y, double theta) {
        return new RobotDummy(id, new Position(x, y, theta), 2) { @Override public void executeLoop() {} };
    }

    @Test
    void aSceneSurvivesTheRoundTrip(@TempDir Path dir) throws Exception {
        Obstacle wall = new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.GRAY, new Position(20, 12, 0.25), 6.5, 2);
        Obstacle pond = new Obstacle(ArenaObjectType.OBSTACLE_OVAL, new Color(40, 80, 200, 128), new Position(8, 30, 0), 4, 3)
            .withBorder(Color.BLACK, 2.5);
        Path f = dir.resolve("scene.json");
        SceneFile.write(f, 50, 30, 0.1, List.of(wall, pond), List.of(robot(1, 5, 5, 0), robot(7, 10.125, 20, -1.5)));

        SceneFile.Scene s = SceneFile.read(f);
        assertEquals(50, s.arenaWidth());
        assertEquals(30, s.arenaHeight());
        assertEquals(0.1, s.metersPerCell());
        assertEquals(2, s.obstacles().size());
        Obstacle w = s.obstacles().get(0), p = s.obstacles().get(1);
        assertEquals(ArenaObjectType.OBSTACLE_RECT, w.getID().getType());
        assertEquals(20, w.getID().getPos().x);
        assertEquals(0.25, w.getID().getPos().theta);
        assertEquals(6.5, w.getID().getWidth());
        assertEquals(Color.GRAY, w.getID().getColor());
        assertNull(w.getCustomBorderColor(), "automatic border stays automatic");
        assertEquals(ArenaObjectType.OBSTACLE_OVAL, p.getID().getType());
        assertEquals(new Color(40, 80, 200, 128), p.getID().getColor(), "alpha is kept");
        assertEquals(Color.BLACK, p.getCustomBorderColor());
        assertEquals(2.5, p.getBorderWidth());
        assertEquals(List.of(new SceneFile.RobotPose(1, 5, 5, 0), new SceneFile.RobotPose(7, 10.125, 20, -1.5)), s.robots());
    }

    @Test
    void theFileIsReadableByHand() {
        String json = SceneFile.toJson(40, 40, 0.05,
            List.of(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.GRAY, new Position(20, 12, 0), 6, 2)),
            List.of(robot(1, 5, 5, 0)));
        assertTrue(json.contains("\"arena\": {\"width\": 40, \"height\": 40, \"meters_per_cell\": 0.05}"), json);
        assertTrue(json.contains("{\"shape\": \"rect\", \"x\": 20, \"y\": 12, \"theta\": 0, \"width\": 6, \"height\": 2, \"color\": \"#808080\"}"), json);
        assertTrue(json.contains("{\"id\": 1, \"x\": 5, \"y\": 5, \"theta\": 0}"), json);
    }

    @Test
    void optionalFieldsHaveDefaults() {
        SceneFile.Scene s = SceneFile.parse("""
            {"pianura_scene": 1, "arena": {"width": 40, "height": 40},
             "obstacles": [{"x": 3, "y": 4, "width": 1, "height": 2}]}
            """);
        assertEquals(Const.DEFAULT_METERS_PER_CELL, s.metersPerCell());
        Obstacle o = s.obstacles().get(0);
        assertEquals(ArenaObjectType.OBSTACLE_RECT, o.getID().getType());
        assertEquals(0, o.getID().getPos().theta);
        assertEquals(Color.GRAY, o.getID().getColor());
        assertTrue(s.robots().isEmpty());
    }

    @Test
    void errorsSayWhatAndWhere() {
        assertTrue(msg("{\"arena\": {}}").contains("not a Pianura scene"));
        assertTrue(msg("{\"pianura_scene\": 2, \"arena\": {\"width\": 1, \"height\": 1}}").contains("newer"));
        assertTrue(msg("{\"pianura_scene\": 1, \"arena\": {\"width\": 40}}").contains("\"height\" must be a number"));
        assertTrue(msg("{\"pianura_scene\": 1, \"arena\": {\"width\": 40, \"height\": 40}, \"obstacles\": [{\"shape\": \"star\", \"x\": 1, \"y\": 1, \"width\": 1, \"height\": 1}]}")
            .contains("obstacles[0]: shape"));
        assertTrue(msg("{\"pianura_scene\": 1, \"arena\": {\"width\": 40, \"height\": 40}, \"robots\": [{\"id\": 1, \"x\": 1}]}")
            .contains("robots[0]: \"y\""));
        assertTrue(msg("{\"pianura_scene\": 1, \"arena\": {\"width\": 40, \"height\": 40}, \"obstacles\": [{\"x\": 1, \"y\": 1, \"width\": 1, \"height\": 1, \"color\": \"red\"}]}")
            .contains("#rrggbb"));
        assertTrue(msg("not json").contains("not a valid JSON"));
    }

    private static String msg(String json) {
        return assertThrows(IllegalArgumentException.class, () -> SceneFile.parse(json)).getMessage();
    }

    @Test
    void aScenarioLoadsASceneAndPlacesRobotsAddedBeforeAndAfter() throws Exception {
        String json = """
            {"pianura_scene": 1, "arena": {"width": 60, "height": 30, "meters_per_cell": 0.02},
             "obstacles": [{"shape": "oval", "x": 30, "y": 15, "width": 4, "height": 4, "color": "#ff0000"}],
             "robots": [{"id": 1, "x": 3, "y": 4, "theta": 1}, {"id": 2, "x": 50, "y": 20, "theta": 0}]}
            """;
        ScenarioManager sc = new ScenarioManager();
        sc.addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.GRAY, new Position(5, 5, 0), 1, 1));
        RobotDummy before = robot(1, 10, 10, 0);
        sc.addRobot(before);
        sc.loadScene(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        RobotDummy after = robot(2, 10, 10, 0), other = robot(3, 12, 12, 0);
        sc.addRobot(after);
        sc.addRobot(other);

        assertEquals(60, sc.getArenaWidth());
        assertEquals(30, sc.getArenaHeight());
        assertEquals(0.02, sc.getMetersPerCell());
        assertEquals(1, sc.getNumObstacles(), "the scene's obstacles replace the ones added before");
        assertEquals(ArenaObjectType.OBSTACLE_OVAL, sc.getObstacles().get(0).getID().getType());
        assertEquals(new Position(3, 4, 1).theta, before.getPosition().theta);
        assertEquals(3, before.getPosition().x);
        assertEquals(50, after.getPosition().x);
        assertEquals(12, other.getPosition().x, "not in the scene: keeps its position");
    }

    @Test
    void saveSceneWritesWhatLoadSceneReads(@TempDir Path dir) throws Exception {
        ScenarioManager a = new ScenarioManager();
        a.setArenaSize(30, 20);
        a.addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.ORANGE, new Position(10, 10, 0), 2, 3));
        a.addRobot(robot(4, 7, 8, 0.5));
        Path f = dir.resolve("a.json");
        a.saveScene(f);
        assertTrue(Files.readString(f).startsWith("{\n  \"pianura_scene\": 1"));

        ScenarioManager b = new ScenarioManager();
        b.addRobot(robot(4, 1, 1, 0));
        b.loadScene(f);
        assertEquals(30, b.getArenaWidth());
        assertEquals(Color.ORANGE, b.getObstacles().get(0).getID().getColor());
        assertEquals(7, b.getRobots().get(0).getPosition().x);
        assertEquals(0.5, b.getRobots().get(0).getPosition().theta);
    }

    @Test
    @Timeout(20)
    void theEngineReplacesTheSceneAndTheSensorsSeeItAtOnce() {
        RobotDummy r = new ControllerRobot(1, new Position(10, 10, 0), 3, 0.1, (rb, t) -> {});
        SimEngine e = new SimEngine(new SimClock(Const.physicsStep), new CollisionDetector(List.of()), List.of(r));
        assertEquals(3.0, r.getIr(0), "empty arena: nothing in range");

        Obstacle wall = new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.GRAY, new Position(16.5, 20, 0), 1, 6);
        e.replaceScene(List.of(wall), Map.of(1, new Position(13, 20, 0), 99, new Position(1, 1, 0)));
        assertEquals(13, r.getPosition().x);
        assertEquals(20, r.getPosition().y);
        assertEquals(2.0, r.getIr(0), 1e-9, "wall face 3 cells from the centre: 2 from the body");
        assertEquals(List.of(wall), e.getDetector().getObstacles());
        assertTrue(e.validate().isOk());
    }
}
