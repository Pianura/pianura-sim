package io.github.raffaelegrandi.pianura.units.collisions;

import java.util.Locale;

import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.arena.ObjectID;
import io.github.raffaelegrandi.pianura.units.UnitObject;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Collision detection by geometric ray casting.
 *
 * For every robot, 18 rays (one per sensor) are cast from the robot's centre in the
 * sensor's absolute direction. For every ray the nearest intersection is searched among:
 *   - the scenario's static obstacles and the arena borders (shapes built once);
 *   - the bodies of the other robots (circles rebuilt at every step by SimEngine).
 * An IR sensor reads the distance in cells from the robot's surface to the first object
 * hit; with nothing in range it reads the sensor's range (saturation, like a real
 * sensor). Bump sensors read 1 on contact, 0 otherwise.
 * Internally the detector works in world pixels (cells x Const.cellSize); to the
 * outside it publishes only cells.
 *
 * The detector has no shared mutable state: it can be called for different robots
 * without synchronisation.
 */
public class CollisionDetector {

    /**
     * Static shapes: all of them (walls + obstacles), obstacles only (parallel to {@code obstacles}),
     * the four walls, and the grid that indexes the obstacles by position.
     */
    private record Shapes(List<CollidableShape> all, List<CollidableShape> perObstacle,
                          List<CollidableShape> walls, ShapeGrid grid) {}

    /** Side of a cell of the obstacle grid, in world pixels (4 cells). */
    static final double GRID_BUCKET_PX = 4 * Const.cellSize;

    /** Tests only: skips the grid and tries every shape (the result must be identical). */
    static volatile boolean bruteForceForTests = false;

    /** Indices (ascending) of the obstacles whose bounds touch the given rectangle. */
    private static int[] near(Shapes sh, double ax, double ay, double bx, double by) {
        if (bruteForceForTests) {
            int[] all = new int[sh.perObstacle().size()];
            int m = 0;
            for (int i = 0; i < all.length; i++) if (sh.perObstacle().get(i) != null) all[m++] = i;
            return java.util.Arrays.copyOf(all, m);
        }
        return sh.grid().query(ax, ay, bx, by);
    }

    /** Replaced as a whole by {@link #rebuild()}: readers always see a consistent set. */
    private volatile Shapes shapes;
    private volatile List<Obstacle> obstacles;
    private final double robotRadiusPx;
    private final double bumpRangePx;
    private final double cellSizePx;
    private final double arenaWidthPx, arenaHeightPx;

    /** Arena standard (Const.arenaWidth x Const.arenaHeight). */
    public CollisionDetector(List<Obstacle> obstacles) {
        this(obstacles, Const.arenaWidth, Const.arenaHeight);
    }

    /**
     * @param arenaWidthPx  arena width in world pixels (cells x Const.cellSize)
     * @param arenaHeightPx arena height in world pixels
     */
    public CollisionDetector(List<Obstacle> obstacles, double arenaWidthPx, double arenaHeightPx) {
        this.arenaWidthPx  = arenaWidthPx;
        this.arenaHeightPx = arenaHeightPx;
        this.robotRadiusPx = Const.unitRadius;
        this.bumpRangePx   = Const.sensorsCheckingRange0;
        this.cellSizePx    = Const.cellSize;
        this.obstacles     = List.copyOf(obstacles);
        this.shapes        = buildShapes(this.obstacles);
    }

    /**
     * Recomputes the shapes after obstacles were moved or rotated (the positions are read
     * from the same Obstacle objects passed to the constructor).
     */
    public void rebuild() {
        shapes = buildShapes(obstacles);
    }

    public List<Obstacle> getObstacles() { return obstacles; }

    /**
     * Replaces the set of obstacles (e.g. a scene opened from file) and recomputes the shapes.
     * Call it with the simulation stopped or paused (SimEngine.replaceScene does it under lock).
     */
    public void replaceObstacles(List<Obstacle> newObstacles) {
        List<Obstacle> copy = List.copyOf(newObstacles);
        shapes = buildShapes(copy);
        obstacles = copy;
    }

