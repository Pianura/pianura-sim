// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui;

/** The views of the simulation window: the same simulation, laid out for different uses. */
public enum WorkspaceView {
    /** To build and tune: toolbar, tools, the robot's panel, timeline and log around the arena. */
    STUDIO,
    /** To show: the arena takes the whole window, the controls float over it. */
    FOCUS
}
