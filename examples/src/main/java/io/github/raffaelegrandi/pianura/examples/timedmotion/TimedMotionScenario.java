package io.github.raffaelegrandi.pianura.examples.timedmotion;

import io.github.raffaelegrandi.pianura.algorithms.timedmotion.TimedMotion;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.utils.Position;

public class TimedMotionScenario extends ScenarioManager {

	public TimedMotionScenario(){
		buildRobotsVector();
		buildObstaclesVector();
	}//ScenarioBuilder
	
	private void buildRobotsVector(){		
		addRobot(new TimedMotion(1,new Position(7,7,0),2));
		addRobot(new TimedMotion(2,new Position(10,10,0),2));
	}//buildRobotsVector
	
	private void buildObstaclesVector(){
		// open arena: no obstacles
	}//buildObstaclesVector
	
}