    /**
     * True if no obstacle (or arena wall) lies on the segment between the two points (pixels).
     * Robots do not block the line of sight.
     */
    public boolean lineOfSight(double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, d = Math.hypot(dx, dy);
        if (d < 1e-9) return true;
        dx /= d;
        dy /= d;
        Shapes sh = shapes;
        for (CollidableShape s : sh.walls()) {
            double hit = s.rayIntersect(ax, ay, dx, dy, d);
            if (hit >= 0 && hit < d - 1e-6) return false;
        }
        for (int i : near(sh, Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by))) {
            double hit = sh.perObstacle().get(i).rayIntersect(ax, ay, dx, dy, d);
            if (hit >= 0 && hit < d - 1e-6) return false;
        }
        return true;
    }

    // ---------------------------------------------------------
    //  Forme statiche
    // ---------------------------------------------------------

    private Shapes buildShapes(List<Obstacle> obstacles) {
        List<CollidableShape> result = new ArrayList<>();
        List<CollidableShape> perObstacle = new ArrayList<>();
        List<CollidableShape> walls = new ArrayList<>();

        // arena walls: outside the floor, with their inner face exactly on the border
        double aw = arenaWidthPx, ah = arenaHeightPx, t = WALL_THICKNESS_PX;
        result.add(new RectShape(-t / 2,     ah / 2,     t,          ah + 2 * t, 0));  // sinistro
        result.add(new RectShape(aw + t / 2, ah / 2,     t,          ah + 2 * t, 0));  // destro
        result.add(new RectShape(aw / 2,     -t / 2,     aw + 2 * t, t,          0));  // superiore
        result.add(new RectShape(aw / 2,     ah + t / 2, aw + 2 * t, t,          0));  // inferiore
        walls.addAll(result);

        for (Obstacle obs : obstacles) {
            ObjectID id = obs.getID();
            double px    = id.getPos().x * cellSizePx;
            double py    = id.getPos().y * cellSizePx;
            double w     = id.getWidth()  * cellSizePx;
            double h     = id.getHeight() * cellSizePx;
            double angle = id.getAngle();

            CollidableShape shape = switch (id.getType()) {
                case OBSTACLE_RECT -> new RectShape(px, py, w, h, angle);
                case OBSTACLE_OVAL -> new OvalShape(px, py, w, h, angle);
                default -> null;
            };
            perObstacle.add(shape);
            if (shape != null) result.add(shape);
        }
        return new Shapes(List.copyOf(result), java.util.Collections.unmodifiableList(perObstacle),
            List.copyOf(walls), new ShapeGrid(perObstacle, GRID_BUCKET_PX));
    }

    // ---------------------------------------------------------
    //  Dynamic shapes (robots)
    // ---------------------------------------------------------

    /**
     * Builds the snapshot of the robots' bodies at their current positions.
     * Call it once per tick, before updating the sensors: this way every robot "sees" the
     * same instant, whatever the order in which they are processed.
     */
    public List<CircleShape> snapshotRobots(List<UnitObject> units) {
        List<CircleShape> list = new ArrayList<>(units.size());
        for (UnitObject u : units) {
            Position p = u.getExactPos();
            list.add(new CircleShape(p.x * cellSizePx, p.y * cellSizePx, robotRadiusPx, u));
        }
        return list;
    }

    // ---------------------------------------------------------
    //  Aggiornamento sensori
    // ---------------------------------------------------------

    /** Static obstacles only (compatibility). */
    public void testUnitForCollisions(UnitObject testUnit) {
        testUnitForCollisions(testUnit, List.of());
    }

    /**
     * Computes the robot's sensors against the static obstacles and the other robots.
     * @param robots snapshot of the robots' bodies (the robot itself is skipped)
     */
    public void testUnitForCollisions(UnitObject testUnit, List<CircleShape> robots) {
        SensorArray bumpSensors = testUnit.getBumpSensors();
        SensorArray irSensors   = testUnit.getIrSensors();
        int n = Const.sensorCount;

        double[] bumpVal = new double[n];
        double[] irVal   = new double[n];
        java.util.Arrays.fill(bumpVal, bumpSensors.getInitValue());
        java.util.Arrays.fill(irVal,   irSensors.getInitValue());
        Point2D[] bumpPts = new Point2D[n];
        Point2D[] irPts   = new Point2D[n];

        double maxRayDist = robotRadiusPx + cellSizePx * testUnit.getSensorRange();

        Position pos   = testUnit.getExactPos();
        double cx      = pos.x * cellSizePx;
        double cy      = pos.y * cellSizePx;
        double heading = pos.theta;

        // only the obstacles and robots the rays can reach
        Shapes sh = shapes;
        int[] nearIdx = near(sh, cx - maxRayDist, cy - maxRayDist, cx + maxRayDist, cy + maxRayDist);
        List<CollidableShape> candidates = new ArrayList<>(sh.walls().size() + nearIdx.length + 4);
        candidates.addAll(sh.walls());
        for (int k : nearIdx) candidates.add(sh.perObstacle().get(k));
        double reach = maxRayDist + robotRadiusPx;
        for (CircleShape s : robots) {
            if (s.getOwner() == testUnit) continue;
            double ddx = s.getCx() - cx, ddy = s.getCy() - cy;
            if (bruteForceForTests || ddx * ddx + ddy * ddy <= reach * reach) candidates.add(s);
        }

        for (int i = 0; i < n; i++) {
            double a  = heading + irSensors.getSensorAngle(i);
            double dx = Math.cos(a);
            double dy = Math.sin(a);

            double d = findNearestHit(candidates, cx, cy, dx, dy, maxRayDist);
            if (d < 0) continue;  // nothing in range: the rest values stay

            double surfacePx = Math.max(0, d - robotRadiusPx);
            Point2D hit = new Point2D.Double((cx + d * dx) / cellSizePx, (cy + d * dy) / cellSizePx);

            irVal[i] = Math.min(surfacePx / cellSizePx, testUnit.getSensorRange());
            irPts[i] = hit;

            if (surfacePx < bumpRangePx) {
                bumpVal[i] = 1;
                bumpPts[i] = hit;
                if (Const.debugEnabled)
                    System.out.println("CD>unit #" + testUnit.getIDnum() + " bump #" + i + " dist:" + surfacePx + "px");
            }
        }

        // Atomic publication: the robot's thread never sees intermediate states
        irSensors.publish(irVal, irPts);
        bumpSensors.publish(bumpVal, bumpPts);
    }

    private static double findNearestHit(List<CollidableShape> candidates,
                                         double ox, double oy, double dx, double dy, double maxDist) {
        double nearest = -1;
        for (CollidableShape s : candidates) {
            double d = s.rayIntersect(ox, oy, dx, dy, maxDist);
            if (d >= 0 && (nearest < 0 || d < nearest)) nearest = d;
        }
        return nearest;
    }

    // ---------------------------------------------------------
    //  Validazione dello scenario
    // ---------------------------------------------------------

    /**
     * Result of the validation: the messages and the elements involved (obstacle indices in
     * the scenario's order, robots), to highlight them in the view.
     */
    public record Validation(List<String> messages, java.util.Set<Integer> badObstacles, java.util.Set<UnitObject> badRobots) {
        public boolean isOk() { return messages.isEmpty(); }
    }

    /** Obstacles (overlapping, outside the arena) and robots (overlapping obstacles, walls or other robots). */
    public Validation validate(List<UnitObject> units) {
        List<String> msgs = new ArrayList<>();
        java.util.Set<Integer> badObs = new java.util.TreeSet<>();
        java.util.Set<UnitObject> badRob = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        checkObstacles(msgs, badObs);
        checkPlacement(units, msgs, badRob);
        return new Validation(List.copyOf(msgs), java.util.Collections.unmodifiableSet(badObs), java.util.Collections.unmodifiableSet(badRob));
    }

    /**
     * Checks that no robot starts overlapping an obstacle, the borders or another robot.
     * Returns a message for every problem found.
     */
    public List<String> validatePlacement(List<UnitObject> units) {
        List<String> msgs = new ArrayList<>();
        checkPlacement(units, msgs, new java.util.HashSet<>());
        return msgs;
    }

    /**
     * Checks that obstacles do not overlap each other and do not stick out of the arena.
     * It is a warning, not an error: two overlapping obstacles may be intended (e.g. an L-shaped
     * wall made of two rectangles), but two distinct physical objects cannot occupy the same space.
     * Overlaps below {@code MIN_OVERLAP_PX2} (contact, rounding) are ignored.
     */
    public List<String> validateObstacles() {
        List<String> msgs = new ArrayList<>();
        checkObstacles(msgs, new java.util.TreeSet<>());
        return msgs;
    }

    private void checkPlacement(List<UnitObject> units, List<String> problems, java.util.Set<UnitObject> bad) {
        Shapes sh = shapes;
        int probes = 72; // one ray every 5 degrees
        for (UnitObject u : units) {
            Position p = u.getExactPos();
            double cx = p.x * cellSizePx, cy = p.y * cellSizePx;
            List<CollidableShape> all = new ArrayList<>(sh.walls());
            for (int k : near(sh, cx - robotRadiusPx, cy - robotRadiusPx, cx + robotRadiusPx, cy + robotRadiusPx))
                all.add(sh.perObstacle().get(k));
            for (int k = 0; k < probes; k++) {
                double a = 2 * Math.PI * k / probes;
                double dx = Math.cos(a), dy = Math.sin(a);
                boolean hit = false;
                for (CollidableShape s : all) {
                    if (s.rayIntersect(cx, cy, dx, dy, robotRadiusPx) >= 0) { hit = true; break; }
                }
                if (hit) {
                    problems.add("Robot #" + u.getIDnum() + " at " + fmt(p) + " overlaps an obstacle or the arena border");
                    bad.add(u);
                    break;
                }
            }
        }
        for (int i = 0; i < units.size(); i++) {
            for (int j = i + 1; j < units.size(); j++) {
                Position a = units.get(i).getExactPos(), b = units.get(j).getExactPos();
                double dist = Math.hypot(a.x - b.x, a.y - b.y) * cellSizePx;
                if (dist < 2 * robotRadiusPx + bumpRangePx) {
                    problems.add("Robot #" + units.get(i).getIDnum() + " and #" + units.get(j).getIDnum()
                        + " start in contact or overlapping (center distance " + String.format(Locale.ROOT, "%.1f", dist / cellSizePx) + " cells)");
                    bad.add(units.get(i));
                    bad.add(units.get(j));
                }
            }
        }
    }

    private void checkObstacles(List<String> problems, java.util.Set<Integer> bad) {
        Shapes sh = shapes;
        List<Area> areas = new ArrayList<>();
        for (CollidableShape s : sh.perObstacle()) areas.add(s == null ? null : new Area(s.outline()));

        double cell2 = cellSizePx * cellSizePx;
        for (int i = 0; i < areas.size(); i++) {
            if (areas.get(i) == null) continue;
            Rectangle2D bi = sh.grid().bounds(i);
            for (int j : near(sh, bi.getMinX(), bi.getMinY(), bi.getMaxX(), bi.getMaxY())) {   // neighbours only, in order
                if (j <= i || areas.get(j) == null) continue;
                // quick rejection: disjoint bounding rectangles
                if (!areas.get(i).getBounds2D().intersects(areas.get(j).getBounds2D())) continue;
                Area inter = new Area(areas.get(i));
                inter.intersect(areas.get(j));
                double a = areaOf(inter);
                if (a > MIN_OVERLAP_PX2) {
                    problems.add(describe(i) + " and " + describe(j)
                        + " overlap (shared area " + String.format(Locale.ROOT, "%.2f", a / cell2) + " cells²)");
                    bad.add(i);
                    bad.add(j);
                }
            }
        }

        Area arena = new Area(new Rectangle2D.Double(0, 0, arenaWidthPx, arenaHeightPx));
        for (int i = 0; i < areas.size(); i++) {
            if (areas.get(i) == null) continue;
            Rectangle2D b = areas.get(i).getBounds2D();
            if (b.getMinX() >= 0 && b.getMinY() >= 0 && b.getMaxX() <= arenaWidthPx && b.getMaxY() <= arenaHeightPx) continue;
            Area outside = new Area(areas.get(i));
            outside.subtract(arena);
            double a = areaOf(outside);
            if (a > MIN_OVERLAP_PX2) {
                problems.add(describe(i) + " extends outside the arena (" + String.format(Locale.ROOT, "%.2f", a / cell2) + " cells² beyond the border)");
                bad.add(i);
            }
        }
    }

    /** Thickness of the arena walls in world pixels (also the drawn one). */
    public static final double WALL_THICKNESS_PX = 6;

    public double getArenaWidthPx()  { return arenaWidthPx; }
    public double getArenaHeightPx() { return arenaHeightPx; }

    /** Smallest overlap reported, in pixels² (below: contact or rounding). */
    private static final double MIN_OVERLAP_PX2 = 1.0;

    /** Area of a region (sum of the areas of its sub-paths, curves approximated). */
    private static double areaOf(Area area) {
        if (area.isEmpty()) return 0;
        double total = 0, sub = 0;
        double[] c = new double[6];
        double startX = 0, startY = 0, lastX = 0, lastY = 0;
        for (PathIterator it = area.getPathIterator(null, 0.1); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> { sub = 0; startX = lastX = c[0]; startY = lastY = c[1]; }
                case PathIterator.SEG_LINETO -> { sub += lastX * c[1] - c[0] * lastY; lastX = c[0]; lastY = c[1]; }
                case PathIterator.SEG_CLOSE  -> { sub += lastX * startY - startX * lastY; total += Math.abs(sub) / 2; sub = 0;
                                                  lastX = startX; lastY = startY; }
                default -> {}
            }
        }
        return total;
    }

    private String describe(int i) {
        ObjectID id = obstacles.get(i).getID();
        String kind = id.getType() == io.github.raffaelegrandi.pianura.arena.ArenaObjectType.OBSTACLE_OVAL ? "oval" : "rectangle";
        return "Obstacle #" + (i + 1) + " (" + colorName(id.getColor()) + " " + kind + " at " + fmt(id.getPos()) + ")";
    }

    private static String colorName(java.awt.Color c) {
        if (c == null) return "";
        java.awt.Color[] known = { java.awt.Color.WHITE, java.awt.Color.BLACK, java.awt.Color.RED, java.awt.Color.GREEN,
            java.awt.Color.BLUE, java.awt.Color.YELLOW, java.awt.Color.ORANGE, java.awt.Color.GRAY,
            java.awt.Color.CYAN, java.awt.Color.MAGENTA, java.awt.Color.PINK };
        String[] names = { "white", "black", "red", "green", "blue", "yellow", "orange", "gray", "cyan", "magenta", "pink" };
        for (int k = 0; k < known.length; k++) if (known[k].equals(c)) return names[k];
        return String.format(Locale.ROOT, "#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String fmt(Position p) {
        return String.format(Locale.ROOT, "(%.1f, %.1f)", p.x, p.y);
    }

}
