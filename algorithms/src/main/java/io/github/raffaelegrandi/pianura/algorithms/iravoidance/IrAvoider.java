// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.algorithms.iravoidance;

import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.UnitObject;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Reactive obstacle avoidance on the infrared sensors: an obstacle on the front right makes the
 * robot turn counter-clockwise, one on the front left clockwise, otherwise it goes straight.
 *
 * <p>From JUSE (2008-2011), where it was {@code TestDummy04}.
 */
public class IrAvoider extends RobotDummy {

	UnitObject tempUObj = this.getUnitObject();
	/** Cruise speed of each wheel, cells/s (500 in JUSE's motor units). */
	double wlk_init = 1.25;
	double wrk_init = 1.25;
	/** Increase of the outer wheel at each step of an avoidance turn, cells/s (+0.5 in JUSE's units). */
	static final double TURN_STEP = 0.00125;
	/** An obstacle closer than this to the body, in cells, triggers a turn (25 px in JUSE). */
	static final double NEAR = 25.0 / 15;
	boolean active = true;
	int lastRotDir = -1;
	boolean avoiding = false;

	public IrAvoider(int unitIDnum, Position unitPos, double sensRange) {
		super(unitIDnum, unitPos, sensRange);
	}

	@Override
	public void executeLoop() {
		System.out.println("IrAvoider #"+this.getIDnum()+" started");
		setWheelSpeeds(wlk_init, wrk_init);
		navigationManager();
		setActive(false);
		System.out.println("EndLoop - Disable Unit");
	}

	private void navigationManager(){
		System.out.println("NavMan");
		double wlk = wlk_init;
		double wrk = wrk_init;
		while(active){
			boolean bumpContact = !tempUObj.getBumpSensors().isEmpty();
			int rotDir = evalSensors(readSensors());

			if (bumpContact && rotDir == 0) {
				rotDir = lastRotDir;
			}

			switch(rotDir){
				case -1 :{
					if(Const.debugEnabled)System.out.println("TD #"+this.getIDnum()+">Increasing CCW rotation");
					wrk += TURN_STEP;
					wlk = 0;
				} break;
				case 0 :{
					if(Const.debugEnabled)System.out.println("TD #"+this.getIDnum()+">Speed reset");
					wrk = wrk_init;
					wlk = wlk_init;
				} break;
				case 1 :{
					if(Const.debugEnabled)System.out.println("TD #"+this.getIDnum()+">Increasing CW rotation");
					wrk = 0;
					wlk += TURN_STEP;
				} break;
			}
			setWheelSpeeds(wlk, wrk);
			sleepSimTime(0.2);
		}
	}

	private double[] readSensors(){
		double[] sensVal = getIr();
		String sensString = "";
		for(int i=0; i<sensVal.length;i++) sensString += sensVal[i]+",";
		if(Const.debugEnabled)System.out.println("TD #"+this.getIDnum()+" sensArr:"+sensString);
		return sensVal;
	}

	private int evalSensors(double[] sensVal){
		double delta = NEAR;
		boolean rightObstacle = (sensVal[0] < delta) || (sensVal[1] < delta) || (sensVal[2] < delta) || (sensVal[3] < delta) || (sensVal[4] < delta);
		boolean leftObstacle = (sensVal[17] < delta) || (sensVal[16] < delta) || (sensVal[15] < delta) || (sensVal[14] < delta);

		if (!rightObstacle && !leftObstacle) {
			avoiding = false;
			return 0;
		}

		if (!avoiding) {
			avoiding = true;
			if (rightObstacle && !leftObstacle) {
				lastRotDir = -1;
			} else if (leftObstacle && !rightObstacle) {
				lastRotDir = 1;
			}
		}
		return lastRotDir;
	}

	private void sleepSimTime(double st_val){
		// simulated time: respects pause, single step and speed
		sleepSim(st_val);
	}

}
