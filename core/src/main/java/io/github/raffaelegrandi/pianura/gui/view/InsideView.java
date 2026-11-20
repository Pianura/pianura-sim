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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JComponent;
import javax.swing.UIManager;

import io.github.raffaelegrandi.pianura.inspect.Inspector;

/**
 * One view of a robot's insides in the inspector: bars for a vector, a heat map for a matrix,
 * a network whose neurons light up with their activation. Positive values are warm (orange),
 * negative ones cold (blue), zero is the background: the same code for the three views.
 */
@SuppressWarnings("serial")
public class InsideView extends JComponent {

    static final Color POS = new Color(0xE0602A), NEG = new Color(0x2F6FE0);

    private Inspector.View view;

    public InsideView() {
        setPreferredSize(new Dimension(400, 150));
        setMinimumSize(new Dimension(200, 100));
    }

    public void set(Inspector.View v) {
        boolean resize = view == null || v == null || view.getClass() != v.getClass() || height(v) != height(view);
        view = v;
        if (resize) {
            setPreferredSize(new Dimension(400, height(v)));
            revalidate();
        }
        repaint();
    }

    public Inspector.View getView() { return view; }

    /** Height that fits the view: bars by count, heat maps by rows, networks by the largest layer. */
    static int height(Inspector.View v) {
        if (v instanceof Inspector.Bars) return 160;
        if (v instanceof Inspector.Matrix m) return 44 + Math.max(60, Math.min(240, m.values().length * 14));
        if (v instanceof Inspector.Network n) {
            int max = 1;
            for (double[] a : n.activations()) max = Math.max(max, a.length);
            return 44 + Math.max(90, Math.min(320, max * 22));
        }
        return 150;
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
        if (view == null) { g.dispose(); return; }
        Font small = getFont() != null ? getFont().deriveFont(11f) : new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        g.setFont(small.deriveFont(Font.BOLD));
        g.setColor(fg);
        String kind = view instanceof Inspector.Bars ? "bars" : view instanceof Inspector.Matrix ? "matrix" : "network";
        g.drawString(view.name(), 10, 15);
        g.setFont(small);
        g.setColor(TimePlot.mix(fg, bg, 0.45));
        g.drawString(kind + "  " + describe(view), 10 + g.getFontMetrics(small.deriveFont(Font.BOLD)).stringWidth(view.name()) + 8, 15);
        Rectangle2D area = new Rectangle2D.Double(10, 24, getWidth() - 20, getHeight() - 32);
        g.setFont(small);
        if (view instanceof Inspector.Bars b) paintBars(g, b, area, fg, bg);
        else if (view instanceof Inspector.Matrix m) paintMatrix(g, m, area, fg, bg);
        else if (view instanceof Inspector.Network n) paintNetwork(g, n, area, fg, bg);
        g.dispose();
    }

    private static String describe(Inspector.View v) {
        if (v instanceof Inspector.Bars b) return b.values().length + " values";
        if (v instanceof Inspector.Matrix m) return m.values().length + " x " + (m.values().length == 0 ? 0 : m.values()[0].length);
        Inspector.Network n = (Inspector.Network) v;
        StringBuilder s = new StringBuilder();
        for (int l = 0; l < n.activations().length; l++) s.append(l == 0 ? "" : "-").append(n.activations()[l].length);
        return s.toString();
    }

    // ---------------------------------------------------------------- bars

