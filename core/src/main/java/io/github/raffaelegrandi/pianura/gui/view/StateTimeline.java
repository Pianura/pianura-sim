// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

import io.github.raffaelegrandi.pianura.inspect.Inspector;
import io.github.raffaelegrandi.pianura.units.RobotUnit;

/**
 * The timeline: one lane per robot, coloured by the state its algorithm declared with
 * {@code inspector().state(...)}, from the start of the simulation to now. At a glance it
 * shows what each robot did and when. A click on a lane selects the robot; the pointer over a
 * segment shows the state and its interval.
 */
@SuppressWarnings("serial")
public class StateTimeline extends JComponent {

    private static final Color[] PALETTE = {
        new Color(0xAEB6C1), new Color(0x2B63D9), new Color(0xC8541E), new Color(0x2E9E60),
        new Color(0x9A4FC0), new Color(0xC09000), new Color(0x0E8FA0), new Color(0xD03A5A) };
    private static final int GUTTER = 70, LANE = 16, GAP = 5, TOP = 22, AXIS = 18;

    private final List<? extends RobotUnit> robots;
    private final DoubleSupplier now;
    private final Consumer<RobotUnit> onSelect;
    private final Map<String, Color> colors = new LinkedHashMap<>();
    private List<RobotUnit> lanes = List.of();
    private List<List<Inspector.Entry>> histories = List.of();
    private RobotUnit selected;
    private double end = 1;

