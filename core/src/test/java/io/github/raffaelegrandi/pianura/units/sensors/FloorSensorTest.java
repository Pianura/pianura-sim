package io.github.raffaelegrandi.pianura.units.sensors;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

class FloorSensorTest {

    /** White floor with a black vertical stripe from x = 292 to x = 308 world px (16 px wide ≈ 1 cell). */
    private static final FloorStyle LINE = (g, w, h, cell) -> {
        double k = cell / Const.cellSize;               // image pixels per world pixel
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.fill(new Rectangle2D.Double(292 * k, 0, 16 * k, h));
    };

    private static RobotDummy robot(double xPx, double yPx, double theta) {
        return new RobotDummy(1, new Position(xPx / Const.cellSize, yPx / Const.cellSize, theta), 2) {
            @Override public void executeLoop() {}
        };
    }

    /** One sensing step without moving anything. */
    private static SimEngine sense(RobotDummy r, FloorStyle floor) {
        SimEngine e = new SimEngine(new SimClock(Const.physicsStep), new CollisionDetector(List.of()), List.of(r));
        e.setLogger(m -> {});
        e.setFloor(floor);
        e.runUntil(0);
        return e;
    }

    @Test
    void plainFloorsReadWhiteAndBlack() {
        RobotDummy r = robot(300, 300, 0);
        sense(r, FloorStyle.plain(Color.WHITE));
        assertArrayEquals(new int[] { 255, 255 }, r.getFloorGrays());
        sense(r, FloorStyle.plain(Color.BLACK));
        assertArrayEquals(new int[] { 0, 0 }, r.getFloorGrays());
        assertEquals(Color.BLACK, r.getFloorColor(0));
    }

    @Test
    void lineUnderLeftSensorOnly() {
        // robot facing up (θ = −90°) with its centre on the right edge of the line:
        // left sensor (−20°) at x ≈ 303 → on the line; right (+20°) at x ≈ 313 → white
        RobotDummy r = robot(308, 300, -Math.PI / 2);
        sense(r, LINE);
        assertTrue(r.getFloorGray(0) < 20, "sinistro dovrebbe vedere nero: " + r.getFloorGray(0));
        assertTrue(r.getFloorGray(1) > 235, "destro dovrebbe vedere bianco: " + r.getFloorGray(1));
        Point2D left = r.getUnitObject().getFloorSensors().getPosition(0);
        assertEquals(308 - 0.97 * 15 * Math.sin(Math.toRadians(20)), left.getX() * Const.cellSize, 1e-6);
    }

    @Test
    void sensorsAreSymmetricOnCenteredLine() {
        RobotDummy r = robot(300, 300, -Math.PI / 2);
        sense(r, LINE);
        assertEquals(r.getFloorGray(0), r.getFloorGray(1));   // both on the line
        assertTrue(r.getFloorGray(0) < 20);
    }

    @Test
    void footprintGivesGradualSignalOnTheEdge() {
        // single sensor exactly on the line's edge (x = 308): half black, half white
        RobotDummy r = robot(300, 300, 0);
        r.configureFloorSensors(8.0 / 15, 0.2, 0);           // 8 px ahead of the centre, to the right (θ = 0); footprint 0.2 cells = 3 px
        sense(r, LINE);
        int g = r.getFloorGray(0);
        assertTrue(g > 90 && g < 165, "the value on the edge should be in between: " + g);
        // point footprint: reads a single pixel, so black or white
        r.configureFloorSensors(8.0 / 15, 0, 0);
        sense(r, LINE);
        int p = r.getFloorGray(0);
        assertTrue(p == 0 || p == 255, "zero footprint: a single pixel, read " + p);
    }

    @Test
    void layoutIsConfigurable() {
        RobotDummy r = robot(300, 300, 0);
        r.configureFloorSensors(0.5, 0.1, -90, 0, 90);
        sense(r, FloorStyle.plain(Color.WHITE));
        assertEquals(3, r.getFloorSensorCount());
        Point2D left = r.getUnitObject().getFloorSensors().getPosition(0);
        assertEquals(300, left.getX() * Const.cellSize, 1e-6);
        assertEquals(300 - 7.5, left.getY() * Const.cellSize, 1e-6);   // −90° = the left of someone facing +x
    }

    @Test
    void changingFloorAtRuntimeUpdatesReadings() {
        RobotDummy r = robot(300, 300, 0);
        SimEngine e = sense(r, FloorStyle.plain(Color.WHITE));
        e.setFloor(FloorStyle.plain(new Color(200, 0, 0)));
        assertEquals(new Color(200, 0, 0), r.getFloorColor(0));
        assertEquals(FloorMap.gray(0xC80000), r.getFloorGray(1));
    }

}
