// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.floor;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * Floor from an image.
 *
 * Modes:
 *   STRETCH  fills the arena (may distort the image)
 *   FIT      fits it keeping the proportions; the space left has the background color
 *   FILL     fits it keeping the proportions and fills the arena, cropping the excess
 *   TILE     repeats it as a mosaic at its original size
 *   CENTER   puts it in the centre at its original size
 *
 * With {@link #withOpacity(float)} the image is lightened over the background: useful with
 * high-contrast photos or floor plans, so that robots and obstacles stay readable.
 */
public class ImageFloor implements FloorStyle {

    public enum Mode { STRETCH, FIT, FILL, TILE, CENTER }

    private final BufferedImage image;
    private final Mode mode;
    private final Color background;
    private final float opacity;

    public ImageFloor(BufferedImage image, Mode mode, Color background, float opacity) {
        if (image == null) throw new IllegalArgumentException("null image");
        this.image = image;
        this.mode = mode;
        this.background = background;
        this.opacity = Math.max(0f, Math.min(1f, opacity));
    }

    public ImageFloor(BufferedImage image, Mode mode) { this(image, mode, Color.WHITE, 1f); }

    /** Same image, drawn with the given opacity (0 = invisible, 1 = full) over the background color. */
    public ImageFloor withOpacity(float alpha) { return new ImageFloor(image, mode, background, alpha); }

    /** Same image with another background color (bands in FIT, base for the opacity). */
    public ImageFloor withBackground(Color bg) { return new ImageFloor(image, mode, bg, opacity); }

    public BufferedImage getImage() { return image; }

    /**
     * Loads from file; if the path does not exist, tries it as a classpath resource (e.g. an
     * image in src/main/resources). On error prints a warning and returns the default checkerboard.
     */
    public static FloorStyle load(String path, Mode mode) {
        try {
            BufferedImage img = null;
            File f = new File(path);
            if (f.isFile()) {
                img = ImageIO.read(f);
            } else {
                String res = path.startsWith("/") ? path : "/" + path;
                try (InputStream in = ImageFloor.class.getResourceAsStream(res)) {
                    if (in != null) img = ImageIO.read(in);
                }
            }
            if (img == null) throw new IOException("file not found or unsupported format");
            return new ImageFloor(img, mode);
        } catch (IOException e) {
            System.err.println("SCENARIO WARNING: cannot load floor image '" + path + "' ("
                + e.getMessage() + "): using the default checkerboard");
            return FloorStyle.checkerboard();
        }
    }

    @Override
    public void paint(Graphics2D g, int width, int height, double cellSizePx) {
        g.setColor(background);
        g.fillRect(0, 0, width, height);

        Composite old = g.getComposite();
        if (opacity < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int iw = image.getWidth(), ih = image.getHeight();

        switch (mode) {
            case STRETCH -> g.drawImage(image, 0, 0, width, height, null);
            case FIT, FILL -> {
                double sx = (double) width / iw, sy = (double) height / ih;
                double s = mode == Mode.FIT ? Math.min(sx, sy) : Math.max(sx, sy);
                int dw = (int) Math.round(iw * s), dh = (int) Math.round(ih * s);
                java.awt.Shape clip = g.getClip();
                g.clipRect(0, 0, width, height);
                g.drawImage(image, (width - dw) / 2, (height - dh) / 2, dw, dh, null);
                g.setClip(clip);
            }
            case TILE -> {   // only the repetitions that fall in the area to draw
                java.awt.Rectangle c = g.getClipBounds();
                int ya = c == null ? 0 : Math.max(0, c.y / ih * ih), yb = c == null ? height : Math.min(height, c.y + c.height);
                int xa = c == null ? 0 : Math.max(0, c.x / iw * iw), xb = c == null ? width : Math.min(width, c.x + c.width);
                for (int y = ya; y < yb; y += ih)
                    for (int x = xa; x < xb; x += iw)
                        g.drawImage(image, x, y, null);
            }
            case CENTER -> g.drawImage(image, (width - iw) / 2, (height - ih) / 2, null);
        }
        g.setComposite(old);
    }

}
