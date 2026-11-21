// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.examples.sandbox;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;

import io.github.raffaelegrandi.pianura.algorithms.iravoidance.IrAvoider;
import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.structures.SceneFile;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * A place to build a scene: an empty arena with one robot that wanders avoiding obstacles, or a
 * scene file with a wandering robot wherever the file puts one. Add obstacles with the tools on
 * the left, save the scene, and find it again in the start screen.
 */
public class SandboxScenario extends ScenarioManager {

	/** An empty 40 x 30 arena with one robot. */
	public SandboxScenario() {
		setArenaSize(40, 30);
		setFloor(FloorStyle.grid(new Color(250, 250, 246), new Color(215, 218, 226), 1));
		addRobot(new IrAvoider(1, new Position(5, 5, 0.6), 2));
	}

	/** The arena, the obstacles and the robots of a scene file; a file without robots gets one. */
	public SandboxScenario(Path sceneFile) throws IOException {
		SceneFile.Scene s = SceneFile.read(sceneFile);
		loadScene(sceneFile);
		setFloor(FloorStyle.grid(new Color(250, 250, 246), new Color(215, 218, 226), 1));
		for (SceneFile.RobotPose p : s.robots()) addRobot(new IrAvoider(p.id(), new Position(p.x(), p.y(), p.theta()), 2));
		if (s.robots().isEmpty()) addRobot(new IrAvoider(1, new Position(2, 2, 0.6), 2));
	}

}
