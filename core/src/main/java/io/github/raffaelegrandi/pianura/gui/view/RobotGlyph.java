// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Drawing of a robot seen from above (body, two wheels with treads, nose, caster) and of
 * its "speech bubble" label.
 *
 * The robot is drawn in world coordinates with a rotation + translation; the label in screen
 * coordinates, always upright, {@code distanceFromCenter} robot radii from the centre, always
 * at the top left at 45° on screen (it does not turn with the robot): 0 = over the centre,
 * 1 = on the edge.
 */
public final class RobotGlyph {

    private RobotGlyph() {}

    public static final Color NOSE = new Color(240, 190, 40);
    private static final Color WHEEL = new Color(85, 85, 85);
    private static final Color TREAD = new Color(160, 160, 160);
    private static final Color CASTER = new Color(125, 125, 125);
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    /** Below this radius on screen (pixels) the robot is drawn simplified: disc + nose. */
    public static final double DETAIL_MIN_SCREEN_RADIUS = 7;

    /**
     * Robot in the local frame, already translated and rotated (x axis = direction of travel).
     * @param R             radius in world units
     * @param px            one screen pixel in world units
     * @param leftTravel    distance travelled by the left wheel (world units): scrolls the tread
     * @param rightTravel   distance travelled by the right wheel
     */
    public static void paintBody(Graphics2D g, double R, double px, Color body, double leftTravel, double rightTravel) {
        boolean detailed = R / px >= DETAIL_MIN_SCREEN_RADIUS;

        g.setColor(body);
        g.fill(new Ellipse2D.Double(-R, -R, 2 * R, 2 * R));
        // nose: colored sector in front
        g.setColor(NOSE);
        g.fill(new Arc2D.Double(-R, -R, 2 * R, 2 * R, -35, 70, Arc2D.PIE));
        if (detailed) {
            // wheels over the body, sticking out of the edge as in a top view of a real robot
            // (left = negative y: y grows downwards and the robot faces +x)
            // wheels near the centre along the axis: they stick out little from the body's circle
            double wl = 0.90 * R, ww = 0.34 * R, wy = 0.78 * R;
            paintWheel(g, -wy, wl, ww, px, leftTravel);
            paintWheel(g, wy, wl, ww, px, rightTravel);
            // rear caster
            double c = 0.16 * R;
            g.setColor(CASTER);
            g.fill(new Ellipse2D.Double(-0.72 * R - c, -c, 2 * c, 2 * c));
        }
    }

    private static void paintWheel(Graphics2D g, double cy, double len, double wid, double px, double travel) {
        RoundRectangle2D wheel = new RoundRectangle2D.Double(-len / 2, cy - wid / 2, len, wid, wid * 0.8, wid * 0.8);
        g.setColor(WHEEL);
        g.fill(wheel);
        // tread: lines that scroll backwards when the wheel moves forward
        Shape oldClip = g.getClip();
        g.clip(wheel);
        g.setColor(TREAD);
        g.setStroke(new BasicStroke((float) Math.max(0.9 * px, wid * 0.08)));
        double step = len / 4.5;
        double off = -(travel % step);
        if (off > 0) off -= step;
        for (double x = -len / 2 + off; x <= len / 2 + step; x += step) {
            g.draw(new Line2D.Double(x, cy - wid / 2, x, cy + wid / 2));
        }
        g.setClip(oldClip);
    }

    /** Direction of the label on screen: top left at 45° (y grows downwards). */
    public static final double LABEL_DIR_X = -Math.sqrt(0.5), LABEL_DIR_Y = -Math.sqrt(0.5);

    /**
     * Centre of the label in world coordinates: top left at 45°, {@code distanceFromCenter}
     * radii from the centre. It does not depend on the robot's heading, so the label does not
     * circle around the robot when it turns.
     */
    public static Point2D labelCenter(double cx, double cy, double R, double distanceFromCenter) {
        double d = Math.max(0, distanceFromCenter) * R;
        return new Point2D.Double(cx + d * LABEL_DIR_X, cy + d * LABEL_DIR_Y);
    }

    /**
     * Label in screen coordinates: an upright rounded box with the number. When it is detached
     * from the robot, a tail joins it to the robot's edge like a speech bubble.
     * @param robotScreen centre of the robot on screen
     * @param labelScreen centre of the label on screen
     * @param robotScreenR radius of the robot on screen
     */
    public static void paintLabel(Graphics2D g, String text, Point2D robotScreen, Point2D labelScreen,
                                  double robotScreenR, Color accent) {
        g.setFont(LABEL_FONT);
        FontMetrics fm = g.getFontMetrics();
        double w = Math.max(18, fm.stringWidth(text) + 10), h = 18;
        double lx = labelScreen.getX(), ly = labelScreen.getY();
        RoundRectangle2D box = new RoundRectangle2D.Double(lx - w / 2, ly - h / 2, w, h, 8, 8);

        // tail: from the label's edge to the robot's edge, only if there is room between them
        double dx = robotScreen.getX() - lx, dy = robotScreen.getY() - ly, dist = Math.hypot(dx, dy);
        double fromBox = Math.min(w, h) / 2;
        if (dist > robotScreenR + fromBox + 2) {
            double ux = dx / dist, uy = dy / dist;
            g.setColor(new Color(60, 60, 60, 200));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Line2D.Double(lx + ux * fromBox, ly + uy * fromBox,
                robotScreen.getX() - ux * (robotScreenR + 1), robotScreen.getY() - uy * (robotScreenR + 1)));
        }
        g.setColor(new Color(255, 255, 255, 235));
        g.fill(box);
        g.setColor(accent != null ? accent : new Color(60, 60, 60));
        g.setStroke(new BasicStroke(accent != null ? 2f : 1.2f));
        g.draw(box);
        g.setColor(Color.BLACK);
        g.drawString(text, (float) (lx - fm.stringWidth(text) / 2.0), (float) (ly + fm.getAscent() / 2.0 - 1.5));
    }

    /** Convenience for drawing outside the view: full robot in world coordinates. */
    public static void paint(Graphics2D g, double cx, double cy, double theta, double R, double px, Color body,
                             double leftTravel, double rightTravel) {
        AffineTransform saved = g.getTransform();
        g.translate(cx, cy);
        g.rotate(theta);
        paintBody(g, R, px, body, leftTravel, rightTravel);
        g.setTransform(saved);
    }

}
