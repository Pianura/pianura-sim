package io.github.raffaelegrandi.pianura.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.RobotUnit;
import io.github.raffaelegrandi.pianura.units.UnitObject;
import io.github.raffaelegrandi.pianura.units.collisions.CircleShape;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;

/**
 * The simulation engine: a dedicated thread advances the world at a fixed step.
 *
 * At every step:
 *   1. it waits until every robot has finished "thinking" (SimClock's barrier);
 *   2. it moves the robots with their current commands (sliding contact response);
 *   3. it recomputes the sensors at the new positions;
 *   4. it advances time and wakes the robots whose instant has come.
 * Robots that wake up therefore find sensors already updated to the current instant.
 *
 * The engine does not depend on the GUI: it can also run without a window (e.g. in
 * tests, or for batches of experiments at full speed).
 *
 * Speed: 1.0 = real time, 2.0 = twice as fast, ..., {@link #MAX_SPEED} = as fast as possible.
 */
public final class SimEngine {

    public static final double MAX_SPEED = Double.POSITIVE_INFINITY;

    /** Longest wait (ms of real time) for a robot before reporting it as unsynchronised. */
    private static final long BARRIER_TIMEOUT_MS = 2000;
    /** Beyond this delay (s) behind real time, pacing realigns instead of "catching up". */
    private static final double MAX_LAG_S = 0.25;

    private final SimClock clock;
    private final CollisionDetector detector;
    private final List<? extends RobotDummy> robots;
    private final List<UnitObject> bodies = new ArrayList<>();

    private final ReentrantLock ctrlLock = new ReentrantLock();
    private final Condition ctrlChanged = ctrlLock.newCondition();
    private boolean paused = true;
    private long stepsToRun = 0;          // steps still to run, requested with step() while paused
    private double speed = 1.0;
    private boolean pacingReset = true;   // realign the real-time clock at the next iteration

    /** A physics step and a scene change (from the editor) never overlap. */
    private final ReentrantLock stepLock = new ReentrantLock();

    private volatile boolean running = false;
    private Thread thread;
    private Consumer<String> logger = System.out::println;
    private volatile io.github.raffaelegrandi.pianura.units.sensors.FloorMap floorMap;
    private final io.github.raffaelegrandi.pianura.comm.CommNetwork network;

    public SimEngine(SimClock clock, CollisionDetector detector, List<? extends RobotDummy> robots) {
        this.clock = clock;
        this.detector = detector;
        this.robots = robots;
        for (RobotUnit r : robots) bodies.add(r.getUnitObject());
        network = new io.github.raffaelegrandi.pianura.comm.CommNetwork(clock, detector, robots);
        for (RobotUnit r : robots) r.attachNetwork(network);
        for (RobotUnit r : robots) r.attachPauseHook(reason -> {
            if (!running) return;   // runUntil and tests: no pause
            logger.accept(String.format(java.util.Locale.ROOT, "Paused by robot #%d at t = %.2f s: %s", r.getIDnum(), clock.now(), reason));
            pause();
        });
        setFloor(io.github.raffaelegrandi.pianura.gui.floor.FloorStyle.checkerboard());
    }

    /**
     * Floor read by the floor sensors (and shown by the view). It can be changed while the
     * simulation runs: the floor sensors are recomputed at once.
     */
    public void setFloor(io.github.raffaelegrandi.pianura.gui.floor.FloorStyle style) {
        double w = detector.getArenaWidthPx(), h = detector.getArenaHeightPx();
        io.github.raffaelegrandi.pianura.units.sensors.FloorMap map = new io.github.raffaelegrandi.pianura.units.sensors.FloorMap(style, w, h,
            io.github.raffaelegrandi.pianura.units.sensors.FloorMap.defaultScale(w, h));
        io.github.raffaelegrandi.pianura.units.sensors.FloorMap old;
        stepLock.lock();
        try {
            old = floorMap;
            floorMap = map;
            if (running || thread != null) sense();
        } finally { stepLock.unlock(); }
        if (old != null) old.dispose();   // stops the background drawing of the old tiles
    }

    public io.github.raffaelegrandi.pianura.units.sensors.FloorMap getFloorMap() { return floorMap; }

    /** Radio network between the robots (graph, link events, message counters). */
    public io.github.raffaelegrandi.pianura.comm.CommNetwork getNetwork() { return network; }

    /** Radio channel of the scenario (range, line of sight, losses, latency). */
    public void setCommSettings(io.github.raffaelegrandi.pianura.comm.CommSettings s) {
        stepLock.lock();
        try { network.setSettings(s); network.updateGraph(clock.now()); } finally { stepLock.unlock(); }
    }

    public SimClock getClock() { return clock; }

    private volatile double metersPerCell = io.github.raffaelegrandi.pianura.utils.Const.DEFAULT_METERS_PER_CELL;

