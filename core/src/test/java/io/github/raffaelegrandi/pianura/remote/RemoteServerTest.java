// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.remote;

import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/** Protocol tests with a UDP client written in Java, against a headless engine. */
class RemoteServerTest {

    /** Minimal client: what a Python program would do. */
    static final class Client implements AutoCloseable {
        final DatagramSocket socket;
        final InetSocketAddress server;
        Client(int port) throws Exception {
            socket = new DatagramSocket();
            socket.setSoTimeout(3000);
            server = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        }
        void send(Map<String, Object> m) throws Exception {
            byte[] b = MiniJson.write(m).getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(b, b.length, server));
        }
        void sendRaw(String s) throws Exception {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(b, b.length, server));
        }
        Map<String, Object> receive() throws Exception {
            byte[] buf = new byte[65507];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            socket.receive(p);
            return MiniJson.parseObject(new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8));
        }
        static Map<String, Object> msg(Object... kv) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
            return m;
        }
        @Override public void close() { socket.close(); }
    }

    /** Engine with one remote robot, run in a background thread up to {@code until} seconds. */
    static final class Sim {
        final RemoteServer server;
        final RobotDummy robot;
        final SimEngine engine;
        final List<String> log = new CopyOnWriteArrayList<>();
        Thread runner;
        Sim(RemoteServer.Mode mode, long timeoutMs) throws Exception {
            server = RemoteServer.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), mode, timeoutMs);
            server.setLogger(log::add);
            robot = server.createRobot(1, new Position(10, 20, 0), 2, 0.1);
            SimClock clock = new SimClock(Const.physicsStep);
            engine = new SimEngine(clock, new CollisionDetector(List.of()), List.of(robot));
            engine.setLogger(log::add);
            robot.attachClock(clock);
        }
        /** Starts robots and engine: the client already said hello (like pressing Run after connecting it). */
        void runAsync(double until) {
            robot.start();
            runner = new Thread(() -> engine.runUntil(until));
            runner.start();
        }
        void finish() throws Exception {
            runner.join(15000);
            engine.stop();
            server.close();
        }
    }

    @Test
    @Timeout(20)
    void theWelcomeDescribesTheRobot() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            Map<String, Object> w = c.receive();
            assertEquals(3.0, w.get("protocol"), "the descriptor adds fields: the version stays 3");
            assertEquals("sim", w.get("kind"));
            String d = (String) w.get("description");
            assertTrue(d.contains("18 infrared") && d.contains("2 cells across (10 cm)"), d);
            Map<?, ?> caps = (Map<?, ?>) w.get("capabilities");
            Map<?, ?> sensors = (Map<?, ?>) caps.get("sensors");
            assertEquals(List.of("pose", "ir", "bump", "contact", "floor", "wheels"), List.copyOf(sensors.keySet()));
            assertEquals("truth", ((Map<?, ?>) sensors.get("pose")).get("source"));
            Map<?, ?> ir = (Map<?, ?>) sensors.get("ir");
            assertEquals(18.0, ir.get("count"));
            assertEquals(sim.robot.getIrRange(), ir.get("range"));
            List<?> angles = (List<?>) ir.get("angles");
            assertEquals(18, angles.size());
            assertEquals(0.0, angles.get(0));
            assertEquals(Math.toRadians(20), (Double) angles.get(1), 1e-4, "sensor 1 is 20 degrees clockwise of the heading");
            Map<?, ?> floor = (Map<?, ?>) sensors.get("floor");
            assertEquals(2.0, floor.get("count"));
            assertEquals(255.0, floor.get("max"));
            assertEquals(Math.toRadians(-20), (Double) ((List<?>) floor.get("angles")).get(0), 1e-4, "sensor 0 is on the left");
            Map<?, ?> drive = (Map<?, ?>) ((Map<?, ?>) caps.get("actuators")).get("drive");
            assertEquals(List.of("v_w", "wheels"), drive.get("modes"));
            assertFalse(drive.containsKey("v_max"), "the simulator has no speed limits: none declared");
            assertEquals(List.of("radio", "draw", "inspector"), caps.get("features"));

            // every sensor declared is a key of the readings, and the sizes agree
            sim.runAsync(0.1);
            Map<String, Object> s;
            do { s = c.receive(); } while (!"sensors".equals(s.get("type")));
            for (Object name : sensors.keySet()) assertTrue(s.containsKey(name), "declared and sent: " + name);
            assertEquals(18, ((List<?>) s.get("ir")).size());
            assertEquals(18, ((List<?>) s.get("bump")).size());
            assertEquals(2, ((List<?>) s.get("floor")).size());
            c.send(Client.msg("type", "bye", "robot", 1));
            sim.finish();
        }
    }

    /**
     * The same check a physical robot will have to pass, run against the simulator: the Python
     * script connects, reads the descriptor and drives. Skipped where there is no python3.
     */
    @Test
    @Timeout(60)
    void theSimulatorPassesTheConformanceCheck() throws Exception {
        java.io.File script = new java.io.File("../clients/python/conformance.py");
        org.junit.jupiter.api.Assumptions.assumeTrue(script.isFile() && hasPython(), "needs python3 and the clients folder");
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 5000);
        Process p = new ProcessBuilder("python3", script.getPath(), "--port", String.valueOf(sim.server.getPort()), "--timeout", "20")
            .redirectErrorStream(true).start();
        try {
            long t0 = System.currentTimeMillis();
            while (!sim.server.controller(1).isConnected() && System.currentTimeMillis() - t0 < 15000) Thread.sleep(20);
            assertTrue(sim.server.controller(1).isConnected(), "the check said hello");
            sim.runAsync(30);
            assertTrue(p.waitFor(40, java.util.concurrent.TimeUnit.SECONDS), "the check ends");
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, p.exitValue(), out);
            assertTrue(out.contains("0 failed: CONFORMS"), out);
            assertTrue(out.contains("ok    drive mode \"wheels\" is accepted"), out);
            assertEquals(0, sim.robot.getLinearSpeed(), 1e-9, "the check leaves the robot still");
        } finally {
            p.destroyForcibly();
            sim.finish();
        }
    }

    private static boolean hasPython() {
        try {
            return new ProcessBuilder("python3", "--version").redirectErrorStream(true).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @Timeout(20)
    void helloGetsWelcome() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            Map<String, Object> w = c.receive();
            assertEquals("welcome", w.get("type"));
            assertEquals(3.0, w.get("protocol"));
            assertEquals("lockstep", w.get("mode"));
            assertEquals(0.1, w.get("period"));
            Map<?, ?> units = (Map<?, ?>) w.get("units");
            assertEquals("cell", units.get("length"));
            assertEquals(0.05, units.get("meters_per_cell"));
            Map<?, ?> body = (Map<?, ?>) w.get("body");
            assertEquals(1.0, body.get("radius"));
            assertEquals(2.0, body.get("wheel_base"));
            assertEquals(18.0, body.get("ir_count"));
            assertEquals(sim.robot.getIrRange(), body.get("ir_range"));
        } finally { sim.engine.stop(); sim.server.close(); }
    }

    @Test
    @Timeout(20)
    void lockstepClientDrivesTheRobotExactly() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        List<Double> times = new ArrayList<>();
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            assertEquals("welcome", c.receive().get("type"));
            sim.runAsync(2.0);
            // every period: sensors → command with the same seq
            while (true) {
                Map<String, Object> s = c.receive();
                if (!"sensors".equals(s.get("type"))) continue;
                times.add((Double) s.get("t"));
                List<?> ir = (List<?>) s.get("ir");
                assertEquals(18, ir.size());
                for (Object v : ir) assertTrue((Double) v >= 0 && (Double) v <= sim.robot.getIrRange(), "IR in cells: " + ir);
                for (Object b : (List<?>) s.get("bump")) assertInstanceOf(Boolean.class, b);
                assertEquals(2, ((List<?>) s.get("floor")).size());
                c.send(Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"), "v", 1.0, "w", 0.0));
                if ((Double) s.get("t") >= 2.0 - 1e-9) break;
            }
            sim.finish();
        }
        assertEquals(21, times.size(), "sensors at t = 0.0, 0.1, …, 2.0: " + times);
        assertEquals(12.0, sim.robot.getPosition().x, 1e-6, "1 cell/s for 2 s starting from x = 10");
        assertTrue(sim.log.stream().noneMatch(m -> m.contains("no reply")), sim.log.toString());
    }

    @Test
    @Timeout(20)
    void wheelCommandsAreInCellsPerSecond() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            sim.runAsync(1.0);
            while (true) {
                Map<String, Object> s = c.receive();
                if (!"sensors".equals(s.get("type"))) continue;
                c.send(Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"), "left", 1.0, "right", 1.0));
                if ((Double) s.get("t") >= 1.0 - 1e-9) break;
            }
            sim.finish();
        }
        // wheels at 1 cell/s: the robot goes straight at 1 cell/s
        assertEquals(11.0, sim.robot.getPosition().x, 1e-6);
    }

    @Test
    @Timeout(20)
    void withoutClientTheSimulationIsNotBlocked() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        long t0 = System.nanoTime();
        sim.runAsync(5.0);
        sim.finish();
        assertTrue((System.nanoTime() - t0) / 1e9 < 3, "without a client the simulation must not wait");
        assertEquals(10.0, sim.robot.getPosition().x, 1e-9, "without a client the robot stays still");
    }

    @Test
    @Timeout(20)
    void silentClientInLockstepTimesOutOnceAndKeepsLastCommand() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 200);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            sim.runAsync(2.0);
            Map<String, Object> s = c.receive();
            c.send(Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"), "v", 1.0));   // a single command, then silence
            sim.finish();
        }
        assertEquals(12.0, sim.robot.getPosition().x, 0.11, "keeps going with the last command");
        assertEquals(1, sim.log.stream().filter(m -> m.contains("no reply")).count(), sim.log.toString());
    }

    @Test
    @Timeout(20)
    void realtimeAppliesLatestCommandWithoutWaiting() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.REALTIME, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            c.send(Client.msg("type", "cmd", "robot", 1, "v", 1.0));   // no seq: fine in real time
            Thread.sleep(200);
            sim.runAsync(3.0);
            sim.finish();
        }
        assertEquals(13.0, sim.robot.getPosition().x, 0.11);
        assertTrue(sim.log.stream().noneMatch(m -> m.contains("no reply")));
    }

    @Test
    @Timeout(20)
    void errorsAreReportedToTheClient() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.sendRaw("{not json");
            assertTrue(((String) c.receive().get("message")).startsWith("malformed JSON"));
            c.send(Client.msg("type", "hello", "robot", 42));
            assertTrue(((String) c.receive().get("message")).contains("unknown robot 42"));
            c.send(Client.msg("type", "cmd", "robot", 1, "v", "fast"));
            assertTrue(((String) c.receive().get("message")).startsWith("cmd:"));
            c.send(Client.msg("type", "dance", "robot", 1));
            assertTrue(((String) c.receive().get("message")).contains("unknown message type"));
            c.send(Client.msg("type", "cmd", "robot", 1, "v", 1.0, "send", List.of(Map.of("data", "no recipient"))));
            assertTrue(((String) c.receive().get("message")).contains("\"send\""));
        } finally { sim.engine.stop(); sim.server.close(); }
    }

    @Test
    @Timeout(20)
    void clientDrawsDebugShapes() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            sim.runAsync(0.3);
            int n = 0;
            while (true) {
                Map<String, Object> s = c.receive();
                if (!"sensors".equals(s.get("type"))) continue;
                List<Object> draw = new ArrayList<>();
                draw.add(Client.msg("op", "clear", "layer", "plan"));
                draw.add(Client.msg("op", "polyline", "layer", "plan", "xs", List.of(1.0, 2.0, 3.0), "ys", List.of(1.0, 1.0, 2.0), "color", "#2060ff"));
                draw.add(Client.msg("op", "text", "layer", "plan", "x", 3.0, "y", 2.0, "text", "goal", "color", List.of(255.0, 0.0, 0.0)));
                draw.add(Client.msg("op", "point", "x", 5.0, "y", 5.0));      // default layer, grows at every step
                c.send(Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"), "v", 1.0, "draw", draw));
                n++;
                if ((Double) s.get("t") >= 0.3 - 1e-9) break;
            }
            sim.finish();
            var d = sim.robot.getDrawings();
            assertEquals(List.of("plan", "default"), List.copyOf(d.keySet()));
            assertEquals(2, d.get("plan").size(), "cleared at every step: only the last polyline and text");
            assertEquals(new java.awt.Color(0x20, 0x60, 0xff), d.get("plan").get(0).color());
            assertEquals("goal", d.get("plan").get(1).text());
            assertEquals(java.awt.Color.RED, d.get("plan").get(1).color());
            assertEquals(n, d.get("default").size(), "never cleared: one point per step");
            assertEquals(10.3, sim.robot.getPosition().x, 1e-6, "drawing does not change the motion");
        }
    }

    @Test
    @Timeout(20)
    void clientFeedsTheInspector() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            sim.runAsync(0.2);
            while (true) {
                Map<String, Object> s = c.receive();
                if (!"sensors".equals(s.get("type"))) continue;
                double t = (Double) s.get("t");
                c.send(Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"), "v", 1.0,
                    "state", t < 0.15 ? "warming up" : "cruising", "log", List.of("tick " + t),
                    "watch", Client.msg("t", t, "count", 3.0, "mode", "fast"), "plot", Client.msg("t", t)));
                if (t >= 0.2 - 1e-9) break;
            }
            sim.finish();
            var snap = sim.robot.inspector().snapshot();
            assertEquals("cruising", snap.state());
            assertEquals("3", snap.watched().get("count"), "integral numbers shown without decimals");
            assertEquals("fast", snap.watched().get("mode"));
            assertEquals(3, snap.series().get(0).size(), "one point per step: t = 0, 0.1, 0.2");
            assertTrue(snap.log().stream().anyMatch(e -> e.text().equals("state: warming up")));
            assertTrue(snap.log().stream().anyMatch(e -> e.text().startsWith("tick 0.1")));
        }
    }

    @Test
    @Timeout(20)
    void clientShowsItsInsides() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            sim.runAsync(0.1);
            while (true) {
                Map<String, Object> s = c.receive();
                if (!"sensors".equals(s.get("type"))) continue;
                double t = (Double) s.get("t");
                c.send(Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"), "v", 1.0,
                    "bars", Client.msg("q", List.of(0.1, t, -0.5), "out", Client.msg("values", List.of(1.0, 2.0), "labels", List.of("left", "right"))),
                    "matrix", Client.msg("w", List.of(List.of(1.0, -1.0), List.of(0.5, 0.0))),
                    "network", Client.msg("net", Client.msg(
                        "activations", List.of(List.of(1.0, 0.0), List.of(0.3)),
                        "weights", List.of(List.of(List.of(0.7, -0.2))),
                        "labels", Arrays.asList(List.of("a", "b"), null)))));
                if (t >= 0.1 - 1e-9) break;
            }
            sim.finish();
            var views = sim.robot.inspector().snapshot().views();
            assertEquals(List.of("q", "out", "w", "net"), views.stream().map(v -> v.name()).toList());
            var q = (io.github.raffaelegrandi.pianura.inspect.Inspector.Bars) views.get(0);
            assertEquals(0.1, q.values()[1], 1e-9, "the latest values replace the previous ones");
            assertArrayEquals(new String[] { "left", "right" }, ((io.github.raffaelegrandi.pianura.inspect.Inspector.Bars) views.get(1)).labels());
            var net = (io.github.raffaelegrandi.pianura.inspect.Inspector.Network) views.get(3);
            assertEquals(-0.2, net.weights()[0][0][1], 1e-9);
            assertNull(net.labels()[1]);
            assertEquals(1, sim.robot.getLinearSpeed(), 1e-9, "views do not change the motion");
        }
    }

    @Test
    @Timeout(20)
    void malformedViewsAreRejected() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "cmd", "robot", 1, "bars", Client.msg("q", List.of(1.0, "x"))));
            assertTrue(((String) c.receive().get("message")).contains("\"bars\": \"q\" must be a list of numbers"));
            c.send(Client.msg("type", "cmd", "robot", 1, "network", Client.msg("n", Client.msg("weights", List.of()))));
            assertTrue(((String) c.receive().get("message")).contains("must be an object with \"activations\""));
        } finally { sim.engine.stop(); sim.server.close(); }
    }

    @Test
    @Timeout(20)
    void malformedInspectorFieldsAreRejected() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "cmd", "robot", 1, "plot", Client.msg("e", "high")));
            assertTrue(((String) c.receive().get("message")).contains("\"plot\": \"e\" must be a number"));
            c.send(Client.msg("type", "cmd", "robot", 1, "state", 3.0));
            assertTrue(((String) c.receive().get("message")).contains("\"state\" must be a string"));
        } finally { sim.engine.stop(); sim.server.close(); }
    }

    @Test
    @Timeout(20)
    void malformedDrawingsAreRejected() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "cmd", "robot", 1, "v", 1.0, "draw", List.of(Client.msg("op", "line", "x1", 1.0))));
            assertTrue(((String) c.receive().get("message")).contains("draw[0]: \"y1\" must be a number"));
            c.send(Client.msg("type", "cmd", "robot", 1, "draw", List.of(Client.msg("op", "spiral"))));
            assertTrue(((String) c.receive().get("message")).contains("unknown op"));
            c.send(Client.msg("type", "cmd", "robot", 1, "draw", List.of(Client.msg("op", "point", "x", 1.0, "y", 1.0, "color", "red"))));
            assertTrue(((String) c.receive().get("message")).contains("\"color\""));
        } finally { sim.engine.stop(); sim.server.close(); }
    }

    @Test
    @Timeout(20)
    void closingTheServerTellsConnectedClients() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            sim.engine.stop();
            sim.server.close();
            Map<String, Object> m = c.receive();
            assertEquals("stopped", m.get("type"));
            assertEquals(1.0, m.get("robot"));
        }
    }

    @Test
    @Timeout(20)
    void remoteRobotExchangesRadioMessagesWithAJavaRobot() throws Exception {
        RemoteServer server = RemoteServer.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), RemoteServer.Mode.LOCKSTEP, 1000);
        List<String> log = new CopyOnWriteArrayList<>();
        server.setLogger(log::add);
        RobotDummy remote = server.createRobot(1, new Position(10, 20, 0), 2, 0.1);
        List<String> gotByJava = new CopyOnWriteArrayList<>();
        RobotDummy local = new io.github.raffaelegrandi.pianura.units.ControllerRobot(2, new Position(15, 20, Math.PI), 2, 0.1, (r, t) -> {
            for (io.github.raffaelegrandi.pianura.comm.Message m : r.receive()) gotByJava.add(m.from() + ":" + m.to() + ":" + m.payload());
            if (t == 0) r.broadcast(Map.of("ping", 2));
        });
        SimClock clock = new SimClock(Const.physicsStep);
        SimEngine engine = new SimEngine(clock, new CollisionDetector(List.of()), List.of(remote, local));
        engine.setLogger(log::add);
        engine.setCommSettings(new io.github.raffaelegrandi.pianura.comm.CommSettings().setRange(6));
        remote.attachClock(clock);
        local.attachClock(clock);

        List<Object> inbox = new ArrayList<>();
        try (Client c = new Client(server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            remote.start();
            local.start();
            Thread runner = new Thread(() -> engine.runUntil(1.0));
            runner.start();
            while (true) {
                Map<String, Object> s = c.receive();
                if (!"sensors".equals(s.get("type"))) continue;
                double t = (Double) s.get("t");
                inbox.addAll((List<?>) s.get("inbox"));
                Map<String, Object> cmd = Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"));
                if (t == 0) { cmd.put("v", 1.0); cmd.put("broadcast", Map.of("hi", "from remote")); }
                else if (Math.abs(t - 0.5) < 1e-9) cmd.put("send", List.of(Map.of("to", 2, "data", "unicast")));   // solo messaggi
                else cmd.put("v", 1.0);
                c.send(cmd);
                if (t >= 1.0 - 1e-9) break;
            }
            runner.join(15000);
            engine.stop();
            server.close();
        }
        // the remote client receives the Java robot's ping in the "inbox" field
        assertEquals(1, inbox.size(), inbox.toString());
        Map<?, ?> m = (Map<?, ?>) inbox.get(0);
        assertEquals(2.0, m.get("from"));
        assertEquals("all", m.get("to"));
        assertEquals(0.0, m.get("t"));
        assertEquals(Map.of("ping", 2.0), m.get("data"));
        // the Java robot receives the broadcast and the direct message, once each
        assertEquals(List.of("1:-1:{hi=from remote}", "1:2:unicast"), gotByJava);
        // a command with messages only keeps the previous speed
        assertEquals(11.0, remote.getPosition().x, 1e-6);
        assertTrue(log.stream().noneMatch(s -> s.contains("no reply")), log.toString());
    }

    @Test
    @Timeout(30)
    void lockstepRunsAreReproducible() throws Exception {
        double[] a = steeringRun(), b = steeringRun();
        assertArrayEquals(a, b, 0.0);
    }

    /** Client that steers by time: it must give the same result at every run. */
    private static double[] steeringRun() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            sim.runAsync(3.0);
            while (true) {
                Map<String, Object> s;
                try { s = c.receive(); } catch (SocketTimeoutException e) { break; }
                if (!"sensors".equals(s.get("type"))) continue;
                double t = (Double) s.get("t");
                c.send(Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"), "v", 1.0, "w", Math.sin(t * 3)));
                if (t >= 3.0 - 1e-9) break;
            }
            sim.finish();
        }
        Position p = sim.robot.getPosition();
        return new double[] { p.x, p.y, p.theta };
    }

}
