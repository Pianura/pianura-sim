// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.structures;

import java.util.ArrayList;
import java.util.List;

import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.utils.Const;

public class ScenarioManager {

	private List<Obstacle> obstacles;
	private List<RobotDummy> robots;
	private boolean unitsManagerSet = false;
	private FloorStyle floor = FloorStyle.checkerboard();
	private final io.github.raffaelegrandi.pianura.comm.CommSettings commSettings = new io.github.raffaelegrandi.pianura.comm.CommSettings();
	private double arenaWidthCells = Const.gridCellCount;
	private double arenaHeightCells = Const.gridCellCount;
	private double metersPerCell = Const.DEFAULT_METERS_PER_CELL;
	private final java.util.Map<Integer, SceneFile.RobotPose> scenePoses = new java.util.HashMap<>();

	public ScenarioManager(){
		robots = new ArrayList<>();
		obstacles = new ArrayList<>();
	}

	public void addRobot(RobotDummy robot){
		robots.add(robot);
		SceneFile.RobotPose p = scenePoses.get(robot.getIDnum());
		if (p != null) robot.getUnitObject().setPos(new io.github.raffaelegrandi.pianura.utils.Position(p.x(), p.y(), p.theta()));
	}

	/**
	 * Loads a scene saved from the window (Save) or written by hand: arena size and scale,
	 * obstacles (they replace the ones added so far) and the robots' positions, by id. Robots
	 * added before or after this call are placed where the scene says; robots the scene does
	 * not mention keep their own position. Call it in the scenario's constructor:
	 * <pre>
	 *   loadScene(Path.of("my-arena.json"));
	 *   loadScene(getClass().getResourceAsStream("my-arena.json"));   // from the jar
	 * </pre>
	 */
	public void loadScene(java.nio.file.Path file) throws java.io.IOException { applyScene(SceneFile.read(file)); }

	/** @see #loadScene(java.nio.file.Path) */
	public void loadScene(java.io.InputStream in) throws java.io.IOException { applyScene(SceneFile.read(in)); }

	private void applyScene(SceneFile.Scene s){
		setArenaSize(s.arenaWidth(), s.arenaHeight());
		setMetersPerCell(s.metersPerCell());
		obstacles.clear();
		obstacles.addAll(s.obstacles());
		scenePoses.clear();
		for (SceneFile.RobotPose p : s.robots()) scenePoses.put(p.id(), p);
		for (RobotDummy r : robots) {
			SceneFile.RobotPose p = scenePoses.get(r.getIDnum());
			if (p != null) r.getUnitObject().setPos(new io.github.raffaelegrandi.pianura.utils.Position(p.x(), p.y(), p.theta()));
		}
	}

	/** Saves arena, obstacles and robot positions as they are now (the same file the window's Save writes). */
	public void saveScene(java.nio.file.Path file) throws java.io.IOException {
		SceneFile.write(file, arenaWidthCells, arenaHeightCells, metersPerCell, obstacles, robots);
	}

	public void addObstacle(Obstacle obstacle){ obstacles.add(obstacle);}

	/** Look of the floor (also read by the floor sensors). Default: the original checkerboard. */
	public void setFloor(FloorStyle floorStyle){ this.floor = floorStyle != null ? floorStyle : FloorStyle.checkerboard(); }

	public FloorStyle getFloor(){ return floor; }

	/**
	 * Radio channel between robots, e.g. {@code getCommSettings().setRange(8).setLineOfSight(true)}.
	 * Defaults: range 6 cells, no line of sight, no losses, delivery at the next physics step.
	 */
	public io.github.raffaelegrandi.pianura.comm.CommSettings getCommSettings(){ return commSettings; }

	/**
	 * Size of the arena in cells (default 40 x 40). Call it in the scenario's constructor,
	 * before starting the simulation.
	 */
	public void setArenaSize(double widthCells, double heightCells){
		if (!(widthCells > 0 && heightCells > 0)) throw new IllegalArgumentException("invalid arena size");
		arenaWidthCells = widthCells;
		arenaHeightCells = heightCells;
	}

	/**
	 * Scale of the scenario: how many meters one cell stands for (default 0.05, so the robot,
	 * 2 cells across, is 10 cm wide). Pianura keeps working in cells; the scale is shown in the
	 * window and sent to remote clients, which may convert to meters (e.g. a ROS bridge).
	 */
	public void setMetersPerCell(double m){
		if (!(m > 0)) throw new IllegalArgumentException("metersPerCell must be > 0");
		metersPerCell = m;
	}

	public double getMetersPerCell(){ return metersPerCell; }

	public double getArenaWidth(){ return arenaWidthCells; }

	public double getArenaHeight(){ return arenaHeightCells; }

	/** Arena width in world pixels (cells x Const.cellSize). */
	public double getArenaWidthPx(){ return arenaWidthCells * Const.cellSize; }

	/** Arena height in world pixels (cells x Const.cellSize). */
	public double getArenaHeightPx(){ return arenaHeightCells * Const.cellSize; }

	public List<RobotDummy> getRobots(){ return robots; }

	public List<Obstacle> getObstacles(){ return obstacles; }

	public int getNumRobots(){ return robots.size();}

	public int getNumObstacles(){ return obstacles.size();}

	public void removeRobot(int index){ robots.remove(index);}

	public void setUnitsManagerSet(boolean set){ this.unitsManagerSet = set; }

	public void enableRobots(){
		if (unitsManagerSet){
			// simulated faults are handled by the engine, in simulated time
			for (RobotDummy r : robots) r.start();
		}
		else { System.err.println("ERROR: unable to start robots - unitsManager not set");}
	}

	public void disableRobots(){
		for(int i = 0; i < robots.size(); i++){
			robots.get(i).setActive(false);
		}
	}

	public void disableRobot(int index){
		robots.get(index).setActive(false);
	}

}
