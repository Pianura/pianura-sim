// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui;

import java.awt.BorderLayout;
import java.awt.Point;
import javax.swing.JFrame;

import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;

/** Main window: resizable, the arena view adapts. */
@SuppressWarnings("serial")
public class MainFrame extends JFrame{

	public SimPanel simPanel;

	public MainFrame(String frameTitle, Point frameLoc, String logInitStr, ScenarioManager scenario, SimEngine engine){
		super(frameTitle);
		getContentPane().setLayout(new BorderLayout());
		simPanel = new SimPanel(logInitStr, scenario, engine);
		getContentPane().add(simPanel, BorderLayout.CENTER);
		pack();
		setLocation(frameLoc);
		fitToScreen(frameLoc);
		setResizable(true);
		setVisible(true);
	}

	/** Keeps the window inside the screen: on a small display it gets shorter and the arena adapts. */
	private void fitToScreen(Point loc){
		if (java.awt.GraphicsEnvironment.isHeadless()) return;
		java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		int w = Math.min(getWidth(), Math.max(480, screen.x + screen.width - loc.x - 10));
		int h = Math.min(getHeight(), Math.max(400, screen.y + screen.height - loc.y - 10));
		if (w != getWidth() || h != getHeight()) setSize(w, h);
	}

	public void showMSG(boolean setClear, String msg){ simPanel.showMSG(setClear, msg); }

	public void startRefresh(){simPanel.startRefresh();}

}
