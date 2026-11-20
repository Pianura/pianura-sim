package io.github.raffaelegrandi.pianura.units;

import java.awt.Color;
import io.github.raffaelegrandi.pianura.arena.ObjectID;
import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimulationStoppedException;
import io.github.raffaelegrandi.pianura.units.collisions.SensorArray;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

public abstract class RobotUnit implements Runnable{

	private Thread thread;
	private volatile SimClock clock;
	private volatile io.github.raffaelegrandi.pianura.comm.CommNetwork network;
	private final java.util.concurrent.ConcurrentLinkedQueue<io.github.raffaelegrandi.pianura.comm.Message> inbox = new java.util.concurrent.ConcurrentLinkedQueue<>();
	private long messageSeq = 0;                       // robot's thread only
	private volatile double commRange = Double.NaN;    // NaN = the scenario's default range

	private ObjectID unitID;
	private UnitObject unitObj;
	protected volatile boolean activeStatus = true;
	// Written by the robot's thread, read by the simulation thread
	private volatile double vK = 0;
	private volatile double wK = 0;
	private volatile boolean collisionStatus = false;
	// distance travelled by each wheel (cells, signed): written by the engine, read by the view
	private volatile double leftWheelTravel = 0, rightWheelTravel = 0;
	private volatile double metersPerCell = Const.DEFAULT_METERS_PER_CELL;
	private volatile io.github.raffaelegrandi.pianura.viz.Viz viz;   // created on first use
	private volatile io.github.raffaelegrandi.pianura.inspect.Inspector inspector;   // created on first use
	private volatile java.util.function.Consumer<String> pauseHook;   // set by the engine

	public RobotUnit(int unitIDnum, Position unitPos, double sensRange, Color unitColor){
		unitObj = new UnitObject(new ObjectID(ArenaObjectType.UNIT, unitColor, unitPos, Const.unitDim), sensRange);
		unitID = unitObj.getID();
		unitID.setIDnum(unitIDnum);
	}

	/***********************************************************************************************************/

	/**
	 * Activates/deactivates the robot. Deactivated, the robot stays still as an obstacle and
	 * its thread ends at the next sleepSim().
	 */
	public void setActive(boolean status){
		activeStatus = status;
		if (!status && clock != null) clock.deregister(this);   // wakes the thread if it is in sleepSim
	}

	public boolean hasCollision(){ return collisionStatus; }

	/***********************************************************************************************************/

	public UnitObject getUnitObject(){ return unitObj; }

	public int getIDnum(){ return unitID.getIDnum(); }

	public Position getPosition(){ return unitObj.getExactPos(); }

	/***********************************************************************************************************/

	/*  Units: lengths in cells, time in seconds, angles in radians. One cell is
	 *  getMetersPerCell() meters (scenario setting, default 0.05 m: a robot 10 cm across). */

	/**
	 * The 18 infrared sensors: distance in cells from the robot's surface to the nearest object,
	 * {@link #getIrRange()} when nothing is in range. Index 0 points straight ahead, then
	 * clockwise on screen every 20 degrees.
	 */
	public double[] getIr(){ return unitObj.getIrSensors().getSensors(); }

	/** Infrared sensor {@code index}, in cells (see {@link #getIr()}). */
	public double getIr(int index){ return unitObj.getIrSensors().getSensorValue(index); }

	/** Range of the infrared sensors in cells, measured from the robot's surface. */
	public double getIrRange(){ return unitObj.getSensorRange(); }

	/** Number of infrared sensors (18). */
	public int getIrCount(){ return unitObj.getIrSensors().length(); }

	/** Radius of the robot's body, cells (1). */
	public double getBodyRadius(){ return (double) Const.unitRadius / Const.cellSize; }

	/** Distance between the two wheels, cells (2: the wheels sit on the rim of the body). */
	public double getWheelBase(){ return unitObj.wheelsDistance; }

	/** Radius of the wheels, cells: converts wheel speeds (cells/s) into wheel turn rates (rad/s). */
	public double getWheelRadius(){ return unitObj.wheelRadius; }

	/** Scale of the scenario: meters per cell (default 0.05). Lengths in the API stay in cells. */
	public double getMetersPerCell(){ return metersPerCell; }

	/** Set by the simulation from the scenario (ScenarioManager.setMetersPerCell). Not meant for robot code. */
	public void attachScale(double metersPerCell){ this.metersPerCell = metersPerCell; }

	/***********************************************************************************************************/
	/*  Floor sensors (line following)                                                                         */
	/***********************************************************************************************************/

	/** Number of floor sensors (2 by default: index 0 = left, 1 = right). */
	public int getFloorSensorCount(){ return unitObj.getFloorSensors().count(); }

	/** Brightness of the floor under sensor i: 0 = black, 255 = white. */
	public int getFloorGray(int i){ return unitObj.getFloorSensors().getGray(i); }

