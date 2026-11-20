// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.sim;

/**
 * Thrown inside a robot's thread when the simulation is stopped while the robot is
 * waiting ({@code sleepSim}). It ends the robots' loops cleanly, even those written as
 * {@code while(true)}. RobotUnit catches it: user code does not need to handle it.
 */
public class SimulationStoppedException extends RuntimeException {
    public SimulationStoppedException() {
        super("simulation stopped", null, false, false);
    }
}