    private void paintBars(Graphics2D g, Inspector.Bars b, Rectangle2D a, Color fg, Color bg) {
        double[] v = b.values();
        int n = v.length;
        if (n == 0) return;
        double lo = 0, hi = 0;
        for (double x : v) if (Double.isFinite(x)) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
        if (hi - lo < 1e-12) hi = lo + 1;
        FontMetrics fm = g.getFontMetrics();
        boolean labels = b.labels() != null && b.labels().length > 0;
        double top = a.getY() + fm.getHeight(), bottom = a.getMaxY() - (labels || n <= 24 ? fm.getHeight() + 2 : 2);
        double zeroY = bottom - (0 - lo) / (hi - lo) * (bottom - top);
        double slot = a.getWidth() / n, w = Math.max(1, slot * 0.7);
        int best = 0;
        for (int i = 1; i < n; i++) if (v[i] > v[best]) best = i;
        g.setColor(TimePlot.mix(fg, bg, 0.75));
        g.draw(new Line2D.Double(a.getX(), zeroY, a.getMaxX(), zeroY));
        for (int i = 0; i < n; i++) {
            double x = a.getX() + i * slot + (slot - w) / 2;
            double y = bottom - (v[i] - lo) / (hi - lo) * (bottom - top);
            Color c = v[i] >= 0 ? POS : NEG;
            g.setColor(i == best ? c.darker() : c);
            g.fill(new Rectangle2D.Double(x, Math.min(y, zeroY), w, Math.max(1, Math.abs(zeroY - y))));
            if (slot >= 26 && Double.isFinite(v[i])) {
                String s = TimePlot.num(v[i]);
                g.setColor(fg);
                g.drawString(s, (float) (x + w / 2 - fm.stringWidth(s) / 2.0), (float) (v[i] >= 0 ? Math.min(y, zeroY) - 3 : Math.max(y, zeroY) + fm.getAscent() + 1));
            }
            String lab = labels && i < b.labels().length && b.labels()[i] != null ? b.labels()[i] : (n <= 24 ? String.valueOf(i) : null);
            if (lab != null) {
                g.setColor(TimePlot.mix(fg, bg, 0.3));
                String s = fit(lab, fm, slot - 2);
                g.drawString(s, (float) (a.getX() + i * slot + slot / 2 - fm.stringWidth(s) / 2.0), (float) a.getMaxY());
            }
        }
    }

    // ---------------------------------------------------------------- heat map

