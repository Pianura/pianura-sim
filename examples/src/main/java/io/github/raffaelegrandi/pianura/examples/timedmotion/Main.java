// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.examples.timedmotion;

import java.awt.Point;
import io.github.raffaelegrandi.pianura.*;

public class Main {
	
	static TimedMotionScenario scenario;	
	
	public static void main(String[] args) {		
		scenario = new TimedMotionScenario();
		Simulator simManager = new Simulator(new Point(30,30),"Pianura - timed motion",scenario);
		simManager.startSimPaused();	// starts paused: arrange the scene, then Run (or Space)
	}//end main
}//end class
