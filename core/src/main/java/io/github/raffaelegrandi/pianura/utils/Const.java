package io.github.raffaelegrandi.pianura.utils;

import java.awt.Color;
import java.awt.Dimension;

/** Constants of the simulator: arena, robot, sensors, drawing. */
public class Const {

	public static final int arenaWidth = 600;
	public static final int arenaHeight = arenaWidth;
	public static final Color arenaFloorColor1 = new Color(188,238,104);
	public static final Color arenaFloorColor2 = new Color(202,255,112);
	public static final Color sensorRangeColor = new Color(119,136,153);

	public static final Color arenaBorderColor = Color.RED;
	public static final int gridCellCount = 40;
	public static final int cellSize = arenaWidth / gridCellCount; // 15 pixels

	public static final String guiFrameTitle = "Pianura";
	public static final int repaintTime = 16; // arena refresh time in ms (~60 fps)

	public static final Color unitColor = Color.BLACK;
	public static final Dimension unitDim = new Dimension(cellSize, cellSize);
	public static final int unitRadius = cellSize; // pixels

	public static final int angleStep = 2;
	public static final int sensorCount = 18;
	public static final int sensorsCheckingRange0 = 3; // in pixels
	public static final double sensorsCheckingRangeMin = 0.5; // in cells

	/** Default scale of a scenario: one cell is 5 cm, so the robot (2 cells across) is 10 cm wide. */
	public static final double DEFAULT_METERS_PER_CELL = 0.05;

	public static boolean debugEnabled = false;
	/** Physics step in seconds of simulated time (100 Hz). */
	public static final double physicsStep = 0.01;

}
