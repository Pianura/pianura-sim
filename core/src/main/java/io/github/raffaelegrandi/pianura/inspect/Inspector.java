// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.inspect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * What a robot's algorithm shows about itself in the inspector window: its current state,
 * a few watched values, time plots, a log of decisions, and views of its insides (bars for a
 * vector, a heat map for a matrix, a neural network with its activations). Times are the
 * simulated time.
 *
 * <pre>
 *   Inspector in = robot.inspector();
 *   in.state("searching");
 *   in.watch("nodes", tree.size());
 *   in.plot("distance to goal", d);
 *   in.log("path found, length " + len);
 *   in.bars("action values", q);                       // one bar per element
 *   in.matrix("weights 1", w1);                        // rows x columns, as a heat map
 *   in.network("policy", activations, weights);        // layers of neurons that light up
 * </pre>
 *
 * The robot's code writes, the window reads copies: all methods are thread-safe. Memory is
 * bounded: each plot keeps its last {@link #MAX_POINTS} points, the log its last
 * {@link #MAX_LOG} lines. None of this affects the simulation.
 */
public final class Inspector {

    /** Points kept per plot series (the oldest are dropped). */
    public static final int MAX_POINTS = 20_000;
    /** Log lines kept (the oldest are dropped). */
    public static final int MAX_LOG = 2_000;
    /** Plot series and watched values per robot; beyond it, new names are ignored. */
    public static final int MAX_NAMES = 64;

    /** A log line. */
    public record Entry(double t, String text) {}

    /** A plot series: times and values, oldest first. */
    public record Series(String name, double[] t, double[] v) {
        public int size() { return t.length; }
        public double last() { return v.length == 0 ? Double.NaN : v[v.length - 1]; }
    }

    /** A view of the robot's insides, replaced at every update. */
    public sealed interface View permits Bars, Matrix, Network {
        String name();
    }

    /** A vector, one bar per element; {@code labels} may be null or shorter than the values. */
    public record Bars(String name, double[] values, String[] labels) implements View {}

    /** A matrix shown as a heat map: {@code values[row][column]}. */
    public record Matrix(String name, double[][] values) implements View {}

    /**
     * A layered network. {@code activations[l]} are the outputs of the neurons of layer l (layer 0
     * = the inputs). {@code weights} may be null; otherwise {@code weights[l][j][i]} is the weight
     * from neuron i of layer l to neuron j of layer l + 1 (out x in, as PyTorch stores a Linear
     * layer). {@code labels} may be null; otherwise {@code labels[l]} names the neurons of layer l
     * (each may be null, e.g. only the inputs and the outputs named).
     */
    public record Network(String name, double[][] activations, double[][][] weights, String[][] labels) implements View {}

    /** A consistent copy of everything, for the window. */
    public record Snapshot(long version, String state, double stateSince, Map<String, String> watched,
                           List<Series> series, List<Entry> log, List<View> views) {
        /** Without views (kept for code written before the views). */
        public Snapshot(long version, String state, double stateSince, Map<String, String> watched,
                        List<Series> series, List<Entry> log) {
            this(version, state, stateSince, watched, series, log, List.of());
        }
    }

    private final DoubleSupplier clock;
    private final ArrayDeque<Entry> states = new ArrayDeque<>();   // every change: (time, new state)
    private String state = "";
    private double stateSince = 0;
    private final Map<String, String> watched = new LinkedHashMap<>();
    private final Map<String, Ring> series = new LinkedHashMap<>();
    private final ArrayDeque<Entry> log = new ArrayDeque<>();
    private final Map<String, View> views = new LinkedHashMap<>();
    private long version = 0;

    /** @param clock current simulated time in seconds */
    public Inspector(DoubleSupplier clock) {
        this.clock = clock != null ? clock : () -> 0;
    }

    /**
     * The algorithm's current state, e.g. "searching", "following the path". A change of state
     * is also written to the log.
     */
    public synchronized Inspector state(String s) {
        s = s == null ? "" : s;
        if (!s.equals(state)) {
            double t = clock.getAsDouble();
            state = s;
            stateSince = t;
            if (states.size() >= MAX_LOG) states.removeFirst();
            states.addLast(new Entry(t, s));
            addLog(t, "state: " + s);
            version++;
        }
        return this;
    }

    /** Shows a named value (numbers with up to 4 decimals; anything else as text). */
    public synchronized Inspector watch(String name, Object value) {
        if (name == null) return this;
        if (!watched.containsKey(name) && watched.size() >= MAX_NAMES) return this;
        watched.put(name, format(value));
        version++;
        return this;
    }

    /** Adds a point at the current simulated time to the plot series {@code name}. */
    public synchronized Inspector plot(String name, double value) {
        if (name == null) return this;
        Ring r = series.get(name);
        if (r == null) {
            if (series.size() >= MAX_NAMES) return this;
            r = new Ring();
            series.put(name, r);
        }
        r.add(clock.getAsDouble(), value);
        version++;
        return this;
    }

    /** Shows a vector as bars (the latest values replace the previous ones). */
    public Inspector bars(String name, double... values) { return bars(name, null, values); }

    /** Shows a vector as bars, each with its label (e.g. "left", "right"). */
    public synchronized Inspector bars(String name, String[] labels, double... values) {
        if (name == null || values == null) return this;
        return putView(new Bars(name, values.clone(), labels == null ? null : labels.clone()));
    }

    /** Shows a matrix as a heat map, {@code values[row][column]} (rows may differ in length). */
    public synchronized Inspector matrix(String name, double[][] values) {
        if (name == null || values == null) return this;
        return putView(new Matrix(name, copy(values)));
    }

    /** Shows a network: neurons coloured by their activation, no connections drawn. */
    public Inspector network(String name, double[][] activations) { return network(name, activations, null, null); }

    /** Shows a network: neurons coloured by their activation, connections by their weight. */
    public Inspector network(String name, double[][] activations, double[][][] weights) {
        return network(name, activations, weights, null);
    }

    /**
     * Shows a network; see {@link Network} for the shapes. Weights whose shape does not match the
     * activations are not drawn.
     */
    public synchronized Inspector network(String name, double[][] activations, double[][][] weights, String[][] labels) {
        if (name == null || activations == null) return this;
        double[][][] w = null;
        if (weights != null) {
            w = new double[weights.length][][];
            for (int l = 0; l < weights.length; l++) w[l] = weights[l] == null ? null : copy(weights[l]);
        }
        String[][] lb = null;
        if (labels != null) {
            lb = new String[labels.length][];
            for (int l = 0; l < labels.length; l++) lb[l] = labels[l] == null ? null : labels[l].clone();
        }
        return putView(new Network(name, copy(activations), w, lb));
    }

    private Inspector putView(View v) {
        if (!views.containsKey(v.name()) && views.size() >= MAX_NAMES) return this;
        views.put(v.name(), v);
        version++;
        return this;
    }

    private static double[][] copy(double[][] m) {
        double[][] c = new double[m.length][];
        for (int i = 0; i < m.length; i++) c[i] = m[i] == null ? new double[0] : m[i].clone();
        return c;
    }

    /** Adds a line to the log, stamped with the simulated time. */
    public synchronized Inspector log(String message) {
        addLog(clock.getAsDouble(), message == null ? "null" : message);
        version++;
        return this;
    }

    /**
     * The history of the state: one entry per change, with the time it began (the last one is
     * the current state). The timeline draws it as a lane of coloured segments.
     */
    public synchronized List<Entry> states() { return List.copyOf(states); }

    /** Forgets plots, watched values, views and log; keeps the state. */
    public synchronized void clear() {
        watched.clear();
        views.clear();
        series.clear();
        log.clear();
        version++;
    }

    /** Changes at every write: the window redraws only when it moved. */
    public synchronized long version() { return version; }

    public synchronized Snapshot snapshot() {
        List<Series> s = new ArrayList<>(series.size());
        for (Map.Entry<String, Ring> e : series.entrySet()) s.add(e.getValue().toSeries(e.getKey()));
        return new Snapshot(version, state, stateSince, java.util.Collections.unmodifiableMap(new LinkedHashMap<>(watched)),
            List.copyOf(s), List.copyOf(log), List.copyOf(views.values()));
    }

    private void addLog(double t, String text) {
        if (log.size() >= MAX_LOG) log.removeFirst();
        log.addLast(new Entry(t, text));
    }

    static String format(Object v) {
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return String.valueOf(d);
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
            String s = String.format(java.util.Locale.ROOT, "%.4f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
            return s.equals("-0") ? "0" : s;
        }
        return String.valueOf(v);
    }

    /** (t, v) points: grows up to MAX_POINTS, then a ring buffer. */
    private static final class Ring {
        private double[] t = new double[256], v = new double[256];
        private int start = 0, size = 0;

        void add(double time, double value) {
            if (size == t.length && t.length < MAX_POINTS) {   // still growing: start is 0
                int n = Math.min(MAX_POINTS, t.length * 2);
                t = java.util.Arrays.copyOf(t, n);
                v = java.util.Arrays.copyOf(v, n);
            }
            int cap = t.length;
            int i = (start + size) % cap;
            t[i] = time;
            v[i] = value;
            if (size < cap) size++; else start = (start + 1) % cap;
        }

        Series toSeries(String name) {
            int cap = t.length;
            double[] ct = new double[size], cv = new double[size];
            for (int k = 0; k < size; k++) {
                int i = (start + k) % cap;
                ct[k] = t[i];
                cv[k] = v[i];
            }
            return new Series(name, ct, cv);
        }
    }
}
