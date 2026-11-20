// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.units.collisions;

/**
 * Rotated rectangle for geometric collision detection. The position is the rectangle's
 * centre (pixels); the rotation angle is in radians.
 *
 * Slab method (AABB in the rectangle's local frame): the ray is transformed into the
 * rectangle's frame (centred at the origin, axis-aligned), then tested against the two slabs.
 */
public class RectShape implements CollidableShape {

    private final double cx, cy;  // centre in pixels
    private final double hw, hh;  // half width and half height in pixels
    private final double cosA, sinA;  // cos(-angle), sin(-angle) for the inverse transformation
    private final double angle;

    /**
     * @param cx     centre X in pixels
     * @param cy     centre Y in pixels
     * @param width  width in pixels
     * @param height height in pixels
     * @param angle  rotation angle in radians
     */
    public RectShape(double cx, double cy, double width, double height, double angle) {
        this.cx = cx;
        this.cy = cy;
        this.hw = width / 2.0;
        this.hh = height / 2.0;
        this.cosA = Math.cos(-angle);
        this.sinA = Math.sin(-angle);
        this.angle = angle;
    }

    @Override
    public java.awt.Shape outline() {
        java.awt.geom.AffineTransform at = java.awt.geom.AffineTransform.getTranslateInstance(cx, cy);
        at.rotate(angle);   // screen coordinates: positive angle = clockwise, as for the robots
        return at.createTransformedShape(new java.awt.geom.Rectangle2D.Double(-hw, -hh, 2 * hw, 2 * hh));
    }

    @Override
    public double rayIntersect(double ox, double oy, double dx, double dy, double maxDist) {
        // Transform the ray into the rectangle's local frame (inverse rotation)
        double rx = ox - cx;
        double ry = oy - cy;
        double lox = cosA * rx - sinA * ry;
        double loy = sinA * rx + cosA * ry;
        double ldx = cosA * dx - sinA * dy;
        double ldy = sinA * dx + cosA * dy;

        // Slab method against the AABB [-hw, hw] x [-hh, hh]
        double tmin = -Double.MAX_VALUE;
        double tmax =  Double.MAX_VALUE;

        // Slab X
        if (Math.abs(ldx) < 1e-10) {
            if (lox < -hw || lox > hw) return -1;
        } else {
            double t1 = (-hw - lox) / ldx;
            double t2 = ( hw - lox) / ldx;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }

        // Slab Y
        if (Math.abs(ldy) < 1e-10) {
            if (loy < -hh || loy > hh) return -1;
        } else {
            double t1 = (-hh - loy) / ldy;
            double t2 = ( hh - loy) / ldy;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }

        // The shape is entirely behind the ray
        if (tmax < 0) return -1;

        // Origin inside the rectangle: immediate contact, wherever the exit point is
        // (it used to be discarded when the exit was beyond maxDist: a robot with its centre inside
        // a large rectangle looked "free" both to its sensors and to the validation)
        if (tmin < 0) return 0;

        return (tmin <= maxDist) ? tmin : -1;
    }

}
