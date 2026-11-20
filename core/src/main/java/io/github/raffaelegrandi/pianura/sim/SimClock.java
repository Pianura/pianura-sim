package io.github.raffaelegrandi.pianura.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fixed-step simulated time, and synchronisation of the robots' threads.
 *
 * Time advances only when the engine calls {@link #advance()}, in steps of
 * {@code dt} seconds. Robot threads do not use Thread.sleep but
 * {@link #sleep(Object, double)}: they block until simulated time has reached
 * the requested instant.
 *
 * Barrier (lockstep mode): before each step the engine waits, with
 * {@link #awaitParticipants(long)}, until every registered robot is blocked in
 * sleep. This way:
 *   - pause, single step and speed-up work without touching the robots' code;
 *   - a simulation run twice with the same data gives the same result, at any speed;
 *   - a slow controller (e.g. remote, over the network) slows the simulation down
 *     instead of losing commands.
 *
 * Non-cooperative robots: a robot that does not call sleep within the timeout (it uses
 * Thread.sleep, or loops without waiting) is reported and marked UNSYNCED: the engine
 * stops waiting for it until it calls sleep again.
 *
 * ReentrantLock/Condition rather than synchronized/wait: on Java 21 a virtual thread
 * blocked inside synchronized pins its carrier thread, and with many robots the
 * carriers could run out.
 */
public final class SimClock {

    private enum State { RUNNING, WAITING, UNSYNCED }

    private static final class Participant {
        final String name;
        State state = State.RUNNING;
        long wakeStep;
        Participant(String name) { this.name = name; }
    }

    private final double dt;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final Map<Object, Participant> participants = new LinkedHashMap<>();
    private long step = 0;
    private boolean stopped = false;

    public SimClock(double dtSeconds) {
        if (dtSeconds <= 0) throw new IllegalArgumentException("dt must be > 0");
        this.dt = dtSeconds;
    }

    public double getDt() { return dt; }

    /** Current simulated time in seconds (computed from the number of steps: no accumulated error). */
    public double now() {
        lock.lock();
        try { return step * dt; } finally { lock.unlock(); }
    }

    public long getStep() {
        lock.lock();
        try { return step; } finally { lock.unlock(); }
    }

    // ------------------------------------------------------------------
    //  Robot side
    // ------------------------------------------------------------------

    /** Registers a participant (call it before starting its thread). */
    public void register(Object who, String name) {
        lock.lock();
        try {
            participants.put(who, new Participant(name));
            changed.signalAll();
        } finally { lock.unlock(); }
    }

    /** Removes a participant (thread ended): the engine no longer waits for it. */
    public void deregister(Object who) {
        lock.lock();
        try {
            participants.remove(who);
            changed.signalAll();
        } finally { lock.unlock(); }
    }

    /**
     * Blocks the caller for {@code seconds} of simulated time (at least one step).
     * @throws SimulationStoppedException if the simulation is stopped while waiting
     */
    public void sleep(Object who, double seconds) {
        long steps = Math.max(1, Math.round(seconds / dt));
        lock.lock();
        try {
            if (stopped) throw new SimulationStoppedException();
            Participant p = participants.get(who);
            if (p == null) {            // not registered: waits in simulated time all the same
                long target = step + steps;
                while (step < target && !stopped) changed.awaitUninterruptibly();
            } else {
                p.wakeStep = step + steps;
                p.state = State.WAITING;
                changed.signalAll();
                while (step < p.wakeStep && !stopped && participants.containsKey(who)) {
                    changed.awaitUninterruptibly();
                }
                p.state = State.RUNNING;
            }
            if (stopped) throw new SimulationStoppedException();
        } finally { lock.unlock(); }
    }

    // ------------------------------------------------------------------
    //  Lato motore
    // ------------------------------------------------------------------

    /**
     * Waits until every synchronised robot is blocked in sleep on a future instant.
     * @return the names of the robots that exceeded the timeout (marked UNSYNCED)
     */
    public List<String> awaitParticipants(long timeoutMs) {
        List<String> late = new ArrayList<>();
        lock.lock();
        try {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (!stopped && anyRunning()) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    for (Participant p : participants.values()) {
                        if (isRunning(p)) { p.state = State.UNSYNCED; late.add(p.name); }
                    }
                    break;
                }
                try { changed.awaitNanos(left); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        } finally { lock.unlock(); }
        return late;
    }

    /** Advances one step and wakes the robots whose instant has come. */
    public void advance() {
        lock.lock();
        try {
            step++;
            changed.signalAll();
        } finally { lock.unlock(); }
    }

    /** Stops the simulation: every robot in sleep receives SimulationStoppedException. */
    public void stop() {
        lock.lock();
        try {
            stopped = true;
            changed.signalAll();
        } finally { lock.unlock(); }
    }

    public boolean isStopped() {
        lock.lock();
        try { return stopped; } finally { lock.unlock(); }
    }

    private boolean anyRunning() {
        for (Participant p : participants.values()) if (isRunning(p)) return true;
        return false;
    }

    /** "Running" = it is computing, or its wake-up instant has already come. */
    private boolean isRunning(Participant p) {
        return p.state == State.RUNNING || (p.state == State.WAITING && p.wakeStep <= step);
    }

}
