package io.github.raffaelegrandi.pianura.examples.braitenberg;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.Simulator;

public class Main {

	public static void main(String[] args) {
		Simulator sim = new Simulator(new Point(30, 30), "Pianura - Braitenberg vehicles", new BraitenbergScenario());
		sim.getViewOptions().trails = true;
		sim.startSimPaused();
	}

}
