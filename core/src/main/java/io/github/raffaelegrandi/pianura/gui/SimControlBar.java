// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui;

import java.util.Locale;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
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
 * time), speed, simulated time and the measured actual speed.
 */
@SuppressWarnings("serial")
public class SimControlBar extends JToolBar {

    public static final double STEP_SECONDS = 0.1;

    private static final String[] SPEED_LABELS = { "0.25x", "0.5x", "1x", "2x", "5x", "10x", "Max" };
    private static final double[] SPEED_VALUES = { 0.25, 0.5, 1, 2, 5, 10, SimEngine.MAX_SPEED };

    private final SimEngine engine;
    private final JButton runPause = new JButton("\u25B6 Run");
    private final JButton step = new JButton("Step");
    private final JButton stop = new JButton("\u25A0 Stop");
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

        // the buttons do not take the focus: Space goes to the shortcut and does not "click" the button
        runPause.setFocusable(false);
        step.setFocusable(false);
        stop.setFocusable(false);
        speed.setFocusable(false);
        runPause.setToolTipText("Run / pause (Space)");
        step.setToolTipText("While paused: advance " + STEP_SECONDS + " s of simulated time (Right arrow)");
        stop.setToolTipText("Stop the simulation for good");
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
        add(new JLabel("Speed "));
        add(speed);
        add(Box.createHorizontalStrut(14));
        add(timeLabel);
        add(Box.createHorizontalGlue());

        refresh();
    }

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
        runPause.setText(paused || stopped ? "\u25B6 Run" : "\u275A\u275A Pause");
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
