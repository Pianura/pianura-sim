// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.start;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.panels.ArenaFloorPanel;
import io.github.raffaelegrandi.pianura.gui.view.ArenaView;
import io.github.raffaelegrandi.pianura.gui.view.ViewOptions;
import io.github.raffaelegrandi.pianura.structures.SceneFile;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.units.sensors.FloorMap;
import io.github.raffaelegrandi.pianura.utils.Const;

/** Small pictures of a scenario or of a scene file, for the start screen. */
public final class Thumbnails {

    private Thumbnails() {}

    /** The scenario as the arena view would show it at the start: floor, obstacles and robots. */
    public static BufferedImage ofScenario(ScenarioManager sc, int w, int h) {
        int aw = (int) Math.round(sc.getArenaWidthPx()), ah = (int) Math.round(sc.getArenaHeightPx());
        FloorMap floor = new FloorMap(sc.getFloor(), aw, ah, FloorMap.defaultScale(aw, ah));
        try {
            ViewOptions o = new ViewOptions();
            o.sensorRange = false;
            o.ids = false;
            o.minimap = false;
            o.floorSensors = false;
            o.drawings = false;
            // drawn at twice the size and scaled down: thin walls and small robots stay visible
            ArenaView view = new ArenaView(floor, aw, ah, sc.getObstacles(), sc.getRobots(), o);
            view.setEditHintVisible(false);
            view.setSize(2 * w, 2 * h);
            view.fitToWindow();
            BufferedImage big = new BufferedImage(2 * w, 2 * h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = big.createGraphics();
            view.paint(g);
            g.dispose();
            return scale(big, w, h);
        } finally {
            floor.dispose();
        }
    }

    /** A scene file: the arena, its obstacles in their colours, the robots as dots. */
    public static BufferedImage ofScene(SceneFile.Scene s, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x3A3F47));
        g.fillRect(0, 0, w, h);
        double aw = s.arenaWidth() * Const.cellSize, ah = s.arenaHeight() * Const.cellSize;
        double k = Math.min((w - 12) / aw, (h - 12) / ah);
        AffineTransform t = new AffineTransform();
        t.translate((w - aw * k) / 2, (h - ah * k) / 2);
        t.scale(k, k);
        g.transform(t);
        g.setColor(new Color(0xF4F4EE));
        g.fill(new Rectangle2D.Double(0, 0, aw, ah));
        for (Obstacle o : s.obstacles()) {
            g.setColor(o.getID().getColor());
            g.fill(ArenaFloorPanel.outlineOf(o));
        }
        double r = Math.max(Const.unitRadius, 2.5 / k);
        g.setColor(new Color(0x20252B));
        for (SceneFile.RobotPose p : s.robots())
            g.fill(new Ellipse2D.Double(p.x() * Const.cellSize - r, p.y() * Const.cellSize - r, 2 * r, 2 * r));
        g.dispose();
        return img;
    }

    private static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
