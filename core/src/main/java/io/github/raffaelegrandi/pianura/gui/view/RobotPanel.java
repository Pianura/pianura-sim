// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import io.github.raffaelegrandi.pianura.inspect.Inspector;
import io.github.raffaelegrandi.pianura.units.RobotUnit;

/**
 * The selected robot at a glance, docked in the Studio view: its state, its body as the
 * sensors see it, the values it watches. The full inspector (plots, views, log) opens from
 * the button, in its own window.
 */
@SuppressWarnings("serial")
public class RobotPanel extends JPanel {

    private final JLabel title = new JLabel("No robot selected");
    private final JLabel state = new JLabel(" ");
    private final JLabel since = new JLabel(" ");
    private final BodyView body = new BodyView();
    private final DefaultTableModel watchModel = new DefaultTableModel(new Object[] { "Name", "Value" }, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JButton open = new JButton("Open the inspector");
    private RobotUnit shown;
    private long lastVersion = -1;

    /** @param openInspector what the button does with the robot shown */
    public RobotPanel(java.util.function.Consumer<RobotUnit> openInspector) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        state.setFont(state.getFont().deriveFont(Font.BOLD));
        since.setForeground(TimePlot.mix(since.getForeground(), Color.GRAY, 0.5));
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(title);
        head.add(Box.createVerticalStrut(4));
        head.add(state);
        head.add(since);
        head.add(Box.createVerticalStrut(6));

        JTable watch = new JTable(watchModel);
        watch.setFillsViewportHeight(true);
        watch.setRowSelectionAllowed(false);
        watch.setFocusable(false);
        JScrollPane watchScroll = new JScrollPane(watch);
        watchScroll.setPreferredSize(new Dimension(200, 110));

        JPanel centre = new JPanel(new BorderLayout(0, 6));
        centre.add(body, BorderLayout.NORTH);
        centre.add(watchScroll, BorderLayout.CENTER);

        open.setFocusable(false);
        open.setToolTipText("Plots, views of the insides and the log, in a separate window");
        open.addActionListener(e -> { if (shown != null) openInspector.accept(shown); });

        add(head, BorderLayout.NORTH);
        add(centre, BorderLayout.CENTER);
        add(open, BorderLayout.SOUTH);
        show(null);
    }

    /** Shows this robot (null = none selected). */
    public void show(RobotUnit r) {
        shown = r;
        lastVersion = -1;
        title.setText(r == null ? "No robot selected" : "Robot #" + r.getIDnum());
        open.setEnabled(r != null);
        refresh();
    }

    /** Updates from the robot: the body always, the rest when the robot wrote something. */
    public void refresh() {
        RobotUnit r = shown;
        body.set(r);
        if (r == null) {
            state.setText("Click a robot in the arena");
            since.setText(" ");
            if (watchModel.getRowCount() > 0) watchModel.setRowCount(0);
            return;
        }
        if (!r.hasInspectorData()) {
            state.setText("—");
            since.setText("its code writes nothing to the inspector");
            if (watchModel.getRowCount() > 0) watchModel.setRowCount(0);
            return;
        }
        Inspector in = r.inspector();
        if (in.version() == lastVersion) return;
        Inspector.Snapshot s = in.snapshot();
        lastVersion = s.version();
        state.setText(s.state().isEmpty() ? "—" : s.state());
        since.setText(s.state().isEmpty() ? " " : String.format(Locale.ROOT, "since t = %.2f s", s.stateSince()));
        List<Map.Entry<String, String>> rows = List.copyOf(s.watched().entrySet());
        if (watchModel.getRowCount() != rows.size()) watchModel.setRowCount(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).getKey().equals(watchModel.getValueAt(i, 0))) watchModel.setValueAt(rows.get(i).getKey(), i, 0);
            if (!rows.get(i).getValue().equals(watchModel.getValueAt(i, 1))) watchModel.setValueAt(rows.get(i).getValue(), i, 1);
        }
    }
}
