package io.github.raffaelegrandi.pianura.examples.largemap;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.Simulator;

/**
 * Large map (300 x 200 cells, 60 robots): fly over it with W A S D, the mouse at the border of
 * the view or the minimap; double click a robot to follow it.
 */
public class Main {

	public static void main(String[] args) {
		Simulator sim = new Simulator(new Point(30, 30), "Pianura - large map", new LargeMapScenario());
		sim.startSimPaused();
	}

}
