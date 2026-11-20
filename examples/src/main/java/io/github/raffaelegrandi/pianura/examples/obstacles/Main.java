package io.github.raffaelegrandi.pianura.examples.obstacles;

import java.awt.Point;
import io.github.raffaelegrandi.pianura.*;

public class Main {

	static ObstaclesScenario scenario;

	public static void main(String[] args) {
		scenario = new ObstaclesScenario();
		Simulator simManager = new Simulator(new Point(30,30), "Pianura - obstacles", scenario);
		simManager.startSimPaused();	// starts paused: arrange the scene, then Run (or Space)
	}//end main

}//end class
