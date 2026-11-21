// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura;

import java.util.Locale;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import io.github.raffaelegrandi.pianura.gui.MainFrame;
import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.UnitObject;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.Const;

/**
 * Entry point: builds the simulation engine and the window for a scenario.
 *
 * <pre>
 *   Simulator sim = new Simulator(new Point(30, 30), "log", new MyScenario());
 *   sim.startSim(2);          // starts after 2 s
 *   // or: sim.startSimPaused();  and start it from the toolbar (Run / Space)
 * </pre>
 */
public class Simulator {

	private final ScenarioManager scenarioMngRef;
	private final SimClock clock;
	private final SimEngine engine;
	private final MainFrame mainFrame;
	private boolean started = false;
	private final List<io.github.raffaelegrandi.pianura.remote.RemoteServer> remoteServers = new ArrayList<>();

	public Simulator(Point frameLoc, String logInitStr, ScenarioManager scenarioMng){
		scenarioMngRef = scenarioMng;

		clock = new SimClock(Const.physicsStep);
		CollisionDetector collDetect = new CollisionDetector(scenarioMngRef.getObstacles(),
			scenarioMngRef.getArenaWidthPx(), scenarioMngRef.getArenaHeightPx());
		for (RobotDummy r : scenarioMngRef.getRobots()) r.attachClock(clock);
		engine = new SimEngine(clock, collDetect, scenarioMngRef.getRobots());
		engine.setFloor(scenarioMngRef.getFloor());   // read by the floor sensors and shown by the view
		engine.setCommSettings(scenarioMngRef.getCommSettings());
		engine.setMetersPerCell(scenarioMngRef.getMetersPerCell());
		scenarioMngRef.setUnitsManagerSet(true);

		io.github.raffaelegrandi.pianura.gui.LookAndFeelSetup.install();   // FlatLaf if present, else Metal
		mainFrame = new MainFrame(Const.guiFrameTitle, frameLoc, logInitStr, scenarioMngRef, engine);
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		java.util.function.Consumer<Simulator> home = homeHandler;
		if (home != null) mainFrame.simPanel.setHomeAction(() -> home.accept(this));
		engine.setLogger(msg -> printLogMsg(false, msg));
		if (mainFrame.simPanel.getControlBar() != null) mainFrame.simPanel.getControlBar().setStopAction(this::stopSim);

		CollisionDetector.Validation validation = engine.validate();
		mainFrame.simPanel.getArenaView().setValidation(validation);   // invalid elements in red
		for (String problem : validation.messages()) {
			System.err.println("SCENARIO WARNING: " + problem);
			printLogMsg(false, "WARNING: " + problem);
		}
	}

	private static volatile java.util.function.Consumer<Simulator> homeHandler;

	/**
	 * Gives a Home button to every simulation window created from now on: pressed, it calls the
	 * handler with its simulator. The start screen uses it to close the simulation and come
	 * back ({@link #close()}). Null removes it.
	 */
	public static void setHomeHandler(java.util.function.Consumer<Simulator> handler) { homeHandler = handler; }

	/** Stops the simulation, if it is not stopped yet, and closes its windows. The program goes on. */
	public void close() {
		if (!engine.isStopped()) stopSim();
		Runnable r = () -> { mainFrame.simPanel.dispose(); mainFrame.dispose(); };
		if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r);
	}

	/** The simulation's window. */
	public JFrame getWindow() { return mainFrame; }

	public void setNewScenario(ScenarioManager newScenario){
		mainFrame.simPanel.configureGUI(newScenario.getRobots(), newScenario.getObstacles());
	}

	/** Starts engine and robots paused: the simulation runs with Run (or Space) or resume(). */
	public void startSimPaused(){
		if (started) return;
		started = true;
		engine.start();                  // paused, sensors computed at instant 0
		scenarioMngRef.enableRobots();   // the robots start and stop at their first sleepSim()
		mainFrame.startRefresh();
		printLogMsg(false, "Simulation ready (paused) - Run / Space to start");
	}

	/** Starts the simulation after {@code delay_sec} seconds of real time. */
	public void startSim(int delay_sec){
		startSimPaused();
		if (delay_sec > 0) {
			printLogMsg(false, "Simulation starting in " + delay_sec + " s");
			try { Thread.sleep(delay_sec * 1000L); }
			catch (InterruptedException e) { Thread.currentThread().interrupt(); }
		}
		resume();
		printLogMsg(false, "Robots enabled");
	}

	/**
	 * Attaches a remote control server: its messages (connections, clients that do not answer,
	 * protocol errors) go to Pianura's log, and it is closed by stopSim().
	 */
	public void attach(io.github.raffaelegrandi.pianura.remote.RemoteServer server){
		remoteServers.add(server);
		server.setLogger(msg -> printLogMsg(false, msg));
		printLogMsg(false, "Remote control: UDP port " + server.getPort() + ", " + server.getMode().name().toLowerCase() + " mode");
	}

	public void stopSim(){
		engine.stop();
		for (io.github.raffaelegrandi.pianura.remote.RemoteServer s : remoteServers) s.close();
		scenarioMngRef.disableRobots();
		printLogMsg(false, "Simulation stopped at t = " + String.format(Locale.ROOT, "%.2f", clock.now()) + " s");
	}

	// ---- controls from code too (the same as the toolbar) ----

	public void pause()                 { engine.pause(); }
	public void resume()                { engine.resume(); }
	public void step(double seconds)    { engine.stepTime(seconds); }
	/** 1.0 = real time, SimEngine.MAX_SPEED = as fast as possible. */
	public void setSpeed(double factor) { engine.setSpeed(factor); }
	public double getSimTime()          { return clock.now(); }
	public SimEngine getEngine()        { return engine; }

	/** Opens the inspector window (as the Inspector button does). */
	public void openInspector() { SwingUtilities.invokeLater(() -> mainFrame.simPanel.setInspectorVisible(true)); }

	/**
	 * Chooses the view of the window: Studio (tools and panels around the arena) or Focus (the
	 * arena takes the whole window, for lessons and demos). The user switches with the toolbar or F.
	 */
	public void setView(io.github.raffaelegrandi.pianura.gui.WorkspaceView view) {
		SwingUtilities.invokeLater(() -> mainFrame.simPanel.setView(view));
	}

	/** A sentence shown over the arena in the Focus view, to explain what is going on (null = none). */
	public void setCaption(String text) { SwingUtilities.invokeLater(() -> mainFrame.simPanel.setCaption(text)); }

	/** View options, e.g. {@code getViewOptions().labelDistanceFromCenter = 0}. */
	public io.github.raffaelegrandi.pianura.gui.view.ViewOptions getViewOptions() { return mainFrame.simPanel.getViewOptions(); }

	/** Opens the live connectivity window (graph + adjacency / incidence matrix). */
	public void openConnectivityWindow() { SwingUtilities.invokeLater(mainFrame.simPanel::openConnectivityWindow); }

	/** Thread-safe: can be called by the engine or by the robots. */
	public void printLogMsg(boolean setClear, String msg){
		if (SwingUtilities.isEventDispatchThread()) mainFrame.showMSG(setClear, msg);
		else SwingUtilities.invokeLater(() -> mainFrame.showMSG(setClear, msg));
	}

}
