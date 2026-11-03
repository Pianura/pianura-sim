package io.github.raffaelegrandi.pianura.algorithms.braitenberg;

import io.github.raffaelegrandi.pianura.units.RobotController;
import io.github.raffaelegrandi.pianura.units.RobotUnit;
import io.github.raffaelegrandi.pianura.utils.Const;

/**
 * A Braitenberg "fear" vehicle on the 18 infrared sensors.
 *
 * <p>Idea (V. Braitenberg, <i>Vehicles: Experiments in Synthetic Psychology</i>, MIT Press, 1984):
 * the controller has no map and no plan. Every sensor is wired to the motors with a fixed weight,
 * and the behaviour comes from the wiring alone:
 * <ul>
 *   <li>lateral wiring: an obstacle on one side turns the vehicle away from it;</li>
 *   <li>frontal wiring: an obstacle ahead slows the vehicle down.</li>
 * </ul>
 * The lateral wiring is skewed by a few degrees on purpose: a perfectly symmetric vehicle facing
 * an obstacle head-on would have no reason to turn either way. After a contact the vehicle turns
 * on the spot, away from the side it was steering to.
 *
 * <p>Infrared readings are the distance in pixels from the robot's surface to the nearest hit,
 * 255 when nothing is within the sensing range; sensors are numbered clockwise from the heading,
 * 20 degrees apart. Give the robot a sensing range of about 4 cells, so it has time to react.
 *
 * <p>Added in 2026. First written for the historical edition of JUSE (as a {@code RobotDummy}
 * loop), then adapted to Pianura's {@link RobotController} API: it runs at a fixed period of
 * simulated time and commands linear and angular speed.
 */
public class BraitenbergVehicle implements RobotController {

	/** Forward speed with nothing in range, cells/s. */
	public static final double CRUISE = 1.5;
	/** Angular speed for a full lateral activation, rad/s (positive = clockwise on screen). */
	public static final double STEER_GAIN = 3.0;
	/** How much a full frontal activation slows the vehicle down (0..1). */
	public static final double BRAKE_GAIN = 0.9;
	/** Skew of the lateral wiring. */
	public static final double SKEW = Math.toRadians(25);
	/** Turn on the spot after a contact: angular speed (rad/s) and duration (control periods). */
	public static final double ESCAPE_SPEED = 2.5;
	public static final int ESCAPE_TICKS = 4;

	private final double[] steerWeight = new double[Const.sensorCount];
	private final double[] brakeWeight = new double[Const.sensorCount];
	private double lastSteer = 0;
	private int escaping = 0;

	public BraitenbergVehicle() {
		// The "wiring": one fixed weight per sensor, computed once from the sensor angle.
		double step = 2 * Math.PI / Const.sensorCount;
		for (int i = 0; i < Const.sensorCount; i++) {
			double a = i * step;                        // clockwise from the heading
			boolean ahead = Math.cos(a) > -0.2;         // sensors on the rear half are not wired
			steerWeight[i] = ahead ? Math.sin(a + SKEW) : 0;   // > 0 on the right side
			brakeWeight[i] = ahead ? Math.max(0, Math.cos(a)) : 0;
		}
	}

	@Override
	public void control(RobotUnit robot, double simTime) {
		if (escaping > 0 || robot.hasCollision()) {     // touched something: turn away on the spot
			if (escaping == 0) escaping = ESCAPE_TICKS;
			escaping--;
			robot.setRobotSpeed(0, lastSteer >= 0 ? -ESCAPE_SPEED : ESCAPE_SPEED);
			return;
		}
		double range = robot.getUnitObject().getSensorRange() * Const.cellSize;   // pixels
		int[] ir = robot.getAllSens();
		double steer = 0, brake = 0;
		for (int i = 0; i < ir.length; i++) {
			double activation = ir[i] < range ? (range - ir[i]) / range : 0;   // 0 far .. 1 touching
			steer += steerWeight[i] * activation;
			brake += brakeWeight[i] * activation;
		}
		steer = Math.max(-1, Math.min(1, steer));       // > 0: obstacle on the right
		brake = Math.max(0, Math.min(1, brake));
		if (steer != 0) lastSteer = steer;

		// obstacle on the right -> turn left (counter-clockwise, negative angular speed)
		robot.setRobotSpeed(CRUISE * (1 - BRAKE_GAIN * brake), -STEER_GAIN * steer);
	}

}