    private void paintMatrix(Graphics2D g, Inspector.Matrix m, Rectangle2D a, Color fg, Color bg) {
        double[][] v = m.values();
        int rows = v.length, cols = 0;
        for (double[] r : v) cols = Math.max(cols, r.length);
        if (rows == 0 || cols == 0) return;
        double max = 0;
        for (double[] r : v) for (double x : r) if (Double.isFinite(x)) max = Math.max(max, Math.abs(x));
        if (max < 1e-12) max = 1;
        FontMetrics fm = g.getFontMetrics();
        double legend = 60;
        double cw = (a.getWidth() - legend) / cols, ch = a.getHeight() / rows;
        double cell = Math.min(cw, ch * 3);
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < v[i].length; j++) {
                g.setColor(heat(v[i][j] / max, bg));
                g.fill(new Rectangle2D.Double(a.getX() + j * cell, a.getY() + i * ch, Math.ceil(cell), Math.ceil(ch)));
            }
        g.setColor(TimePlot.mix(fg, bg, 0.7));
        g.draw(new Rectangle2D.Double(a.getX(), a.getY(), cell * cols, ch * rows));
        // scale
        double lx = a.getX() + cell * cols + 10;
        for (int k = 0; k <= 20; k++) {
            g.setColor(heat(1 - k / 10.0, bg));
            g.fill(new Rectangle2D.Double(lx, a.getY() + k * (Math.min(a.getHeight(), 100) / 21), 10, Math.min(a.getHeight(), 100) / 21 + 1));
        }
        g.setColor(fg);
        g.drawString(TimePlot.num(max), (float) lx + 14, (float) a.getY() + fm.getAscent());
        g.drawString(TimePlot.num(-max), (float) lx + 14, (float) (a.getY() + Math.min(a.getHeight(), 100)));
    }

    // ---------------------------------------------------------------- network

    private void paintNetwork(Graphics2D g, Inspector.Network n, Rectangle2D a, Color fg, Color bg) {
        double[][] act = n.activations();
        int layers = act.length;
        if (layers == 0) return;
        FontMetrics fm = g.getFontMetrics();
        // room for the labels of the first and last layer
        double padL = labelWidth(n, 0, fm), padR = labelWidth(n, layers - 1, fm);
        double x0 = a.getX() + padL + 10, x1 = a.getMaxX() - padR - 10;
        double[][] px = new double[layers][], py = new double[layers][];
        double maxN = 1;
        for (double[] l : act) maxN = Math.max(maxN, l.length);
        double r = Math.max(2.5, Math.min(9, a.getHeight() / maxN * 0.36));
        for (int l = 0; l < layers; l++) {
            int k = act[l].length;
            px[l] = new double[k];
            py[l] = new double[k];
            double x = layers == 1 ? (x0 + x1) / 2 : x0 + (x1 - x0) * l / (layers - 1);
            double step = a.getHeight() / Math.max(1, k);
            for (int i = 0; i < k; i++) { px[l][i] = x; py[l][i] = a.getY() + step * (i + 0.5); }
        }
        // connections
        double[][][] w = n.weights();
        if (w != null) {
            double wmax = 0;
            for (int l = 0; l < Math.min(w.length, layers - 1); l++)
                if (fits(w[l], act[l + 1].length, act[l].length))
                    for (double[] row : w[l]) for (double x : row) if (Double.isFinite(x)) wmax = Math.max(wmax, Math.abs(x));
            if (wmax > 1e-12) {
                for (int l = 0; l < Math.min(w.length, layers - 1); l++) {
                    if (!fits(w[l], act[l + 1].length, act[l].length)) continue;
                    // a connection is brighter when its input neuron is active: what flows now
                    double amax = maxAbs(act[l]);
                    for (int j = 0; j < w[l].length; j++)
                        for (int i = 0; i < w[l][j].length; i++) {
                            double k = Math.abs(w[l][j][i]) / wmax;
                            if (k < 0.04) continue;
                            double flow = amax > 1e-12 ? 0.25 + 0.75 * Math.abs(act[l][i]) / amax : 0.4;
                            Color c = w[l][j][i] >= 0 ? POS : NEG;
                            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(30 + 200 * k * flow)));
                            g.setStroke(new BasicStroke((float) (0.6 + 2.6 * k)));
                            g.draw(new Line2D.Double(px[l][i], py[l][i], px[l + 1][j], py[l + 1][j]));
                        }
                }
            }
        }
        // neurons: filled by activation, scaled per layer
        g.setStroke(new BasicStroke(1f));
        for (int l = 0; l < layers; l++) {
            double amax = Math.max(1e-12, maxAbs(act[l]));
            for (int i = 0; i < act[l].length; i++) {
                double v = act[l][i];
                Ellipse2D e = new Ellipse2D.Double(px[l][i] - r, py[l][i] - r, 2 * r, 2 * r);
                g.setColor(heat(Double.isFinite(v) ? v / amax : 0, bg));
                g.fill(e);
                g.setColor(TimePlot.mix(fg, bg, 0.35));
                g.draw(e);
                String lab = label(n, l, i);
                if (lab != null && (l == 0 || l == layers - 1)) {
                    g.setColor(fg);
                    String s = lab + " " + TimePlot.num(v);
                    float y = (float) (py[l][i] + fm.getAscent() / 2.0 - 1);
                    if (l == 0) g.drawString(s, (float) (px[l][i] - r - 4 - fm.stringWidth(s)), y);
                    else g.drawString(s, (float) (px[l][i] + r + 4), y);
                }
            }
        }
    }

    private static boolean fits(double[][] w, int out, int in) {
        if (w == null || w.length != out) return false;
        for (double[] row : w) if (row == null || row.length != in) return false;
        return true;
    }

    private static double labelWidth(Inspector.Network n, int l, FontMetrics fm) {
        double w = 0;
        for (int i = 0; i < n.activations()[l].length; i++) {
            String lab = label(n, l, i);
            if (lab != null) w = Math.max(w, fm.stringWidth(lab + " -0.000"));
        }
        return Math.min(140, w);
    }

    private static String label(Inspector.Network n, int l, int i) {
        String[][] lb = n.labels();
        if (lb == null || l >= lb.length || lb[l] == null || i >= lb[l].length) return null;
        return lb[l][i];
    }

    private static double maxAbs(double[] v) {
        double m = 0;
        for (double x : v) if (Double.isFinite(x)) m = Math.max(m, Math.abs(x));
        return m;
    }

    /** -1 = cold, 0 = background, +1 = warm. */
    static Color heat(double k, Color bg) {
        k = Math.max(-1, Math.min(1, k));
        Color c = k >= 0 ? POS : NEG;
        return TimePlot.mix(c, bg, 1 - Math.abs(k));
    }

    private static String fit(String s, FontMetrics fm, double w) {
        if (fm.stringWidth(s) <= w) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static Color orDefault(Color c, Color d) { return c != null ? c : d; }
}
