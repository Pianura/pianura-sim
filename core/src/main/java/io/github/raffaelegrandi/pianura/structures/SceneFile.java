// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.structures;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.ObjectID;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.remote.MiniJson;
import io.github.raffaelegrandi.pianura.units.RobotUnit;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * A scene on file: the arena size and scale, the obstacles and where the robots stand.
 * JSON, readable and editable by hand:
 *
 * <pre>
 * {
 *   "pianura_scene": 1,
 *   "arena": {"width": 40, "height": 40, "meters_per_cell": 0.05},
 *   "obstacles": [
 *     {"shape": "rect", "x": 20, "y": 12, "theta": 0, "width": 6, "height": 2, "color": "#808080"},
 *     {"shape": "oval", "x": 8, "y": 30, "theta": 0.5, "width": 4, "height": 3, "color": "#4060c0", "border": "#000000", "border_width": 2}
 *   ],
 *   "robots": [
 *     {"id": 1, "x": 5, "y": 5, "theta": 0}
 *   ]
 * }
 * </pre>
 *
 * Lengths in cells, angles in radians, positions of the centres. The robots' code is not in the
 * file: a scene gives the stage, the scenario gives the actors. The floor is not saved either.
 */
public final class SceneFile {

    /** Version of the file format. */
    public static final int FORMAT = 1;

    /** Where a robot stands, by id. */
    public record RobotPose(int id, double x, double y, double theta) {}

    /** Contents of a scene file. */
    public record Scene(double arenaWidth, double arenaHeight, double metersPerCell,
                        List<Obstacle> obstacles, List<RobotPose> robots) {}

    private SceneFile() {}

    // ------------------------------------------------------------------ writing

    /** Writes the scene to {@code file} (UTF-8, one obstacle and one robot per line). */
    public static void write(Path file, double arenaWidth, double arenaHeight, double metersPerCell,
                             List<Obstacle> obstacles, List<? extends RobotUnit> robots) throws IOException {
        Files.writeString(file, toJson(arenaWidth, arenaHeight, metersPerCell, obstacles, robots), StandardCharsets.UTF_8);
    }

