// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

import io.github.raffaelegrandi.pianura.inspect.Inspector;
import io.github.raffaelegrandi.pianura.units.RobotUnit;

/**
 * The inspector: a separate window about one robot. On the left its body as Pianura sees it
 * (sensors, wheels, speeds: nothing to write for it); on the right what its algorithm says
 * about itself (see {@link Inspector}): state, views of its insides (bars, heat maps, networks),
 * time plots drawn by Pianura, watched values and the log of decisions with their simulated
 * time. It follows the robot selected in the arena, or the one chosen in its own menu.
 */
@SuppressWarnings("serial")
public class InspectorWindow extends JFrame {

    private static final String[] WINDOWS = { "Last 10 s", "Last 30 s", "Last 60 s", "All" };
    private static final double[] WINDOW_S = { 10, 30, 60, 0 };

    private final List<? extends RobotUnit> robots;
    private final DoubleSupplier simTime;
    private final Consumer<RobotUnit> selectInArena;
    private final JComboBox<String> robotBox = new JComboBox<>();
    private final JCheckBox followSelection = new JCheckBox("Follow selection", true);
    private final JComboBox<String> windowBox = new JComboBox<>(WINDOWS);
    private final JLabel stateLabel = new JLabel(" ");
    private final JLabel sinceLabel = new JLabel(" ");
    private final JPanel plots = new JPanel();
    private final JPanel views = new JPanel();
    private final BodyView body = new BodyView();
    private final DefaultTableModel values = new DefaultTableModel(new Object[] { "Name", "Value" }, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTextArea log = new JTextArea();
    private final Timer timer;

    private RobotUnit shown;
    private long shownVersion = -1;
    private double shownWindow = -2;
    private List<Inspector.Entry> shownLog = List.of();
    private boolean syncing;

    /**
     * @param robots        the scenario's robots
     * @param simTime       current simulated time (right edge of the plots)
     * @param selectInArena called when a robot is chosen here, to select it in the arena too
     */
    public InspectorWindow(List<? extends RobotUnit> robots, DoubleSupplier simTime, Consumer<RobotUnit> selectInArena) {
        super("Pianura Sim - Inspector");
        this.robots = robots;
        this.simTime = simTime;
        this.selectInArena = selectInArena != null ? selectInArena : r -> {};

        for (RobotUnit r : robots) robotBox.addItem("Robot #" + r.getIDnum());
        robotBox.addActionListener(e -> {
            if (syncing) return;
            int i = robotBox.getSelectedIndex();
            if (i >= 0 && i < robots.size()) {
                show(robots.get(i));
                this.selectInArena.accept(robots.get(i));
            }
        });
        windowBox.setSelectedIndex(3);   // All: the whole run, e.g. the search and then the drive
        windowBox.addActionListener(e -> refresh());
        followSelection.setToolTipText("Show the robot selected in the arena");

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(robotBox);
        top.add(followSelection);
        top.add(Box.createHorizontalStrut(12));
        top.add(new JLabel("Plots:"));
        top.add(windowBox);

        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD, 16f));
        JPanel state = new JPanel();
        state.setLayout(new BoxLayout(state, BoxLayout.Y_AXIS));
        state.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 10));
        JLabel stateTitle = new JLabel("State");
        stateTitle.setFont(stateTitle.getFont().deriveFont(11f));
        state.add(stateTitle);
        state.add(stateLabel);
        state.add(sinceLabel);

        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.NORTH);
        north.add(state, BorderLayout.CENTER);

        plots.setLayout(new BoxLayout(plots, BoxLayout.Y_AXIS));
        views.setLayout(new BoxLayout(views, BoxLayout.Y_AXIS));
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(views);
        stack.add(plots);
        JScrollPane plotScroll = new JScrollPane(stack, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        plotScroll.getVerticalScrollBar().setUnitIncrement(16);
        plotScroll.setBorder(BorderFactory.createTitledBorder("Views and plots"));
        plotScroll.setPreferredSize(new Dimension(600, 400));

        JTable table = new JTable(values);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Watched values"));

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(log, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));

        JSplitPane bottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, logScroll);
        bottom.setResizeWeight(0.3);
        tableScroll.setPreferredSize(new Dimension(210, 220));
        logScroll.setPreferredSize(new Dimension(390, 220));
        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, plotScroll, bottom);
        main.setResizeWeight(0.62);

        JPanel bodyPanel = new JPanel(new BorderLayout());
        bodyPanel.setBorder(BorderFactory.createTitledBorder("Body"));
        bodyPanel.add(body, BorderLayout.NORTH);
        bodyPanel.setToolTipText("What Pianura knows about the robot: IR rays (heading up), contacts, floor sensors, wheels");

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(north, BorderLayout.NORTH);
        getContentPane().add(bodyPanel, BorderLayout.WEST);
        getContentPane().add(main, BorderLayout.CENTER);
        setSize(900, 800);
        setMinimumSize(new Dimension(560, 440));
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        timer = new Timer(100, e -> refresh());
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { timer.start(); refresh(); }
            @Override public void componentHidden(java.awt.event.ComponentEvent e) { timer.stop(); }
        });
        if (!robots.isEmpty()) show(robots.get(0));
    }

    /** The robot selected in the arena changed (null = none). */
    public void arenaSelectionChanged(RobotUnit r) {
        if (r != null && followSelection.isSelected() && r != shown) show(r);
    }

    /** Shows this robot. */
    public void show(RobotUnit r) {
        shown = r;
        shownVersion = -1;
        shownLog = List.of();
        log.setText("");
        int i = robots.indexOf(r);
        if (i >= 0 && robotBox.getSelectedIndex() != i) {
            syncing = true;
            robotBox.setSelectedIndex(i);
            syncing = false;
        }
        refresh();
    }

    public RobotUnit getShown() { return shown; }

    /** Reads the robot's inspector and updates what changed. */
    public void refresh() {
        RobotUnit r = shown;
        body.set(r);                        // the body changes at every step: always redrawn
        if (r == null) return;
        double window = WINDOW_S[Math.max(0, windowBox.getSelectedIndex())];
        double now = simTime.getAsDouble();
        if (!r.hasInspectorData()) {
            stateLabel.setText("—");
            sinceLabel.setText("<html>Robot #" + r.getIDnum() + " has no inspector data yet. In Java: "
                + "<tt>inspector().state(...)</tt>, <tt>.watch(...)</tt>, <tt>.plot(...)</tt>, <tt>.log(...)</tt>; "
                + "from Python and MATLAB see clients/README.md.</html>");
            if (plots.getComponentCount() > 0) { plots.removeAll(); plots.revalidate(); plots.repaint(); }
            if (views.getComponentCount() > 0) { views.removeAll(); views.revalidate(); views.repaint(); }
            values.setRowCount(0);
            return;
        }
        Inspector in = r.inspector();
        long version = in.version();
        boolean dataChanged = version != shownVersion;
        if (!dataChanged && window == shownWindow) {
            // only the time moved: slide the plots
            for (Component c : plots.getComponents()) if (c instanceof TimePlot p) p.set(p.getSeries(), now, window);
            return;
        }
        Inspector.Snapshot s = in.snapshot();
        shownVersion = s.version();
        shownWindow = window;

        stateLabel.setText(s.state().isEmpty() ? "—" : s.state());
        sinceLabel.setText(s.state().isEmpty() ? " " : String.format(Locale.ROOT, "since t = %.2f s", s.stateSince()));

        // views of the insides: one per name, in order of first use
        List<Inspector.View> vs = s.views();
        if (views.getComponentCount() != vs.size()) {
            views.removeAll();
            for (int i = 0; i < vs.size(); i++) views.add(new InsideView());
            views.revalidate();
        }
        for (int i = 0; i < vs.size(); i++) ((InsideView) views.getComponent(i)).set(vs.get(i));

        // plots: one per series, in order of first use
        List<Inspector.Series> series = s.series();
        if (plots.getComponentCount() != series.size()) {
            plots.removeAll();
            for (int i = 0; i < series.size(); i++) plots.add(new TimePlot(i));
            plots.revalidate();
        }
        for (int i = 0; i < series.size(); i++) ((TimePlot) plots.getComponent(i)).set(series.get(i), now, window);

        // watched values
        Map<String, String> w = s.watched();
        values.setRowCount(w.size());
        int row = 0;
        for (Map.Entry<String, String> e : w.entrySet()) {
            values.setValueAt(e.getKey(), row, 0);
            values.setValueAt(e.getValue(), row, 1);
            row++;
        }

        // log: append the new lines when the old ones are still there, otherwise rebuild
        List<Inspector.Entry> entries = s.log();
        boolean append = !shownLog.isEmpty() && !entries.isEmpty() && entries.size() >= shownLog.size()
            && entries.get(0).equals(shownLog.get(0));
        if (append) {
            StringBuilder b = new StringBuilder();
            for (int i = shownLog.size(); i < entries.size(); i++) line(b, entries.get(i));
            if (b.length() > 0) log.append(b.toString());
        } else {
            StringBuilder b = new StringBuilder();
            for (Inspector.Entry e : entries) line(b, e);
            log.setText(b.toString());
        }
        if (entries.size() != shownLog.size() || !append) log.setCaretPosition(log.getDocument().getLength());
        shownLog = new ArrayList<>(entries);
    }

    private static void line(StringBuilder b, Inspector.Entry e) {
        b.append(String.format(Locale.ROOT, "%8.2f s  %s%n", e.t(), e.text()));
    }

    @Override
    public void dispose() {
        timer.stop();
        super.dispose();
    }
}
