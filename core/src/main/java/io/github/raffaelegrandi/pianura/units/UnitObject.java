// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.units;

import java.awt.geom.Point2D;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectABS;
import io.github.raffaelegrandi.pianura.arena.ObjectID;
import io.github.raffaelegrandi.pianura.units.collisions.SensorArray;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/** Body of a robot in the arena: pose, sensors and wheel geometry. */
public class UnitObject extends ArenaObjectABS {

	private final SensorArray bumpSensors;
	private final SensorArray irSensors;
	private final io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray floorSensors = new io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray();
	private final double sensorRange;

	double wheelRadius = 0.25; // cells
	double wheelsDistance = 2; // cells: distance between the wheels (= robot diameter)

	public UnitObject(ObjectID unitIDref, double sensRange){
		super(unitIDref);
		sensorRange = sensRange < Const.sensorsCheckingRangeMin ? Const.sensorsCheckingRangeMin : sensRange;
		bumpSensors = new SensorArray();
		irSensors = new SensorArray(sensorRange);   // at rest = range: nothing in range
	}

	public int getIDnum(){return objectID.getIDnum();}

	public SensorArray getBumpSensors(){ return bumpSensors; }

	public SensorArray getIrSensors(){ return irSensors; }

	/** Range of the IR sensors in cells, measured from the robot's surface. */
	public double getSensorRange(){ return sensorRange; }

	/** Optical sensors facing the floor. */
	public io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray getFloorSensors(){ return floorSensors; }

	public void setPos(Position newPos){ objectID.setPos(newPos);}

	public void setLoc(Point2D newLoc){ objectID.setLoc(new Point2D.Double(newLoc.getX(),newLoc.getY())); }

	public Position getExactPos(){return objectID.getPos();}

}
