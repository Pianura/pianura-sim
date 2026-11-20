package io.github.raffaelegrandi.pianura.units.collisions;

import java.awt.geom.Point2D;
import java.util.Arrays;

import io.github.raffaelegrandi.pianura.utils.Const;

/**
 * Array of radial sensors of a robot.
 *
 * Thread-safety: the values are written by the simulation thread (SimEngine) and read
 * by the robot's thread. So that the robot never reads a half-updated state (e.g. all
 * values just reset), the values live in an immutable snapshot published with a single
 * volatile assignment: readers always see a complete, consistent reading of the last cycle.
 */
public class SensorArray {

	private record Snapshot(double[] values, Point2D[] points, int activeCount) {}

	private final int arrayLength = Const.sensorCount;
	private final double[] sensorAngleArray;
	private final double initSensValue;
	private volatile Snapshot snapshot;

	public SensorArray(double initVal){
		initSensValue = initVal;
		sensorAngleArray = new double[arrayLength];
		double sensorStepAngle = 360.0 / arrayLength; // 20 gradi
		for (int i = 0; i < arrayLength; i++) {
			sensorAngleArray[i] = Math.toRadians(sensorStepAngle * i);
		}
		resetSensors();
	}

	public SensorArray(){ this(0); }

	/***********************************************************************************************/

	/** Rest value of the sensor: 0 for bump sensors, the range (cells) for IR sensors. */
	public double getInitValue(){ return initSensValue; }

	/**
	 * Atomically publishes a new set of readings. The arrays are copied: the caller may reuse
	 * them. A sensor counts as active if its value differs from the rest value.
	 */
	public void publish(double[] values, Point2D[] points){
		double[] v = Arrays.copyOf(values, arrayLength);
		Point2D[] p = Arrays.copyOf(points, arrayLength);
		int count = 0;
		for (double x : v) if (x != initSensValue) count++;
		snapshot = new Snapshot(v, p, count);
	}

	public void resetSensors(){
		double[] v = new double[arrayLength];
		Arrays.fill(v, initSensValue);
		snapshot = new Snapshot(v, new Point2D[arrayLength], 0);
	}

	/* Single-value changes (copy-on-write on the snapshot). The simulator uses publish();
	 * these methods remain for tests and existing code. */

	public synchronized void setSensorValue(int sensNum, double sensValue){
		Snapshot s = snapshot;
		double[] v = s.values().clone();
		v[sensNum] = sensValue;
		publish(v, s.points());
	}

	public synchronized void resetSensorValue(int sensNum){ setSensorValue(sensNum, initSensValue); }

	public synchronized void setCheckingPoint(int sensNum, Point2D cPoint){
		Snapshot s = snapshot;
		Point2D[] p = s.points().clone();
		p[sensNum] = cPoint;
		publish(s.values(), p);
	}

	/***********************************************************************************************/

	public double getSensorValue(int sensNum){ return snapshot.values()[sensNum]; }

	/** Returns a copy of the current readings. */
	public double[] getSensors(){ return snapshot.values().clone(); }

	public double getSensorAngle(int sensNum){ return sensorAngleArray[sensNum]; }

	public Point2D getCheckPoint(int sensNum){ return snapshot.points()[sensNum]; }

	public int length(){ return arrayLength; }

	public int collisionNum(){ return snapshot.activeCount(); }

	public boolean isFull(){ return collisionNum() == length(); }

	public boolean isEmpty(){ return collisionNum() == 0; }

	public String toString(){
		Snapshot s = snapshot;
		StringBuilder sb = new StringBuilder("\n");
		for (int i = 0; i < arrayLength; i++){
			sb.append("sensor# ").append(i)
			  .append(", value: ").append(s.values()[i])
			  .append(", relativeCP: ").append(s.points()[i]).append('\n');
		}
		return sb.toString();
	}

}
