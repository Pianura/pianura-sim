// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.remote;

import java.util.Locale;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.github.raffaelegrandi.pianura.comm.Message;
import io.github.raffaelegrandi.pianura.units.RobotController;
import io.github.raffaelegrandi.pianura.units.RobotUnit;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * {@link RobotController} that forwards sensors to a remote client and applies its commands.
 * Created by {@link RemoteServer}; see there for the protocol and the two modes.
 */
public final class RemoteController implements RobotController {

    /** A command: either body speeds (v cells/s, w rad/s) or wheel speeds (left, right, cells/s). */
    private record Command(long seq, boolean wheels, double a, double b) {}

    /** A radio message asked by the client: to = Message.BROADCAST or a robot id. */
    private record Outgoing(int to, Object data) {}

    /** Sensor datagrams stay below this size: if the inbox is too big, the oldest messages are dropped. */
    static final int MAX_DATAGRAM = 60_000;

    private final RemoteServer server;
    private final int robotId;
    private volatile double period = Double.NaN;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition replied = lock.newCondition();
    private SocketAddress client;          // guarded by lock
    private Command last;                  // guarded by lock
    private long seq = 0;                  // last sensor message sent, guarded by lock
    private boolean lagging = false;       // lockstep: client missed a reply, guarded by lock
    private boolean idleApplied = false;
    private boolean stoppedSent = false;   // guarded by lock
    private final List<Outgoing> outgoing = new ArrayList<>();   // radio messages to send, guarded by lock
    private final List<java.util.function.Consumer<io.github.raffaelegrandi.pianura.viz.Viz>> drawing = new ArrayList<>();   // debug drawings to apply, guarded by lock
    private final List<java.util.function.Consumer<io.github.raffaelegrandi.pianura.inspect.Inspector>> inspecting = new ArrayList<>();   // inspector updates, guarded by lock

    private volatile RobotUnit robot;   // known once the robot is created (geometry and scale for the welcome)

    RemoteController(RemoteServer server, int robotId) {
        this.server = server;
        this.robotId = robotId;
    }

    void setPeriod(double p) { period = p; }

    void setRobot(RobotUnit r) { robot = r; }

    public int getRobotId() { return robotId; }

    /** True once a client has said hello (and not bye). */
    public boolean isConnected() {
        lock.lock();
        try { return client != null; } finally { lock.unlock(); }
    }

    // ------------------------------------------------------------------ from the network

    void connect(SocketAddress from) {
        lock.lock();
        try {
            client = from;
            lagging = false;
        } finally { lock.unlock(); }
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("type", "welcome");
        w.put("protocol", RemoteServer.PROTOCOL_VERSION);
        w.put("robot", robotId);
        w.put("mode", server.getMode().name().toLowerCase());
        if (!Double.isNaN(period)) w.put("period", period);
        RobotUnit r = robot;
        Map<String, Object> units = new LinkedHashMap<>();
        units.put("length", "cell");
        units.put("meters_per_cell", r != null ? r.getMetersPerCell() : io.github.raffaelegrandi.pianura.utils.Const.DEFAULT_METERS_PER_CELL);
        units.put("time", "s");
        units.put("angle", "rad");
        w.put("units", units);
        if (r != null) {                    // protocol 3: the robot's geometry, in cells
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("radius", r.getBodyRadius());
            body.put("wheel_base", r.getWheelBase());
            body.put("wheel_radius", r.getWheelRadius());
            body.put("ir_count", r.getIrCount());
            body.put("ir_range", r.getIrRange());
            body.put("floor_count", r.getFloorSensorCount());
            w.put("body", body);
        }
        // the descriptor: what this robot is and has, so that a client written for the simulator
        // can find out what differs on another robot (fewer sensors, no true pose, no radio...)
        w.put("kind", KIND);
        if (r != null) {
            w.put("description", describe(r));
            w.put("capabilities", capabilities(r));
        }
        server.send(from, w);
        server.log("Remote client for robot #" + robotId + " connected from " + from);
    }

    /** What answers: the simulator. A physical robot that speaks this protocol says "bot". */
    public static final String KIND = "sim";

    /** What the simulator offers beyond sensors and motors; a robot lists only what it has. */
    public static final List<String> FEATURES = List.of("radio", "draw", "inspector");

