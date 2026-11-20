package io.github.raffaelegrandi.pianura.inspect;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.units.ControllerRobot;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

class InspectorTest {

    private double now = 0;

    @Test
    void stateChangesAreLoggedWithTheirTime() {
        Inspector in = new Inspector(() -> now);
        in.state("searching");
        now = 2.5;
        in.state("searching");          // same state: nothing new
        in.state("following");
        Inspector.Snapshot s = in.snapshot();
        assertEquals("following", s.state());
        assertEquals(2.5, s.stateSince());
        assertEquals(List.of(new Inspector.Entry(0, "state: searching"), new Inspector.Entry(2.5, "state: following")), s.log());
    }

    @Test
    void watchedValuesKeepTheirOrderAndAreFormatted() {
        Inspector in = new Inspector(() -> now);
        in.watch("nodes", 12).watch("error", 0.123456789).watch("ratio", 2.0).watch("mode", "fast").watch("almost", 1.99999);
        in.watch("nodes", 13);
        var w = in.snapshot().watched();
        assertEquals(List.of("nodes", "error", "ratio", "mode", "almost"), List.copyOf(w.keySet()));
        assertEquals("13", w.get("nodes"));
        assertEquals("0.1235", w.get("error"));
        assertEquals("2", w.get("ratio"));
        assertEquals("2", w.get("almost"), "rounded to 4 decimals, no trailing point");
    }

    @Test
    void plotsAreTimeSeriesWithBoundedMemory() {
        Inspector in = new Inspector(() -> now);
        for (int i = 0; i < Inspector.MAX_POINTS + 10; i++) {
            now = i * 0.1;
            in.plot("e", i);
        }
        Inspector.Series s = in.snapshot().series().get(0);
        assertEquals("e", s.name());
        assertEquals(Inspector.MAX_POINTS, s.size());
        assertEquals(10, s.v()[0], "the oldest points are dropped");
        assertEquals(Inspector.MAX_POINTS + 9, s.last());
        assertEquals(1.0, s.t()[0], 1e-9);
    }

    @Test
    void theLogIsBoundedToo() {
        Inspector in = new Inspector(() -> now);
        for (int i = 0; i < Inspector.MAX_LOG + 5; i++) in.log("line " + i);
        List<Inspector.Entry> log = in.snapshot().log();
        assertEquals(Inspector.MAX_LOG, log.size());
        assertEquals("line 5", log.get(0).text());
    }

    @Test
    void theVersionMovesAtEveryWrite() {
        Inspector in = new Inspector(() -> now);
        long v0 = in.version();
        in.plot("x", 1);
        assertTrue(in.version() > v0);
        long v1 = in.version();
        in.snapshot();
        assertEquals(v1, in.version(), "reading does not change it");
    }

    @Test
    @Timeout(20)
    void aRobotCanPauseTheSimulation() throws Exception {
        RobotDummy r = new ControllerRobot(1, new Position(10, 10, 0), 2, 0.1, (rb, t) -> {
            rb.inspector().plot("t", t);
            if (Math.abs(t - 0.5) < 1e-9) rb.pauseSimulation("half a second");
        });
        SimClock clock = new SimClock(Const.physicsStep);
        SimEngine e = new SimEngine(clock, new CollisionDetector(List.of()), List.of(r));
        StringBuilder logged = new StringBuilder();
        e.setLogger(logged::append);
        r.attachClock(clock);
        r.start();
        e.start();
        e.setSpeed(SimEngine.MAX_SPEED);
        e.resume();
        long t0 = System.currentTimeMillis();
        while (!e.isPaused() && System.currentTimeMillis() - t0 < 10_000) Thread.sleep(5);
        Thread.sleep(100);
        assertTrue(e.isPaused());
        assertEquals(0.5, clock.now(), 0.02, "paused right after the robot asked");
        assertTrue(logged.toString().contains("Paused by robot #1 at t = 0.5"), logged.toString());
        assertTrue(r.inspector().snapshot().log().stream().anyMatch(en -> en.text().contains("half a second")));
        e.stop();
    }
}
