package io.github.raffaelegrandi.pianura.gui.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.UIManager;

import io.github.raffaelegrandi.pianura.inspect.Inspector;

/**
 * One time plot of the inspector: a series against simulated time, over the last
 * {@code window} seconds (or all of it), y range fitted to the visible points. Drawn by
 * Pianura itself, no chart library.
 */
@SuppressWarnings("serial")
public class TimePlot extends JComponent {

    private static final Color[] PALETTE = {
        new Color(0x2060ff), new Color(0xe0602a), new Color(0x20a050), new Color(0xb040c0),
        new Color(0xc09000), new Color(0x0090a0), new Color(0xd03050), new Color(0x607080) };

    private Inspector.Series series;
    private double now;        // current simulated time: the right edge of the plot
    private double window;     // seconds shown; <= 0 = everything
    private final Color color;

    public TimePlot(int index) {
        color = PALETTE[Math.floorMod(index, PALETTE.length)];
        setPreferredSize(new Dimension(400, 118));
        setMinimumSize(new Dimension(200, 90));
    }

    /** New data; repaints. */
    public void set(Inspector.Series s, double now, double window) {
        this.series = s;
        this.now = now;
        this.window = window;
        repaint();
    }

    public Inspector.Series getSeries() { return series; }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color fg = orDefault(UIManager.getColor("Label.foreground"), Color.DARK_GRAY);
        Color bg = orDefault(UIManager.getColor("Table.background"), Color.WHITE);
        Color grid = mix(fg, bg, 0.85), axis = mix(fg, bg, 0.55);
        int w = getWidth(), h = getHeight();
        g.setColor(bg);
        g.fillRect(0, 0, w, h);
        if (series == null) { g.dispose(); return; }

        Font small = getFont() != null ? getFont().deriveFont(11f) : new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        g.setFont(small);
        FontMetrics fm = g.getFontMetrics();
        int left = 52, right = 10, top = 20, bottom = 18;
        int pw = Math.max(10, w - left - right), ph = Math.max(10, h - top - bottom);

        double[] t = series.t(), v = series.v();
        double t1 = Math.max(now, t.length > 0 ? t[t.length - 1] : 0);
        double t0 = window > 0 ? Math.max(0, t1 - window) : (t.length > 0 ? Math.min(t[0], 0) : 0);   // simulated time starts at 0
        if (t1 - t0 < 1e-9) t0 = t1 - 1;
        int first = 0;
        while (first < t.length && t[first] < t0) first++;
        int from = Math.max(0, first - 1);          // one point before the window, so the line enters from the edge

        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = from; i < t.length; i++) if (Double.isFinite(v[i])) { lo = Math.min(lo, v[i]); hi = Math.max(hi, v[i]); }
        if (lo > hi) { lo = 0; hi = 1; }
        if (hi - lo < 1e-12) { double c = lo; lo = c - (Math.abs(c) > 1e-9 ? Math.abs(c) * 0.1 : 1); hi = c + (Math.abs(c) > 1e-9 ? Math.abs(c) * 0.1 : 1); }
        double pad = (hi - lo) * 0.08;
        lo -= pad;
        hi += pad;

        // grid and labels
        g.setStroke(new BasicStroke(1f));
        for (int k = 0; k <= 4; k++) {
            int y = top + (int) Math.round(ph * k / 4.0);
            g.setColor(grid);
            g.drawLine(left, y, left + pw, y);
            if (k % 2 == 0) {
                g.setColor(axis);
                String lab = num(hi - (hi - lo) * k / 4.0);
                g.drawString(lab, left - 6 - fm.stringWidth(lab), y + fm.getAscent() / 2 - 1);
            }
        }
        g.setColor(axis);
        g.drawRect(left, top, pw, ph);
        String tl = String.format(Locale.ROOT, "%.1f s", t0), tr = String.format(Locale.ROOT, "%.1f s", t1);
        g.drawString(tl, left, top + ph + fm.getAscent() + 2);
        g.drawString(tr, left + pw - fm.stringWidth(tr), top + ph + fm.getAscent() + 2);

        // the line
        Path2D path = new Path2D.Double();
        boolean pen = false;
        for (int i = from; i < t.length; i++) {
            if (!Double.isFinite(v[i])) { pen = false; continue; }
            double x = left + (t[i] - t0) / (t1 - t0) * pw;
            double y = top + (hi - v[i]) / (hi - lo) * ph;
            if (pen) path.lineTo(x, y); else { path.moveTo(x, y); pen = true; }
        }
        g.clip(new java.awt.Rectangle(left, top, pw + 1, ph + 1));
        g.setColor(color);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
        g.setClip(null);

        // title and last value
        g.setFont(small.deriveFont(Font.BOLD));
        g.setColor(color);
        g.drawString(series.name(), left, top - 6);
        String last = "= " + num(series.last());
        g.setFont(small);
        g.setColor(fg);
        g.drawString(last, left + g.getFontMetrics(small.deriveFont(Font.BOLD)).stringWidth(series.name()) + 6, top - 6);
        g.dispose();
    }

    static String num(double d) {
        if (!Double.isFinite(d)) return String.valueOf(d);
        double a = Math.abs(d);
        if (a != 0 && (a >= 1e5 || a < 1e-3)) return String.format(Locale.ROOT, "%.2e", d);
        String s = String.format(Locale.ROOT, a >= 100 ? "%.1f" : "%.3f", d);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }

    private static Color orDefault(Color c, Color d) { return c != null ? c : d; }

    private static Color mix(Color a, Color b, double k) {
        return new Color((int) (a.getRed() * (1 - k) + b.getRed() * k), (int) (a.getGreen() * (1 - k) + b.getGreen() * k),
                         (int) (a.getBlue() * (1 - k) + b.getBlue() * k));
    }
}
