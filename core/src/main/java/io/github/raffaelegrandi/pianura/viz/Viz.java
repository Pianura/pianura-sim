// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.viz;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Debug drawings of one robot, organised in named layers ({@link VizLayer}).
 *
 * <p>Thread-safety, as for the sensors: the robot's thread fills the layers; at every
 * {@code sleepSim()} (or {@link #publish()}) the changed layers are copied into an immutable
 * snapshot, published with a single volatile write; the view reads the latest snapshot at
 * every frame. Drawings never affect the simulation.
 *
 * <p>A robot can hold at most {@link #MAX_SHAPES} shapes over all its layers; further shapes are
 * dropped and a warning is reported once. Clear layers that are redrawn at every step.
 */
public final class Viz {

    /** Name of the layer used when none is given (e.g. by remote clients). */
    public static final String DEFAULT_LAYER = "default";

    /** Most shapes a robot can hold over all its layers. */
    public static final int MAX_SHAPES = 50_000;

    private final Map<String, VizLayer> layers = new LinkedHashMap<>();   // robot thread only
    private int count = 0;
    private boolean warned = false;
    private final Consumer<String> warn;
    private volatile Map<String, List<Shape>> snapshot = Map.of();

    /** @param warn where to report that the limit was reached (e.g. System.err) */
    public Viz(Consumer<String> warn) {
        this.warn = warn != null ? warn : m -> {};
    }

    /** Layer {@code name}, created empty on first use. Layers are drawn in order of creation. */
    public VizLayer layer(String name) {
        if (name == null || name.isEmpty()) name = DEFAULT_LAYER;
        return layers.computeIfAbsent(name, n -> new VizLayer(this, n));
    }

    /** Clears every layer. */
    public void clearAll() {
        for (VizLayer l : layers.values()) l.clear();
    }

    /** Makes what was drawn so far visible now (done automatically at every sleepSim()). */
    public void publish() {
        boolean changed = false;
        for (VizLayer l : layers.values()) changed |= l.isDirty();
        if (!changed && snapshot.size() == layers.size()) return;
        Map<String, List<Shape>> m = new LinkedHashMap<>();
        for (VizLayer l : layers.values()) m.put(l.name(), l.snapshot());
        snapshot = Collections.unmodifiableMap(m);
    }

    /** Latest published drawings, layer name to shapes, in drawing order. Safe from any thread. */
    public Map<String, List<Shape>> snapshot() { return snapshot; }

    /** Total shapes held now (robot thread). */
    public int shapeCount() { return count; }

    boolean reserve(int n) {
        if (count + n > MAX_SHAPES) {
            if (!warned) {
                warned = true;
                warn.accept("debug drawings: more than " + MAX_SHAPES + " shapes, further shapes are dropped (clear the layers you redraw)");
            }
            return false;
        }
        count += n;
        return true;
    }

    void removed(int n) {
        count -= n;
    }
}
