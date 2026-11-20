package io.github.raffaelegrandi.pianura.utils;

import java.awt.geom.Point2D;

	

/**
 * A pose: x and y in cells, theta in radians.
 *
 * <p>The poses Pianura publishes ({@code getPosition()}, {@code ObjectID.getPos()}) are
 * snapshots: Pianura never changes them after handing them out, so they can be kept and read
 * from any thread. The setters below are for one's own copies; changing a published pose does
 * not move the robot (use {@code setPos} with a new object) and confuses other readers.
 */
@SuppressWarnings("serial")
public class Position extends Point2D.Double {
	
	public double theta = 0;
	
	public Position(double pX, double pY, double yaw){
		super(pX,pY);
		theta=yaw;		
	}
	
	public Position(Position newPos){
		this(newPos.x, newPos.y, newPos.theta);
	}
	
	public Position(double pX, double pY){
		super(pX,pY);				
	}
	
	public Position(){ this(0,0,0); }
	
	/**********************************************************************************************/
	
	public Position getPosition(){ return new Position(x,y,theta); }
	
	public double getX(){ return x; }
	
	public double getY(){ return y; }
		
	public double getTheta(){ return theta%(2*Math.PI); }
	
	public Point2D getLocation(){ return new Point2D.Double(x,y);}
	
	/**********************************************************************************************/
	
	public void setPosition(double pX, double pY, double newYaw){
		x = pX;
		y = pY;
		theta = newYaw;
	}
	
	public void setPosition(Position newPos){ setPosition(newPos.x, newPos.y, newPos.theta);	}
	
	public void setPosition(int newX, int newY){
		x = newX;
		y = newY;
	}
	
	public void setLocation(Point2D.Double newPoint){ x=newPoint.x; y=newPoint.y;	}
		
	public void setLocation(double pX, double pY) {
		this.setPosition(pX, pY, 0);		
	}

	/**********************************************************************************************/
	
	public String toString(){ return new String("("+x+","+y+","+theta+")"); }
	
	/*
	public double distance(Position pointToTest){
		double X2 = Math.pow(this.x - pointToTest.x,2);
		double Y2 = Math.pow(this.y - pointToTest.y,2);
		double Z2 = Math.pow(this.teta - pointToTest.teta,2);
		return Math.sqrt(X2 + Y2 + Z2);
	}
	*/
	
}//end class