    /**
     * The robot's capabilities for the welcome message. Every key of {@code sensors} is a key of
     * the sensor messages, with what a client needs to use it without assuming the simulator's
     * robot: how many readings, where they point (radians from the heading, clockwise), their
     * unit and range. {@code pose.source} says where the pose comes from: "truth" here.
     */
    static Map<String, Object> capabilities(RobotUnit r) {
        Map<String, Object> sensors = new LinkedHashMap<>();
        sensors.put("pose", Map.of("source", "truth"));

        var irs = r.getUnitObject().getIrSensors();
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("count", irs.length());
        ir.put("unit", "cell");
        ir.put("range", r.getIrRange());
        ir.put("angles", angles(irs));
        sensors.put("ir", ir);

        var bumps = r.getUnitObject().getBumpSensors();
        Map<String, Object> bump = new LinkedHashMap<>();
        bump.put("count", bumps.length());
        bump.put("angles", angles(bumps));
        sensors.put("bump", bump);

        sensors.put("contact", new LinkedHashMap<String, Object>());

        var lay = r.getUnitObject().getFloorSensors().getLayout();
        Map<String, Object> floor = new LinkedHashMap<>();
        floor.put("count", lay.count());
        floor.put("min", 0);
        floor.put("max", 255);
        double[] fa = new double[lay.count()];
        for (int i = 0; i < fa.length; i++) fa[i] = round4(lay.angleRad(i));
        floor.put("angles", fa);
        floor.put("radius", round4(lay.radiusFraction() * r.getBodyRadius()));
        sensors.put("floor", floor);

        sensors.put("wheels", Map.of("unit", "cell"));

        Map<String, Object> drive = new LinkedHashMap<>();
        drive.put("modes", List.of("v_w", "wheels"));     // the simulator sets no speed limits: none declared
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("sensors", sensors);
        caps.put("actuators", Map.of("drive", drive));
        caps.put("features", FEATURES);
        return caps;
    }

    private static double[] angles(io.github.raffaelegrandi.pianura.units.collisions.SensorArray a) {
        double[] out = new double[a.length()];
        for (int i = 0; i < out.length; i++) out[i] = round4(a.getSensorAngle(i));
        return out;
    }

    private static double round4(double v) { return Math.round(v * 1e4) / 1e4; }

