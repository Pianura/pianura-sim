// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.examples;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import io.github.raffaelegrandi.pianura.Simulator;
import io.github.raffaelegrandi.pianura.gui.LookAndFeelSetup;
import io.github.raffaelegrandi.pianura.gui.start.StartPanel;
import io.github.raffaelegrandi.pianura.gui.start.StartStore;
import io.github.raffaelegrandi.pianura.gui.start.StartWindow;
import io.github.raffaelegrandi.pianura.remote.RemoteServer;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;

/**
 * Entry point of the runnable jar. {@code java -jar pianura.jar} opens the start screen: the
 * scenes and examples opened last, the favourites, all the examples, and the scene files of a
 * folder; the Home button of a simulation comes back to it. {@code java -jar pianura.jar
 * planning} starts one example directly, {@code --list} prints them, {@code --version} the version.
 */
public final class Launcher {

    /** An example: package name (also its command-line name), a one-line description and a short tag. */
    public record Example(String name, String description, String tag, Supplier<ScenarioManager> preview) {
        String mainClass() { return "io.github.raffaelegrandi.pianura.examples." + name + ".Main"; }

        @Override
        public String toString() { return name; }
    }

    /** The examples, in the order of examples/README.md. */
    public static final List<Example> EXAMPLES = List.of(
        new Example("timedmotion", "Two robots in an open arena, moving open-loop", "basics",
            io.github.raffaelegrandi.pianura.examples.timedmotion.TimedMotionScenario::new),
        new Example("obstacles", "Nine robots and mixed obstacles, with the scenario warnings", "basics",
            io.github.raffaelegrandi.pianura.examples.obstacles.ObstaclesScenario::new),
        new Example("rooms", "An 80x50 arena with a stone floor, rooms and corridors", "large arena",
            io.github.raffaelegrandi.pianura.examples.rooms.RoomsScenario::new),
        new Example("linefollowing", "A track on the floor: writing the controller is the exercise", "exercise",
            io.github.raffaelegrandi.pianura.examples.linefollowing.LineFollowingScenario::new),
        new Example("remote", "Two robots driven from Python or MATLAB over UDP", "UDP",
            () -> withServer(io.github.raffaelegrandi.pianura.examples.remote.RemoteScenario::new)),
        new Example("radio", "Fourteen robots exchanging radio beacons", "radio",
            io.github.raffaelegrandi.pianura.examples.radio.RadioScenario::new),
        new Example("largemap", "A 300x200 building explored by 60 robots, with the minimap", "large arena",
            io.github.raffaelegrandi.pianura.examples.largemap.LargeMapScenario::new),
        new Example("braitenberg", "Five Braitenberg vehicles with their trails", "algorithm",
            io.github.raffaelegrandi.pianura.examples.braitenberg.BraitenbergScenario::new),
        new Example("planning", "An RRT grows, then the robot follows the path; with the inspector", "algorithm",
            io.github.raffaelegrandi.pianura.examples.planning.PlanningScenario::new),
        new Example("brains", "One brain per robot: eight robots driven by separate programs over UDP", "UDP",
            () -> withServer(s -> new io.github.raffaelegrandi.pianura.examples.brains.BrainsScenario(s, 8))),
        new Example("sandbox", "A scene of your own: an empty arena and one wandering robot", "your scene",
            io.github.raffaelegrandi.pianura.examples.sandbox.SandboxScenario::new));

    private static StartWindow startWindow;

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
            start(e, java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            printList();
            return;
        }
        SwingUtilities.invokeLater(Launcher::showStart);
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
        System.out.println("Pianura " + version() + "\n\nUsage: java -jar pianura.jar [example [arguments] | --list | --version]\n\nExamples:");
        for (Example e : EXAMPLES) System.out.printf(Locale.ROOT, "  %-14s %s%n", e.name(), e.description());
    }

    /** Runs the example's Main, as {@code ./gradlew run -Pexample=name} does. */
    static void start(Example e, String... args) throws Exception {
        Class.forName(e.mainClass()).getMethod("main", String[].class).invoke(null, (Object) args);
    }

    /** A scenario that needs a remote server, built with a throwaway one: only for its picture. */
    private static ScenarioManager withServer(java.util.function.Function<RemoteServer, ScenarioManager> build) {
        try {
            RemoteServer s = RemoteServer.start(0, RemoteServer.Mode.LOCKSTEP);
            try { return build.apply(s); } finally { s.close(); }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ------------------------------------------------------------------ the start screen

    private static void showStart() {
        LookAndFeelSetup.install();
        StartStore store = StartStore.user();
        List<StartPanel.Example> list = new ArrayList<>();
        for (Example e : EXAMPLES) list.add(new StartPanel.Example(e.name(), e.description(), e.tag(), e.preview()));
        startWindow = new StartWindow(store, list, new StartPanel.Actions() {
            @Override public void openExample(String name) {
                store.touch(StartStore.Item.example(name));
                launch(find(name));
            }
            @Override public void openScene(Path file) {
                store.touch(StartStore.Item.scene(file));
                launch(find("sandbox"), file.toString());
            }
            @Override public void newScene() { launch(find("sandbox")); }
        }, version());
        // Home in a simulation window: close the simulation and come back here
        Simulator.setHomeHandler(sim -> {
            sim.close();
            startWindow.setLocation(sim.getWindow().getLocation());
            startWindow.comeBack();
        });
        startWindow.setVisible(true);
    }

    /** Hides the start screen and runs the example on its own thread, as its main would. */
    private static void launch(Example e, String... args) {
        startWindow.setVisible(false);
        Thread t = new Thread(() -> {
            try {
                start(e, args);
            } catch (Exception ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                cause.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    startWindow.comeBack();
                    JOptionPane.showMessageDialog(startWindow, "Cannot open " + e.name() + ":\n" + cause.getMessage(),
                        "Pianura", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "pianura-example-" + e.name());
        t.start();
    }
}
