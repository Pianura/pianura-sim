// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui;

import java.util.Locale;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import java.awt.Insets;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;

import io.github.raffaelegrandi.pianura.sim.SimEngine;

/**
 * Simulation toolbar.
 *
 * Run/Pause (Space), Step (Right arrow: advances {@value #STEP_SECONDS} s of simulated
 * time), Stop, speed, simulated time and the measured actual speed. The buttons carry icons;
 * their names and shortcuts are in the tooltips.
 */
@SuppressWarnings("serial")
public class SimControlBar extends JToolBar {

    public static final double STEP_SECONDS = 0.1;

    static final String[] SPEED_LABELS = { "0.25x", "0.5x", "1x", "2x", "5x", "10x", "Max" };
    static final double[] SPEED_VALUES = { 0.25, 0.5, 1, 2, 5, 10, SimEngine.MAX_SPEED };

    private final SimEngine engine;
    /** Size of the toolbar icons, px. */
    public static final int ICON = 20;
    private static final Icon PLAY = Icons.of(Icons.Kind.PLAY, ICON), PAUSE = Icons.of(Icons.Kind.PAUSE, ICON);
    private final JButton runPause = iconButton(Icons.Kind.PLAY, "Run (Space)");
    private final JButton step = iconButton(Icons.Kind.STEP, "Step: while paused, advance " + STEP_SECONDS + " s of simulated time (Right arrow)");
    private final JButton stop = iconButton(Icons.Kind.STOP, "Stop the simulation for good");
    private Runnable stopAction;
    private boolean syncing;   // speed menu updated from code: do not send it back to the engine
    private final JComboBox<String> speed = new JComboBox<>(SPEED_LABELS);
    private final JLabel timeLabel = new JLabel();

    // measurement of the actual speed (simulated time / real time)
    private long lastWall = System.nanoTime();
    private double lastSim = 0;
    private double effective = 0;

    public SimControlBar(SimEngine engine) {
        this.engine = engine;
        this.stopAction = engine::stop;
        setFloatable(false);

        speed.setFocusable(false);
        speed.setToolTipText("Speed of the simulation: 1x = real time");
        speed.setSelectedIndex(2);  // 1x
        speed.setMaximumSize(speed.getPreferredSize());
        timeLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        runPause.addActionListener(e -> toggle());
        step.addActionListener(e -> doStep());
        stop.addActionListener(e -> confirmStop());
        speed.addActionListener(e -> { if (!syncing) engine.setSpeed(SPEED_VALUES[speed.getSelectedIndex()]); });

        add(runPause);
        add(step);
        add(stop);
        addSeparator();
        add(speed);
        add(Box.createHorizontalStrut(14));
        add(timeLabel);
        add(Box.createHorizontalGlue());

        refresh();
    }

    /**
     * A toolbar button with an icon and no text: the name goes in the tooltip, and for screen
     * readers. It does not take the focus, so Space goes to the shortcut and does not "click" it.
     */
    public static JButton iconButton(Icons.Kind kind, String tooltip) {
        JButton b = new JButton(Icons.of(kind, ICON));
        decorate(b, tooltip);
        return b;
    }

    /** As {@link #iconButton}, for a button that stays pressed. */
    public static JToggleButton iconToggle(Icons.Kind kind, String tooltip, boolean selected) {
        JToggleButton b = new JToggleButton(Icons.of(kind, ICON), selected);
        decorate(b, tooltip);
        return b;
    }

    private static void decorate(AbstractButton b, String tooltip) {
        b.setFocusable(false);
        b.setToolTipText(tooltip);
        b.getAccessibleContext().setAccessibleName(tooltip);
        b.setMargin(new Insets(4, 6, 4, 6));
    }

    /** Runs or pauses, as the Run button does. */
    public void toggleRun() { toggle(); }

    /** Advances one step, as the Step button does. */
    public void stepOnce() { doStep(); }

    /** Asks for confirmation, then stops for good, as the Stop button does. */
    public void requestStop() { confirmStop(); }

    /** Registers the keyboard shortcuts on the given component (valid in the whole window). */
    public void installKeyBindings(JComponent target) {
        target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "pianura-toggle");
        target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "pianura-step");
        target.getActionMap().put("pianura-toggle", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { toggle(); }
        });
        target.getActionMap().put("pianura-step", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { doStep(); }
        });
    }

    /** Action run by Stop (Simulator replaces it with stopSim, which also stops the robots and writes to the log). */
    public void setStopAction(Runnable action) { stopAction = action != null ? action : engine::stop; }

    private void confirmStop() {
        if (engine.isStopped()) return;
        int choice = javax.swing.JOptionPane.showConfirmDialog(this,
            "Stop the simulation?\nIt cannot be resumed afterwards.", "Stop simulation",
            javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
        if (choice == javax.swing.JOptionPane.OK_OPTION) { stopAction.run(); refresh(); }
    }

    private void toggle() {
        if (!engine.isRunning()) return;
        if (engine.isPaused()) engine.resume(); else engine.pause();
        refresh();
    }

    private void doStep() {
        if (!engine.isRunning()) return;
        engine.pause();
        engine.stepTime(STEP_SECONDS);
        refresh();
    }

    /** Refreshes labels and button states (called by the repaint timer). */
    public void refresh() {
        boolean stopped = engine.isStopped();
        boolean started = engine.isRunning() || stopped;
        boolean paused = engine.isPaused();
        boolean showPlay = paused || stopped || !started;
        Icon want = showPlay ? PLAY : PAUSE;
        if (runPause.getIcon() != want) {
            runPause.setIcon(want);
            runPause.setToolTipText(showPlay ? "Run (Space)" : "Pause (Space)");
            runPause.getAccessibleContext().setAccessibleName(showPlay ? "Run" : "Pause");
        }
        runPause.setEnabled(!stopped && started);
        step.setEnabled(paused && !stopped && started);
        stop.setEnabled(started && !stopped);

        // the menu follows the speed even when set from code (setSpeed)
        double sp = engine.getSpeed();
        for (int i = 0; i < SPEED_VALUES.length; i++) {
            if (SPEED_VALUES[i] == sp && speed.getSelectedIndex() != i) { syncing = true; speed.setSelectedIndex(i); syncing = false; }
        }
        speed.setEnabled(!stopped);

        double sim = engine.getClock().now();
        long wall = System.nanoTime();
        double dWall = (wall - lastWall) / 1e9;
        if (dWall >= 0.5) {
            effective = (sim - lastSim) / dWall;
            lastWall = wall;
            lastSim = sim;
        }
        String state;
        if (stopped)          state = "<font color='#c0392b'>&#9632; STOPPED</font>";
        else if (!started)    state = "<font color='#7f8c8d'>&#9675; READY</font>";
        else if (paused)      state = "<font color='#d35400'>&#10074;&#10074; PAUSED</font>";
        else                  state = String.format(Locale.ROOT, "<font color='#1e8449'>&#9654; RUNNING &times;%.1f</font>", effective);
        String time = String.format(Locale.ROOT, "t = %8.2f s", sim).replace(" ", "&nbsp;");   // fixed width
        timeLabel.setText("<html><tt>" + time + "</tt>&nbsp;&nbsp;&nbsp;<b>" + state + "</b>&nbsp;</html>");
    }

}