    public static String toJson(double arenaWidth, double arenaHeight, double metersPerCell,
                                List<Obstacle> obstacles, List<? extends RobotUnit> robots) {
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"pianura_scene\": ").append(FORMAT).append(",\n");
        b.append("  \"arena\": {\"width\": ").append(n(arenaWidth)).append(", \"height\": ").append(n(arenaHeight))
         .append(", \"meters_per_cell\": ").append(n(metersPerCell)).append("},\n");
        b.append("  \"obstacles\": [");
        for (int i = 0; i < obstacles.size(); i++) {
            Obstacle o = obstacles.get(i);
            ObjectID id = o.getID();
            Position p = id.getPos();
            b.append(i == 0 ? "\n" : ",\n").append("    {\"shape\": \"")
             .append(id.getType() == ArenaObjectType.OBSTACLE_OVAL ? "oval" : "rect").append('"')
             .append(", \"x\": ").append(n(p.x)).append(", \"y\": ").append(n(p.y)).append(", \"theta\": ").append(n(p.theta))
             .append(", \"width\": ").append(n(id.getWidth())).append(", \"height\": ").append(n(id.getHeight()))
             .append(", \"color\": \"").append(hex(id.getColor())).append('"');
            if (o.getCustomBorderColor() != null) b.append(", \"border\": \"").append(hex(o.getCustomBorderColor())).append('"');
            if (o.getBorderWidth() != Obstacle.DEFAULT_BORDER_WIDTH) b.append(", \"border_width\": ").append(n(o.getBorderWidth()));
            b.append('}');
        }
        b.append(obstacles.isEmpty() ? "],\n" : "\n  ],\n");
        b.append("  \"robots\": [");
        for (int i = 0; i < robots.size(); i++) {
            RobotUnit r = robots.get(i);
            Position p = r.getPosition();
            b.append(i == 0 ? "\n" : ",\n").append("    {\"id\": ").append(r.getIDnum())
             .append(", \"x\": ").append(n(p.x)).append(", \"y\": ").append(n(p.y)).append(", \"theta\": ").append(n(p.theta)).append('}');
        }
        b.append(robots.isEmpty() ? "]\n" : "\n  ]\n");
        return b.append("}\n").toString();
    }

    /** Numbers rounded to 1/10000 (of a cell or a radian), written without useless zeros. */
    private static String n(double v) {
        double r = Math.round(v * 10000) / 10000.0;
        if (r == Math.rint(r) && Math.abs(r) < 1e15) return String.valueOf((long) r);
        String s = String.format(Locale.ROOT, "%.4f", r);
        return s.replaceAll("0+$", "");
    }

    private static String hex(Color c) {
        String s = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
        return c.getAlpha() == 255 ? s : s + String.format("%02x", c.getAlpha());
    }

    // ------------------------------------------------------------------ reading

    public static Scene read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Reads a scene, e.g. from a resource: {@code SceneFile.read(getClass().getResourceAsStream("arena.json"))}. */
    public static Scene read(InputStream in) throws IOException {
        if (in == null) throw new IOException("scene not found (null stream)");
        try (in) { return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)); }
    }

    /** @throws IllegalArgumentException with a message that says what is wrong and where */
    public static Scene parse(String json) {
        Map<String, Object> root;
        try { root = MiniJson.parseObject(json); }
        catch (RuntimeException e) { throw new IllegalArgumentException("not a valid JSON object: " + e.getMessage()); }
        if (!(root.get("pianura_scene") instanceof Double v)) throw new IllegalArgumentException("not a Pianura scene (missing \"pianura_scene\")");
        if (v > FORMAT) throw new IllegalArgumentException("scene format " + v.intValue() + " is newer than this Pianura (" + FORMAT + ")");

        Map<?, ?> arena = obj(root, "arena", "");
        double w = num(arena, "width", "arena"), h = num(arena, "height", "arena");
        if (!(w > 0 && h > 0)) throw new IllegalArgumentException("arena: width and height must be > 0");
        double mpc = arena.containsKey("meters_per_cell") ? num(arena, "meters_per_cell", "arena") : io.github.raffaelegrandi.pianura.utils.Const.DEFAULT_METERS_PER_CELL;
        if (!(mpc > 0)) throw new IllegalArgumentException("arena: meters_per_cell must be > 0");

        List<Obstacle> obstacles = new ArrayList<>();
        List<?> ol = list(root, "obstacles");
        for (int i = 0; i < ol.size(); i++) {
            String where = "obstacles[" + i + "]";
            if (!(ol.get(i) instanceof Map<?, ?> m)) throw new IllegalArgumentException(where + " must be an object");
            String shape = m.get("shape") instanceof String s ? s : "rect";
            ArenaObjectType type = switch (shape) {
                case "rect" -> ArenaObjectType.OBSTACLE_RECT;
                case "oval" -> ArenaObjectType.OBSTACLE_OVAL;
                default -> throw new IllegalArgumentException(where + ": shape must be \"rect\" or \"oval\"");
            };
            double ow = num(m, "width", where), oh = num(m, "height", where);
            if (!(ow > 0 && oh > 0)) throw new IllegalArgumentException(where + ": width and height must be > 0");
            Position p = new Position(num(m, "x", where), num(m, "y", where), m.containsKey("theta") ? num(m, "theta", where) : 0);
            Color fill = m.containsKey("color") ? color(m.get("color"), where) : Color.GRAY;
            Obstacle o = new Obstacle(type, fill, p, ow, oh);
            if (m.containsKey("border") || m.containsKey("border_width")) {
                Color border = m.containsKey("border") ? color(m.get("border"), where) : o.getBorderColor();
                double bw = m.containsKey("border_width") ? num(m, "border_width", where) : Obstacle.DEFAULT_BORDER_WIDTH;
                o.withBorder(border, bw);
            }
            obstacles.add(o);
        }

        List<RobotPose> robots = new ArrayList<>();
        List<?> rl = list(root, "robots");
        for (int i = 0; i < rl.size(); i++) {
            String where = "robots[" + i + "]";
            if (!(rl.get(i) instanceof Map<?, ?> m)) throw new IllegalArgumentException(where + " must be an object");
            double id = num(m, "id", where);
            if (id != Math.rint(id)) throw new IllegalArgumentException(where + ": id must be an integer");
            robots.add(new RobotPose((int) id, num(m, "x", where), num(m, "y", where), m.containsKey("theta") ? num(m, "theta", where) : 0));
        }
        return new Scene(w, h, mpc, List.copyOf(obstacles), List.copyOf(robots));
    }

    private static Map<?, ?> obj(Map<?, ?> m, String key, String where) {
        if (!(m.get(key) instanceof Map<?, ?> o)) throw new IllegalArgumentException(where + "missing \"" + key + "\" object");
        return o;
    }

    private static List<?> list(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v == null) return List.of();
        if (!(v instanceof List<?> l)) throw new IllegalArgumentException("\"" + key + "\" must be a list");
        return l;
    }

    private static double num(Map<?, ?> m, String key, String where) {
        if (!(m.get(key) instanceof Number n) || !Double.isFinite(n.doubleValue()))
            throw new IllegalArgumentException(where + ": \"" + key + "\" must be a number");
        return n.doubleValue();
    }

    private static Color color(Object c, String where) {
        if (c instanceof String s && s.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            int r = Integer.parseInt(s.substring(1, 3), 16), g = Integer.parseInt(s.substring(3, 5), 16), b = Integer.parseInt(s.substring(5, 7), 16);
            return new Color(r, g, b, s.length() == 9 ? Integer.parseInt(s.substring(7, 9), 16) : 255);
        }
        throw new IllegalArgumentException(where + ": colors must be \"#rrggbb\" or \"#rrggbbaa\"");
    }
}
