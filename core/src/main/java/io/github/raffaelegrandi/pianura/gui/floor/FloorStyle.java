package io.github.raffaelegrandi.pianura.gui.floor;

import java.awt.Color;
import java.awt.Graphics2D;

import io.github.raffaelegrandi.pianura.utils.Const;

/**
 * Look of the arena's floor. The IR sensors work on the obstacles' geometry and do not see
 * it; the floor sensors (line following) read its color.
 *
 * The floor is drawn in tiles, when needed (see {@link #paintRegion}), and the tiles are
 * kept: even a very large arena does not weigh on the frame rate.
 *
 * In a scenario:
 * <pre>
 *   setFloor(FloorStyle.checkerboard());                          // default
 *   setFloor(FloorStyle.grid(Color.WHITE, Color.LIGHT_GRAY, 1));  // 1-cell grid
 *   setFloor(FloorStyle.plain(new Color(230, 230, 220)));
 *   setFloor(FloorStyle.randomTiles());                           // tiles
 *   setFloor(FloorStyle.voronoiTiles());                          // stone slabs
 *   setFloor(FloorStyle.image("floors/lab.png", ImageFloor.Mode.FIT));
 * </pre>
 */
public interface FloorStyle {

    /**
     * Draws the floor in the area {@code [0, width] x [0, height]} (pixels).
     * @param cellSizePx size of a cell in pixels (for patterns tied to the grid)
     */
    void paint(Graphics2D g, int width, int height, double cellSizePx);

    /**
     * Draws only the part {@code [x0, x0+w) x [y0, y0+h)} of a floor {@code fullW x fullH}
     * pixels large, with pixel (x0, y0) at the origin of {@code g}.
     *
     * The pixels must be the same {@link #paint} would draw at the same place, whatever the
     * region: this way the floor can be generated in tiles without seams, even when the arena is
     * too large for a single image. The built-in styles draw only the region; this fallback draws
     * the whole floor clipped to the region (correct, but slow on very large arenas).
     */
    default void paintRegion(Graphics2D g, int x0, int y0, int w, int h, int fullW, int fullH, double cellSizePx) {
        Graphics2D c = (Graphics2D) g.create();
        c.clipRect(0, 0, w, h);
        c.translate(-x0, -y0);
        paint(c, fullW, fullH, cellSizePx);
        c.dispose();
    }

    // ---- fabbriche ----

    /** Green checkerboard of the original JUSE, with the name Pianura at the bottom right. */
    static FloorStyle checkerboard() {
        return new CheckerboardFloor(Const.arenaFloorColor1, Const.arenaFloorColor2, 1, true);
    }

    /** Checkerboard with chosen colors and square side (cells). */
    static FloorStyle checkerboard(Color c1, Color c2, double squareCells) {
        return new CheckerboardFloor(c1, c2, squareCells, false);
    }

    /** Plain background with grid lines every {@code spacingCells} cells. */
    static FloorStyle grid(Color background, Color lines, double spacingCells) {
        return new GridFloor(background, lines, spacingCells);
    }

    /** Square tiles in warm gray tones, with grain and grout (side 3 cells). */
    static FloorStyle randomTiles() {
        return new RandomTilesFloor(new Color(180, 176, 168), 3, 17, 1L);
    }

    /** Square tiles: average color, side in cells, seed (same seed = same floor). */
    static FloorStyle randomTiles(Color base, double tileCells, long seed) {
        return new RandomTilesFloor(base, tileCells, 17, seed);
    }

    /** Irregular stone-like slabs (Voronoi), average size 3 cells. */
    static FloorStyle voronoiTiles() {
        return new VoronoiFloor(new Color(196, 186, 168), 3, 20, 1L);
    }

    /** Irregular slabs: average color, average size in cells, seed. */
    static FloorStyle voronoiTiles(Color base, double meanCellCells, long seed) {
        return new VoronoiFloor(base, meanCellCells, 20, seed);
    }

    /** Line-following track: a black line 2/3 of a cell wide on white, 4 cells from the edge. */
    static FloorStyle track() {
        return new TrackFloor(Color.WHITE, new Color(20, 20, 20), 2.0 / 3, 4);
    }

    /** Track with chosen colors, line width and margin from the edge (cells). */
    static FloorStyle track(Color background, Color line, double lineCells, double marginCells) {
        return new TrackFloor(background, line, lineCells, marginCells);
    }

    /** Tinta unita. */
    static FloorStyle plain(Color color) {
        return (g, w, h, cell) -> { g.setColor(color); g.fillRect(0, 0, w, h); };
    }

    /**
     * Image from file (PNG, JPG, GIF, BMP). If the file cannot be read, a warning is printed and
     * the default checkerboard is used: the scenario starts anyway.
     */
    static FloorStyle image(String path, ImageFloor.Mode mode) {
        return ImageFloor.load(path, mode);
    }

    /** Immagine da file, adattata all'arena mantenendo le proporzioni. */
    static FloorStyle image(String path) {
        return ImageFloor.load(path, ImageFloor.Mode.FIT);
    }

}
