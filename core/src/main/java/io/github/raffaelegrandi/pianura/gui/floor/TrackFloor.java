package io.github.raffaelegrandi.pianura.gui.floor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * Line-following track: a dark loop on a light background, with an "S" bend on the bottom side
 * to make the path less trivial. The line width is in cells (2/3 of a cell ≈ the distance
 * between the two default floor sensors).
 */
public class TrackFloor implements FloorStyle {

    private final Color background, line;
    private final double lineCells, marginCells;

    public TrackFloor(Color background, Color line, double lineCells, double marginCells) {
        this.background = background;
        this.line = line;
        this.lineCells = lineCells;
        this.marginCells = marginCells;
    }

    @Override
    public void paint(Graphics2D g, int width, int height, double cellSizePx) {
        g.setColor(background);
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(line);
        BasicStroke stroke = new BasicStroke((float) (lineCells * cellSizePx), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        double m = marginCells * cellSizePx;
        double w = width - 2 * m, h = height - 2 * m;
        double r = Math.min(w, h) * 0.35;
        // loop with rounded corners: top and sides
        Path2D p = new Path2D.Double();
        double x0 = m, y0 = m, x1 = m + w, y1 = m + h;
        p.moveTo(x0 + r, y0);
        p.lineTo(x1 - r, y0);
        p.quadTo(x1, y0, x1, y0 + r);
        p.lineTo(x1, y1 - r);
        p.quadTo(x1, y1, x1 - r, y1);
        // bottom side with an "S"
        double sx = (x0 + x1) / 2, amp = h * 0.18;
        p.curveTo(sx + w * 0.15, y1, sx + w * 0.1, y1 - amp, sx, y1 - amp);
        p.curveTo(sx - w * 0.1, y1 - amp, sx - w * 0.15, y1, x0 + r, y1);
        p.quadTo(x0, y1, x0, y1 - r);
        p.lineTo(x0, y0 + r);
        p.quadTo(x0, y0, x0 + r, y0);
        p.closePath();
        // line → polygon in absolute coordinates, then filled: the result does not depend on the
        // clip, so the track can be drawn in tiles without seams
        g.fill(flatten(stroke.createStrokedShape(flatten(p))));
    }

    /** Polyline equivalent to the shape (tolerance 0.05 pixels). */
    private static Path2D flatten(java.awt.Shape shape) {
        Path2D out = new Path2D.Double();
        out.append(new java.awt.geom.FlatteningPathIterator(shape.getPathIterator(null), 0.05, 16), false);
        return out;
    }

}
