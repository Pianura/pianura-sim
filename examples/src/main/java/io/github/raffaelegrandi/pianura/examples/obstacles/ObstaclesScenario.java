// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.examples.obstacles;

import io.github.raffaelegrandi.pianura.algorithms.iravoidance.IrAvoider;
import java.awt.Color;
import java.awt.Dimension;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.utils.Position;

public class ObstaclesScenario extends ScenarioManager {

	public ObstaclesScenario(){
		buildRobotsVector();
		buildObstaclesVector();
	}//ScenarioBuilder

	private void buildRobotsVector(){
		addRobot(new IrAvoider(1,new Position(7,17,Math.toRadians(90)),2));
		addRobot(new IrAvoider(2,new Position(10,20,0),2));
		// was (11,12): with the yellow square turned by 30 degrees its corner reaches y = 11.8
		addRobot(new IrAvoider(3,new Position(11,13,0),2));
		addRobot(new IrAvoider(4,new Position(5,15,0),2));
		addRobot(new IrAvoider(5,new Position(17,19,0),2));
		addRobot(new IrAvoider(6,new Position(21,30,0),2));
		addRobot(new IrAvoider(7,new Position(30,18,0),2));
		addRobot(new IrAvoider(8,new Position(31,12,0),2));
		// was (33,12): touching robot 8 (centres 2 cells apart = sum of the radii)
		addRobot(new IrAvoider(9,new Position(34,12,0),2));
	}//buildRobotsVector

	private void buildObstaclesVector(){
		// Rectangles: the position was already the centre, unchanged
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT,Color.YELLOW,new Position(10,5,Math.toRadians(30)),new Dimension(10,10)));

		// Ovals: position moved to the centre (old top-left + dim/2)
		// ex (30,5) dim(3,3)  → centre (31.5, 6.5)
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL,Color.WHITE,new Position(31.5,6.5,0),new Dimension(3,3)));
		// ex (20,5) dim(3,3)  → centre (21.5, 6.5)
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL,Color.WHITE,new Position(21.5,6.5,0),new Dimension(3,3)));
		// ex (20,15) dim(4,4) → centre (22.0, 17.0)
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL,Color.WHITE,new Position(22.0,17.0,0),new Dimension(4,4)));
		// ex (11,15) dim(3,3) → centre (12.5, 16.5)
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL,Color.WHITE,new Position(12.5,16.5,0),new Dimension(3,3)));
		// ex (30,5) dim(3,3)  → centre (31.5, 6.5)  [duplicate of the first oval]
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL,Color.WHITE,new Position(31.5,6.5,0),new Dimension(3,3)));

		// Rectangles: unchanged
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT,Color.WHITE,new Position(20,35,Math.toRadians(20)),new Dimension(5,8)));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT,Color.RED,new Position(30,37,Math.toRadians(30)),new Dimension(2,15)));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT,Color.BLUE,new Position(30,25,Math.toRadians(10)),new Dimension(7,7)));

		// ex (8,28) dim(6,3)  → centre (11.0, 29.5)
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL,Color.WHITE,new Position(11.0,29.5,0),new Dimension(6,3)));
	}//buildObstaclesVector

}//end ObstaclesScenario
