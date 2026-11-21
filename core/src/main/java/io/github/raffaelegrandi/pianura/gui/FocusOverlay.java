// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

import io.github.raffaelegrandi.pianura.gui.view.ArenaView;
import io.github.raffaelegrandi.pianura.gui.view.ViewOptions;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.units.RobotUnit;
import io.github.raffaelegrandi.pianura.units.collisions.SensorArray;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * The Focus view: the arena takes the whole window and the controls float over it. A pill at
 * the bottom runs, steps and sets the speed; the time is at the top left, the layers at the
 * top right; a card follows the selected robot; a caption explains what is going on. Made for
 * lessons and demos. The components live inside the arena view and show only in this view.
 */
public final class FocusOverlay {

    private static final Color ACCENT_FALLBACK = new Color(0x2B63D9), DARK = new Color(0x20252B);

    private final ArenaView arena;
    private final SimEngine engine;
    private final SimControlBar bar;
    private final ViewOptions options;
    private final List<JComponent> all = new ArrayList<>();

    private final Pill transport = new Pill(null, 30);
    private final Pill status = new Pill(DARK, 18);
    private final Pill layers = new Pill(null, 18);
    private final Pill captionPill = new Pill(new Color(0x20, 0x25, 0x2B, 230), 10);
    private final JLabel timeLabel = new JLabel();
    private final JLabel captionLabel = new JLabel();
    private final RoundButton run;
    private final JComboBox<String> speed = new JComboBox<>(SimControlBar.SPEED_LABELS);
    private final RobotCard card = new RobotCard();
    private boolean visible, syncing;
    private String caption = "";