	/** Brightness under all the floor sensors. */
	public int[] getFloorGrays(){ return unitObj.getFloorSensors().getGrays(); }

	/** Color of the floor under sensor i (average over the sensitive area). */
	public Color getFloorColor(int i){ return unitObj.getFloorSensors().getColor(i); }

	/**
	 * Configures the floor sensors: distance from the centre as a fraction of the robot's
	 * radius, radius of the sensitive area in cells, angles in degrees from the direction of
	 * travel (negative = left, positive = right). Example: {@code configureFloorSensors(0.97, 0.1, -20, 20)}.
	 */
	public void configureFloorSensors(double radiusFraction, double footprintCells, double... anglesDeg){
		unitObj.getFloorSensors().setLayout(new io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray.Layout(radiusFraction, footprintCells, anglesDeg));
	}

	/**********************************************************************************************************/

	/**
	 * Wheel speeds in cells/s (speed of each wheel on the ground; negative = backwards).
	 * Equal speeds go straight; left faster than right turns clockwise on screen (to the right).
	 * Equivalent to {@code setRobotSpeed((left + right) / 2, (left - right) / getWheelBase())}.
	 */
	public void setWheelSpeeds(double left, double right){
		double L = unitObj.wheelsDistance;
		vK = (left + right) / 2;
		wK = (left - right) / L;
	}

	/** Body speeds: linear in cells/s, angular in rad/s (positive = clockwise on screen). */
	public void setRobotSpeed(double linSpeed, double angSpeed){
		vK = linSpeed;
		wK = angSpeed;
	}

	public boolean isActive(){ return activeStatus; }

	/** Commanded linear speed (cells/s). */
	public double getLinearSpeed(){ return vK; }

	/** Commanded angular speed (rad/s, positive = clockwise on screen). */
	public double getAngularSpeed(){ return wK; }

	/**
	 * Distance travelled by the left wheel (cells, signed) since the start of the simulation.
	 * It counts the wheel's rotation: if the robot pushes against an obstacle the wheel turns
	 * anyway (slipping). The basis for odometry.
	 */
	public double getLeftWheelTravel(){ return leftWheelTravel; }

	/** Distance travelled by the right wheel (cells, signed). */
	public double getRightWheelTravel(){ return rightWheelTravel; }

	/***********************************************************************************************************/
	/*  Debug drawings                                                                                         */
	/***********************************************************************************************************/

	/**
	 * Debug drawing layer {@code name} of this robot, drawn over the arena (world coordinates in
	 * cells). Shows what the algorithm thinks: a planned path, a tree, an estimated map...
	 * <pre>
	 *   viz("plan").clear().color(Color.BLUE).polyline(xs, ys).disc(goalX, goalY, 0.3);
	 * </pre>
	 * What is drawn appears at the next {@link #sleepSim(double)}. Call it from the robot's own code.
	 */
	public io.github.raffaelegrandi.pianura.viz.VizLayer viz(String name){ return drawings().layer(name); }

	/** The default debug drawing layer ({@code viz("default")}). */
	public io.github.raffaelegrandi.pianura.viz.VizLayer viz(){ return drawings().layer(io.github.raffaelegrandi.pianura.viz.Viz.DEFAULT_LAYER); }

	/** All the debug drawings of this robot (layers, clearAll(), publish()). */
	public io.github.raffaelegrandi.pianura.viz.Viz drawings(){
		io.github.raffaelegrandi.pianura.viz.Viz v = viz;
		if (v == null) {
			synchronized (this) {
				if (viz == null) viz = new io.github.raffaelegrandi.pianura.viz.Viz(m -> System.err.println("Robot #" + getIDnum() + ": " + m));
				v = viz;
			}
		}
		return v;
	}

	/** Published debug drawings (layer name to shapes), empty if the robot never drew. For the view; any thread. */
	public java.util.Map<String, java.util.List<io.github.raffaelegrandi.pianura.viz.Shape>> getDrawings(){
		io.github.raffaelegrandi.pianura.viz.Viz v = viz;
		return v == null ? java.util.Map.of() : v.snapshot();
	}

	/**
	 * What this robot shows in the inspector window: state, watched values, time plots and a
	 * log of decisions, all stamped with the simulated time.
	 * <pre>
	 *   inspector().state("searching").watch("nodes", n).plot("distance to goal", d);
	 *   inspector().log("path found");
	 * </pre>
	 */
	public io.github.raffaelegrandi.pianura.inspect.Inspector inspector(){
		io.github.raffaelegrandi.pianura.inspect.Inspector in = inspector;
		if (in == null) {
			synchronized (this) {
				if (inspector == null) inspector = new io.github.raffaelegrandi.pianura.inspect.Inspector(this::getSimTime);
				in = inspector;
			}
		}
		return in;
	}

