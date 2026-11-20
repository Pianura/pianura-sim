package io.github.raffaelegrandi.pianura.gui.floor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Irregular slabs (Voronoi diagram), stone paving style.
 *
 * Points are scattered at random; every pixel takes the color of the nearest point and
 * becomes "grout" where the two nearest points are almost equidistant. The computation is
 * done per region, when needed. With the same seed the floor is always identical.
 */
public class VoronoiFloor implements FloorStyle {

    private final Color base;
    private final double meanCellCells;
    private final int variation;
    private final long seed;

    /**
     * @param base          average color of the slabs
     * @param meanCellCells average size of a slab in cells
     * @param variation     largest brightness variation between slabs (0–255)
     * @param seed          seed of the random generator
     */
    public VoronoiFloor(Color base, double meanCellCells, int variation, long seed) {
        if (!(meanCellCells > 0)) throw new IllegalArgumentException("meanCellCells must be > 0");
        this.base = base;
        this.meanCellCells = meanCellCells;
        this.variation = Math.max(0, variation);
        this.seed = seed;
    }

    @Override
    public void paint(Graphics2D g, int width, int height, double cellSizePx) {
        paintRegion(g, 0, 0, width, height, width, height, cellSizePx);
    }

    /**
     * Points on a "jittered" grid as large as the whole floor: the position of every point
     * depends only on its grid cell and on the seed, so every region is computed on its own,
     * without seams.
     */
    @Override
    public void paintRegion(Graphics2D g, int x0, int y0, int w, int h, int width, int height, double cellSizePx) {
        if (w <= 0 || h <= 0) return;
        double cellPx = meanCellCells * cellSizePx;
        int n = Math.max(2, (int) Math.round((double) width * height / (cellPx * cellPx)));
        int gx = Math.max(1, (int) Math.round(Math.sqrt(n * (double) width / height)));
        int gy = Math.max(1, (int) Math.ceil((double) n / gx));
        double sx = (double) width / gx, sy = (double) height / gy;

        // points of the grid cells that can be the nearest to the region's pixels (2-cell margin)
        int i0 = Math.max(0, (int) (x0 / sx) - 2), i1 = Math.min(gx - 1, (int) ((x0 + w) / sx) + 2);
        int j0 = Math.max(0, (int) (y0 / sy) - 2), j1 = Math.min(gy - 1, (int) ((y0 + h) / sy) + 2);
        int ni = i1 - i0 + 1, nj = j1 - j0 + 1;
        double[] px = new double[ni * nj], py = new double[ni * nj];
        int[] colors = new int[ni * nj];
        int baseRgb = base.getRGB();
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                int k = (j - j0) * ni + (i - i0);
                px[k] = (i + 0.1 + 0.8 * FloorHash.unit(seed, i, j, 1)) * sx;
                py[k] = (j + 0.1 + 0.8 * FloorHash.unit(seed, i, j, 2)) * sy;
                colors[k] = FloorHash.shade(baseRgb, FloorHash.symmetric(seed, i, j, 3, variation));
            }
        }

        int grout = FloorHash.shade(baseRgb, -70);
        double groutPx = 1.6;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] buf = ((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData();
        for (int yy = 0; yy < h; yy++) {
            int y = y0 + yy;
            int cj = Math.min(gy - 1, (int) (y / sy));
            for (int xx = 0; xx < w; xx++) {
                int x = x0 + xx;
                int ci = Math.min(gx - 1, (int) (x / sx));
                // with the jittered grid the nearest point lies in the 5x5 grid cells around
                double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
                int best = 0;
                for (int j = Math.max(j0, cj - 2); j <= Math.min(j1, cj + 2); j++) {
                    for (int i = Math.max(i0, ci - 2); i <= Math.min(i1, ci + 2); i++) {
                        int k = (j - j0) * ni + (i - i0);
                        double dx = x - px[k], dy = y - py[k];
                        double d = dx * dx + dy * dy;
                        if (d < d1) { d2 = d1; d1 = d; best = k; }
                        else if (d < d2) d2 = d;
                    }
                }
                // distance from the boundary line between the two nearest points ≈ (√d2 − √d1)/2
                boolean edge = (Math.sqrt(d2) - Math.sqrt(d1)) / 2 < groutPx / 2 + 0.5;
                int rgb = edge ? grout : colors[best];
                if (!edge) {   // grana leggera
                    long hg = FloorHash.mix(seed, x, y, 4);
                    if ((hg & 0xffff) < 65536 / 30) rgb = FloorHash.shade(rgb, (int) Math.floorMod(hg >>> 16, 25L) - 12);
                }
                buf[yy * w + xx] = rgb;
            }
        }
        g.drawImage(img, 0, 0, null);
    }

}
