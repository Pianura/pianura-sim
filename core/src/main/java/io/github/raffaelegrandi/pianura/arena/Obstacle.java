// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.arena;

import java.awt.Color;
import java.awt.Dimension;

import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Static obstacle (rotated rectangle or oval); position = centre, in cells.
 *
 * Border: by default a darker shade of the fill color, so that even a light obstacle stays
 * visible on a light floor. For another color use {@link #withBorder(Color)}; for no border,
 * pass the obstacle's own color. The border is drawn inside the shape: the visible obstacle
 * is exactly the one used for collisions.
 *
 * <pre>
 *   addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.WHITE,
 *       new Position(20, 35, 0), new Dimension(5, 8)).withBorder(Color.BLACK, 2));
 * </pre>
 */
public class Obstacle extends ArenaObjectABS {

	/** Default border width in pixels. */
	public static final double DEFAULT_BORDER_WIDTH = 1.5;

	private Color borderColor;          // null = automatic (darker than the fill)
	private double borderWidth = DEFAULT_BORDER_WIDTH;

	public Obstacle(ArenaObjectType obstType, Color color, Position obstPos, Dimension obstDim ){
		super(new ObjectID(obstType, color, obstPos, obstDim));
	}

	/** Obstacle with non-integer size: width and height in cells. */
	public Obstacle(ArenaObjectType obstType, Color color, Position obstPos, double widthCells, double heightCells){
		super(new ObjectID(obstType, color, obstPos, widthCells, heightCells));
	}

	public Obstacle(ArenaObjectType obstType, Color color, Position obstPos, Dimension obstDim, Color borderColor){
		this(obstType, color, obstPos, obstDim);
		this.borderColor = borderColor;
	}

	/** Sets the border color (the obstacle's own color = no visible border). */
	public Obstacle withBorder(Color color){
		this.borderColor = color;
		return this;
	}

	/** Sets border color and width in pixels. */
	public Obstacle withBorder(Color color, double widthPx){
		this.borderColor = color;
		this.borderWidth = Math.max(0, widthPx);
		return this;
	}

	/** Actual border color (the one set, or the automatic one). */
	public Color getBorderColor(){
		return borderColor != null ? borderColor : autoBorder(objectID.getColor());
	}

	public double getBorderWidth(){ return borderWidth; }

	/** Border color set with withBorder, or null if automatic. */
	public Color getCustomBorderColor(){ return borderColor; }

	/** Automatic border: the fill color darkened; for very dark colors, lightened. */
	public static Color autoBorder(Color fill){
		if (fill == null) return Color.DARK_GRAY;
		double lum = 0.299 * fill.getRed() + 0.587 * fill.getGreen() + 0.114 * fill.getBlue();
		if (lum < 60) return new Color(
			Math.min(255, fill.getRed() + 90), Math.min(255, fill.getGreen() + 90), Math.min(255, fill.getBlue() + 90));
		return new Color((int) (fill.getRed() * 0.55), (int) (fill.getGreen() * 0.55), (int) (fill.getBlue() * 0.55));
	}

}//end class
