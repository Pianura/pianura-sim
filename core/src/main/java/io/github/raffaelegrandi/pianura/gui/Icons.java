// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Pianura's icons, drawn by code on a 24 x 24 grid: no image files, any size, and they take
 * the colour of the component they sit on, so they follow the light and the dark theme.
 */
public final class Icons {

    /** The icons. Each is a simple, common sign: a triangle to run, two bars to pause, ... */
    public enum Kind {
        PLAY, PAUSE, STEP, STOP, OPEN, SAVE, INSPECT, SIDEBAR, HOME, FOCUS, STUDIO, SELECT, RECT, OVAL,
        TRASH, LOG, TIMELINE, FIT, ZOOM_IN, ZOOM_OUT, CLOSE, LAYERS, NEW
    }

    private Icons() {}

    /** An icon in the colour of its component (dimmed when the component is disabled). */
    public static Icon of(Kind kind, int size) { return new Drawn(kind, size, null); }

    /** An icon in a fixed colour (e.g. white on a coloured button). */
    public static Icon of(Kind kind, int size, Color color) { return new Drawn(kind, size, color); }

    private record Drawn(Kind kind, int size, Color fixed) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Color col = fixed;
            if (col == null) {
                col = c != null ? c.getForeground() : UIManager.getColor("Label.foreground");
                if (col == null) col = Color.DARK_GRAY;
                if (c != null && !c.isEnabled()) {
                    Color d = UIManager.getColor("Button.disabledText");
                    col = d != null ? d : new Color(col.getRed(), col.getGreen(), col.getBlue(), 90);
                }
            }
            g.setColor(col);
            g.translate(x, y);
            g.scale(size / 24.0, size / 24.0);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            draw(g, kind);
            g.dispose();
        }
    }

    private static Path2D path(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        return p;
    }

    private static Path2D closed(double... xy) {
        Path2D p = path(xy);
        p.closePath();
        return p;
    }

    private static void draw(Graphics2D g, Kind k) {
        switch (k) {
            case PLAY -> g.fill(closed(7.5, 4.5, 19.5, 12, 7.5, 19.5));
            case PAUSE -> {
                g.fill(new RoundRectangle2D.Double(6.5, 5, 4, 14, 1.5, 1.5));
                g.fill(new RoundRectangle2D.Double(13.5, 5, 4, 14, 1.5, 1.5));
            }
            case STEP -> {
                g.fill(closed(5, 5, 14.5, 12, 5, 19));
                g.fill(new RoundRectangle2D.Double(16, 5, 3.2, 14, 1.2, 1.2));
            }
            case STOP -> g.fill(new RoundRectangle2D.Double(6, 6, 12, 12, 2.5, 2.5));
            case OPEN -> g.draw(closed(3, 6.5, 9, 6.5, 11, 9, 21, 9, 21, 19, 3, 19));
            case SAVE -> {
                g.draw(closed(4, 4, 16.5, 4, 20, 7.5, 20, 20, 4, 20));
                g.draw(new Rectangle2D.Double(8, 13, 8, 7));
                g.draw(path(8, 4, 8, 8.5, 14.5, 8.5, 14.5, 4));
            }
            case INSPECT -> {
                g.draw(new Ellipse2D.Double(4, 4, 12, 12));
                g.draw(path(14.5, 14.5, 20, 20));
            }
            case SIDEBAR -> {
                g.draw(new RoundRectangle2D.Double(3, 5, 18, 14, 3, 3));
                g.fill(new Rectangle2D.Double(14.5, 5.5, 6, 13));
            }
            case HOME -> {
                g.draw(path(3, 12, 12, 4, 21, 12));
                g.draw(path(6, 10, 6, 20, 18, 20, 18, 10));
            }
            case FOCUS -> {
                g.draw(path(4, 9, 4, 4, 9, 4));
                g.draw(path(15, 4, 20, 4, 20, 9));
                g.draw(path(20, 15, 20, 20, 15, 20));
                g.draw(path(9, 20, 4, 20, 4, 15));
            }
            case STUDIO -> {
                g.draw(new RoundRectangle2D.Double(3, 4, 18, 16, 3, 3));
                g.draw(path(8, 4, 8, 20));
                g.draw(path(8, 15, 21, 15));
            }
            case SELECT -> g.draw(closed(6, 3.5, 18, 12, 12, 13.2, 9, 19.5));
            case RECT -> g.draw(new Rectangle2D.Double(4, 7, 16, 10));
            case OVAL -> g.draw(new Ellipse2D.Double(3.5, 6.5, 17, 11));
            case TRASH -> {
                g.draw(path(4.5, 7, 19.5, 7));
                g.draw(path(9, 7, 9, 4, 15, 4, 15, 7));
                g.draw(path(6.5, 7, 7.5, 20, 16.5, 20, 17.5, 7));
            }
            case LOG -> {
                g.draw(path(5, 7, 19, 7));
                g.draw(path(5, 12, 19, 12));
                g.draw(path(5, 17, 13, 17));
            }
            case TIMELINE -> {
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                g.draw(path(4, 7, 13, 7));
                g.draw(path(4, 12, 19, 12));
                g.draw(path(4, 17, 10, 17));
                g.setStroke(new BasicStroke(1.6f));
                g.draw(path(15.5, 3.5, 15.5, 20.5));
            }
            case FIT -> {
                g.draw(new RoundRectangle2D.Double(3.5, 3.5, 17, 17, 3, 3));
                g.fill(new Rectangle2D.Double(8.5, 8.5, 7, 7));
            }
            case ZOOM_IN, ZOOM_OUT -> {
                g.draw(new Ellipse2D.Double(4, 4, 12, 12));
                g.draw(path(14.5, 14.5, 20, 20));
                g.draw(path(7, 10, 13, 10));
                if (k == Kind.ZOOM_IN) g.draw(path(10, 7, 10, 13));
            }
            case CLOSE -> {
                g.draw(path(6, 6, 18, 18));
                g.draw(path(18, 6, 6, 18));
            }
            case LAYERS -> {
                g.draw(closed(12, 4, 21, 9, 12, 14, 3, 9));
                g.draw(path(3, 13, 12, 18, 21, 13));
            }
            case NEW -> {
                g.draw(closed(5, 3.5, 14, 3.5, 19, 8.5, 19, 20.5, 5, 20.5));
                g.draw(path(12, 10, 12, 16));
                g.draw(path(9, 13, 15, 13));
            }
        }
    }
}
