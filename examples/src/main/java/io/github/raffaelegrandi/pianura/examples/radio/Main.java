package io.github.raffaelegrandi.pianura.examples.radio;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.Simulator;

public class Main {

	public static void main(String[] args) {
		Simulator sim = new Simulator(new Point(30, 30), "Pianura - radio", new RadioScenario());
		sim.getViewOptions().commLinks = true;
		sim.getViewOptions().commRange = false;
		sim.startSimPaused();
		sim.openConnectivityWindow();
	}

}
