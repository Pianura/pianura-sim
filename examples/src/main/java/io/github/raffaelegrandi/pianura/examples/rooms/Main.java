// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.examples.rooms;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.Simulator;

public class Main {

	public static void main(String[] args) {
		Simulator sim = new Simulator(new Point(30, 30), "Pianura Sim - arena 80x50", new RoomsScenario());
		sim.startSimPaused();	// starts paused: arrange the scene, then Run (or Space)
	}

}
