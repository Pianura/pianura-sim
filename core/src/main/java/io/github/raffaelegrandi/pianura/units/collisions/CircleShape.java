// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.units.collisions;

/**
 * Circle for geometric collision detection (pixels). Represents the robots' bodies: the
 * {@code owner} field lets the detector skip the robot itself in its own ray casting.
 */
public class CircleShape implements CollidableShape {

    private final double cx, cy, r;
    private final Object owner;

    public CircleShape(double cx, double cy, double radius, Object owner) {
        this.cx = cx;
        this.cy = cy;
        this.r = radius;
        this.owner = owner;
    }

    public CircleShape(double cx, double cy, double radius) { this(cx, cy, radius, null); }

    public Object getOwner() { return owner; }
    public double getCx() { return cx; }
    public double getCy() { return cy; }
    public double getRadius() { return r; }

    @Override
    public java.awt.Shape outline() {
        return new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
    }

    @Override
    public double rayIntersect(double ox, double oy, double dx, double dy, double maxDist) {
        double rx = ox - cx;
        double ry = oy - cy;
        // |d| = 1  →  t² + B t + C = 0
        double B = 2.0 * (rx * dx + ry * dy);
        double C = rx * rx + ry * ry - r * r;
        double disc = B * B - 4.0 * C;
        if (disc < 0) return -1;

        double sqrtDisc = Math.sqrt(disc);
        double t1 = (-B - sqrtDisc) / 2.0;
        double t2 = (-B + sqrtDisc) / 2.0;

        if (t2 < 0) return -1;      // circle behind the origin
        if (t1 < 0) return 0;       // origin inside the circle
        return (t1 <= maxDist) ? t1 : -1;
    }

}
