// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.units.collisions;

/**
 * Geometric shapes used for collision detection. Ray casting replaced JUSE's pixel
 * sampling, which removed the GUI from the physics loop.
 */
public interface CollidableShape {

    /**
     * Intersection of a ray with this shape.
     *
     * @param ox    X of the ray's origin (pixels)
     * @param oy    Y of the ray's origin (pixels)
     * @param dx    X component of the direction (unit vector)
     * @param dy    Y component of the direction (unit vector)
     * @param maxDist maximum detection distance (pixels)
     * @return distance in pixels from the origin to the intersection,
     *         0 if the origin is already inside the shape,
     *         -1 if there is no intersection within maxDist
     */
    double rayIntersect(double ox, double oy, double dx, double dy, double maxDist);

    /**
     * Outline of the shape in pixels, in the same coordinates used for ray casting. Used for
     * shape-against-shape tests (e.g. overlapping obstacles) and to draw exactly what the
     * simulator uses for collisions.
     */
    java.awt.Shape outline();

}
