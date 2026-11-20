package io.github.raffaelegrandi.pianura.units;

/**
 * Control logic of a robot, separate from the robot itself.
 *
 * The simulator calls {@link #control(RobotUnit, double)} at regular intervals of
 * simulated time: the controller reads the sensors from the robot and sets the motors.
 * It is the "callback" alternative to the sequential style of RobotDummy.executeLoop():
 *
 * <pre>
 *   addRobot(new ControllerRobot(1, new Position(5, 5, 0), 2, 0.1,
 *       (robot, t) -&gt; robot.forward(1.0)));   // 1 cell/s straight ahead
 * </pre>
 *
 * Remote controllers follow the same contract (e.g. an external program connected over
 * the network): an implementation can forward the sensors to another process and receive
 * the commands from it.
 */
@FunctionalInterface
public interface RobotController {

    /**
     * One control cycle.
     * @param robot   the controlled robot (sensors to read, motors to set)
     * @param simTime current simulated time in seconds
     */
    void control(RobotUnit robot, double simTime);

    /** Called once before the first cycle. */
    default void onStart(RobotUnit robot) {}

    /** Called when the robot stops (end of the simulation, fault, error). */
    default void onStop(RobotUnit robot) {}

}
