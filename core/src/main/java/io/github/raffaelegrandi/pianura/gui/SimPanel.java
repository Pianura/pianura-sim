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
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.LogArea;

/**
 * Main panel: toolbar (top), arena view (centre), view side panel (right, can be hidden),
 * log (bottom).
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
	private final ScenarioManager scenario;
	private static java.io.File lastSceneDir;   // folder of the last scene opened or saved

	public SimPanel(String logAreaInit, ScenarioManager scenario, SimEngine engine) {
		arenaWpx = (int) Math.round(scenario.getArenaWidthPx());
		arenaHpx = (int) Math.round(scenario.getArenaHeightPx());
		this.engine = engine;
		this.scenario = scenario;

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
		}

		if (engine != null) arena.setNetwork(engine.getNetwork(), engine.getClock());
		sidePanel = new ViewSidePanel(arena, options, scenario.getFloor(), this::changeFloor,
			ViewSidePanel.arenaInfo(scenario.getArenaWidth(), scenario.getArenaHeight(), scenario.getMetersPerCell(),
				scenario.getNumObstacles(), scenario.getNumRobots()),
			engine != null ? engine.getNetwork() : null, this::openConnectivityWindow);
		this.robots = scenario.getRobots();
		JScrollPane sideScroll = new JScrollPane(sidePanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		sideScroll.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.LIGHT_GRAY));
		add(sideScroll, BorderLayout.EAST);
		arena.setSelectionListener(r -> sidePanel.refresh());
		arena.setViewListener(sidePanel::refresh);

		if (engine != null) {
			controlBar = new SimControlBar(engine);
			controlBar.installKeyBindings(this);
			JToggleButton panelToggle = new JToggleButton("Panel", true);
			panelToggle.setFocusable(false);
			panelToggle.setToolTipText("Show/hide the view panel");
			panelToggle.addActionListener(e -> { sideScroll.setVisible(panelToggle.isSelected()); revalidate(); });
			JButton openScene = new JButton("Open…");
			JButton saveScene = new JButton("Save…");
			openScene.setFocusable(false);
			saveScene.setFocusable(false);
			openScene.setToolTipText("Open a scene: obstacles and robot positions (Ctrl+O; before the start or paused)");
			saveScene.setToolTipText("Save the scene as it is now: obstacles and robot positions (Ctrl+S)");
			openScene.addActionListener(e -> openScene());
			saveScene.addActionListener(e -> saveScene());
			controlBar.addSeparator();
			controlBar.add(openScene);
			controlBar.add(saveScene);
			controlBar.addSeparator();
			controlBar.add(panelToggle);
			int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();   // Ctrl, or Cmd on macOS
			getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, menu), "pianura-open");
			getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, menu), "pianura-save");
			getActionMap().put("pianura-open", new AbstractAction() { public void actionPerformed(ActionEvent e) { openScene(); } });
			getActionMap().put("pianura-save", new AbstractAction() { public void actionPerformed(ActionEvent e) { saveScene(); } });
			add(controlBar, BorderLayout.NORTH);
		}
		JScrollPane logScroll = new JScrollPane(logArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(logScroll, BorderLayout.SOUTH);
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

	/** View options (layers, label distance, ...). */
	public ViewOptions getViewOptions(){ return options; }

	/** Toolbar (null if the panel was created without an engine). */
	public SimControlBar getControlBar(){ return controlBar; }

	public void startRefresh(){repainter.start();}

	@Override
	public void actionPerformed(ActionEvent arg0) {
		arena.repaint();
		// etichette e pannello non servono a 60 Hz
		if (++refreshCounter % 6 == 0) {
			if (controlBar != null) controlBar.refresh();
			sidePanel.refresh();
		}
	}

}
