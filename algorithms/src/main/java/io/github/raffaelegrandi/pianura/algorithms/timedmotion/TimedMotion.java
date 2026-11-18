package io.github.raffaelegrandi.pianura.algorithms.timedmotion;

import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.UnitObject;
import io.github.raffaelegrandi.pianura.utils.Position;


/**
 * Open-loop motion: a fixed sequence of wheel speeds (cells/s), each held for a fixed time, then
 * the robot stops. No sensor is read.
 *
 * <p>From JUSE (2008-2011), where it was {@code TestDummy03}. The sequence keeps the original
 * ratios between the wheels (1.5:1.5, 1.5:6, 2:6, 2:2); in the old motor units those speeds were
 * so low that the robot barely moved, so they are now half a cell per second per unit.
 */
public class TimedMotion extends RobotDummy {

	/* 
	 * Robot physical parameter
	 */

	UnitObject tempUObj = this.getUnitObject();
	double wlk_init, wrk_init;
	boolean active = true;

	public TimedMotion(int unitIDnum, Position unitPos, double sensRange) {
		super(unitIDnum, unitPos, sensRange);
	}

	@Override

	public void executeLoop() {
		System.out.println("TimedMotion #"+this.getIDnum()+" started");
		setWheelSpeeds(0.75, 0.75);   // straight on
		sleepSimTime(2);
		setWheelSpeeds(0.75, 3);      // right wheel faster: curve to the left
		sleepSimTime(2);
		setWheelSpeeds(1, 3);
		sleepSimTime(1);
		setWheelSpeeds(1, 1);
		sleepSimTime(4);
		setActive(false);		
		System.out.println("EndLoop - Disable Unit");
	}//end executeLoop	
	
	private void sleepSimTime(double st_val){
		// tempo simulato: rispetta pausa, passo singolo e velocità
		sleepSim(st_val);
	}//sleepTime	
	
}
