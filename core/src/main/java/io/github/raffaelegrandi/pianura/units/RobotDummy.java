// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.units;

import java.awt.Color;

import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Base class for user-defined robots: implement {@link #executeLoop()}.
 *
 * The simulated fault ({@code faultTime}) is in SIMULATED time: when that instant comes
 * the engine deactivates the robot, which stays in the arena as a still obstacle.
 */
public abstract class RobotDummy extends RobotUnit {

	/** Instant of the fault in seconds of simulated time, negative = no fault. */
	private final double faultTimeSec;

	/**
	 * @param faultTime instant of the simulated fault in milliseconds (simulated time), negative = none
	 */
	public RobotDummy(int unitIDnum, Position unitPos, double sensRange, int faultTime, Color unitColor){
		super(unitIDnum, unitPos, sensRange, unitColor);
		faultTimeSec = faultTime >= 0 ? faultTime / 1000.0 : -1;
	}

	public RobotDummy(int unitIDnum, Position unitPos, double sensRange){
		this(unitIDnum, unitPos, sensRange, -1, Const.unitColor);
	}

	/** Instant of the simulated fault in seconds, negative if none. */
	public double getFaultTime(){ return faultTimeSec; }

	public abstract void executeLoop();

}
