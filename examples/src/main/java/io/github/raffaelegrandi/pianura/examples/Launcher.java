// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.examples;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import io.github.raffaelegrandi.pianura.gui.LookAndFeelSetup;

/**
 * Entry point of the runnable jar: {@code java -jar pianura.jar} opens a small window to pick
 * an example, {@code java -jar pianura.jar planning} starts one directly, and
 * {@code java -jar pianura.jar --list} prints them ({@code --version} the version).
 */
public final class Launcher {

    /** An example: package name (also its command-line name) and a one-line description. */
    public record Example(String name, String description) {
        String mainClass() { return "io.github.raffaelegrandi.pianura.examples." + name + ".Main"; }

        @Override
        public String toString() { return name; }
    }

    /** The examples, in the order of examples/README.md. */
    public static final List<Example> EXAMPLES = List.of(
        new Example("timedmotion", "Two robots in an open arena, moving open-loop"),
        new Example("obstacles", "Nine robots and mixed obstacles, with the scenario warnings"),
        new Example("rooms", "An 80x50 arena with a stone floor, rooms and corridors"),
        new Example("linefollowing", "A track on the floor: writing the controller is the exercise"),
        new Example("remote", "Two robots driven from Python or MATLAB over UDP"),
        new Example("radio", "Fourteen robots exchanging radio beacons"),
        new Example("largemap", "A 300x200 building explored by 60 robots, with the minimap"),
        new Example("braitenberg", "Five Braitenberg vehicles with their trails"),
        new Example("planning", "An RRT grows, then the robot follows the path; with the inspector"),
        new Example("brains", "One brain per robot: eight robots driven by separate programs over UDP"));

    private Launcher() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            String a = args[0].toLowerCase(Locale.ROOT);
            if (a.equals("--version") || a.equals("-v")) {
                System.out.println("Pianura " + version());
                return;
            }
            if (a.equals("--list") || a.equals("-l") || a.equals("--help") || a.equals("-h")) {
                printList();
                return;
            }
            Example e = find(a);
            if (e == null) {
                System.err.println("Unknown example '" + args[0] + "'.");
                printList();
                System.exit(2);
            }
            start(e);
            return;
        }
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            printList();
            return;
        }
        SwingUtilities.invokeLater(Launcher::showChooser);
    }

    static Example find(String name) {
        for (Example e : EXAMPLES) if (e.name().equals(name)) return e;
        return null;
    }

    /** Version from the jar's manifest; "dev" when running from the build folders. */
    static String version() {
        String v = Launcher.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }

    private static void printList() {
        System.out.println("Pianura " + version() + "\n\nUsage: java -jar pianura.jar [example | --list | --version]\n\nExamples:");
        for (Example e : EXAMPLES) System.out.printf(Locale.ROOT, "  %-14s %s%n", e.name(), e.description());
    }

    /** Runs the example's Main, as {@code ./gradlew run -Pexample=name} does. */
    static void start(Example e) throws Exception {
        Class.forName(e.mainClass()).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }

    private static void showChooser() {
        LookAndFeelSetup.install();
        JFrame f = new JFrame("Pianura " + version() + " - examples");
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JList<Example> list = new JList<>(EXAMPLES.toArray(new Example[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(EXAMPLES.indexOf(find("obstacles")));
        list.setCellRenderer((l, value, index, selected, focus) -> {
            JLabel lab = new JLabel("<html><b>" + value.name() + "</b><br>" + value.description() + "</html>");
            lab.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            lab.setOpaque(true);
            lab.setBackground(selected ? l.getSelectionBackground() : l.getBackground());
            lab.setForeground(selected ? l.getSelectionForeground() : l.getForeground());
            return lab;
        });

        JButton run = new JButton("Start");
        Runnable go = () -> {
            Example e = list.getSelectedValue();
            if (e == null) return;
            f.dispose();
            try {
                start(e);
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(1);
            }
        };
        run.addActionListener(ev -> go.run());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent ev) { if (ev.getClickCount() == 2) go.run(); }
        });

        JLabel title = new JLabel("Pick an example (double click or Start). They open paused: press Run.");
        title.setFont(title.getFont().deriveFont(Font.PLAIN));
        title.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(run);

        f.getContentPane().add(title, BorderLayout.NORTH);
        f.getContentPane().add(new JScrollPane(list), BorderLayout.CENTER);
        f.getContentPane().add(buttons, BorderLayout.SOUTH);
        f.getRootPane().setDefaultButton(run);
        f.setSize(new Dimension(520, 560));
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
