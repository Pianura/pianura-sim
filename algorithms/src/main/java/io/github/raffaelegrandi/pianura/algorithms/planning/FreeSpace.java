// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.algorithms.planning;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.ObjectID;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * The map a planner knows: the arena and its obstacles, in cells, with a clearance around
 * the robot. A point is free when a square of side {@code 2 * clearance} centred on it touches
 * no obstacle and stays inside the arena (a square, not a circle: slightly cautious, and cheap).
 *
 * <p>It is a snapshot of the obstacles taken when it is built: a planner builds it when it
 * starts, so obstacles moved with the mouse before Run are taken into account.
 */
public final class FreeSpace {

    private final List<Shape> shapes = new ArrayList<>();
    private final double width, height, clearance;

    /**
     * @param obstacles the scenario's obstacles
     * @param width     arena width in cells
     * @param height    arena height in cells
     * @param clearance distance to keep from obstacles and walls, cells (robot radius plus a margin)
     */
    public FreeSpace(List<Obstacle> obstacles, double width, double height, double clearance) {
        this.width = width;
        this.height = height;
        this.clearance = clearance;
        for (Obstacle o : obstacles) shapes.add(shapeOf(o));
    }

    /** Obstacle outline in cells: rectangle or ellipse centred on its position, rotated by theta. */
    static Shape shapeOf(Obstacle o) {
        ObjectID id = o.getID();
        Position p = id.getPos();
        double w = id.getWidth(), h = id.getHeight();
        Shape s = id.getType() == ArenaObjectType.OBSTACLE_OVAL
            ? new Ellipse2D.Double(p.x - w / 2, p.y - h / 2, w, h)
            : new Rectangle2D.Double(p.x - w / 2, p.y - h / 2, w, h);
        return p.theta == 0 ? s : AffineTransform.getRotateInstance(p.theta, p.x, p.y).createTransformedShape(s);
    }

    public double width() { return width; }

    public double height() { return height; }

    public double clearance() { return clearance; }

    /** True if the robot can stand at (x, y). */
    public boolean isFree(double x, double y) {
        double c = clearance;
        if (x < c || y < c || x > width - c || y > height - c) return false;
        for (Shape s : shapes) if (s.intersects(x - c, y - c, 2 * c, 2 * c)) return false;
        return true;
    }

    /** True if the robot can move in a straight line from (x1, y1) to (x2, y2) (checked every quarter cell). */
    public boolean isFree(double x1, double y1, double x2, double y2) {
        double d = Math.hypot(x2 - x1, y2 - y1);
        int n = Math.max(1, (int) Math.ceil(d / 0.25));
        for (int i = 0; i <= n; i++) {
            double k = (double) i / n;
            if (!isFree(x1 + (x2 - x1) * k, y1 + (y2 - y1) * k)) return false;
        }
        return true;
    }
}