	/** True if the robot has used its inspector (for the window: robots without data are listed last). */
	public boolean hasInspectorData(){ return inspector != null; }

	/**
	 * Pauses the whole simulation, as the Pause button does, e.g. to look at a decision:
	 * {@code if (pathFound) pauseSimulation("first path found")}. The reason goes to the log and
	 * to the inspector. The robot itself goes on with its current step; Run resumes everything.
	 * Ignored outside a running simulation (and in runUntil).
	 */
	public void pauseSimulation(String reason){
		java.util.function.Consumer<String> h = pauseHook;
		if (inspector != null || reason != null) inspector().log("paused the simulation: " + reason);
		if (h != null) h.accept(reason);
	}

	/** Set by the simulation engine. Not meant for robot code. */
	public void attachPauseHook(java.util.function.Consumer<String> hook){ this.pauseHook = hook; }

	private void publishDrawings(){
		io.github.raffaelegrandi.pianura.viz.Viz v = viz;
		if (v != null) v.publish();
	}

	/***********************************************************************************************************/
	/*  Simulated time                                                                                         */
	/***********************************************************************************************************/

	/***********************************************************************************************************/
	/*  Communication between robots                                                                            */
	/***********************************************************************************************************/

	/** Connects the robot to the radio network (done by SimEngine). */
	public void attachNetwork(io.github.raffaelegrandi.pianura.comm.CommNetwork net){ this.network = net; }

	/**
	 * Sends a message to every robot in range (one hop). The payload must be JSON-like data:
	 * String, number, boolean, null, Map&lt;String,?&gt; or List&lt;?&gt; (numbers arrive as Double).
	 */
	public void broadcast(Object payload){ requireNetwork().submit(this, io.github.raffaelegrandi.pianura.comm.Message.BROADCAST, payload, messageSeq++); }

	/** Sends a message to robot {@code toId}: it arrives only if that robot is directly in range (one hop). */
	public void send(int toId, Object payload){
		if (toId < 0) throw new IllegalArgumentException("invalid receiver id " + toId);
		requireNetwork().submit(this, toId, payload, messageSeq++);
	}

	/** Messages received since the last call (oldest first); empty list if none. */
	public java.util.List<io.github.raffaelegrandi.pianura.comm.Message> receive(){
		java.util.List<io.github.raffaelegrandi.pianura.comm.Message> out = new java.util.ArrayList<>();
		for (io.github.raffaelegrandi.pianura.comm.Message m; (m = inbox.poll()) != null; ) out.add(m);
		return out;
	}

	/** True if there are messages waiting to be read with receive(). */
	public boolean hasMessages(){ return !inbox.isEmpty(); }

	/** Transmission range of this robot in cells; NaN restores the scenario default. */
	public void setCommRange(double cells){ commRange = cells; }

	/** Own transmission range in cells, or NaN if the scenario default is used. */
	public double getCommRange(){ return commRange; }

	/** Called by the network: puts a message in the inbox. Not meant for robot code. */
	public void deliverMessage(io.github.raffaelegrandi.pianura.comm.Message m){ inbox.add(m); }

	private io.github.raffaelegrandi.pianura.comm.CommNetwork requireNetwork(){
		io.github.raffaelegrandi.pianura.comm.CommNetwork n = network;
		if (n == null) throw new IllegalStateException("robot #" + getIDnum() + " is not part of a simulation: no radio network");
		return n;
	}

	/** Connects the robot to the simulation's clock (done by Simulator/SimEngine). */
	public void attachClock(SimClock clock){ this.clock = clock; }

	/** Current simulated time in seconds (0 if the robot is not in a simulation). */
	public double getSimTime(){ return clock != null ? clock.now() : 0; }

	/**
	 * Waits {@code seconds} of SIMULATED time. Use it instead of Thread.sleep() in robot code:
	 * this way pause, single step and speed-up work, and the simulation is reproducible.
	 */
	public void sleepSim(double seconds){
		publishDrawings();   // what was drawn since the last sleep becomes visible all at once
		if (clock == null) {   // outside a simulation (e.g. tests): real time
			try { Thread.sleep((long)(seconds * 1000)); }
			catch (InterruptedException e) { Thread.currentThread().interrupt(); }
			return;
		}
		clock.sleep(this, seconds);
		if (!activeStatus) throw new SimulationStoppedException();
	}

	/***********************************************************************************************************/

	public void start() {
		activeStatus = true;
		if (clock != null) clock.register(this, "Robot #" + getIDnum());
		thread = Thread.ofVirtual().name("robot-" + getIDnum()).start(this);
	}

	public void run(){
		try {
			while(activeStatus) executeLoop();
		} catch (SimulationStoppedException e) {
			// simulation stopped or robot deactivated: normal exit
		} catch (RuntimeException e) {
			System.err.println("Robot #" + getIDnum() + " terminated by an error: " + e);
			e.printStackTrace();
		} finally {
			publishDrawings();
			activeStatus = false;
			if (clock != null) clock.deregister(this);
		}
	}

