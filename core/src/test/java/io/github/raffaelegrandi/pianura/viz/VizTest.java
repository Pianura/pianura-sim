package io.github.raffaelegrandi.pianura.viz;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.units.ControllerRobot;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

class VizTest {

    @Test
    void shapesAreVisibleOnlyAfterPublish() {
        Viz v = new Viz(null);
        v.layer("plan").line(0, 0, 1, 1).point(2, 2);
        assertTrue(v.snapshot().isEmpty(), "nothing published yet");
        v.publish();
        assertEquals(2, v.snapshot().get("plan").size());
        v.layer("plan").clear();
        assertEquals(2, v.snapshot().get("plan").size(), "the view keeps the last published frame");
        v.publish();
        assertTrue(v.snapshot().get("plan").isEmpty());
    }

    @Test
    void colorAndWidthApplyToTheFollowingShapes() {
        Viz v = new Viz(null);
        v.layer("a").point(0, 0).color(Color.BLUE).width(3).line(0, 0, 1, 0).color(10, 20, 30, 40).rect(1, 1, 1, 1);
        v.publish();
        List<Shape> s = v.snapshot().get("a");
        assertEquals(VizLayer.DEFAULT_COLOR, s.get(0).color());
        assertEquals(Color.BLUE, s.get(1).color());
        assertEquals(3f, s.get(1).width());
        assertEquals(new Color(10, 20, 30, 40), s.get(2).color());
        assertEquals(Shape.Kind.RECT, s.get(2).kind());
        assertEquals(4, s.get(2).size());
    }

    @Test
    void layersKeepTheirOrderAndTheDefaultLayerHasAName() {
        Viz v = new Viz(null);
        v.layer("b").point(0, 0);
        v.layer("a").point(0, 0);
        v.layer(null).point(0, 0);
        v.publish();
        assertEquals(List.of("b", "a", Viz.DEFAULT_LAYER), List.copyOf(v.snapshot().keySet()));
    }

    @Test
    void polylinesAreInterleavedAndValidated() {
        Viz v = new Viz(null);
        v.layer("p").polyline(new double[] { 1, 2, 3 }, new double[] { 4, 5, 6 });
        v.publish();
        Shape s = v.snapshot().get("p").get(0);
        assertArrayEquals(new double[] { 1, 4, 2, 5, 3, 6 }, s.coords());
        assertThrows(IllegalArgumentException.class, () -> v.layer("p").polygon(new double[] { 1, 2 }, new double[] { 1 }));
        assertThrows(IllegalArgumentException.class, () -> v.layer("p").line(0, 0, Double.NaN, 1));
    }

    @Test
    void theSnapshotCannotBeChangedFromOutside() {
        Viz v = new Viz(null);
        v.layer("p").point(1, 1);
        v.publish();
        Map<String, List<Shape>> snap = v.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snap.get("p").clear());
        assertThrows(UnsupportedOperationException.class, () -> snap.clear());
    }

    @Test
    void tooManyShapesAreDroppedWithOneWarning() {
        List<String> warnings = new ArrayList<>();
        Viz v = new Viz(warnings::add);
        VizLayer l = v.layer("map");
        for (int i = 0; i < Viz.MAX_SHAPES + 10; i++) l.point(i, 0);
        assertEquals(Viz.MAX_SHAPES, l.size());
        assertEquals(1, warnings.size());
        l.clear();
        assertEquals(0, v.shapeCount());
        l.point(0, 0);
        assertEquals(1, l.size(), "room again after clear()");
    }

    @Test
    @Timeout(20)
    void aRobotDrawsDuringTheSimulationAndTheViewSeesEachStep() {
        RobotDummy r = new ControllerRobot(1, new Position(10, 10, 0), 2, 0.1, (rb, t) -> {
            Position p = rb.getPosition();
            rb.viz("now").clear().disc(p.x, p.y, 0.5).text(p.x, p.y - 1, String.format("t=%.1f", t));
            rb.viz("trail").point(p.x, p.y);
            rb.setRobotSpeed(1, 0);
        });
        assertTrue(r.getDrawings().isEmpty(), "no drawings before the robot draws");
        SimClock clock = new SimClock(Const.physicsStep);
        SimEngine e = new SimEngine(clock, new CollisionDetector(List.of()), List.of(r));
        e.setLogger(m -> {});
        r.attachClock(clock);
        r.start();
        e.runUntil(1.0);
        e.stop();
        Map<String, List<Shape>> d = r.getDrawings();
        assertEquals(2, d.get("now").size());
        assertEquals("t=1.0", d.get("now").get(1).text());
        assertEquals(11, d.get("trail").size(), "one point per control step, t = 0 .. 1");
        assertEquals(11.0, d.get("now").get(0).c(0), 1e-6, "the disc follows the robot");
    }
}