    /**
     * @param robots   the scenario's robots
     * @param now      current simulated time
     * @param onSelect called with the robot of the lane that was clicked
     */
    public StateTimeline(List<? extends RobotUnit> robots, DoubleSupplier now, Consumer<RobotUnit> onSelect) {
        this.robots = robots;
        this.now = now;
        this.onSelect = onSelect;
        ToolTipManager.sharedInstance().registerComponent(this);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int i = laneAt(e.getY());
                if (i >= 0 && StateTimeline.this.onSelect != null) StateTimeline.this.onSelect.accept(lanes.get(i));
            }
        });
    }

    /** The robot highlighted (the one selected in the arena). */
    public void setSelected(RobotUnit r) {
        if (selected != r) { selected = r; repaint(); }
    }

    /** Reads the robots' state histories again. Call it on the EDT, a few times a second. */
    public void refresh() {
        List<RobotUnit> ls = new ArrayList<>();
        List<List<Inspector.Entry>> hs = new ArrayList<>();
        for (RobotUnit r : robots) {
            if (!r.hasInspectorData()) continue;
            List<Inspector.Entry> h = r.inspector().states();
            if (h.isEmpty()) continue;
            ls.add(r);
            hs.add(h);
            for (Inspector.Entry e : h) if (!colors.containsKey(e.text())) colors.put(e.text(), PALETTE[colors.size() % PALETTE.length]);
        }
        boolean resize = ls.size() != lanes.size();
        lanes = ls;
        histories = hs;
        end = Math.max(1, now.getAsDouble());
        if (resize) revalidate();
        repaint();
    }

    /** Number of lanes shown (robots that declared a state). */
    public int laneCount() { return lanes.size(); }

    /** The colour given to a state (states get colours in order of first appearance). */
    public Color colorOf(String state) { return colors.get(state); }

    @Override
    public Dimension getPreferredSize() {
        if (lanes.isEmpty()) return new Dimension(400, TOP + 10);       // only the line that says what it is
        return new Dimension(400, TOP + lanes.size() * (LANE + GAP) + AXIS + 4);
    }

    private int laneAt(int y) {
        int i = (y - TOP) / (LANE + GAP);
        return y >= TOP && i >= 0 && i < lanes.size() && (y - TOP) % (LANE + GAP) < LANE ? i : -1;
    }

    private double xOf(double t) { return GUTTER + (getWidth() - GUTTER - 12) * (t / end); }

    @Override
    public String getToolTipText(MouseEvent e) {
        int i = laneAt(e.getY());
        if (i < 0 || e.getX() < GUTTER) return null;
        double t = (e.getX() - GUTTER) / (double) Math.max(1, getWidth() - GUTTER - 12) * end;
        List<Inspector.Entry> h = histories.get(i);
        for (int k = h.size() - 1; k >= 0; k--) {
            if (h.get(k).t() <= t) {
                double to = k + 1 < h.size() ? h.get(k + 1).t() : end;
                return String.format(Locale.ROOT, "Robot #%d: %s, %.2f s to %.2f s", lanes.get(i).getIDnum(), h.get(k).text(), h.get(k).t(), to);
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color fg = orDefault(UIManager.getColor("Label.foreground"), Color.DARK_GRAY);
        Color bg = orDefault(UIManager.getColor("Panel.background"), Color.WHITE);
        Color muted = TimePlot.mix(fg, bg, 0.4), faint = TimePlot.mix(fg, bg, 0.85);
        g.setColor(bg);
        g.fillRect(0, 0, getWidth(), getHeight());
        Font small = getFont() != null ? getFont().deriveFont(11f) : new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        g.setFont(small);
        FontMetrics fm = g.getFontMetrics();

        if (lanes.isEmpty()) {
            g.setColor(muted);
            g.drawString("Timeline: a robot that calls inspector().state(\"...\") gets a lane here, coloured by its state.", 12, TOP);
            g.dispose();
            return;
        }

        // legend: the states, in order of first appearance
        int lx = GUTTER;
        for (Map.Entry<String, Color> e : colors.entrySet()) {
            int w = fm.stringWidth(e.getKey());
            if (lx + 14 + w > getWidth() - 12) break;
            g.setColor(e.getValue());
            g.fillRect(lx, 6, 10, 10);
            g.setColor(muted);
            g.drawString(e.getKey(), lx + 14, 15);
            lx += 14 + w + 14;
        }

        double right = xOf(end);
        for (int i = 0; i < lanes.size(); i++) {
            int y = TOP + i * (LANE + GAP);
            RobotUnit r = lanes.get(i);
            g.setFont(r == selected ? small.deriveFont(Font.BOLD) : small);
            g.setColor(r == selected ? fg : muted);
            g.drawString("robot " + r.getIDnum(), 12, y + LANE - 4);
            g.setColor(faint);
            g.fill(new Rectangle2D.Double(GUTTER, y, right - GUTTER, LANE));
            List<Inspector.Entry> h = histories.get(i);
            for (int k = 0; k < h.size(); k++) {
                double x0 = xOf(h.get(k).t()), x1 = k + 1 < h.size() ? xOf(h.get(k + 1).t()) : right;
                g.setColor(colors.getOrDefault(h.get(k).text(), PALETTE[0]));
                g.fill(new Rectangle2D.Double(x0, y, Math.max(1, x1 - x0), LANE));
            }
            if (r == selected) {
                g.setColor(fg);
                g.draw(new Rectangle2D.Double(GUTTER - 0.5, y - 0.5, right - GUTTER, LANE));
            }
        }

        // time axis
        g.setFont(small);
        int ay = TOP + lanes.size() * (LANE + GAP);
        g.setColor(faint);
        g.drawLine(GUTTER, ay, (int) right, ay);
        double step = niceStep(end / Math.max(2, (getWidth() - GUTTER) / 90.0));
        g.setColor(muted);
        for (double t = 0; t <= end + 1e-9; t += step) {
            int x = (int) Math.round(xOf(t));
            g.drawLine(x, ay, x, ay + 4);
            String s = TimePlot.num(t) + " s";
            g.drawString(s, Math.min(x, getWidth() - fm.stringWidth(s) - 2), ay + 4 + fm.getAscent());
        }
        g.dispose();
    }

    /** A round step (1, 2 or 5 times a power of ten) not smaller than {@code raw}. */
    static double niceStep(double raw) {
        if (!(raw > 0)) return 1;
        double p = Math.pow(10, Math.floor(Math.log10(raw)));
        double m = raw / p;
        return (m <= 1 ? 1 : m <= 2 ? 2 : m <= 5 ? 5 : 10) * p;
    }

    private static Color orDefault(Color c, Color d) { return c != null ? c : d; }
}
