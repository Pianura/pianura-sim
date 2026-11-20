package io.github.raffaelegrandi.pianura.units.collisions;

/**
 * Ellipse for geometric collision detection, possibly rotated. The position is the
 * ellipse's centre (pixels); the angle follows the robots' convention (screen
 * coordinates, positive = clockwise).
 *
 * The ray is brought into the ellipse's frame (inverse rotation), where the ellipse is
 * axis-aligned; then the ray's parametric equation is substituted into the ellipse's
 * equation and the quadratic is solved:
 *
 * With origin (ox, oy) and unit direction (dx, dy), in the local frame:
 *   A·t² + B·t + C = 0
 * where:
 *   A = dx²/a² + dy²/b²
 *   B = 2·(rx·dx/a² + ry·dy/b²)    rx = ox - cx
 *   C = rx²/a² + ry²/b² - 1        ry = oy - cy
 *
 * The parameter t is the real Euclidean distance, since (dx, dy) is a unit vector
 * (rotation preserves lengths).
 */
public class OvalShape implements CollidableShape {

    private final double cx, cy;      // centro in pixel
    private final double a, b;        // semi-assi in pixel
    private final double angle;
    private final double cosA, sinA;  // cos(-angle), sin(-angle) for the inverse transformation

    /** Axis-aligned ellipse. */
    public OvalShape(double cx, double cy, double width, double height) {
        this(cx, cy, width, height, 0);
    }

    /**
     * @param cx     centro X in pixel
     * @param cy     centro Y in pixel
     * @param width  larghezza dell'ellisse (asse x locale) in pixel
     * @param height altezza dell'ellisse (asse y locale) in pixel
     * @param angle  rotazione in radianti
     */
    public OvalShape(double cx, double cy, double width, double height, double angle) {
        this.cx = cx;
        this.cy = cy;
        this.a = width / 2.0;
        this.b = height / 2.0;
        this.angle = angle;
        this.cosA = Math.cos(-angle);
        this.sinA = Math.sin(-angle);
    }

    @Override
    public java.awt.Shape outline() {
        java.awt.Shape e = new java.awt.geom.Ellipse2D.Double(-a, -b, 2 * a, 2 * b);
        java.awt.geom.AffineTransform at = java.awt.geom.AffineTransform.getTranslateInstance(cx, cy);
        at.rotate(angle);
        return at.createTransformedShape(e);
    }

    @Override
    public double rayIntersect(double ox, double oy, double dx0, double dy0, double maxDist) {
        // riferimento locale: centro nell'origine, assi dell'ellisse allineati
        double px = ox - cx, py = oy - cy;
        double rx = cosA * px - sinA * py;
        double ry = sinA * px + cosA * py;
        double dx = cosA * dx0 - sinA * dy0;
        double dy = sinA * dx0 + cosA * dy0;

        double A = (dx * dx) / (a * a) + (dy * dy) / (b * b);
        double B = 2.0 * (rx * dx / (a * a) + ry * dy / (b * b));
        double C = (rx * rx) / (a * a) + (ry * ry) / (b * b) - 1.0;

        double disc = B * B - 4.0 * A * C;
        if (disc < 0) return -1;  // nessuna intersezione

        double sqrtDisc = Math.sqrt(disc);
        double t1 = (-B - sqrtDisc) / (2.0 * A);
        double t2 = (-B + sqrtDisc) / (2.0 * A);

        // Both intersections are behind the origin
        if (t2 < 0) return -1;

        // Origine dentro l'ellisse (t1 < 0 < t2): intersezione immediata
        if (t1 < 0) return 0;

        return (t1 <= maxDist) ? t1 : -1;
    }

}
