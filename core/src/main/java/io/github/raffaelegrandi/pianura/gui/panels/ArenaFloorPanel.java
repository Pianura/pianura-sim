// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.panels;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectABS;
import io.github.raffaelegrandi.pianura.arena.ObjectID;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.units.collisions.OvalShape;
import io.github.raffaelegrandi.pianura.units.collisions.RectShape;
import io.github.raffaelegrandi.pianura.utils.Const;

/**
 * Static background of the arena: floor + obstacles, in world pixels (cells x Const.cellSize).
 * Drawn once into an image; the view (ArenaView) scales and moves it. The arena walls are
 * not here: the view draws them, outside the floor.
 */
@SuppressWarnings("serial")
public class ArenaFloorPanel extends JPanel{

	private Graphics pgc;
	private List<ArenaObjectABS> obstVect;
	private final FloorStyle floor;
	private final int widthPx, heightPx;

	public ArenaFloorPanel(List<ArenaObjectABS> obstacles, FloorStyle floorStyle, int widthPx, int heightPx){
		obstVect = obstacles;
		floor = floorStyle != null ? floorStyle : FloorStyle.checkerboard();
		this.widthPx = widthPx;
		this.heightPx = heightPx;
		setDoubleBuffered(true);
		setSize(widthPx, heightPx);
	}

	/** Arena standard. */
	public ArenaFloorPanel(List<ArenaObjectABS> obstacles, FloorStyle floorStyle){
		this(obstacles, floorStyle, Const.arenaWidth, Const.arenaHeight);
	}

	public ArenaFloorPanel(List<ArenaObjectABS> obstacles){ this(obstacles, FloorStyle.checkerboard()); }

	/** Draws floor and obstacles into a new image of the given size (world pixels). */
	public static BufferedImage renderBackground(List<? extends Obstacle> obstacles, FloorStyle floor, int widthPx, int heightPx){
		ArenaFloorPanel p = new ArenaFloorPanel(new ArrayList<ArenaObjectABS>(obstacles), floor, widthPx, heightPx);
		BufferedImage img = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		p.paintComponent(g);
		g.dispose();
		return img;
	}

	public void paintComponent(Graphics g){
		pgc = g;
		Graphics2D g2 = (Graphics2D) g.create();
		floor.paint(g2, widthPx, heightPx, Const.cellSize);
		g2.dispose();
		drawArenaObst();
	}

	/*****************************************************************************************************/

	private void drawArenaObst(){
		for (ArenaObjectABS arenaObj : obstVect) drawObstacle((Obstacle) arenaObj);
	}

	private void drawObstacle(Obstacle obst){
		Graphics2D g2 = (Graphics2D) pgc.create();
		paintObstacle(g2, obst, outlineOf(obst));
		g2.dispose();
	}

	/**
	 * Draws an obstacle with the same outline used for collisions (RectShape/OvalShape.outline()):
	 * graphics and physics cannot diverge. The border is drawn inside the shape (clip + a stroke
	 * of double width). Also used by the view, which draws the obstacles as vectors at every frame.
	 */
	public static void paintObstacle(Graphics2D g, Obstacle obst, Shape shape){
		if (shape == null) return;
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2.setColor(obst.getID().getColor());
		g2.fill(shape);
		Color border = obst.getBorderColor();
		if (obst.getBorderWidth() > 0 && !border.equals(obst.getID().getColor())) {
			g2.clip(shape);
			g2.setColor(border);
			g2.setStroke(new BasicStroke((float) (2 * obst.getBorderWidth()), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
			g2.draw(shape);
		}
		g2.dispose();
	}

	/**
	 * Floor only, rendered at {@code scale} times the world resolution (e.g. 2 = two image
	 * pixels per world pixel): it stays sharp when the view zooms in.
	 */
	public static BufferedImage renderFloor(FloorStyle floor, int widthPx, int heightPx, double scale){
		int w = (int) Math.round(widthPx * scale), h = (int) Math.round(heightPx * scale);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		(floor != null ? floor : FloorStyle.checkerboard()).paint(g, w, h, Const.cellSize * scale);
		g.dispose();
		return img;
	}

	/** Outline in pixels of the obstacle, identical to the one used by CollisionDetector. */
	public static Shape outlineOf(Obstacle obst){
		ObjectID id = obst.getID();
		double cell = Const.cellSize;
		double cx = cell * id.getPos().x, cy = cell * id.getPos().y;
		double w  = cell * id.getWidth(), h = cell * id.getHeight();
		return switch (id.getType()) {
			case OBSTACLE_RECT -> new RectShape(cx, cy, w, h, id.getAngle()).outline();
			case OBSTACLE_OVAL -> new OvalShape(cx, cy, w, h, id.getAngle()).outline();
			default -> null;
		};
	}

}
