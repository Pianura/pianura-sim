package io.github.raffaelegrandi.pianura.examples.braitenberg;

import java.awt.Color;

import io.github.raffaelegrandi.pianura.algorithms.braitenberg.BraitenbergVehicle;
import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.units.ControllerRobot;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Five Braitenberg vehicles ({@link BraitenbergVehicle}) in a 40 x 30 arena with a few obstacles.
 * The vehicles sense up to 4 cells away and are controlled every 0.05 s of simulated time.
 */
public class BraitenbergScenario extends ScenarioManager {

	public BraitenbergScenario() {
		setArenaSize(40, 30);

		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, new Color(230, 150, 60), new Position(10, 8, Math.toRadians(30)), 6, 6));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(22, 7, 0), 3, 3));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(31, 10, 0), 4, 4));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(19, 17, 0), 5, 3));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, new Color(70, 110, 160), new Position(30, 22, Math.toRadians(15)), 6, 5));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, new Color(200, 70, 70), new Position(9, 23, Math.toRadians(-20)), 2, 9));

		double[][] start = { {4, 4, 0.3}, {36, 4, 2.4}, {20, 27, -1.4}, {36, 27, 3.6}, {16, 12, 0.9} };
		for (int i = 0; i < start.length; i++) {
			addRobot(new ControllerRobot(i + 1, new Position(start[i][0], start[i][1], start[i][2]), 4, 0.05,
					new BraitenbergVehicle()));
		}
	}

}
