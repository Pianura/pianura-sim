package io.github.raffaelegrandi.pianura.gui.view;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * World → screen transformation of the view: {@code screen = world * scale + offset}.
 * The world is in world pixels (cells x Const.cellSize); the screen in panel pixels.
 * No dependency on Swing: it is the testable part of the view.
 */
public final class ViewTransform {

    public static final double MIN_SCALE = 0.05;
    public static final double MAX_SCALE = 20;

    private double scale = 1, offX = 0, offY = 0;

    public double getScale() { return scale; }
    public double getOffsetX() { return offX; }
    public double getOffsetY() { return offY; }

    public AffineTransform asAffine() {
        AffineTransform at = AffineTransform.getTranslateInstance(offX, offY);
        at.scale(scale, scale);
        return at;
    }

    public Point2D toScreen(double wx, double wy) { return new Point2D.Double(wx * scale + offX, wy * scale + offY); }

    public Point2D toWorld(double sx, double sy) { return new Point2D.Double((sx - offX) / scale, (sy - offY) / scale); }

    /** Fits the world rectangle to the view, centred, with a margin in screen pixels. */
    public void fit(Rectangle2D world, double viewW, double viewH, double paddingPx) {
        double availW = Math.max(1, viewW - 2 * paddingPx), availH = Math.max(1, viewH - 2 * paddingPx);
        scale = clamp(Math.min(availW / world.getWidth(), availH / world.getHeight()));
        offX = (viewW - world.getWidth() * scale) / 2 - world.getX() * scale;
        offY = (viewH - world.getHeight() * scale) / 2 - world.getY() * scale;
    }

    /** Multiplies the zoom by {@code factor}, keeping the screen point (sx, sy) still. */
    public void zoomAt(double factor, double sx, double sy) {
        setScaleAt(scale * factor, sx, sy);
    }

    /** Sets the zoom, keeping the screen point (sx, sy) still. */
    public void setScaleAt(double newScale, double sx, double sy) {
        Point2D w = toWorld(sx, sy);
        scale = clamp(newScale);
        offX = sx - w.getX() * scale;
        offY = sy - w.getY() * scale;
    }

    /** Moves the view by (dx, dy) screen pixels. */
    public void pan(double dx, double dy) {
        offX += dx;
        offY += dy;
    }

    /** World point at the centre of a view of {@code viewW x viewH} pixels. */
    public Point2D center(double viewW, double viewH) { return toWorld(viewW / 2, viewH / 2); }

    /** Brings the world point (wx, wy) to the centre of the view, without changing the zoom. */
    public void centerOn(double wx, double wy, double viewW, double viewH) {
        offX = viewW / 2 - wx * scale;
        offY = viewH / 2 - wy * scale;
    }

    /**
     * Moves the centre of the view towards the point (wx, wy) by the fraction {@code f}
     * (0 = still, 1 = at once): used to follow a robot smoothly.
     */
    public void moveCenterToward(double wx, double wy, double viewW, double viewH, double f) {
        Point2D c = center(viewW, viewH);
        centerOn(c.getX() + (wx - c.getX()) * f, c.getY() + (wy - c.getY()) * f, viewW, viewH);
    }

    /**
     * Keeps the centre of the view inside the world rectangle: you can fly over the map but not
     * get lost in the void around it.
     */
    public void clampCenter(Rectangle2D world, double viewW, double viewH) {
        Point2D c = center(viewW, viewH);
        double x = Math.max(world.getMinX(), Math.min(world.getMaxX(), c.getX()));
        double y = Math.max(world.getMinY(), Math.min(world.getMaxY(), c.getY()));
        if (x != c.getX() || y != c.getY()) centerOn(x, y, viewW, viewH);
    }

    /**
     * Scroll direction when the pointer is near the edge of the view: {-1, 0, +1} for x and for y
     * (+1 = towards the right / the bottom of the world).
     */
    public static int[] edgeDirection(double x, double y, double viewW, double viewH, double marginPx) {
        int dx = x < marginPx ? -1 : (x > viewW - marginPx ? 1 : 0);
        int dy = y < marginPx ? -1 : (y > viewH - marginPx ? 1 : 0);
        return new int[] { dx, dy };
    }

    private static double clamp(double s) { return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s)); }

}
