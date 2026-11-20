// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.units;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/** Units of the robot API: lengths in cells, wheel speeds in cells/s, a declared scale in meters per cell. */
class UnitsTest {

    private static SimEngine run(List<RobotDummy> robots, List<Obstacle> obstacles, double seconds) {
        SimClock clock = new SimClock(Const.physicsStep);
        SimEngine engine = new SimEngine(clock, new CollisionDetector(obstacles), robots);
        engine.setLogger(m -> {});
        for (RobotDummy r : robots) { r.attachClock(clock); r.start(); }
        engine.runUntil(seconds);
        engine.stop();
        return engine;
    }

    @Test
    @Timeout(20)
    void equalWheelSpeedsGoStraightAtThatSpeed() {
        RobotDummy r = new ControllerRobot(1, new Position(10, 20, 0), 2, 0.1, (rb, t) -> rb.setWheelSpeeds(1.5, 1.5));
        run(List.of(r), List.of(), 2.0);
        assertEquals(13.0, r.getPosition().x, 1e-6, "1.5 cells/s for 2 s");
        assertEquals(20.0, r.getPosition().y, 1e-6);
    }

    @Test
    @Timeout(20)
    void oppositeWheelSpeedsTurnOnTheSpot() {
        // left +1, right -1 cells/s on a 2-cell wheel base: w = (1 - (-1)) / 2 = 1 rad/s, clockwise on screen
        RobotDummy r = new ControllerRobot(1, new Position(10, 20, 0), 2, 0.1, (rb, t) -> rb.setWheelSpeeds(1, -1));
        run(List.of(r), List.of(), 1.0);
        assertEquals(10.0, r.getPosition().x, 1e-6);
        assertEquals(20.0, r.getPosition().y, 1e-6);
        assertEquals(1.0, r.getPosition().theta, 1e-6);
        assertEquals(0, r.getLinearSpeed(), 1e-12);
        assertEquals(1.0, r.getAngularSpeed(), 1e-12);
    }

    @Test
    @Timeout(20)
    void wheelTravelMatchesWheelSpeeds() {
        RobotDummy r = new ControllerRobot(1, new Position(20, 20, 0), 2, 0.1, (rb, t) -> rb.setWheelSpeeds(0.5, 1.0));
        run(List.of(r), List.of(), 2.0);
        assertEquals(1.0, r.getLeftWheelTravel(), 1e-6);
        assertEquals(2.0, r.getRightWheelTravel(), 1e-6);
    }

    @Test
    void forwardAndBackwardAreInCellsPerSecond() {
        RobotDummy r = new RobotDummy(1, new Position(10, 10, 0), 2) { @Override public void executeLoop() {} };
        r.forward(0.8);
        assertEquals(0.8, r.getLinearSpeed(), 0);
        assertEquals(0, r.getAngularSpeed(), 0);
        r.backward(0.8);
        assertEquals(-0.8, r.getLinearSpeed(), 0);
    }

    @Test
    void irReadsCellsFromTheBodyAndSaturatesAtTheRange() {
        // wall face 3 cells ahead of the centre: 2 cells from the robot's surface
        Obstacle wall = new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.GRAY, new Position(13.5, 10, 0), 1, 6);
        RobotDummy r = new RobotDummy(1, new Position(10, 10, 0), 3) { @Override public void executeLoop() {} };
        new CollisionDetector(List.of(wall)).testUnitForCollisions(r.getUnitObject());
        assertEquals(3.0, r.getIrRange());
        assertEquals(18, r.getIrCount());
        assertEquals(2.0, r.getIr(0), 1e-9, "front sensor");
        assertEquals(r.getIr(0), r.getIr()[0]);
        assertEquals(3.0, r.getIr(9), "rear sensor: nothing in range, reads the range");
        assertFalse(r.hasCollision());
    }

    @Test
    void geometryIsDeclaredInCells() {
        RobotDummy r = new RobotDummy(1, new Position(10, 10, 0), 2) { @Override public void executeLoop() {} };
        assertEquals(1.0, r.getBodyRadius());
        assertEquals(2.0, r.getWheelBase());
        assertEquals(0.25, r.getWheelRadius());
        assertEquals(Const.DEFAULT_METERS_PER_CELL, r.getMetersPerCell());
    }

    @Test
    void scaleGoesFromTheEngineToTheRobots() {
        RobotDummy r = new RobotDummy(1, new Position(10, 10, 0), 2) { @Override public void executeLoop() {} };
        SimEngine engine = new SimEngine(new SimClock(Const.physicsStep), new CollisionDetector(List.of()), List.of(r));
        engine.setMetersPerCell(0.1);
        assertEquals(0.1, engine.getMetersPerCell());
        assertEquals(0.1, r.getMetersPerCell());
        assertThrows(IllegalArgumentException.class, () -> engine.setMetersPerCell(0));
    }

    @Test
    void scenarioScaleDefaultsToFiveCentimetres() {
        ScenarioManager s = new ScenarioManager();
        assertEquals(0.05, s.getMetersPerCell());
        s.setMetersPerCell(0.025);
        assertEquals(0.025, s.getMetersPerCell());
        assertThrows(IllegalArgumentException.class, () -> s.setMetersPerCell(-1));
    }

}