    /** A sentence about the robot for people and for agents, not for code. */
    static String describe(RobotUnit r) {
        double across = 2 * r.getBodyRadius();
        return String.format(Locale.ROOT,
            "Simulated differential-drive robot, %s cells across (%s cm): %d infrared distance sensors and %d bump sensors "
            + "around the body, %d floor sensors under the nose, wheel travel, and its true pose from the simulator.",
            trim(across), trim(across * r.getMetersPerCell() * 100), r.getIrCount(), r.getUnitObject().getBumpSensors().length(),
            r.getFloorSensorCount());
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    void disconnect(SocketAddress from) {
        lock.lock();
        try {
            if (from.equals(client)) client = null;
            replied.signalAll();
        } finally { lock.unlock(); }
        server.log("Remote client for robot #" + robotId + " disconnected");
    }

    /** @return an error message for the client, or null if the command was accepted */
    String onCommand(SocketAddress from, Map<String, Object> msg) {
        long s = (long) MiniJson.num(msg, "seq", -1);

        // radio messages (protocol 2): "broadcast": data, "send": [{"to": id, "data": data}, ...]
        List<Outgoing> radio = new ArrayList<>();
        if (msg.containsKey("broadcast")) radio.add(new Outgoing(Message.BROADCAST, msg.get("broadcast")));
        if (msg.containsKey("send")) {
            if (!(msg.get("send") instanceof List<?> list)) return "cmd: \"send\" must be a list of {\"to\": id, \"data\": ...}";
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m) || !m.containsKey("data")
                        || !(m.get("to") instanceof Number || "all".equals(m.get("to"))))
                    return "cmd: each \"send\" item must be {\"to\": robot id or \"all\", \"data\": ...}";
                int to = m.get("to") instanceof Number n ? n.intValue() : Message.BROADCAST;
                if (to < 0 && to != Message.BROADCAST) return "cmd: \"send\": invalid robot id " + to;
                radio.add(new Outgoing(to, m.get("data")));
            }
        }

        // debug drawings (protocol 3): "draw": [{"op": ..., ...}, ...]
        List<java.util.function.Consumer<io.github.raffaelegrandi.pianura.viz.Viz>> draw = List.of();
        if (msg.containsKey("draw")) {
            try { draw = DrawOps.parse(msg.get("draw")); }
            catch (IllegalArgumentException e) { return "cmd: " + e.getMessage(); }
        }

        // inspector (protocol 3): "state", "log", "watch", "plot"
        List<java.util.function.Consumer<io.github.raffaelegrandi.pianura.inspect.Inspector>> insp;
        try { insp = parseInspector(msg); }
        catch (IllegalArgumentException e) { return "cmd: " + e.getMessage(); }

        Command c = null;
        if (msg.containsKey("left") || msg.containsKey("right")) {
            double l = MiniJson.num(msg, "left", Double.NaN), r = MiniJson.num(msg, "right", Double.NaN);
            if (!Double.isFinite(l) || !Double.isFinite(r)) return "cmd: \"left\" and \"right\" must both be numbers";
            c = new Command(s, true, l, r);
        } else if (msg.containsKey("v") || (radio.isEmpty() && !msg.containsKey("draw") && insp.isEmpty())) {
            double v = MiniJson.num(msg, "v", Double.NaN), w = MiniJson.num(msg, "w", 0);
            if (!Double.isFinite(v) || !Double.isFinite(w)) return "cmd: \"v\" (and optional \"w\") must be numbers, or use \"left\"/\"right\"";
            c = new Command(s, false, v, w);
        }
        lock.lock();
        try {
            if (client == null) client = from;   // a command implies a connection
            if (c != null) last = c;
            else if (last != null) last = new Command(s, last.wheels(), last.a(), last.b());   // messages only: keep moving as before
            else last = new Command(s, false, 0, 0);
            outgoing.addAll(radio);
            drawing.addAll(draw);
            inspecting.addAll(insp);
            lagging = false;
            replied.signalAll();
        } finally { lock.unlock(); }
        return null;
    }

    void wakeUp() {
        lock.lock();
        try { replied.signalAll(); } finally { lock.unlock(); }
    }

    // ------------------------------------------------------------------ from the simulation

    @Override
    public void control(RobotUnit robot, double simTime) {
        this.robot = robot;
        SocketAddress to;
        long mySeq;
        lock.lock();
        try {
            to = client;
            mySeq = ++seq;
        } finally { lock.unlock(); }

        if (to == null) {                       // nobody is driving this robot yet: stand still
            if (!idleApplied) { robot.setRobotSpeed(0, 0); idleApplied = true; }
            if (robot.hasMessages()) robot.receive();   // nobody listens: the radio messages are lost
            return;
        }
        idleApplied = false;
        server.send(to, sensors(robot, simTime, mySeq));

        Command cmd;
        List<Outgoing> radio;
        List<java.util.function.Consumer<io.github.raffaelegrandi.pianura.viz.Viz>> draw;
        List<java.util.function.Consumer<io.github.raffaelegrandi.pianura.inspect.Inspector>> insp;
        lock.lock();
        try {
            if (server.getMode() == RemoteServer.Mode.LOCKSTEP && !lagging) {
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(server.getReplyTimeoutMs());
                while (client != null && (last == null || last.seq() < mySeq)) {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) {
                        lagging = true;
                        server.log("Remote: robot #" + robotId + " got no reply for t=" + String.format(Locale.ROOT, "%.2f", simTime)
                            + " s within " + server.getReplyTimeoutMs() + " ms: keeping its last command");
                        break;
                    }
                    try { replied.awaitNanos(left); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            }
            cmd = last;
            radio = new ArrayList<>(outgoing);      // each message is sent once, now, from the robot thread
            outgoing.clear();
            draw = new ArrayList<>(drawing);
            drawing.clear();
            insp = new ArrayList<>(inspecting);
            inspecting.clear();
        } finally { lock.unlock(); }

        if (cmd != null) {
            if (cmd.wheels()) robot.setWheelSpeeds(cmd.a(), cmd.b());
            else robot.setRobotSpeed(cmd.a(), cmd.b());
        }
        if (!draw.isEmpty()) {
            io.github.raffaelegrandi.pianura.viz.Viz viz = robot.drawings();
            for (var op : draw) op.accept(viz);
        }
        if (!insp.isEmpty()) {
            io.github.raffaelegrandi.pianura.inspect.Inspector in = robot.inspector();
            for (var op : insp) op.accept(in);
        }
        for (Outgoing o : radio) {
            try {
                if (o.to() == Message.BROADCAST) robot.broadcast(o.data());
                else robot.send(o.to(), o.data());
            } catch (IllegalStateException | IllegalArgumentException e) {
                server.sendError(to, "robot " + robotId + ": cannot send radio message: " + e.getMessage());
            }
        }
    }

    @Override
    public void onStop(RobotUnit robot) { notifyStopped(); }

    /** Sends "stopped" to the client (once): robot disabled, simulation stopped or server closing. */
    void notifyStopped() {
        // Sent while holding the lock: when the robot's thread (onStop) and RemoteServer.close()
        // race, close() waits here until the message is out, and only then closes the socket.
        // Otherwise the robot's thread could mark it as sent and lose it on the closed socket.
        lock.lock();
        try {
            SocketAddress to = client;
            if (stoppedSent || to == null) return;
            stoppedSent = true;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "stopped");
            m.put("robot", robotId);
            server.send(to, m);
        } finally { lock.unlock(); }
    }

    private Map<String, Object> sensors(RobotUnit r, double t, long s) {
        Position p = r.getPosition();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "sensors");
        m.put("robot", robotId);
        m.put("seq", s);
        m.put("t", Math.round(t * 1e6) / 1e6);
        Map<String, Object> pose = new LinkedHashMap<>();
        pose.put("x", p.x);
        pose.put("y", p.y);
        pose.put("theta", p.theta);
        m.put("pose", pose);
        double[] ir = r.getIr();
        for (int i = 0; i < ir.length; i++) ir[i] = Math.round(ir[i] * 1000) / 1000.0;   // 1/1000 of a cell is plenty
        m.put("ir", ir);
        double[] bumpValues = r.getUnitObject().getBumpSensors().getSensors();
        List<Boolean> bump = new ArrayList<>(bumpValues.length);
        for (double b : bumpValues) bump.add(b != 0);
        m.put("bump", bump);
        m.put("contact", r.hasCollision());
        m.put("floor", r.getFloorGrays());
        Map<String, Object> wheels = new LinkedHashMap<>();
        wheels.put("left", r.getLeftWheelTravel());
        wheels.put("right", r.getRightWheelTravel());
        m.put("wheels", wheels);
        m.put("active", r.isActive());

        // radio messages received since the last sensor message (protocol 2)
        List<Map<String, Object>> inbox = new ArrayList<>();
        for (Message msg : r.receive()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("from", msg.from());
            e.put("to", msg.isBroadcast() ? "all" : msg.to());
            e.put("t", Math.round(msg.sentAt() * 1e6) / 1e6);
            e.put("data", msg.payload());
            inbox.add(e);
        }
        m.put("inbox", inbox);
        int dropped = 0;
        while (!inbox.isEmpty() && MiniJson.write(m).length() > MAX_DATAGRAM) { inbox.remove(0); dropped++; }
        if (dropped > 0) {
            m.put("inbox_dropped", dropped);
            server.log("Remote: robot #" + robotId + " inbox too large for one datagram, " + dropped + " oldest messages dropped");
        }
        return m;
    }


    /**
     * Inspector fields of a command: "state": text, "log": text or list of texts,
     * "watch": {name: value}, "plot": {name: number}. Applied in this order.
     */
    static List<java.util.function.Consumer<io.github.raffaelegrandi.pianura.inspect.Inspector>> parseInspector(Map<String, Object> msg) {
        List<java.util.function.Consumer<io.github.raffaelegrandi.pianura.inspect.Inspector>> ops = new ArrayList<>();
        if (msg.containsKey("state")) {
            if (!(msg.get("state") instanceof String st)) throw new IllegalArgumentException("\"state\" must be a string");
            ops.add(in -> in.state(st));
        }
        if (msg.containsKey("log")) {
            Object l = msg.get("log");
            List<?> lines = l instanceof List<?> list ? list : List.of(l);
            for (Object line : lines) {
                if (!(line instanceof String text)) throw new IllegalArgumentException("\"log\" must be a string or a list of strings");
                ops.add(in -> in.log(text));
            }
        }
        if (msg.containsKey("watch")) {
            if (!(msg.get("watch") instanceof Map<?, ?> m)) throw new IllegalArgumentException("\"watch\" must be an object {name: value}");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String name = String.valueOf(e.getKey());
                Object value = e.getValue() instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15 ? (Object) d.longValue() : e.getValue();
                ops.add(in -> in.watch(name, value));
            }
        }
        if (msg.containsKey("plot")) {
            if (!(msg.get("plot") instanceof Map<?, ?> m)) throw new IllegalArgumentException("\"plot\" must be an object {name: number}");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!(e.getValue() instanceof Number n)) throw new IllegalArgumentException("\"plot\": \"" + e.getKey() + "\" must be a number");
                String name = String.valueOf(e.getKey());
                double v = n.doubleValue();
                ops.add(in -> in.plot(name, v));
            }
        }
        if (msg.containsKey("bars")) {
            if (!(msg.get("bars") instanceof Map<?, ?> m)) throw new IllegalArgumentException("\"bars\" must be an object {name: [numbers]}");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String name = String.valueOf(e.getKey()), where = "\"bars\": \"" + name + "\"";
                Object val = e.getValue();
                double[] values;
                String[] labels = null;
                if (val instanceof Map<?, ?> o) {
                    values = vector(o.get("values"), where + ".values");
                    if (o.get("labels") != null) labels = strings(o.get("labels"), where + ".labels");
                } else values = vector(val, where);
                String[] lb = labels;
                ops.add(in -> in.bars(name, lb, values));
            }
        }
        if (msg.containsKey("matrix")) {
            if (!(msg.get("matrix") instanceof Map<?, ?> m)) throw new IllegalArgumentException("\"matrix\" must be an object {name: [[numbers]]}");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String name = String.valueOf(e.getKey());
                double[][] values = matrix(e.getValue(), "\"matrix\": \"" + name + "\"");
                ops.add(in -> in.matrix(name, values));
            }
        }
        if (msg.containsKey("network")) {
            if (!(msg.get("network") instanceof Map<?, ?> m))
                throw new IllegalArgumentException("\"network\" must be an object {name: {\"activations\": [[...]], \"weights\": [[[...]]]}}");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String name = String.valueOf(e.getKey()), where = "\"network\": \"" + name + "\"";
                if (!(e.getValue() instanceof Map<?, ?> o) || o.get("activations") == null)
                    throw new IllegalArgumentException(where + " must be an object with \"activations\"");
                double[][] act = matrix(o.get("activations"), where + ".activations");
                double[][][] w = null;
                if (o.get("weights") != null) {
                    if (!(o.get("weights") instanceof List<?> wl)) throw new IllegalArgumentException(where + ".weights must be a list of matrices");
                    w = new double[wl.size()][][];
                    for (int l = 0; l < wl.size(); l++) w[l] = wl.get(l) == null ? null : matrix(wl.get(l), where + ".weights[" + l + "]");
                }
                String[][] lb = null;
                if (o.get("labels") != null) {
                    if (!(o.get("labels") instanceof List<?> ll)) throw new IllegalArgumentException(where + ".labels must be a list of lists of strings");
                    lb = new String[ll.size()][];
                    for (int l = 0; l < ll.size(); l++) lb[l] = ll.get(l) == null ? null : strings(ll.get(l), where + ".labels[" + l + "]");
                }
                double[][][] ww = w;
                String[][] lbl = lb;
                ops.add(in -> in.network(name, act, ww, lbl));
            }
        }
        return ops;
    }

    /** A list of numbers (null = NaN, a gap). */
    private static double[] vector(Object o, String where) {
        if (!(o instanceof List<?> l)) throw new IllegalArgumentException(where + " must be a list of numbers");
        double[] v = new double[l.size()];
        for (int i = 0; i < v.length; i++) {
            Object x = l.get(i);
            if (x == null) v[i] = Double.NaN;
            else if (x instanceof Number n) v[i] = n.doubleValue();
            else throw new IllegalArgumentException(where + " must be a list of numbers");
        }
        return v;
    }

    private static double[][] matrix(Object o, String where) {
        if (!(o instanceof List<?> l)) throw new IllegalArgumentException(where + " must be a list of lists of numbers");
        double[][] m = new double[l.size()][];
        for (int i = 0; i < m.length; i++) m[i] = vector(l.get(i), where);
        return m;
    }

    private static String[] strings(Object o, String where) {
        if (!(o instanceof List<?> l)) throw new IllegalArgumentException(where + " must be a list of strings");
        String[] s = new String[l.size()];
        for (int i = 0; i < s.length; i++) s[i] = l.get(i) == null ? null : String.valueOf(l.get(i));
        return s;
    }
}
