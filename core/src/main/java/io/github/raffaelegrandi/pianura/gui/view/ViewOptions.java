// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.view;

/** Display layers switched from the side panel. Read by the drawing thread. */
public final class ViewOptions {
    /** Circle of the IR sensors' range (as in the original version). */
    public volatile boolean sensorRange = true;
    /** Rays of the 18 sensors with the point hit. */
    public volatile boolean sensorRays = false;
    /** Path followed by the robots. */
    public volatile boolean trails = false;
    /** Robot number (speech-bubble label). */
    public volatile boolean ids = true;
    /**
     * Distance of the label from the robot's centre, in robot radii, top left at 45°:
     * 0 = over the centre, 1 = on the edge, 2 = detached like a speech bubble.
     */
    public volatile double labelDistanceFromCenter = 2.0;
    /** Floor sensors, colored with the value they read. */
    public volatile boolean floorSensors = true;
    /** Radio links between robots (color = connected component, dashed + arrow = one way) and messages in flight. */
    public volatile boolean commLinks = false;
    /** Transmission range of each robot. */
    public volatile boolean commRange = false;
    /** Miniature of the whole arena in the bottom-right corner (shown only when the arena does not fit the view). */
    public volatile boolean minimap = true;
    /** The view scrolls when the pointer rests near its border (like strategy games). */
    public volatile boolean edgeScroll = true;
    /** Debug drawings made by the robots' code (robot.viz(...)). */
    public volatile boolean drawings = true;
    /** Only the drawings of the selected robot (useful with many robots). */
    public volatile boolean drawingsSelectedOnly = false;
    /** Grid of the cells over the floor. */
    public volatile boolean grid = false;
}