    /**
     * @param exit    leaves the Focus view (the button at the top right, and Esc)
     * @param inspect opens the inspector on a robot (a click on its card)
     */
    public FocusOverlay(ArenaView arena, SimEngine engine, SimControlBar bar, ViewOptions options,
                        Runnable exit, Consumer<RobotUnit> inspect) {
        this.arena = arena;
        this.engine = engine;
        this.bar = bar;
        this.options = options;
        arena.setLayout(null);

        // transport: run / pause, step, speed
        run = new RoundButton(Icons.Kind.PLAY, 46, true);
        run.addActionListener(e -> { bar.toggleRun(); refresh(); });
        RoundButton step = new RoundButton(Icons.Kind.STEP, 46, false);
        step.setToolTipText("Step: while paused, advance " + SimControlBar.STEP_SECONDS + " s (Right arrow)");
        step.addActionListener(e -> { bar.stepOnce(); refresh(); });
        speed.setFocusable(false);
        speed.setToolTipText("Speed of the simulation: 1x = real time");
        speed.addActionListener(e -> { if (!syncing) engine.setSpeed(SimControlBar.SPEED_VALUES[speed.getSelectedIndex()]); });
        transport.setLayout(new FlowLayout(FlowLayout.CENTER, 8, 6));
        transport.add(run);
        transport.add(step);
        transport.add(speed);

        // time and state
        timeLabel.setForeground(Color.WHITE);
        timeLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        status.setLayout(new FlowLayout(FlowLayout.CENTER, 12, 6));
        status.add(timeLabel);

        // layers and exit
        layers.setLayout(new FlowLayout(FlowLayout.CENTER, 4, 4));
        layers.add(chip("Trails", "Show the robots' trails", options.trails, on -> options.trails = on));
        layers.add(chip("Sensors", "Show the infrared rays", options.sensorRays, on -> options.sensorRays = on));
        layers.add(chip("Radio", "Show the radio links", options.commLinks, on -> options.commLinks = on));
        layers.add(chip("Drawings", "Show what the robots draw", options.drawings, on -> options.drawings = on));
        JButton back = SimControlBar.iconButton(Icons.Kind.STUDIO, "Back to the Studio view (Esc)");
        back.addActionListener(e -> exit.run());
        layers.add(back);

        // caption
        captionLabel.setForeground(Color.WHITE);
        captionLabel.setFont(captionLabel.getFont().deriveFont(17f));
        captionPill.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 8));
        captionPill.add(captionLabel);

        card.setToolTipText("Click to open the inspector on this robot");
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { if (card.robot != null) inspect.accept(card.robot); }
        });

        for (JComponent c : List.of(transport, status, layers, captionPill, card)) {
            c.setVisible(false);
            arena.add(c);
            all.add(c);
        }
        arena.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { layout(); }
        });
    }

    private JToggleButton chip(String text, String tip, boolean on, Consumer<Boolean> set) {
        JToggleButton b = new JToggleButton(text, on);
        b.setFocusable(false);
        b.setToolTipText(tip);
        b.addActionListener(e -> { set.accept(b.isSelected()); arena.repaint(); });
        return b;
    }

    /** Shows or hides the floating controls (the Focus view on or off). */
    public void setVisible(boolean on) {
        visible = on;
        if (on) {
            // the chips follow the options, which the Studio panel may have changed
            Component[] cs = layers.getComponents();
            boolean[] vals = { options.trails, options.sensorRays, options.commLinks, options.drawings };
            for (int i = 0; i < vals.length; i++) ((JToggleButton) cs[i]).setSelected(vals[i]);
        }
        refresh();
    }

    public boolean isVisible() { return visible; }

    /** Text shown at the bottom left in the Focus view (empty or null = none). */
    public void setCaption(String text) {
        caption = text == null ? "" : text.trim();
        refresh();
    }

    public String getCaption() { return caption; }

    /** Updates labels, buttons and positions; called by the repaint timer. */
    public void refresh() {
        if (!visible) {
            for (JComponent c : all) if (c.isVisible()) c.setVisible(false);
            return;
        }
        boolean stopped = engine.isStopped(), started = engine.isRunning() || stopped, paused = engine.isPaused();
        boolean showPlay = paused || stopped || !started;
        run.setKind(showPlay ? Icons.Kind.PLAY : Icons.Kind.PAUSE);
        run.setToolTipText(showPlay ? "Run (Space)" : "Pause (Space)");
        run.setEnabled(started && !stopped);
        double sp = engine.getSpeed();
        for (int i = 0; i < SimControlBar.SPEED_VALUES.length; i++)
            if (SimControlBar.SPEED_VALUES[i] == sp && speed.getSelectedIndex() != i) { syncing = true; speed.setSelectedIndex(i); syncing = false; }
        String st = stopped ? "stopped" : !started ? "ready" : paused ? "paused" : "running";
        timeLabel.setText(String.format(Locale.ROOT, "t = %.2f s   %s", engine.getClock().now(), st));
        boolean hasCaption = !caption.isEmpty();
        if (hasCaption) {
            int w = Math.max(200, Math.min(560, arena.getWidth() - 40));
            captionLabel.setText("<html><div style='width:" + (w - 30) + "px'>" + escape(caption) + "</div></html>");
        }
        card.robot = arena.getSelected();
        transport.setVisible(true);
        status.setVisible(true);
        layers.setVisible(true);
        captionPill.setVisible(hasCaption);
        card.setVisible(card.robot != null);
        layout();
    }

    /** Keeps the robot's card next to its robot between two refreshes. */
    public void follow() { layout(); }

    private void layout() {
        if (!visible) return;
        int w = arena.getWidth(), h = arena.getHeight(), m = 14;
        Dimension d = status.getPreferredSize();
        status.setBounds(m, m, d.width, d.height);
        d = layers.getPreferredSize();
        layers.setBounds(Math.max(m, w - m - d.width), m, d.width, d.height);
        d = transport.getPreferredSize();
        int ty = h - m - d.height;
        transport.setBounds((w - d.width) / 2, ty, d.width, d.height);
        if (captionPill.isVisible()) {
            d = captionPill.getPreferredSize();
            captionPill.setBounds(m, ty - 10 - d.height, d.width, d.height);
        }
        RobotUnit r = card.robot;
        if (r != null) {
            Position p = r.getPosition();
            Point2D s = arena.getTransform().toScreen(p.x * Const.cellSize, p.y * Const.cellSize);
            double rr = Const.unitRadius * arena.getTransform().getScale();
            d = card.getPreferredSize();
            int x = (int) Math.round(s.getX() + rr + 22), y = (int) Math.round(s.getY() - rr - d.height - 6);
            if (x + d.width > w - m) x = (int) Math.round(s.getX() - rr - 22 - d.width);     // no room on the right
            x = Math.max(m, Math.min(w - m - d.width, x));
            y = Math.max(m + 44, Math.min(h - m - d.height - 70, y));
            card.setBounds(x, y, d.width, d.height);
            card.repaint();
        }
    }

    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    static Color accent() {
        Color c = UIManager.getColor("Component.accentColor");
        return c != null ? c : ACCENT_FALLBACK;
    }

    // ------------------------------------------------------------------ parts

    /** A rounded, slightly translucent container. */
    @SuppressWarnings("serial")
    private static final class Pill extends JPanel {
        private final Color fill;
        private final int arc;

        Pill(Color fill, int arc) {
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = fill;
            if (bg == null) {
                Color p = UIManager.getColor("Panel.background");
                if (p == null) p = Color.WHITE;
                bg = new Color(p.getRed(), p.getGreen(), p.getBlue(), 240);
            }
            RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 2.0 * arc, 2.0 * arc);
            g.setColor(bg);
            g.fill(r);
            if (fill == null) {
                g.setColor(new Color(0, 0, 0, 60));
                g.draw(r);
            }
            g.dispose();
        }
    }

    /** A round button with an icon; the main one is filled with the accent colour. */
    @SuppressWarnings("serial")
    private static final class RoundButton extends JButton {
        private final int size;
        private final boolean main;
        private Icons.Kind kind;

        RoundButton(Icons.Kind kind, int size, boolean main) {
            this.kind = kind;
            this.size = size;
            this.main = main;
            setFocusable(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setPreferredSize(new Dimension(size, size));
        }

        void setKind(Icons.Kind k) {
            if (k != kind) { kind = k; repaint(); }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.foreground");
            if (fg == null) fg = Color.DARK_GRAY;
            Ellipse2D e = new Ellipse2D.Double(1, 1, size - 2, size - 2);
            boolean pressed = getModel().isArmed() || getModel().isRollover();
            if (main) {
                Color a = isEnabled() ? accent() : Color.GRAY;
                g.setColor(pressed ? a.darker() : a);
                g.fill(e);
            } else {
                if (pressed) { g.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 30)); g.fill(e); }
                g.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 90));
                g.draw(e);
            }
            int is = size / 2;
            Icons.of(kind, is, main ? Color.WHITE : (isEnabled() ? fg : Color.GRAY)).paintIcon(this, g, (size - is) / 2, (size - is) / 2);
            g.dispose();
        }
    }

    /** The card that follows the selected robot: number, state, sensors, speeds. */
    @SuppressWarnings("serial")
    private static final class RobotCard extends JComponent {
        RobotUnit robot;

        RobotCard() { setPreferredSize(new Dimension(216, 92)); }

        @Override
        protected void paintComponent(Graphics g0) {
            RobotUnit r = robot;
            if (r == null) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.foreground"), bg = UIManager.getColor("Panel.background");
            if (fg == null) fg = Color.DARK_GRAY;
            if (bg == null) bg = Color.WHITE;
            RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 245));
            g.fill(box);
            g.setColor(new Color(0, 0, 0, 70));
            g.draw(box);

            // sensors: a small ring, heading up
            double cx = 44, cy = getHeight() / 2.0, body = 9, len = 26;
            SensorArray irs = r.getUnitObject().getIrSensors();
            double[] ir = r.getIr();
            double range = Math.max(1e-9, r.getIrRange());
            for (int i = 0; i < ir.length; i++) {
                double a = irs.getSensorAngle(i), k = Math.max(0, Math.min(1, ir[i] / range));
                double dx = Math.sin(a), dy = -Math.cos(a);
                boolean hit = k < 1;
                g.setColor(hit ? new Color(0xC8541E) : new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 60));
                g.setStroke(new BasicStroke(hit ? 2.4f : 1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(cx + dx * body, cy + dy * body, cx + dx * (body + len * k), cy + dy * (body + len * k)));
            }
            g.setColor(new Color(0x20252B));
            g.fill(new Ellipse2D.Double(cx - body, cy - body, 2 * body, 2 * body));
            g.setColor(new Color(0xF0B020));
            g.fill(new Ellipse2D.Double(cx - 2.5, cy - body + 1.5, 5, 5));

            // text
            Font f = getFont() != null ? getFont() : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            int x = 90, y = 24;
            g.setFont(f.deriveFont(Font.BOLD, 14f));
            g.setColor(fg);
            g.drawString("Robot #" + r.getIDnum(), x, y);
            String st = r.hasInspectorData() ? r.inspector().snapshot().state() : "";
            g.setFont(f.deriveFont(Font.BOLD, 12f));
            FontMetrics fm = g.getFontMetrics();
            if (!st.isEmpty()) {
                String s = st;
                while (s.length() > 3 && fm.stringWidth(s) > getWidth() - x - 22) s = s.substring(0, s.length() - 2);
                if (!s.equals(st)) s += "…";
                Color a = accent();
                g.setColor(new Color(a.getRed(), a.getGreen(), a.getBlue(), 40));
                g.fill(new RoundRectangle2D.Double(x - 2, y + 8, fm.stringWidth(s) + 14, 20, 20, 20));
                g.setColor(fg);
                g.drawString(s, x + 5, y + 22);
            }
            g.setFont(f.deriveFont(12f));
            g.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 190));
            g.drawString(String.format(Locale.ROOT, "v %.2f   ω %.2f", r.getLinearSpeed(), r.getAngularSpeed()), x, y + (st.isEmpty() ? 26 : 48));
            g.dispose();
        }
    }
}
