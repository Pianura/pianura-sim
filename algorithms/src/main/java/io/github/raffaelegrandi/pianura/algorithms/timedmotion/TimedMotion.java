package io.github.raffaelegrandi.pianura.algorithms.timedmotion;

import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.UnitObject;
import io.github.raffaelegrandi.pianura.utils.Position;


/**
 * Open-loop motion: a fixed sequence of wheel speeds, each held for a fixed time, then the robot
 * stops. No sensor is read.
 *
 * <p>From JUSE (2008-2011), where it was {@code TestDummy03}.
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
		setMotorSpeed(1.5,1.5);		
		sleepSimTime(2);
		setMotorSpeed(1.5,6);
		sleepSimTime(2);
		setMotorSpeed(2,6);
		sleepSimTime(1);
		setMotorSpeed(2,2);
		sleepSimTime(4);
		setActive(false);		
		System.out.println("EndLoop - Disable Unit");
	}//end executeLoop	
	
	private void sleepSimTime(double st_val){
		// tempo simulato: rispetta pausa, passo singolo e velocità
		sleepSim(st_val);
	}//sleepTime	
	
}//end TestDummy
