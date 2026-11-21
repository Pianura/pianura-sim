// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui;

import io.github.raffaelegrandi.pianura.units.sensors.FloorMap;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;

import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.gui.panels.ArenaFloorPanel;
import io.github.raffaelegrandi.pianura.gui.view.ArenaView;
import io.github.raffaelegrandi.pianura.gui.view.ViewOptions;
import io.github.raffaelegrandi.pianura.gui.view.ViewSidePanel;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.structures.SceneFile;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.RobotUnit;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.LogArea;

/**
 * The simulation window's content, in two views (see {@link WorkspaceView}).
 *
 * <p>Studio: toolbar with icons (top), tools to build the scene (left), the arena (centre), a
 * side panel with the view options and the selected robot (right), the timeline of the robots'
 * states and the log (bottom); each part can be hidden. Focus: only the arena, with floating
 * controls ({@link FocusOverlay}).
 */
@SuppressWarnings("serial")
public class SimPanel extends JPanel implements ActionListener{

	protected LogArea logArea;
	private final ArenaView arena;
	private final ViewSidePanel sidePanel;
	private final ViewOptions options = new ViewOptions();
	private SimControlBar controlBar;
	protected Timer repainter;
	private int refreshCounter = 0;
	private final int arenaWpx, arenaHpx;
	private final SimEngine engine;
	private List<RobotDummy> robots;
	private io.github.raffaelegrandi.pianura.gui.view.ConnectivityWindow connectivity;
	private io.github.raffaelegrandi.pianura.gui.view.InspectorWindow inspector;
	private JToggleButton inspectorToggle, studioToggle, focusToggle;
	private JButton homeButton;
	private Runnable homeAction;
	private JToolBar toolRail;
	private final JTabbedPane dock;
	private final JPanel bottom;
	private final JScrollPane timelineScroll, logScroll;
	private final io.github.raffaelegrandi.pianura.gui.view.RobotPanel robotPanel;
	private final io.github.raffaelegrandi.pianura.gui.view.StateTimeline timeline;
	private FocusOverlay overlay;
	private WorkspaceView view = WorkspaceView.STUDIO;
	private static final Color NEW_OBSTACLE = new Color(112, 124, 140);
	private final ScenarioManager scenario;
	private static java.io.File lastSceneDir;   // folder of the last scene opened or saved

	public SimPanel(String logAreaInit, ScenarioManager scenario, SimEngine engine) {
		arenaWpx = (int) Math.round(scenario.getArenaWidthPx());
		arenaHpx = (int) Math.round(scenario.getArenaHeightPx());
		this.engine = engine;
		this.scenario = scenario;
		this.robots = scenario.getRobots();
		logArea = new LogArea(logAreaInit, 4, 5);
		repainter = new Timer(Const.repaintTime, this);
		setLayout(new BorderLayout());

		// with the engine, the view shows the same floor the floor sensors read
		FloorMap floorMap = engine != null ? engine.getFloorMap() : newFloorMap(scenario.getFloor());
		arena = new ArenaView(floorMap, arenaWpx, arenaHpx, scenario.getObstacles(), scenario.getRobots(), options);
		add(arena, BorderLayout.CENTER);

		if (engine != null) {
			arena.setSceneEditor(new ArenaView.SceneEditor() {
				@Override public boolean canEdit() { return engine.isEditable(); }
				@Override public CollisionDetector.Validation changed(boolean finished) {
					engine.sceneChanged();
					CollisionDetector.Validation v = engine.validate();
					if (finished) reportValidation(v);
					return v;
				}
			});
			arena.setNetwork(engine.getNetwork(), engine.getClock());
		}

		// right: the selected robot and the view options, in two tabs
		sidePanel = new ViewSidePanel(arena, options, scenario.getFloor(), this::changeFloor,
			ViewSidePanel.arenaInfo(scenario.getArenaWidth(), scenario.getArenaHeight(), scenario.getMetersPerCell(),
				scenario.getNumObstacles(), scenario.getNumRobots()),
			engine != null ? engine.getNetwork() : null, this::openConnectivityWindow);
		JScrollPane sideScroll = new JScrollPane(sidePanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		sideScroll.setBorder(null);
		robotPanel = new io.github.raffaelegrandi.pianura.gui.view.RobotPanel(this::inspect);
		dock = new JTabbedPane();
		dock.setFocusable(false);
		dock.addTab("View", sideScroll);
		dock.addTab("Robot", robotPanel);
		dock.setToolTipTextAt(0, "Zoom, layers, floor, radio and arena");
		dock.setToolTipTextAt(1, "The selected robot: state, body and watched values");
		dock.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.LIGHT_GRAY));
		add(dock, BorderLayout.EAST);

