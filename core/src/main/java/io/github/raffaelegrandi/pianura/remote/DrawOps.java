// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.remote;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.github.raffaelegrandi.pianura.viz.Viz;
import io.github.raffaelegrandi.pianura.viz.VizLayer;

/**
 * The {@code "draw"} field of a remote command: a list of debug drawing operations, applied to
 * the robot's drawings ({@link Viz}) on the robot's thread, at the control instant of the command.
 *
 * <pre>
 *   "draw": [
 *     {"op": "clear", "layer": "plan"},
 *     {"op": "polyline", "layer": "plan", "xs": [5, 10, 20], "ys": [5, 8, 8], "color": "#2060ff"},
 *     {"op": "disc", "layer": "plan", "x": 20, "y": 8, "r": 0.3, "color": [255, 0, 0]},
 *     {"op": "text", "layer": "plan", "x": 20.5, "y": 8, "text": "goal"}
 *   ]
 * </pre>
 *
 * Every operation may carry {@code "layer"} (default {@code "default"}), {@code "color"}
 * ({@code "#rrggbb"}, {@code "#rrggbbaa"} or {@code [r, g, b]} / {@code [r, g, b, a]} 0-255) and
 * {@code "width"} (screen pixels). Coordinates are in cells.
 */
final class DrawOps {

    private DrawOps() {}

    /** Parses the whole list; any error rejects the command. */
    static List<Consumer<Viz>> parse(Object field) {
        if (!(field instanceof List<?> list)) throw new IllegalArgumentException("\"draw\" must be a list of operations");
        List<Consumer<Viz>> ops = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> m)) throw new IllegalArgumentException("draw[" + i + "] must be an object");
            try {
                ops.add(parseOne(m));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("draw[" + i + "]: " + e.getMessage());
            }
        }
        return ops;
    }

    private static Consumer<Viz> parseOne(Map<?, ?> m) {
        Object opObj = m.get("op");
        if (!(opObj instanceof String op)) throw new IllegalArgumentException("missing \"op\"");
        String layer = m.get("layer") instanceof String s ? s : Viz.DEFAULT_LAYER;
        if (op.equals("clear_all")) return Viz::clearAll;

        Color color = m.containsKey("color") ? color(m.get("color")) : null;
        double width = m.containsKey("width") ? num(m, "width") : Double.NaN;
        if (m.containsKey("width") && !(width > 0)) throw new IllegalArgumentException("\"width\" must be > 0");

        Consumer<VizLayer> shape = switch (op) {
            case "clear" -> VizLayer::clear;
            case "point" -> { double x = num(m, "x"), y = num(m, "y"); yield l -> l.point(x, y); }
            case "line", "arrow" -> {
                double x1 = num(m, "x1"), y1 = num(m, "y1"), x2 = num(m, "x2"), y2 = num(m, "y2");
                yield op.equals("line") ? l -> l.line(x1, y1, x2, y2) : l -> l.arrow(x1, y1, x2, y2);
            }
            case "circle", "disc" -> {
                double x = num(m, "x"), y = num(m, "y"), r = num(m, "r");
                if (!(r >= 0)) throw new IllegalArgumentException("\"r\" must be >= 0");
                yield op.equals("circle") ? l -> l.circle(x, y, r) : l -> l.disc(x, y, r);
            }
            case "rect" -> {
                double x = num(m, "x"), y = num(m, "y"), w = num(m, "w"), h = num(m, "h");
                yield l -> l.rect(x, y, w, h);
            }
            case "polyline", "polygon" -> {
                double[] xs = nums(m, "xs"), ys = nums(m, "ys");
                if (xs.length != ys.length || xs.length < 2) throw new IllegalArgumentException("\"xs\" and \"ys\" must have the same length, at least 2");
                yield op.equals("polyline") ? l -> l.polyline(xs, ys) : l -> l.polygon(xs, ys);
            }
            case "text" -> {
                double x = num(m, "x"), y = num(m, "y");
                Object t = m.get("text");
                String text = t instanceof String s ? s : t instanceof Double d && d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(t);
                yield l -> l.text(x, y, text);
            }
            default -> throw new IllegalArgumentException("unknown op \"" + op + "\" (clear, clear_all, point, line, arrow, circle, disc, rect, polyline, polygon, text)");
        };
        return viz -> {
            VizLayer l = viz.layer(layer);
            if (color != null) l.color(color);
            if (!Double.isNaN(width)) l.width(width);
            shape.accept(l);
        };
    }

    private static double num(Map<?, ?> m, String key) {
        if (!(m.get(key) instanceof Number n) || !Double.isFinite(n.doubleValue()))
            throw new IllegalArgumentException("\"" + key + "\" must be a number");
        return n.doubleValue();
    }

    private static double[] nums(Map<?, ?> m, String key) {
        if (!(m.get(key) instanceof List<?> list)) throw new IllegalArgumentException("\"" + key + "\" must be a list of numbers");
        double[] v = new double[list.size()];
        for (int i = 0; i < v.length; i++) {
            if (!(list.get(i) instanceof Number n) || !Double.isFinite(n.doubleValue()))
                throw new IllegalArgumentException("\"" + key + "\" must be a list of numbers");
            v[i] = n.doubleValue();
        }
        return v;
    }

    static Color color(Object c) {
        if (c instanceof String s && s.startsWith("#") && (s.length() == 7 || s.length() == 9)) {
            try {
                int r = Integer.parseInt(s.substring(1, 3), 16), g = Integer.parseInt(s.substring(3, 5), 16), b = Integer.parseInt(s.substring(5, 7), 16);
                int a = s.length() == 9 ? Integer.parseInt(s.substring(7, 9), 16) : 255;
                return new Color(r, g, b, a);
            } catch (NumberFormatException e) { /* below */ }
        }
        if (c instanceof List<?> l && (l.size() == 3 || l.size() == 4)) {
            int[] v = new int[4];
            v[3] = 255;
            for (int i = 0; i < l.size(); i++) {
                if (!(l.get(i) instanceof Number n)) throw new IllegalArgumentException("\"color\" components must be numbers 0-255");
                v[i] = (int) Math.round(n.doubleValue());
                if (v[i] < 0 || v[i] > 255) throw new IllegalArgumentException("\"color\" components must be numbers 0-255");
            }
            return new Color(v[0], v[1], v[2], v[3]);
        }
        throw new IllegalArgumentException("\"color\" must be \"#rrggbb\", \"#rrggbbaa\" or [r, g, b(, a)] with 0-255 values");
    }
}
