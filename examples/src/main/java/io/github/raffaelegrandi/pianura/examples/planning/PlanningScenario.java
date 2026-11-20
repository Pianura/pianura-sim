package io.github.raffaelegrandi.pianura.examples.planning;

import java.awt.Color;

import io.github.raffaelegrandi.pianura.algorithms.planning.FreeSpace;
import io.github.raffaelegrandi.pianura.algorithms.planning.RrtPlanner;
import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.units.ControllerRobot;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Path planning with an RRT: a 40 x 30 arena with two long walls and a few obstacles, the
 * robot at the bottom left, the goal at the top right. The tree grows under your eyes; the
 * simulation pauses when the path is found, then Run makes the robot follow it. Open the
 * Inspector to watch the planner's state, values, plots and log.
 *
 * <p>Before Run you can move the obstacles or the robot with the mouse: the planner reads the
 * map when it starts.
 */
public class PlanningScenario extends ScenarioManager {

	public static final double GOAL_X = 36, GOAL_Y = 5;
	private static final Color WALL = new Color(90, 95, 110);
	private static final Color BOX = new Color(200, 140, 60);

	private final RrtPlanner planner;

	public PlanningScenario() { this(7); }

	/** @param seed seed of the planner's random samples (same seed, same tree) */
	public PlanningScenario(long seed) {
		setArenaSize(40, 30);
		setFloor(FloorStyle.grid(new Color(250, 250, 246), new Color(215, 218, 226), 1));

		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, WALL, new Position(13, 11, 0), 1, 22));   // from the top
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, WALL, new Position(26, 19, 0), 1, 22));   // from the bottom
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, BOX, new Position(6, 12, Math.toRadians(25)), 5, 3));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, BOX, new Position(19, 22, 0), 4, 4));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, BOX, new Position(33, 13, Math.toRadians(-20)), 6, 2));

		planner = new RrtPlanner(() -> new FreeSpace(getObstacles(), getArenaWidth(), getArenaHeight(), 1.3),
			GOAL_X, GOAL_Y, seed);
		addRobot(new ControllerRobot(1, new Position(4, 26, 0), 2, 0.05, planner));
	}

	public RrtPlanner getPlanner() { return planner; }
}
