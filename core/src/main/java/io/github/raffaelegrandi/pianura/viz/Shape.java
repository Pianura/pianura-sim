package io.github.raffaelegrandi.pianura.viz;

import java.awt.Color;

/**
 * One debug shape, in world coordinates (cells). Immutable.
 *
 * <p>Coordinates by kind:
 * <ul>
 *   <li>{@code POINT}: x, y</li>
 *   <li>{@code LINE}, {@code ARROW}: x1, y1, x2, y2 (the arrow points at x2, y2)</li>
 *   <li>{@code CIRCLE} (outline), {@code DISC} (filled): x, y, r</li>
 *   <li>{@code RECT} (filled): x, y of the top-left corner, w, h</li>
 *   <li>{@code POLYLINE}, {@code POLYGON} (closed outline): x0, y0, x1, y1, ...</li>
 *   <li>{@code TEXT}: x, y of the left end of the baseline, plus {@link #text()}</li>
 * </ul>
 *
 * @param width stroke width in screen pixels (the same at any zoom)
 */
public record Shape(Kind kind, double[] coords, String text, Color color, float width) {

    public enum Kind { POINT, LINE, ARROW, CIRCLE, DISC, RECT, POLYLINE, POLYGON, TEXT }

    public Shape {
        coords = coords.clone();
    }

    /** Coordinate {@code i} (see the table above). */
    public double c(int i) { return coords[i]; }

    /** Number of coordinates. */
    public int size() { return coords.length; }

    @Override
    public double[] coords() { return coords.clone(); }
}
