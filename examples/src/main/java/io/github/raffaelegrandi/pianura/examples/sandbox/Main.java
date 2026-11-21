// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.examples.sandbox;

import java.awt.Point;
import java.nio.file.Path;

import io.github.raffaelegrandi.pianura.Simulator;

/**
 * A scene of your own: without arguments an empty arena with one wandering robot, with the path
 * of a scene file that scene ({@code ./gradlew run -Pexample=sandbox --args=my-scene.json}).
 */
public class Main {

	public static void main(String[] args) throws Exception {
		boolean file = args.length > 0;
		SandboxScenario scenario = file ? new SandboxScenario(Path.of(args[0])) : new SandboxScenario();
		String name = file ? Path.of(args[0]).getFileName().toString() : "new scene";
		Simulator sim = new Simulator(new Point(30, 30), "Pianura - " + name, scenario);
		sim.printLogMsg(false, file ? "Scene " + name + ": the robots wander avoiding obstacles"
			: "A new scene: add obstacles with the tools on the left, Save it (Ctrl+S), then Run");
		sim.startSimPaused();
	}

}
