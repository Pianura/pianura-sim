// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.start;

import java.awt.BorderLayout;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import io.github.raffaelegrandi.pianura.gui.LookAndFeelSetup;

/** The start screen in its window: where a session of Pianura begins, and where Home comes back to. */
@SuppressWarnings("serial")
public class StartWindow extends JFrame {

    private final StartPanel panel;

    public StartWindow(StartStore store, List<StartPanel.Example> examples, StartPanel.Actions actions, String version) {
        super("Pianura Sim");
        LookAndFeelSetup.install();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        panel = new StartPanel(store, examples, actions, version);
        getContentPane().add(panel, BorderLayout.CENTER);
        setSize(1000, 700);
        setLocationRelativeTo(null);
    }

    public StartPanel getPanel() { return panel; }

    /** Shows the screen again with what changed meanwhile (recent items, favourites, files). */
    public void comeBack() {
        panel.reload();
        setVisible(true);
        toFront();
    }
}