    /** Scale of the scenario (meters per cell), passed on to the robots. Lengths in the simulation stay in cells. */
    public void setMetersPerCell(double m) {
        if (!(m > 0)) throw new IllegalArgumentException("metersPerCell must be > 0");
        metersPerCell = m;
        for (RobotUnit r : robots) r.attachScale(m);
    }

    public double getMetersPerCell() { return metersPerCell; }

    public void setLogger(Consumer<String> logger) { this.logger = logger; }

    // ------------------------------------------------------------------
    //  Life cycle
    // ------------------------------------------------------------------

    /** Starts the engine's thread (paused: use resume() or step()). */
    public void start() {
        if (thread != null) return;
        running = true;
        sense();  // sensors valid at instant 0 already, before the robots read them
        network.updateGraph(clock.now());
        thread = Thread.ofPlatform().name("pianura-engine").daemon(true).start(this::loop);
    }

    /** Stops engine and robots (the robots in sleepSim receive SimulationStoppedException). */
    public void stop() {
        running = false;
        clock.stop();
        ctrlLock.lock();
        try { ctrlChanged.signalAll(); } finally { ctrlLock.unlock(); }
    }

    /** Waits for the engine's thread to end (useful in tests). */
    public void join() throws InterruptedException {
        if (thread != null) thread.join();
    }

    // ------------------------------------------------------------------
    //  Controls
    // ------------------------------------------------------------------

    public void pause() {
        ctrlLock.lock();
        try { paused = true; stepsToRun = 0; ctrlChanged.signalAll(); } finally { ctrlLock.unlock(); }
    }

    public void resume() {
        ctrlLock.lock();
        try { paused = false; pacingReset = true; ctrlChanged.signalAll(); } finally { ctrlLock.unlock(); }
    }

    /** While paused: runs {@code n} steps and pauses again. */
    public void step(int n) {
        ctrlLock.lock();
        try {
            if (paused) { stepsToRun += n; ctrlChanged.signalAll(); }
        } finally { ctrlLock.unlock(); }
    }

    /** While paused: advances {@code seconds} of simulated time. */
    public void stepTime(double seconds) {
        step((int) Math.max(1, Math.round(seconds / clock.getDt())));
    }

    public void setSpeed(double factor) {
        if (!(factor > 0)) throw new IllegalArgumentException("speed must be > 0");
        ctrlLock.lock();
        try { speed = factor; pacingReset = true; } finally { ctrlLock.unlock(); }
    }

    public double getSpeed() {
        ctrlLock.lock();
        try { return speed; } finally { ctrlLock.unlock(); }
    }

    public boolean isPaused() {
        ctrlLock.lock();
        try { return paused && stepsToRun == 0; } finally { ctrlLock.unlock(); }
    }

    public boolean isRunning() { return running; }

    /** The simulation was stopped with stop(): it cannot restart. */
    public boolean isStopped() { return clock.isStopped(); }

    /**
     * Runs the simulation in the calling thread up to instant {@code simTime}, at full
     * speed. Meant for experiments without GUI and for tests; do not combine it with start().
     */
    public void runUntil(double simTime) {
        running = true;
        sense();
        network.updateGraph(clock.now());
        while (running && clock.now() < simTime - 1e-9) doStep();
        // the robots woken at the final instant complete their cycle before returning:
        // the returned state is deterministic (no robot "halfway")
        clock.awaitParticipants(BARRIER_TIMEOUT_MS);
    }

    // ------------------------------------------------------------------
    //  Main loop
    // ------------------------------------------------------------------

    private void loop() {
        long wallAnchor = 0;
        double simAnchor = 0;
        while (running) {
            // --- pause / single step ---
            double currentSpeed;
            boolean reanchor;
            ctrlLock.lock();
            try {
                while (running && paused && stepsToRun == 0) ctrlChanged.awaitUninterruptibly();
                if (!running) break;
                boolean single = paused;          // paused with steps requested: no pacing
                if (single) stepsToRun--;
                currentSpeed = single ? MAX_SPEED : speed;
                reanchor = pacingReset || single;
                pacingReset = false;
            } finally { ctrlLock.unlock(); }

            if (reanchor) { wallAnchor = System.nanoTime(); simAnchor = clock.now(); }

            doStep();

            // --- pacing against real time ---
            if (currentSpeed != MAX_SPEED) {
                double simElapsed = clock.now() - simAnchor;
                long targetWall = wallAnchor + (long) (simElapsed / currentSpeed * 1e9);
                long delay = targetWall - System.nanoTime();
                if (delay > 0) {
                    sleepNanos(delay);
                } else if (-delay > MAX_LAG_S * 1e9) {
                    // too far behind (slow machine or debugger): restart from here instead of speeding up
                    wallAnchor = System.nanoTime();
                    simAnchor = clock.now();
                }
            }
        }
    }

