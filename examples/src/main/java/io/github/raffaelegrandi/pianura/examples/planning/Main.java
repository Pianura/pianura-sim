// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.examples.planning;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.Simulator;

public class Main {

	public static void main(String[] args) {
		Simulator sim = new Simulator(new Point(30, 30), "Pianura - RRT path planning", new PlanningScenario());
		sim.startSimPaused();      // starts paused: press Run and watch the tree grow
		sim.openInspector();       // the planner's state, values, plots and log
	}

}
