// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.examples.linefollowing;

import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.units.ControllerRobot;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Test bench for line following: a track on the floor and a robot placed on the line.
 *
 * The robot has two optical sensors under its nose (index 0 = left, 1 = right) that read
 * the floor's brightness: 0 = black (line), 255 = white. The algorithm is yours to write:
 * the controller below is empty on purpose and the robot stays still.
 */
public class LineFollowingScenario extends ScenarioManager {

	public LineFollowingScenario(){
		setArenaSize(40, 30);
		setFloor(FloorStyle.track());

		// the top side of the track is 4 cells from the edge: the robot starts there, facing right
		addRobot(new ControllerRobot(1, new Position(16, 4, 0), 2, 0.05, (robot, t) -> {
			int left  = robot.getFloorGray(0);   // 0 = black, 255 = white
			int right = robot.getFloorGray(1);
			// TODO: line-following algorithm, e.g. a proportional control on the error (left - right)
			//       robot.setWheelSpeeds(leftSpeed, rightSpeed);   // cells/s, e.g. 1.0 ± k * (left - right)
			robot.setWheelSpeeds(0, 0);
		}));
	}

}