	public abstract void executeLoop();

	/**********************************	Moving Methods **********************************************************/

	/**
	 * Turns on the spot by {@code angle} degrees (positive = clockwise on screen), at
	 * Const.angleStep degrees every {@code tms} milliseconds. The rotation happens in simulated
	 * time through the motors, so it respects physics, pause and speed-up.
	 */
	public void rotateBy(int angle, int tms){
		if (angle == 0) return;
		double w = Math.toRadians(Const.angleStep) / (tms / 1000.0);   // rad/s
		double duration = Math.toRadians(Math.abs(angle)) / w;
		setRobotSpeed(0, Math.signum(angle) * w);
		sleepSim(duration);
		setRobotSpeed(0, 0);
		collisionStatus = false;
	}

	/** Straight ahead at {@code speed} cells/s. */
	public void forward(double speed){ setRobotSpeed(Math.abs(speed), 0); }

	/** Straight back at {@code speed} cells/s. */
	public void backward(double speed){ setRobotSpeed(-Math.abs(speed), 0); }

	/***********************************************************************************************************/

	/**
	 * Advances the robot by one step, respecting contacts ("sliding" response).
	 *
	 * The robot's body is a disc, so rotation can never cause interpenetration and is always
	 * allowed. Of the centre's translation, only the component towards each active contact
	 * (bump sensor) is removed: the robot does not enter the obstacle but can slide along it,
	 * and can always move away from it. The motors are NOT reset: as on a real robot, it is
	 * the robot's logic that decides how to react to the bump.
	 *
	 * The previous rule (full stop except pure rotation on the spot) deadlocked the strategies
	 * that steer on one wheel: such a turn always has a forward component, which was blocked.
	 */
	public void evaluateMovement(double timeSample){
		// wheel travel: the wheels turn as commanded even when a contact blocks the body (slipping)
		double v = vK, w = wK, half = unitObj.wheelsDistance / 2;
		leftWheelTravel  += (v + w * half) * timeSample;
		rightWheelTravel += (v - w * half) * timeSample;

		Position cur  = unitObj.getExactPos();
		Position next = computeNext(cur, timeSample);
		double dx = next.x - cur.x;
		double dy = next.y - cur.y;

		boolean blocked = false;
		SensorArray bump = unitObj.getBumpSensors();
		if (!bump.isEmpty()) {
			double[] contacts = bump.getSensors();
			// Two passes: with several contacts (e.g. a corner between two walls) a single
			// projection can reintroduce a component towards the first contact.
			for (int pass = 0; pass < 2; pass++) {
				for (int i = 0; i < contacts.length; i++) {
					if (contacts[i] == bump.getInitValue()) continue;
					double a  = cur.theta + bump.getSensorAngle(i);
					double nx = Math.cos(a), ny = Math.sin(a);
					double p  = dx * nx + dy * ny;
					if (p > 0) { dx -= p * nx; dy -= p * ny; blocked = true; }
				}
			}
		}
		collisionStatus = blocked;
		if (Const.debugEnabled && blocked) System.out.println("RU" + getIDnum() + "> motion constrained by contact");
		unitObj.setPos(new Position(cur.x + dx, cur.y + dy, next.theta));
	}

	/***********************************************************************************************************/

	/** Next pose by differential kinematics (vK, wK read once). */
	private Position computeNext(Position currPos, double timeSample){
		double v = vK, w = wK;
		if (Math.abs(w) < 0.001) return computeRungeKutta(currPos, v, 0, timeSample);
		return computeExact(currPos, v, w, timeSample);
	}

	/************************************************************************************************/

	private Position computeRungeKutta(Position currPos, double vK, double wK, double timeSample){
		double nextTheta = (currPos.theta + wK*timeSample)%(2*Math.PI);
		double nextPosX = currPos.x + vK*timeSample*Math.cos(currPos.theta + wK*timeSample/2);
		double nextPosY = currPos.y + vK*timeSample*Math.sin(currPos.theta + wK*timeSample/2);
		Position newPos = new Position(nextPosX,nextPosY,nextTheta);
		return newPos;
	}

	/************************************************************************************************/

	private Position computeExact(Position currPos, double vK, double wK, double timeSample){
		double nextTheta = (currPos.theta + wK*timeSample)%(2*Math.PI);
		double nextPosX = currPos.x + (vK/wK)*(Math.sin(nextTheta) - Math.sin(currPos.theta));
		double nextPosY = currPos.y - (vK/wK)*(Math.cos(nextTheta) - Math.cos(currPos.theta));
		Position newPos = new Position(nextPosX,nextPosY,nextTheta);
		return newPos;
	}

}
