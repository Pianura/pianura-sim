// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.arena;
	
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Point2D;

import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Identity and pose of an object in the arena.
 *
 * <p>The pose is published as a whole: every change replaces the {@link Position} object with
 * a new one, and the one returned by {@link #getPos()} is never modified afterwards. A reader
 * on another thread (a robot's controller, the view, a remote client) therefore always sees
 * x, y and theta of the same instant, and can keep the object as a snapshot.
 */
public class ObjectID {
	
	private int objIDnum;
	private ArenaObjectType objType;
	private volatile Position objPos;   // replaced, never modified in place
	private volatile double width, height;   // in cells, obstacles only
	private Color objColor;
	
	
	public ObjectID(ArenaObjectType objectType, Color objectColor, Position objectPosition, Dimension objectDimension){
		objType = objectType;
		objColor = objectColor;
		objPos = new Position(objectPosition);   // a copy: the caller may reuse its object
		if (objectDimension != null) { width = objectDimension.width; height = objectDimension.height; }
	}

	/** Obstacle with non-integer size (cells). */
	public ObjectID(ArenaObjectType objectType, Color objectColor, Position objectPosition, double widthCells, double heightCells){
		this(objectType, objectColor, objectPosition, (Dimension) null);
		width = widthCells;
		height = heightCells;
	}
	
	public synchronized int getIDnum(){return objIDnum;}
	
	public void setIDnum(int objIDnumber){objIDnum = objIDnumber;}
	
	/** The current pose: a snapshot, never modified afterwards (do not modify it either). */
	public Position getPos(){return objPos;}
	
	public void setPos(Position newPos){objPos = new Position(newPos);}

	public synchronized void setLoc(Point2D newLoc){ Position p = objPos; objPos = new Position(newLoc.getX(), newLoc.getY(), p.theta); }
	
	public double getAngle(){return objPos.theta;} //radians
	
	public synchronized void setAngle(double newAngle){ Position p = objPos; objPos = new Position(p.x, p.y, newAngle%(2*Math.PI)); }
	
	public ArenaObjectType getType(){return objType;}
	
	public Color getColor(){return objColor;}
	
	/** Size rounded to integers (compatibility): for the exact values use getWidth()/getHeight(). */
	public Dimension getDim(){ return new Dimension((int) Math.round(width), (int) Math.round(height)); }

	/** Width in cells (the obstacle's local x axis). */
	public double getWidth(){ return width; }

	/** Height in cells (the obstacle's local y axis). */
	public double getHeight(){ return height; }

	/** New size in cells (e.g. from resizing with the mouse). */
	public synchronized void setSize(double widthCells, double heightCells){ width = widthCells; height = heightCells; }
	
}//end class
