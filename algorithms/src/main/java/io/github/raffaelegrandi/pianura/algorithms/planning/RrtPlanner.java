// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.algorithms.planning;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import io.github.raffaelegrandi.pianura.inspect.Inspector;
import io.github.raffaelegrandi.pianura.units.RobotController;
import io.github.raffaelegrandi.pianura.units.RobotUnit;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Plans a path to a goal with an RRT (Rapidly-exploring Random Tree, LaValle 1998), then
 * drives along it.
 *
 * <p>The tree grows a few iterations per control step, so it is seen growing in the arena
 * (layer {@code "tree"}; the last random sample in {@code "sample"}): slow the simulation
 * down or step through it to follow every choice. One iteration: pick a random point (the
 * goal itself with probability {@link #goalBias}), find the nearest node of the tree, take a
 * step of {@link #stepCells} towards the point, and add the new node if the straight segment
 * is free. When a node can see the goal, the path is read back through the parents, shortened
 * by skipping the waypoints it can see past (layer {@code "path"}), and followed with a
 * proportional controller on distance and heading.
 *
 * <p>The inspector shows the state (searching, following, arrived), the tree's size, the
 * distance from the goal of the closest node, the heading error while following, and a log of
 * the decisions. With {@code pauseWhenFound} the simulation pauses when the path is found.
 *
 * <p>The planner knows the map ({@link FreeSpace}): the robot's sensors are not used.
 */
public class RrtPlanner implements RobotController {

    /** Probability of sampling the goal instead of a random point. */
    public double goalBias = 0.1;
    /** Length of a tree edge, cells. */
    public double stepCells = 1.5;
    /** Iterations per control step: with a period of 0.05 s, 3 iterations = 60 per simulated second. */
    public int iterationsPerStep = 3;
    /** Give up after this many iterations. */
    public int maxIterations = 20_000;
    /** Pause the simulation when the path is found, to look at the tree. */
    public boolean pauseWhenFound = true;
    /** Top speed while following the path, cells/s. */
    public double maxSpeed = 2.0;

    private enum Phase { START, SEARCH, FOLLOW, ARRIVED, FAILED }

    private final Supplier<FreeSpace> mapSupplier;
    private final double goalX, goalY;
    private final Random rnd;
    private FreeSpace map;
    private Phase phase = Phase.START;
    private final List<double[]> nodes = new ArrayList<>();      // x, y
    private final List<Integer> parent = new ArrayList<>();
    private int iterations = 0;
    private double closest = Double.POSITIVE_INFINITY;
    private List<double[]> path = List.of();
    private int target = 0;

    private static final Color TREE = new Color(70, 110, 190, 170);
    private static final Color PATH = new Color(255, 120, 0);
    private static final Color GOAL = new Color(220, 40, 40);

    /**
     * @param map   builds the map when the planner starts (so it sees the scene as it is at Run)
     * @param goalX goal, cells
     * @param goalY goal, cells
     * @param seed  seed of the random samples: same seed, same tree
     */
    public RrtPlanner(Supplier<FreeSpace> map, double goalX, double goalY, long seed) {
        this.mapSupplier = map;
        this.goalX = goalX;
        this.goalY = goalY;
        this.rnd = new Random(seed);
    }

    @Override
    public void control(RobotUnit robot, double t) {
        Inspector in = robot.inspector();
        switch (phase) {
            case START -> start(robot, in);
            case SEARCH -> search(robot, in);
            case FOLLOW -> follow(robot, in);
            case ARRIVED, FAILED -> robot.setRobotSpeed(0, 0);
        }
    }

    private void start(RobotUnit robot, Inspector in) {
        map = mapSupplier.get();
        Position p = robot.getPosition();
        nodes.add(new double[] { p.x, p.y });
        parent.add(-1);
        robot.viz("goal").clear().color(GOAL).circle(goalX, goalY, 0.6).disc(goalX, goalY, 0.2)
            .text(goalX + 0.8, goalY - 0.8, "goal");
        in.log(String.format("start (%.1f, %.1f), goal (%.1f, %.1f)", p.x, p.y, goalX, goalY));
        if (!map.isFree(goalX, goalY)) {
            phase = Phase.FAILED;
            in.state("failed");
            in.log("the goal is inside an obstacle (or too close to one)");
            return;
        }
        phase = Phase.SEARCH;
        in.state("searching");
        robot.setRobotSpeed(0, 0);
    }

    private void search(RobotUnit robot, Inspector in) {
        robot.setRobotSpeed(0, 0);
        var tree = robot.viz("tree").color(TREE).width(1);
        var sample = robot.viz("sample").clear().color(GOAL);
        for (int k = 0; k < iterationsPerStep && phase == Phase.SEARCH; k++) {
            iterations++;
            boolean toGoal = rnd.nextDouble() < goalBias;
            double sx = toGoal ? goalX : rnd.nextDouble() * map.width();
            double sy = toGoal ? goalY : rnd.nextDouble() * map.height();
            sample.point(sx, sy);

            int near = nearest(sx, sy);
            double[] n = nodes.get(near);
            double d = Math.hypot(sx - n[0], sy - n[1]);
            if (d < 1e-9) continue;
            double step = Math.min(stepCells, d);
            double nx = n[0] + (sx - n[0]) / d * step, ny = n[1] + (sy - n[1]) / d * step;
            if (!map.isFree(n[0], n[1], nx, ny)) continue;

            nodes.add(new double[] { nx, ny });
            parent.add(near);
            tree.line(n[0], n[1], nx, ny);
            closest = Math.min(closest, Math.hypot(goalX - nx, goalY - ny));

            if (Math.hypot(goalX - nx, goalY - ny) <= stepCells && map.isFree(nx, ny, goalX, goalY)) {
                nodes.add(new double[] { goalX, goalY });
                parent.add(nodes.size() - 2);
                tree.line(nx, ny, goalX, goalY);
                closest = 0;
                found(robot, in);
            }
        }
        in.watch("iterations", iterations).watch("nodes", nodes.size())
          .watch("closest to goal (cells)", closest);
        in.plot("nodes", nodes.size());
        in.plot("closest to goal", closest);
        if (phase == Phase.SEARCH && iterations >= maxIterations) {
            phase = Phase.FAILED;
            in.state("failed");
            in.log("no path after " + iterations + " iterations");
        }
    }

    private int nearest(double x, double y) {
        int best = 0;
        double bd = Double.POSITIVE_INFINITY;
        for (int i = 0; i < nodes.size(); i++) {
            double[] n = nodes.get(i);
            double d = (n[0] - x) * (n[0] - x) + (n[1] - y) * (n[1] - y);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    private void found(RobotUnit robot, Inspector in) {
        List<double[]> raw = new ArrayList<>();
        for (int i = nodes.size() - 1; i >= 0; i = parent.get(i)) raw.add(0, nodes.get(i));
        path = shortcut(raw);
        double rawLen = length(raw), len = length(path);
        double[] xs = new double[path.size()], ys = new double[path.size()];
        for (int i = 0; i < path.size(); i++) { xs[i] = path.get(i)[0]; ys[i] = path.get(i)[1]; }
        robot.viz("path").clear().color(PATH).width(3).polyline(xs, ys);
        robot.viz("sample").clear();
        in.log(String.format("path found after %d iterations: %d nodes in the tree, %d on the path, length %.1f cells",
            iterations, nodes.size(), raw.size(), rawLen));
        in.log(String.format("shortened to %d waypoints, length %.1f cells", path.size(), len));
        in.watch("path length (cells)", len).watch("waypoints", path.size());
        phase = Phase.FOLLOW;
        target = 1;
        in.state("following the path");
        if (pauseWhenFound) robot.pauseSimulation("path found, press Run to follow it");
    }

    /** Greedy shortcut: from each waypoint jump to the farthest one it can see in a straight line. */
    private List<double[]> shortcut(List<double[]> p) {
        List<double[]> out = new ArrayList<>();
        int i = 0;
        out.add(p.get(0));
        while (i < p.size() - 1) {
            int j = p.size() - 1;
            while (j > i + 1 && !map.isFree(p.get(i)[0], p.get(i)[1], p.get(j)[0], p.get(j)[1])) j--;
            out.add(p.get(j));
            i = j;
        }
        return out;
    }

    private static double length(List<double[]> p) {
        double s = 0;
        for (int i = 1; i < p.size(); i++) s += Math.hypot(p.get(i)[0] - p.get(i - 1)[0], p.get(i)[1] - p.get(i - 1)[1]);
        return s;
    }

    private void follow(RobotUnit robot, Inspector in) {
        Position p = robot.getPosition();
        double[] w = path.get(target);
        double dist = Math.hypot(w[0] - p.x, w[1] - p.y);
        boolean last = target == path.size() - 1;
        if (dist < (last ? 0.3 : 0.6)) {
            if (last) {
                phase = Phase.ARRIVED;
                robot.setRobotSpeed(0, 0);
                robot.viz("aim").clear();
                in.state("arrived");
                in.log(String.format("goal reached, %.2f cells from it", dist));
                return;
            }
            target++;
            in.log("waypoint " + (target - 1) + " reached, heading to waypoint " + target);
            w = path.get(target);
            dist = Math.hypot(w[0] - p.x, w[1] - p.y);
        }
        double e = Math.atan2(w[1] - p.y, w[0] - p.x) - p.theta;
        e = Math.atan2(Math.sin(e), Math.cos(e));                       // wrap to [-pi, pi]
        double remaining = dist;                                         // along the path, to slow down only at the end
        for (int i = target + 1; i < path.size(); i++)
            remaining += Math.hypot(path.get(i)[0] - path.get(i - 1)[0], path.get(i)[1] - path.get(i - 1)[1]);
        double v = Math.min(maxSpeed, 1.2 * remaining) * Math.max(0, Math.cos(e));
        robot.setRobotSpeed(v, 3.0 * e);
        robot.viz("aim").clear().color(PATH).width(2).arrow(p.x, p.y, w[0], w[1]);
        in.plot("heading error (rad)", e);
        in.plot("remaining path (cells)", remaining);
        in.watch("waypoint", target + " of " + (path.size() - 1)).watch("speed (cells/s)", v);
    }

    /** True once the robot reached the goal. */
    public boolean arrived() { return phase == Phase.ARRIVED; }

    /** Waypoints of the shortened path (empty until found). */
    public List<double[]> path() { return List.copyOf(path); }
}
