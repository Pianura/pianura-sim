// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.examples.brains;

import java.awt.Color;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.remote.RemoteServer;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * A swarm where every robot has its own brain outside Pianura: one program per robot, attached
 * over UDP (see clients/python/brain.py and swarm.py). The robots stand still until their brain
 * says hello, and talk to each other only through Pianura's radio.
 */
public class BrainsScenario extends ScenarioManager {

	/** Most robots the arena holds (a 7 x 4 grid of starting places). */
	public static final int MAX_ROBOTS = 28;

	public BrainsScenario(RemoteServer server, int robots) {
		if (robots < 1 || robots > MAX_ROBOTS)
			throw new IllegalArgumentException("robots: 1 to " + MAX_ROBOTS + ", not " + robots);
		setArenaSize(50, 35);
		setFloor(FloorStyle.voronoiTiles());
		getCommSettings().setRange(10);

		Color box = new Color(70, 110, 160), wall = new Color(120, 110, 100);
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, wall, new Position(25, 9, 0), 1, 12));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, wall, new Position(25, 30, 0), 1, 10));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, box, new Position(12, 18, Math.toRadians(20)), 5, 3));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, box, new Position(38, 17, Math.toRadians(-30)), 4, 4));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(43, 27.5, 0), 4, 2));

		// starting places on a 7 x 4 grid clear of the obstacles, taken in an order that spreads
		// even a few robots over the whole arena; headings vary with the index
		double[] xs = { 4, 10, 17, 21, 32, 39, 46 };
		double[] ys = { 4, 12, 24, 31 };
		for (int i = 0; i < robots; i++) {
			int place = (i * 11) % MAX_ROBOTS;               // 11 and 28 are coprime: every place once
			double x = xs[place % xs.length], y = ys[place / xs.length];
			// id, pose, IR range (cells), control period (s)
			addRobot(server.createRobot(i + 1, new Position(x, y, i * 0.9), 2, 0.1));
		}
	}

}