    private void doStep() {
        stepLock.lock();
        try { doStepLocked(); } finally { stepLock.unlock(); }
    }

    private void doStepLocked() {
        // 1. barrier: every robot has finished deciding its commands
        for (String late : clock.awaitParticipants(BARRIER_TIMEOUT_MS)) {
            logger.accept("WARNING: " + late + " did not call sleepSim() for "
                + BARRIER_TIMEOUT_MS + " ms: the simulation goes on without waiting for it "
                + "(use sleepSim() instead of Thread.sleep() or busy loops)");
        }
        double t = clock.now();

        // simulated faults (in simulated time)
        for (RobotDummy r : robots) {
            if (r.isActive() && r.getFaultTime() >= 0 && t >= r.getFaultTime()) {
                r.setActive(false);
                logger.accept("Robot #" + r.getIDnum() + " disabled (simulated fault at t=" + r.getFaultTime() + " s)");
            }
        }

        // 2. motion
        double dt = clock.getDt();
        for (RobotUnit r : robots) if (r.isActive()) r.evaluateMovement(dt);

        // 3. sensors at the new positions
        sense();

        // 3b. radio network: graph, routing and delivery of the messages for the instant the robots will see
        network.step(t + dt);

        // 4. advance time
        clock.advance();
    }

    // ------------------------------------------------------------------
    //  Scene changes (obstacles and robots moved from the GUI)
    // ------------------------------------------------------------------

    public CollisionDetector getDetector() { return detector; }

    /** The scene can be changed before the start or while paused (not after stop()). */
    public boolean isEditable() { return !isStopped() && (!running || isPaused()); }

    /**
     * Call it after moving or rotating obstacles or robots: rebuilds the collision shapes
     * and recomputes the sensors, so the robots see the new scene at once.
     */
    public void sceneChanged() {
        stepLock.lock();
        try {
            detector.rebuild();
            sense();
            network.updateGraph(clock.now());
        } finally { stepLock.unlock(); }
    }

    /**
     * Replaces the obstacles and moves the robots listed in {@code poses} (id to pose), e.g. a
     * scene opened from file. Only while editable (before the start or paused); the sensors are
     * recomputed at once.
     */
    public void replaceScene(List<io.github.raffaelegrandi.pianura.arena.Obstacle> obstacles,
                             java.util.Map<Integer, io.github.raffaelegrandi.pianura.utils.Position> poses) {
        if (!isEditable()) throw new IllegalStateException("the scene can be replaced only before the start or while paused");
        stepLock.lock();
        try {
            detector.replaceObstacles(obstacles);
            for (RobotUnit r : robots) {
                io.github.raffaelegrandi.pianura.utils.Position p = poses.get(r.getIDnum());
                if (p != null) r.getUnitObject().setPos(new io.github.raffaelegrandi.pianura.utils.Position(p));
            }
            detector.rebuild();
            sense();
            network.updateGraph(clock.now());
        } finally { stepLock.unlock(); }
    }

    /** Validation of the current scene (obstacles and robot positions). */
    public CollisionDetector.Validation validate() {
        return detector.validate(bodies);
    }

    /** Updates the sensors of every active robot at the same instant (shared snapshot). */
    private void sense() {
        List<CircleShape> shapes = detector.snapshotRobots(bodies);
        io.github.raffaelegrandi.pianura.units.sensors.FloorMap fm = floorMap;
        for (RobotUnit r : robots) {
            if (!r.isActive()) continue;
            detector.testUnitForCollisions(r.getUnitObject(), shapes);
            if (fm != null) senseFloor(r.getUnitObject(), fm);
        }
    }

    /** Floor sensors: average color of the floor under each sensor. */
    private static void senseFloor(UnitObject u, io.github.raffaelegrandi.pianura.units.sensors.FloorMap fm) {
        io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray fs = u.getFloorSensors();
        io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray.Layout l = fs.getLayout();
        io.github.raffaelegrandi.pianura.utils.Position p = u.getExactPos();
        double cell = io.github.raffaelegrandi.pianura.utils.Const.cellSize, d = l.radiusFraction() * io.github.raffaelegrandi.pianura.utils.Const.unitRadius;
        int[] rgb = new int[l.count()];
        java.awt.geom.Point2D[] at = new java.awt.geom.Point2D[l.count()];
        for (int i = 0; i < l.count(); i++) {
            double a = p.theta + l.angleRad(i);
            double x = p.x * cell + d * Math.cos(a), y = p.y * cell + d * Math.sin(a);
            rgb[i] = fm.sampleRGB(x, y, l.footprintCells() * cell);
            at[i] = new java.awt.geom.Point2D.Double(x / cell, y / cell);
        }
        fs.publish(rgb, at);
    }

    private static void sleepNanos(long nanos) {
        try { Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

}
