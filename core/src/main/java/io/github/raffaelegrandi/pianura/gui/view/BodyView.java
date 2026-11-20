// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.UIManager;

import io.github.raffaelegrandi.pianura.units.RobotUnit;
import io.github.raffaelegrandi.pianura.units.collisions.SensorArray;
import io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray;

/**
 * The robot's body as its sensors and motors see it, with the heading pointing up: the infrared
 * readings as rays (shorter and redder when something is close), the bump sensors in contact,
 * the floor sensors with the gray they read, the two wheels with their speed, and the numbers
 * underneath. It needs nothing from the robot's code: Pianura knows all of it.
 */
@SuppressWarnings("serial")
public class BodyView extends JComponent {

    private static final Color NEAR = new Color(0xD03030), FAR = new Color(0x30A060), BODY = new Color(0x202428);
    private RobotUnit robot;

    public BodyView() {
        setPreferredSize(new Dimension(250, 360));
        setMinimumSize(new Dimension(200, 300));
    }

    public void set(RobotUnit r) {
        robot = r;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color fg = orDefault(UIManager.getColor("Label.foreground"), Color.DARK_GRAY);
        Color bg = orDefault(UIManager.getColor("Table.background"), Color.WHITE);
        g.setColor(bg);
        g.fillRect(0, 0, getWidth(), getHeight());
        RobotUnit r = robot;
        if (r == null) { g.dispose(); return; }
        Font small = getFont() != null ? getFont().deriveFont(11f) : new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        g.setFont(small);
        FontMetrics fm = g.getFontMetrics();

        int w = getWidth();
        double diagram = Math.min(w - 20, 230);
        double cx = w / 2.0, cy = 12 + diagram / 2;
        double bodyR = diagram * 0.16;                 // the robot's disc
        double rayLen = diagram / 2 - bodyR - 6;       // IR range drawn as this many pixels
        double cellPx = bodyR / r.getBodyRadius();     // pixels per cell, at the body's scale

        // range circle
        g.setColor(TimePlot.mix(fg, bg, 0.85));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 3f, 3f }, 0f));
        g.draw(new Ellipse2D.Double(cx - bodyR - rayLen, cy - bodyR - rayLen, 2 * (bodyR + rayLen), 2 * (bodyR + rayLen)));

        // IR rays: from the surface, as long as the reading
        SensorArray irs = r.getUnitObject().getIrSensors();
        double[] ir = r.getIr();
        double range = Math.max(1e-9, r.getIrRange());
        Path2D outline = new Path2D.Double();
        for (int i = 0; i < ir.length; i++) {
            double a = irs.getSensorAngle(i);
            double k = Math.max(0, Math.min(1, ir[i] / range));
            double dx = Math.sin(a), dy = -Math.cos(a);           // heading up, clockwise = to the right
            double x0 = cx + dx * bodyR, y0 = cy + dy * bodyR;
            double x1 = cx + dx * (bodyR + rayLen * k), y1 = cy + dy * (bodyR + rayLen * k);
            Color c = TimePlot.mix(NEAR, FAR, k);
            g.setStroke(new BasicStroke(k < 1 ? 2.2f : 1f));
            g.setColor(k < 1 ? c : TimePlot.mix(FAR, bg, 0.6));
            g.draw(new Line2D.Double(x0, y0, x1, y1));
            if (k < 1) g.fill(new Ellipse2D.Double(x1 - 2.5, y1 - 2.5, 5, 5));
            if (i == 0) outline.moveTo(x1, y1); else outline.lineTo(x1, y1);
        }
        outline.closePath();
        g.setColor(new Color(FAR.getRed(), FAR.getGreen(), FAR.getBlue(), 28));
        g.fill(outline);

        // body, wheels, heading
        double base = r.getWheelBase() / 2 * cellPx;
        double v = r.getLinearSpeed(), om = r.getAngularSpeed(), half = r.getWheelBase() / 2;
        double left = v + om * half, right = v - om * half;          // cells/s, as in evaluateMovement
        double wheelW = bodyR * 0.28, wheelH = bodyR * 0.9;
        g.setColor(BODY);
        g.fill(new Ellipse2D.Double(cx - bodyR, cy - bodyR, 2 * bodyR, 2 * bodyR));
        paintWheel(g, cx - base, cy, wheelW, wheelH, left, bodyR);
        paintWheel(g, cx + base, cy, wheelW, wheelH, right, bodyR);
        g.setColor(new Color(0xF0B020));
        Path2D nose = new Path2D.Double();
        nose.moveTo(cx, cy - bodyR + 3);
        nose.lineTo(cx - bodyR * 0.35, cy - bodyR * 0.25);
        nose.lineTo(cx + bodyR * 0.35, cy - bodyR * 0.25);
        nose.closePath();
        g.fill(nose);

        // bump sensors in contact: red arcs on the edge
        SensorArray bump = r.getUnitObject().getBumpSensors();
        double[] bv = bump.getSensors();
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(NEAR);
        double span = 360.0 / Math.max(1, bv.length);
        for (int i = 0; i < bv.length; i++) {
            if (bv[i] == bump.getInitValue()) continue;
            double deg = Math.toDegrees(bump.getSensorAngle(i));
            // Arc2D: 0 deg = east, counter-clockwise; our 0 = up, clockwise
            g.draw(new Arc2D.Double(cx - bodyR - 3, cy - bodyR - 3, 2 * bodyR + 6, 2 * bodyR + 6, 90 - deg - span / 2, span, Arc2D.OPEN));
        }

        // floor sensors: squares with the colour they read
        FloorSensorArray fs = r.getUnitObject().getFloorSensors();
        FloorSensorArray.Layout lay = fs.getLayout();
        int[] grays = r.getFloorGrays();
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < lay.count() && i < grays.length; i++) {
            double a = lay.angleRad(i), d = lay.radiusFraction() * bodyR;
            double x = cx + Math.sin(a) * d, y = cy - Math.cos(a) * d;
            double s = Math.max(6, bodyR * 0.22);
            g.setColor(r.getFloorColor(i));
            g.fill(new Rectangle2D.Double(x - s / 2, y - s / 2, s, s));
            g.setColor(new Color(0xF0B020));
            g.draw(new Rectangle2D.Double(x - s / 2, y - s / 2, s, s));
        }

        // numbers
        int minIdx = 0;
        for (int i = 1; i < ir.length; i++) if (ir[i] < ir[minIdx]) minIdx = i;
        double y = cy + diagram / 2 + 10;
        String[][] rows = {
            { "v", f(v) + " cells/s" },
            { "ω", f(om) + " rad/s" },
            { "wheels", f(left) + " | " + f(right) + " cells/s" },
            { "travel", f(r.getLeftWheelTravel()) + " | " + f(r.getRightWheelTravel()) + " cells" },
            { "IR min", ir.length == 0 || ir[minIdx] >= range ? "nothing in range" : f(ir[minIdx]) + " cells (s" + minIdx + ")" },
            { "contact", bump.isEmpty() ? "no" : bump.collisionNum() + " sensors" },
            { "floor", grays(grays) },
        };
        double labelW = 0;
        for (String[] row : rows) labelW = Math.max(labelW, fm.stringWidth(row[0]));
        for (String[] row : rows) {
            y += fm.getHeight();
            g.setColor(TimePlot.mix(fg, bg, 0.4));
            g.drawString(row[0], 12, (float) y);
            g.setColor(row[0].equals("contact") && !bump.isEmpty() ? NEAR : fg);
            g.drawString(row[1], (float) (12 + labelW + 10), (float) y);
        }
        g.dispose();
    }

    /** A wheel with an arrow: up = forward, length = speed. */
    private static void paintWheel(Graphics2D g, double x, double y, double w, double h, double speed, double scale) {
        g.setColor(new Color(0x606870));
        g.fill(new Rectangle2D.Double(x - w / 2, y - h / 2, w, h));
        if (Math.abs(speed) < 1e-6) return;
        double len = Math.min(scale * 1.4, Math.abs(speed) * scale * 0.6) * Math.signum(speed);
        double x0 = x, y0 = y, y1 = y - len;
        g.setColor(speed > 0 ? InsideView.POS : InsideView.NEG);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x0, y0, x0, y1));
        double head = Math.signum(len) * 5;
        g.draw(new Line2D.Double(x0, y1, x0 - 4, y1 + head));
        g.draw(new Line2D.Double(x0, y1, x0 + 4, y1 + head));
    }

    private static String grays(int[] gr) {
        if (gr.length == 0) return "—";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < gr.length; i++) s.append(i == 0 ? "" : " · ").append(gr[i]);
        return s.toString();
    }

    private static String f(double d) { return String.format(Locale.ROOT, "%.2f", d).replace("-0.00", "0.00"); }

    private static Color orDefault(Color c, Color d) { return c != null ? c : d; }
}
