package io.github.raffaelegrandi.pianura.arena;
	
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Point2D;

import io.github.raffaelegrandi.pianura.utils.Position;

public class ObjectID {
	
	private int objIDnum;
	private ArenaObjectType objType;
	private Position objPos;
	private volatile double width, height;   // in cells, obstacles only
	private Color objColor;
	
	
	public ObjectID(ArenaObjectType objectType, Color objectColor, Position objectPosition, Dimension objectDimension){
		objType = objectType;
		objColor = objectColor;
		objPos = objectPosition;
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
	
	public synchronized Position getPos(){return objPos;}
	
	public synchronized void setPos(Position newPos){objPos = newPos;}

	public synchronized void setLoc(Point2D newLoc){objPos.x = newLoc.getX(); objPos.y = newLoc.getY(); }
	
	public synchronized double getAngle(){return objPos.theta;} //radians
	
	public synchronized void setAngle(double newAngle){ objPos.theta = newAngle%(2*Math.PI); }
	
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
