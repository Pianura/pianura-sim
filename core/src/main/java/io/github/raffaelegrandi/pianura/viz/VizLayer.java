// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.viz;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * A named layer of debug drawings of one robot: what the algorithm "thinks" (a planned path,
 * a search tree, an estimated map, the chosen direction), drawn over the arena.
 *
 * <p>Coordinates are world coordinates in cells, as for {@code getPosition()}. Shapes stay until
 * the layer is cleared, so a layer can either be redrawn at every control step
 * ({@code clear()} first) or grow over time (a trail, a tree, a map).
 *
 * <pre>
 *   robot.viz("plan").clear().color(Color.BLUE).polyline(xs, ys)
 *        .color(Color.RED).disc(goalX, goalY, 0.3).text(goalX + 0.5, goalY, "goal");
 * </pre>
 *
 * Every method returns the layer, so calls can be chained. The current color and width apply to
 * the shapes added after them. Use it from the robot's own code (its control loop or controller):
 * what is drawn appears at the robot's next {@code sleepSim()}, all at once.
 */
public final class VizLayer {

    /** Default color: orange, visible on the default floors. */
    public static final Color DEFAULT_COLOR = new Color(255, 120, 0);

    private final Viz owner;
    private final String name;
    private final ArrayList<Shape> shapes = new ArrayList<>();
    private Color color = DEFAULT_COLOR;
    private float width = 1.5f;
    private boolean dirty = false;
    private List<Shape> published = List.of();

    VizLayer(Viz owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    public String name() { return name; }

    // ------------------------------------------------------------------ style

    /** Color of the next shapes (alpha is kept: use it for see-through maps). */
    public VizLayer color(Color c) {
        if (c == null) throw new IllegalArgumentException("color must not be null");
        color = c;
        return this;
    }

    /** Color of the next shapes, components 0-255. */
    public VizLayer color(int r, int g, int b) { return color(new Color(r, g, b)); }

    /** Color of the next shapes, components 0-255, alpha 0 (invisible) to 255 (opaque). */
    public VizLayer color(int r, int g, int b, int alpha) { return color(new Color(r, g, b, alpha)); }

    /** Stroke width of the next lines and outlines, in screen pixels. */
    public VizLayer width(double pixels) {
        if (!(pixels > 0)) throw new IllegalArgumentException("width must be > 0");
        width = (float) pixels;
        return this;
    }

    // ------------------------------------------------------------------ shapes

    /** Removes every shape of this layer (color and width are kept). */
    public VizLayer clear() {
        owner.removed(shapes.size());
        shapes.clear();
        dirty = true;
        return this;
    }

    /** A dot at (x, y), a few pixels wide at any zoom. */
    public VizLayer point(double x, double y) { return add(Shape.Kind.POINT, null, x, y); }

    public VizLayer line(double x1, double y1, double x2, double y2) { return add(Shape.Kind.LINE, null, x1, y1, x2, y2); }

    /** A line with an arrowhead at (x2, y2). */
    public VizLayer arrow(double x1, double y1, double x2, double y2) { return add(Shape.Kind.ARROW, null, x1, y1, x2, y2); }

    /** Circle outline, radius in cells. */
    public VizLayer circle(double x, double y, double r) { return add(Shape.Kind.CIRCLE, null, x, y, r); }

    /** Filled circle, radius in cells. */
    public VizLayer disc(double x, double y, double r) { return add(Shape.Kind.DISC, null, x, y, r); }

    /**
     * Filled rectangle with its top-left corner at (x, y), axis-aligned. One cell of a grid map:
     * {@code rect(i, j, 1, 1)}.
     */
    public VizLayer rect(double x, double y, double w, double h) { return add(Shape.Kind.RECT, null, x, y, w, h); }

    /** Open line through the points (xs[k], ys[k]). */
    public VizLayer polyline(double[] xs, double[] ys) { return add(Shape.Kind.POLYLINE, null, interleave(xs, ys)); }

    /** Closed outline through the points (xs[k], ys[k]). */
    public VizLayer polygon(double[] xs, double[] ys) { return add(Shape.Kind.POLYGON, null, interleave(xs, ys)); }

    /** Text with the left end of its baseline at (x, y); same size at any zoom. */
    public VizLayer text(double x, double y, String text) {
        return add(Shape.Kind.TEXT, text == null ? "null" : text, x, y);
    }

    // ------------------------------------------------------------------ internals

    /** Number of shapes in the layer now (not yet published ones included). */
    public int size() { return shapes.size(); }

    private VizLayer add(Shape.Kind kind, String text, double... c) {
        for (double v : c) if (!Double.isFinite(v)) throw new IllegalArgumentException(kind + ": coordinates must be finite numbers");
        if (!owner.reserve(1)) return this;    // over the robot's limit: dropped (reported once)
        shapes.add(new Shape(kind, c, text, color, width));
        dirty = true;
        return this;
    }

    private static double[] interleave(double[] xs, double[] ys) {
        if (xs == null || ys == null || xs.length != ys.length) throw new IllegalArgumentException("xs and ys must have the same length");
        if (xs.length < 2) throw new IllegalArgumentException("at least two points are needed");
        double[] c = new double[2 * xs.length];
        for (int i = 0; i < xs.length; i++) { c[2 * i] = xs[i]; c[2 * i + 1] = ys[i]; }
        return c;
    }

    /** Called by Viz when publishing: the shapes to show, copied only if something changed. */
    List<Shape> snapshot() {
        if (dirty) {
            published = List.copyOf(shapes);
            dirty = false;
        }
        return published;
    }

    boolean isDirty() { return dirty; }
}