		// bottom: the timeline of the robots' states, and the log
		timeline = new io.github.raffaelegrandi.pianura.gui.view.StateTimeline(robots,
			() -> engine != null ? engine.getClock().now() : 0, arena::select);
		timelineScroll = new JScrollPane(timeline, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
			@Override public java.awt.Dimension getPreferredSize() {
				java.awt.Dimension d = super.getPreferredSize();
				return new java.awt.Dimension(d.width, Math.min(d.height, 172));
			}
		};
		timelineScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
		timelineScroll.getVerticalScrollBar().setUnitIncrement(21);
		logScroll = new JScrollPane(logArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		bottom = new JPanel(new BorderLayout());
		bottom.add(timelineScroll, BorderLayout.NORTH);
		bottom.add(logScroll, BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);

		arena.setSelectionListener(r -> {
			sidePanel.refresh();
			robotPanel.show(r);
			timeline.setSelected(r);
			if (r != null && view == WorkspaceView.STUDIO) dock.setSelectedIndex(1);
			if (inspector != null) inspector.arenaSelectionChanged(r);
		});
		arena.setViewListener(sidePanel::refresh);
		arena.setInspectHandler(this::inspect);

		if (engine != null) {
			buildToolbar();
			buildToolRail();
			overlay = new FocusOverlay(arena, engine, controlBar, options, () -> setView(WorkspaceView.STUDIO), this::inspect);
			bindKey(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, 0), "pianura-focus",
				() -> setView(view == WorkspaceView.FOCUS ? WorkspaceView.STUDIO : WorkspaceView.FOCUS));
			bindKey(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "pianura-studio",
				() -> { if (view == WorkspaceView.FOCUS) setView(WorkspaceView.STUDIO); });
			bindKey(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), "pianura-delete", this::deleteSelectedObstacle);
		}
	}

	private void bindKey(KeyStroke k, String name, Runnable r) {
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(k, name);
		getActionMap().put(name, new AbstractAction() { public void actionPerformed(ActionEvent e) { r.run(); } });
	}

	/** Top bar: run, step, stop, speed and time; scene files; what to show; the views. */
	private void buildToolbar() {
		controlBar = new SimControlBar(engine);
		controlBar.installKeyBindings(this);

		homeButton = SimControlBar.iconButton(Icons.Kind.HOME, "Back to the start screen: examples and recent scenes");
		homeButton.setVisible(false);
		homeButton.addActionListener(e -> { if (homeAction != null) homeAction.run(); });
		controlBar.add(homeButton, 0);

		JButton openScene = SimControlBar.iconButton(Icons.Kind.OPEN, "Open a scene: obstacles and robot positions (Ctrl+O; before the start or paused)");
		JButton saveScene = SimControlBar.iconButton(Icons.Kind.SAVE, "Save the scene as it is now: obstacles and robot positions (Ctrl+S)");
		openScene.addActionListener(e -> openScene());
		saveScene.addActionListener(e -> saveScene());
		controlBar.addSeparator();
		controlBar.add(openScene);
		controlBar.add(saveScene);

		inspectorToggle = SimControlBar.iconToggle(Icons.Kind.INSPECT, "Inspector: plots, views and log of the selected robot, in its own window", false);
		inspectorToggle.addActionListener(e -> setInspectorVisible(inspectorToggle.isSelected()));
		JToggleButton timelineToggle = SimControlBar.iconToggle(Icons.Kind.TIMELINE, "Show/hide the timeline of the robots' states", true);
		timelineToggle.addActionListener(e -> { timelineScroll.setVisible(timelineToggle.isSelected()); revalidate(); });
		JToggleButton logToggle = SimControlBar.iconToggle(Icons.Kind.LOG, "Show/hide the log", true);
		logToggle.addActionListener(e -> { logScroll.setVisible(logToggle.isSelected()); revalidate(); });
		JToggleButton dockToggle = SimControlBar.iconToggle(Icons.Kind.SIDEBAR, "Show/hide the side panel (view options and selected robot)", true);
		dockToggle.addActionListener(e -> { dock.setVisible(dockToggle.isSelected()); revalidate(); });
		controlBar.addSeparator();
		controlBar.add(inspectorToggle);
		controlBar.add(timelineToggle);
		controlBar.add(logToggle);
		controlBar.add(dockToggle);

		studioToggle = SimControlBar.iconToggle(Icons.Kind.STUDIO, "Studio view: tools and panels around the arena", true);
		focusToggle = SimControlBar.iconToggle(Icons.Kind.FOCUS, "Focus view: the arena takes the whole window (F; Esc to come back)", false);
		ButtonGroup views = new ButtonGroup();
		views.add(studioToggle);
		views.add(focusToggle);
		studioToggle.addActionListener(e -> setView(WorkspaceView.STUDIO));
		focusToggle.addActionListener(e -> setView(WorkspaceView.FOCUS));
		controlBar.addSeparator();
		controlBar.add(studioToggle);
		controlBar.add(focusToggle);

		int menu = GraphicsEnvironment.isHeadless() ? java.awt.event.InputEvent.CTRL_DOWN_MASK
			: Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();          // Ctrl, or Cmd on macOS
		bindKey(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, menu), "pianura-open", this::openScene);
		bindKey(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, menu), "pianura-save", this::saveScene);
		add(controlBar, BorderLayout.NORTH);
	}

	/** Left rail: tools to build the scene and to move around it. */
	private void buildToolRail() {
		toolRail = new JToolBar(JToolBar.VERTICAL);
		toolRail.setFloatable(false);
		toolRail.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY));
		JButton addRect = SimControlBar.iconButton(Icons.Kind.RECT, "Add a rectangle in the middle of the view (before the start or paused)");
		JButton addOval = SimControlBar.iconButton(Icons.Kind.OVAL, "Add an oval in the middle of the view (before the start or paused)");
		JButton delete = SimControlBar.iconButton(Icons.Kind.TRASH, "Delete the selected obstacle (Delete)");
		JButton zoomIn = SimControlBar.iconButton(Icons.Kind.ZOOM_IN, "Zoom in (+)");
		JButton zoomOut = SimControlBar.iconButton(Icons.Kind.ZOOM_OUT, "Zoom out (-)");
		JButton fit = SimControlBar.iconButton(Icons.Kind.FIT, "Fit the arena to the window (Home)");
		addRect.addActionListener(e -> addObstacle(io.github.raffaelegrandi.pianura.arena.ArenaObjectType.OBSTACLE_RECT));
		addOval.addActionListener(e -> addObstacle(io.github.raffaelegrandi.pianura.arena.ArenaObjectType.OBSTACLE_OVAL));
		delete.addActionListener(e -> deleteSelectedObstacle());
		zoomIn.addActionListener(e -> arena.zoom(1.25));
		zoomOut.addActionListener(e -> arena.zoom(1 / 1.25));
		fit.addActionListener(e -> arena.fitToWindow());
		toolRail.add(addRect);
		toolRail.add(addOval);
		toolRail.add(delete);
		toolRail.addSeparator();
		toolRail.add(zoomIn);
		toolRail.add(zoomOut);
		toolRail.add(fit);
		add(toolRail, BorderLayout.WEST);
	}

	// ------------------------------------------------------------------ views

	/** Switches between the Studio and the Focus view (EDT). */
	public void setView(WorkspaceView v) {
		if (engine == null || v == null) return;
		view = v;
		boolean studio = v == WorkspaceView.STUDIO;
		controlBar.setVisible(studio);
		toolRail.setVisible(studio);
		dock.setVisible(studio);
		bottom.setVisible(studio);
		studioToggle.setSelected(studio);
		focusToggle.setSelected(!studio);
		overlay.setVisible(!studio);
		arena.setEditHintVisible(studio);
		revalidate();
		repaint();
	}

	public WorkspaceView getView() { return view; }

	/** A sentence shown over the arena in the Focus view, to explain what is going on (null = none). */
	public void setCaption(String text) { if (overlay != null) overlay.setCaption(text); }

	/** Shows a Home button on the toolbar that runs this action (null = no button). */
	public void setHomeAction(Runnable action) {
		homeAction = action;
		if (homeButton != null) homeButton.setVisible(action != null);
	}

	// ------------------------------------------------------------------ building the scene

	private boolean editableOrExplain() {
		if (engine != null && engine.isEditable()) return true;
		showMSG(false, "Pause the simulation to change the scene");
		return false;
	}

	/** Adds an obstacle in the middle of the view; it can then be moved and resized with the mouse. */
	public void addObstacle(io.github.raffaelegrandi.pianura.arena.ArenaObjectType type) {
		if (!editableOrExplain()) return;
		java.awt.geom.Point2D c = arena.getTransform().center(arena.getWidth(), arena.getHeight());
		boolean oval = type == io.github.raffaelegrandi.pianura.arena.ArenaObjectType.OBSTACLE_OVAL;
		double w = oval ? 3 : 4, h = oval ? 3 : 2;
		double x = Math.max(w / 2, Math.min(scenario.getArenaWidth() - w / 2, c.getX() / Const.cellSize));
		double y = Math.max(h / 2, Math.min(scenario.getArenaHeight() - h / 2, c.getY() / Const.cellSize));
		Obstacle o = new Obstacle(type, NEW_OBSTACLE, new io.github.raffaelegrandi.pianura.utils.Position(x, y, 0), w, h);
		List<Obstacle> list = new java.util.ArrayList<>(scenario.getObstacles());
		list.add(o);
		replaceObstacles(list);
		arena.selectObstacleAt(list.size() - 1);
		showMSG(false, "Added " + (oval ? "an oval" : "a rectangle") + ": drag it to move it, its handles to resize or turn it");
	}

	/** Deletes the obstacle selected in the arena, if any. */
	public void deleteSelectedObstacle() {
		int i = arena.getSelectedObstacle();
		if (i < 0 || i >= scenario.getObstacles().size() || !editableOrExplain()) return;
		List<Obstacle> list = new java.util.ArrayList<>(scenario.getObstacles());
		list.remove(i);
		replaceObstacles(list);
		showMSG(false, "Obstacle deleted");
	}

	private void replaceObstacles(List<Obstacle> list) {
		engine.replaceScene(list, java.util.Map.of());
		scenario.getObstacles().clear();
		scenario.getObstacles().addAll(list);
		arena.obstaclesReplaced();
		sidePanel.setArenaInfo(ViewSidePanel.arenaInfo(scenario.getArenaWidth(), scenario.getArenaHeight(),
			scenario.getMetersPerCell(), scenario.getNumObstacles(), scenario.getNumRobots()));
		CollisionDetector.Validation v = engine.validate();
		arena.setValidation(v);
		if (!v.isOk()) reportValidation(v);
	}

	/** Changes the floor: view and floor sensors. */
	private void changeFloor(FloorStyle style){
		if (engine != null) {
			engine.setFloor(style);   // also changes what the floor sensors read
			arena.setFloor(engine.getFloorMap());
		} else {
			FloorMap old = arena.getFloor();
			arena.setFloor(newFloorMap(style));
			if (old != null) old.dispose();
		}
	}

	private FloorMap newFloorMap(FloorStyle style){
		return new FloorMap(style, arenaWpx, arenaHpx, FloorMap.defaultScale(arenaWpx, arenaHpx));
	}

	// ------------------------------------------------------------------ scene files

	private JFileChooser sceneChooser(String title) {
		JFileChooser fc = new JFileChooser(lastSceneDir);
		fc.setDialogTitle(title);
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Pianura scenes (*.json)", "json"));
		return fc;
	}

	/** Saves arena, obstacles and robot positions as they are now. */
	public void saveScene() {
		JFileChooser fc = sceneChooser("Save scene");
		if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		java.io.File f = fc.getSelectedFile();
		if (!f.getName().contains(".")) f = new java.io.File(f.getParentFile(), f.getName() + ".json");
		if (f.exists() && JOptionPane.showConfirmDialog(this, f.getName() + " already exists. Replace it?", "Save scene",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
		lastSceneDir = f.getParentFile();
		try {
			SceneFile.write(f.toPath(), scenario.getArenaWidth(), scenario.getArenaHeight(), scenario.getMetersPerCell(),
				scenario.getObstacles(), robots);
			showMSG(false, "Scene saved: " + f.getName() + " (" + scenario.getNumObstacles() + " obstacles, " + robots.size() + " robots)");
		} catch (java.io.IOException ex) {
			JOptionPane.showMessageDialog(this, "Cannot save the scene:\n" + ex.getMessage(), "Save scene", JOptionPane.ERROR_MESSAGE);
		}
	}

	/** Opens a scene: replaces the obstacles and moves the robots it lists. Before the start or paused. */
	public void openScene() {
		if (engine == null) return;
		if (!engine.isEditable()) {
			JOptionPane.showMessageDialog(this, "Pause the simulation to open a scene.", "Open scene", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JFileChooser fc = sceneChooser("Open scene");
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		java.io.File f = fc.getSelectedFile();
		lastSceneDir = f.getParentFile();
		try {
			applyScene(SceneFile.read(f.toPath()), f.getName());
		} catch (java.io.IOException | IllegalArgumentException ex) {
			JOptionPane.showMessageDialog(this, "Cannot open " + f.getName() + ":\n" + ex.getMessage(), "Open scene", JOptionPane.ERROR_MESSAGE);
		}
	}

	/** Applies a scene read from file to the running scenario (EDT, simulation editable). */
	public void applyScene(SceneFile.Scene s, String name) {
		if (Math.abs(s.arenaWidth() - scenario.getArenaWidth()) > 1e-9 || Math.abs(s.arenaHeight() - scenario.getArenaHeight()) > 1e-9)
			throw new IllegalArgumentException(String.format(java.util.Locale.ROOT,
				"the scene is for a %s × %s arena, this one is %s × %s.%nA different arena size can be loaded in the scenario's code (loadScene).",
				fmt(s.arenaWidth()), fmt(s.arenaHeight()), fmt(scenario.getArenaWidth()), fmt(scenario.getArenaHeight())));
		java.util.Map<Integer, io.github.raffaelegrandi.pianura.utils.Position> poses = new java.util.HashMap<>();
		java.util.Set<Integer> ids = new java.util.HashSet<>();
		for (RobotDummy r : robots) ids.add(r.getIDnum());
		java.util.List<Integer> unknown = new java.util.ArrayList<>();
		for (SceneFile.RobotPose p : s.robots()) {
			if (ids.contains(p.id())) poses.put(p.id(), new io.github.raffaelegrandi.pianura.utils.Position(p.x(), p.y(), p.theta()));
			else unknown.add(p.id());
		}
		engine.replaceScene(s.obstacles(), poses);
		engine.setMetersPerCell(s.metersPerCell());
		scenario.setMetersPerCell(s.metersPerCell());
		scenario.getObstacles().clear();
		scenario.getObstacles().addAll(s.obstacles());
		arena.obstaclesReplaced();
		sidePanel.setArenaInfo(ViewSidePanel.arenaInfo(scenario.getArenaWidth(), scenario.getArenaHeight(),
			scenario.getMetersPerCell(), scenario.getNumObstacles(), scenario.getNumRobots()));
		showMSG(false, "Scene opened: " + name + " (" + s.obstacles().size() + " obstacles, " + poses.size() + " robots placed)");
		if (!unknown.isEmpty()) showMSG(false, "  - robots " + unknown + " are in the file but not in this scenario: ignored");
		CollisionDetector.Validation v = engine.validate();
		arena.setValidation(v);
		reportValidation(v);
	}

	private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

	/** Result of the validation after a scene change, in the log. */
	private void reportValidation(CollisionDetector.Validation v){
		if (v.isOk()) {
			showMSG(false, "Scenario valid: no overlaps");
		} else {
			showMSG(false, "Scenario: " + v.messages().size() + (v.messages().size() == 1 ? " warning" : " warnings"));
			for (String m : v.messages()) showMSG(false, "  - " + m);
		}
	}

	/** Replaces the robots and obstacles shown (default floor). */
	public void configureGUI(List<RobotDummy> dummyVec, List<? extends Obstacle> obstVec){
		if (obstVec == null || dummyVec == null) {
			showMSG(true, "Unable to initialize Arena: robots or obstacles list is null");
			return;
		}
		// note: the view's obstacles stay those of the initial scenario
		changeFloor(FloorStyle.checkerboard());
		arena.setRobots(dummyVec);
	}

	/** Call it on the EDT (Simulator.printLogMsg does). */
	public void showMSG(boolean setClear, String msg){
		if (setClear)logArea.setClear(true);
		logArea.addMSG(msg);
	}

	public ArenaView getArenaView(){ return arena; }

	/** Opens (or brings to front) the live connectivity window. */
	public void openConnectivityWindow(){
		if (engine == null) return;
		if (connectivity == null || !connectivity.isDisplayable()) {
			connectivity = new io.github.raffaelegrandi.pianura.gui.view.ConnectivityWindow(engine.getNetwork(), engine.getClock(), robots, arenaWpx, arenaHpx);
			java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
			if (owner != null) connectivity.setLocation(owner.getX() + owner.getWidth() - 60, owner.getY() + 40);
		}
		connectivity.setVisible(true);
		connectivity.toFront();
	}

	/** Opens or closes the inspector window (separate from the main window). */
	public void setInspectorVisible(boolean visible) {
		if (engine == null) return;
		if (visible && inspector == null) {
			inspector = new io.github.raffaelegrandi.pianura.gui.view.InspectorWindow(robots, () -> engine.getClock().now(), arena::select);
			java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
			if (owner != null) inspector.setLocation(owner.getX() + owner.getWidth() + 8, owner.getY());
			inspector.addComponentListener(new java.awt.event.ComponentAdapter() {
				@Override public void componentHidden(java.awt.event.ComponentEvent e) {
					if (inspectorToggle != null) inspectorToggle.setSelected(false);
				}
			});
			RobotUnit sel = arena.getSelected();
			if (sel != null) inspector.show(sel);
		}
		if (inspector == null) return;
		inspector.setVisible(visible);
		if (visible) inspector.toFront();
		if (inspectorToggle != null) inspectorToggle.setSelected(visible);
	}

	/** Opens the inspector on this robot (from the robot's right-click menu). */
	public void inspect(RobotUnit r) {
		setInspectorVisible(true);
		if (inspector != null && r != null) inspector.show(r);
	}

	/** The inspector window, or null if it was never opened. */
	public io.github.raffaelegrandi.pianura.gui.view.InspectorWindow getInspectorWindow(){ return inspector; }

	/** View options (layers, label distance, ...). */
	public ViewOptions getViewOptions(){ return options; }

	/** Toolbar (null if the panel was created without an engine). */
	public SimControlBar getControlBar(){ return controlBar; }

	public void startRefresh(){repainter.start();}

	@Override
	public void actionPerformed(ActionEvent arg0) {
		arena.repaint();
		boolean focus = view == WorkspaceView.FOCUS;
		// labels and panels do not need 60 Hz
		if (++refreshCounter % 6 == 0) {
			if (focus) {
				overlay.refresh();
			} else {
				if (controlBar != null) controlBar.refresh();
				if (dock.isVisible()) {
					if (dock.getSelectedIndex() == 0) sidePanel.refresh(); else robotPanel.refresh();
				}
				if (timelineScroll.isVisible()) timeline.refresh();
			}
		} else if (focus) {
			overlay.follow();   // the card keeps up with its robot
		}
	}

}
