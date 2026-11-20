package io.github.raffaelegrandi.pianura.gui.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import io.github.raffaelegrandi.pianura.arena.ObjectID;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.panels.ArenaFloorPanel;
import io.github.raffaelegrandi.pianura.units.RobotUnit;
import io.github.raffaelegrandi.pianura.units.UnitObject;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.units.collisions.SensorArray;
import io.github.raffaelegrandi.pianura.units.sensors.FloorMap;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * View of the arena with zoom, panning and scene editing.
 *
 * Everything is drawn in world coordinates through a single {@link ViewTransform}: floor
 * (tiles of the {@link FloorMap} at the resolution suited to the zoom), walls, obstacles
 * (vectors, from the same outlines used for collisions), optional layers and robots
 * (rotation + translation).
 *
 * Mouse, always: wheel = zoom around the pointer · drag on empty space = pan · click =
 * select · double click on empty space = fit to the window · double click on a robot =
 * follow it · pointer resting near the edge = scroll (if enabled in the options).
 * Keyboard: W A S D = fly over the map (Shift = faster) · + / − = zoom · Home = fit.
 * The view does not drift away from the map: its centre always stays inside the arena.
 * Mouse, before the start or while paused: dragging an obstacle or a robot moves it
 * (Ctrl/Cmd = snap to half a cell); Shift+wheel or Q/E rotate it by 5°; the handles of the
 * selected obstacle resize it, keeping the opposite side still.
 * Elements in an invalid position are outlined in red.
 */
@SuppressWarnings("serial")
public class ArenaView extends JPanel {

    /** Link to the engine for scene editing. */
    public interface SceneEditor {
        /** Can the scene be edited now? (before the start or while paused) */
        boolean canEdit();
        /**
         * The scene changed: rebuild collisions and sensors and return the validation.
         * @param finished true on mouse release / after a rotation from the keyboard
         */
        CollisionDetector.Validation changed(boolean finished);
    }

    private static final Color OUTSIDE = new Color(58, 62, 68);
    private static final Color SELECTION = new Color(255, 200, 0);
    private static final Color INVALID = new Color(230, 30, 30);
    private static final Font HINT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final double FIT_PADDING = 12;
    private static final int TRAIL_MAX = 4000;
    private static final double ROTATION_STEP = Math.toRadians(5);
    private static final double SNAP_CELLS = 0.5;
    private static final double MIN_SIZE_CELLS = 0.5;
    private static final double HANDLE_PX = 7;          // side of a handle, screen pixels

    private final ViewTransform vt = new ViewTransform();
    private final ViewOptions options;
    private final double arenaW, arenaH;
    private volatile FloorMap floor;
    private final List<Obstacle> obstacles;
    private volatile List<Shape> outlines = List.of();
    private volatile List<RobotUnit> robots;
    private final Map<RobotUnit, ArrayDeque<Point2D>> trails = new IdentityHashMap<>();

    private SceneEditor editor;
    private volatile io.github.raffaelegrandi.pianura.comm.CommNetwork network;
    private volatile io.github.raffaelegrandi.pianura.sim.SimClock clock;
    private volatile Set<Integer> badObstacles = Set.of();
    private volatile Set<UnitObject> badRobots = Set.of();

    private boolean autoFit = true;
    private RobotUnit selected;
    private int selectedObstacle = -1;
    private Consumer<RobotUnit> selectionListener = r -> {};

    // navigation: keys held down, pointer at the edge, followed robot
    private static final double EDGE_MARGIN_PX = 18;
    private static final long EDGE_DWELL_NS = 300_000_000L;   // the pointer must rest at the edge
    private final boolean[] panKeys = new boolean[4];         // up, left, down, right
    private boolean panFast;
    private Point2D edgePointer;
    private long edgeSince;
    private volatile RobotUnit followed;
    private long lastNavTick;
    private final javax.swing.Timer navTimer = new javax.swing.Timer(16, e -> navTick());
    /** Tests switch it off and call navigate() themselves: the real timer would move the view concurrently. */
    boolean navTimerEnabled = true;
    private final Minimap minimap = new Minimap();
    private boolean minimapDrag;
    private Runnable viewListener = () -> {};

    /**
     * @param floor     floor (the same one read by the floor sensors, if there is an engine)
     * @param obstacles the scenario's obstacles (the same objects the engine uses)
     */
    public ArenaView(FloorMap floor, double arenaWidthPx, double arenaHeightPx,
                     List<Obstacle> obstacles, List<? extends RobotUnit> robots, ViewOptions options) {
        this.floor = floor;
        this.arenaW = arenaWidthPx;
        this.arenaH = arenaHeightPx;
        this.obstacles = obstacles;
        this.robots = new ArrayList<>(robots);
        this.options = options;
        refreshOutlines();
        setBackground(OUTSIDE);
        setOpaque(true);
        installMouse();
        installKeys();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { if (autoFit) fitToWindow(); }
        });
    }

    // ------------------------------------------------------------------
    //  API
    // ------------------------------------------------------------------

    public ViewTransform getTransform() { return vt; }

    public void setFloor(FloorMap f) { floor = f; repaint(); }

    public FloorMap getFloor() { return floor; }

    /** World rectangle (pixels) visible in the view now. */
    public Rectangle2D visibleWorld() {
        Point2D a = vt.toWorld(0, 0), b = vt.toWorld(getWidth(), getHeight());
        return new Rectangle2D.Double(a.getX(), a.getY(), b.getX() - a.getX(), b.getY() - a.getY());
    }

    public void setRobots(List<? extends RobotUnit> list) { robots = new ArrayList<>(list); trails.clear(); repaint(); }

    public void setSceneEditor(SceneEditor e) { editor = e; }

    /** Radio network and clock, for the communication layers. */
    public void setNetwork(io.github.raffaelegrandi.pianura.comm.CommNetwork net, io.github.raffaelegrandi.pianura.sim.SimClock clk) { network = net; clock = clk; }

    /** Highlights invalid elements in red. */
    public void setValidation(CollisionDetector.Validation v) {
        badObstacles = v != null ? v.badObstacles() : Set.of();
        badRobots = v != null ? v.badRobots() : Set.of();
        repaint();
    }

    public RobotUnit getSelected() { return selected; }

    public int getSelectedObstacle() { return selectedObstacle; }

    public void setSelectionListener(Consumer<RobotUnit> l) { selectionListener = l != null ? l : r -> {}; }

    /** Called when zoom or panning change (to refresh the panel). */
    public void setViewListener(Runnable l) { viewListener = l != null ? l : () -> {}; }

    private boolean editable() { return editor != null && editor.canEdit(); }

    /** World rectangle to frame: arena + walls. */
    private Rectangle2D worldBounds() {
        double t = CollisionDetector.WALL_THICKNESS_PX;
        return new Rectangle2D.Double(-t, -t, arenaW + 2 * t, arenaH + 2 * t);
    }

    public void fitToWindow() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        vt.fit(worldBounds(), getWidth(), getHeight(), FIT_PADDING);
        autoFit = true;
        followed = null;
        viewListener.run();
        repaint();
    }

    /** Zoom around the centre of the view. */
    public void zoom(double factor) {
        vt.zoomAt(factor, getWidth() / 2.0, getHeight() / 2.0);
        clampView();
        autoFit = false;
        viewListener.run();
        repaint();
    }

    /** 1:1 scale (one world pixel = one screen pixel), centred on the centre of the view. */
    public void actualSize() {
        vt.setScaleAt(1.0, getWidth() / 2.0, getHeight() / 2.0);
        autoFit = false;
        viewListener.run();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        double s = Math.min(1.0, Math.min(900 / arenaW, 680 / arenaH));
        return new Dimension((int) Math.max(360, arenaW * s + 2 * FIT_PADDING), (int) Math.max(300, arenaH * s + 2 * FIT_PADDING));
    }

    public void select(RobotUnit r) {
        selected = r;
        if (r != null) selectedObstacle = -1;
        selectionListener.accept(r);
        repaint();
    }

    private void selectObstacle(int i) {
        selectedObstacle = i;
        if (i >= 0) { selected = null; selectionListener.accept(null); }
        repaint();
    }

    // ------------------------------------------------------------------
    //  Scene editing
    // ------------------------------------------------------------------

    /**
     * The obstacles list was replaced (scene opened from file): forget the selection and the
     * trails, recompute the outlines. On the EDT.
     */
    public void obstaclesReplaced() {
        selectedObstacle = -1;
        badObstacles = Set.of();
        badRobots = Set.of();
        trails.clear();
        refreshOutlines();
        repaint();
    }

    private void refreshOutlines() {
        List<Shape> list = new ArrayList<>(obstacles.size());
        for (Obstacle o : obstacles) list.add(ArenaFloorPanel.outlineOf(o));
        outlines = list;
    }

    private int obstacleAt(Point2D w) {
        List<Shape> ol = outlines;
        for (int i = ol.size() - 1; i >= 0; i--) {   // the last one drawn is the one on top
            if (ol.get(i) != null && ol.get(i).contains(w)) return i;
        }
        return -1;
    }

    private RobotUnit robotAt(Point2D w) {
        RobotUnit best = null;
        double bestD = Const.unitRadius * 1.15;
        for (RobotUnit r : robots) {
            Position p = r.getPosition();
            double d = Math.hypot(p.x * Const.cellSize - w.getX(), p.y * Const.cellSize - w.getY());
            if (d < bestD) { bestD = d; best = r; }
        }
        return best;
    }

    /** Position of the centre, in cells, of the dragged element. */
    private static Position centerOf(Object target) {
        if (target instanceof RobotUnit r) return r.getPosition();
        return ((Obstacle) target).getID().getPos();
    }

    private void moveTo(Object target, double xCells, double yCells) {
        if (target instanceof RobotUnit r) {
            Position p = r.getPosition();
            r.getUnitObject().setPos(new Position(xCells, yCells, p.theta));
        } else {
            ObjectID id = ((Obstacle) target).getID();
            id.setPos(new Position(xCells, yCells, id.getAngle()));
        }
    }

    private void rotate(Object target, double delta) {
        if (target instanceof RobotUnit r) {
            Position p = r.getPosition();
            r.getUnitObject().setPos(new Position(p.x, p.y, normalize(p.theta + delta)));
        } else {
            ObjectID id = ((Obstacle) target).getID();
            Position p = id.getPos();
            id.setPos(new Position(p.x, p.y, normalize(id.getAngle() + delta)));
        }
    }

    private static double normalize(double a) {
        double t = a % (2 * Math.PI);
        return t < 0 ? t + 2 * Math.PI : t;
    }

    private void notifyChanged(boolean finished) {
        refreshOutlines();
        if (editor != null) setValidation(editor.changed(finished));
        repaint();
    }

    /** Selected element to rotate with Q/E, or null. */
    private Object selectedTarget() {
        if (selected != null) return selected;
        if (selectedObstacle >= 0 && selectedObstacle < obstacles.size()) return obstacles.get(selectedObstacle);
        return null;
    }

    // ------------------------------------------------------------------
    //  Resize handles
    // ------------------------------------------------------------------

    /** Positions of the 8 handles in the obstacle's local frame, in half-size units. */
    private static final int[][] HANDLES = { {-1,-1}, {0,-1}, {1,-1}, {1,0}, {1,1}, {0,1}, {-1,1}, {-1,0} };

    /** Centre of handle k of obstacle i, in world pixels. */
    private Point2D handlePos(int i, int k) {
        ObjectID id = obstacles.get(i).getID();
        Position p = id.getPos();
        double hw = id.getWidth() * Const.cellSize / 2, hh = id.getHeight() * Const.cellSize / 2;
        double lx = HANDLES[k][0] * hw, ly = HANDLES[k][1] * hh;
        double c = Math.cos(id.getAngle()), s = Math.sin(id.getAngle());
        return new Point2D.Double(p.x * Const.cellSize + c * lx - s * ly, p.y * Const.cellSize + s * lx + c * ly);
    }

    /** Handle under the screen point, or -1 (selected obstacle only). */
    private int handleAt(double sx, double sy) {
        int i = selectedObstacle;
        if (i < 0 || i >= obstacles.size()) return -1;
        for (int k = 0; k < HANDLES.length; k++) {
            Point2D h = vt.toScreen(handlePos(i, k).getX(), handlePos(i, k).getY());
            if (Math.abs(h.getX() - sx) <= HANDLE_PX && Math.abs(h.getY() - sy) <= HANDLE_PX) return k;
        }
        return -1;
    }

    /** Resize cursor oriented like the handle (rotation included). */
    private Cursor resizeCursor(int i, int k) {
        ObjectID id = obstacles.get(i).getID();
        double a = Math.atan2(HANDLES[k][1], HANDLES[k][0]) + id.getAngle();
        int oct = (int) Math.round(normalize(a) / (Math.PI / 4)) % 8;   // 0 = east, then clockwise (y down)
        int[] cursors = { Cursor.E_RESIZE_CURSOR, Cursor.SE_RESIZE_CURSOR, Cursor.S_RESIZE_CURSOR, Cursor.SW_RESIZE_CURSOR,
                          Cursor.W_RESIZE_CURSOR, Cursor.NW_RESIZE_CURSOR, Cursor.N_RESIZE_CURSOR, Cursor.NE_RESIZE_CURSOR };
        return Cursor.getPredefinedCursor(cursors[oct]);
    }

    /** Initial state of a resize: the side opposite the handle stays still. */
    private record ResizeStart(int obstacle, int handle, double cx, double cy, double angle, double w, double h) {}

    private ResizeStart startResize(int i, int k) {
        ObjectID id = obstacles.get(i).getID();
        Position p = id.getPos();
        return new ResizeStart(i, k, p.x * Const.cellSize, p.y * Const.cellSize, id.getAngle(),
            id.getWidth() * Const.cellSize, id.getHeight() * Const.cellSize);
    }

    private void applyResize(ResizeStart r, Point2D mouseWorld, boolean snap) {
        double c = Math.cos(r.angle()), s = Math.sin(r.angle());
        double dx = mouseWorld.getX() - r.cx(), dy = mouseWorld.getY() - r.cy();
        double lx = c * dx + s * dy, ly = -s * dx + c * dy;   // mouse in the initial local frame
        int sx = HANDLES[r.handle()][0], sy = HANDLES[r.handle()][1];
        double min = MIN_SIZE_CELLS * Const.cellSize, snapPx = SNAP_CELLS * Const.cellSize;
        double w = r.w(), h = r.h(), cxl = 0, cyl = 0;
        if (sx != 0) {
            double ox = -sx * r.w() / 2;                       // opposite side, still
            w = Math.max(min, sx * (lx - ox));
            if (snap) w = Math.max(min, Math.round(w / snapPx) * snapPx);
            cxl = ox + sx * w / 2;
        }
        if (sy != 0) {
            double oy = -sy * r.h() / 2;
            h = Math.max(min, sy * (ly - oy));
            if (snap) h = Math.max(min, Math.round(h / snapPx) * snapPx);
            cyl = oy + sy * h / 2;
        }
        double ncx = r.cx() + c * cxl - s * cyl, ncy = r.cy() + s * cxl + c * cyl;
        ObjectID id = obstacles.get(r.obstacle()).getID();
        id.setSize(w / Const.cellSize, h / Const.cellSize);
        id.setPos(new Position(ncx / Const.cellSize, ncy / Const.cellSize, r.angle()));
    }

    // ------------------------------------------------------------------
    //  Mouse and keyboard
    // ------------------------------------------------------------------

    private void installMouse() {
        MouseAdapter m = new MouseAdapter() {
            private Point2D last;
            private boolean dragged;
            private Object dragTarget;        // dragged obstacle or robot, null = panning the view
            private double grabDx, grabDy;    // distance (world pixels) between the grabbed point and the centre
            private ResizeStart resizing;     // resize in progress with a handle

            @Override public void mousePressed(MouseEvent e) {
                edgePointer = null;
                if (e.getButton() == MouseEvent.BUTTON1 && overMinimap(e.getX(), e.getY())) {
                    minimapDrag = true;
                    centerFromMinimap(e.getX(), e.getY());
                    return;
                }
                last = e.getPoint();
                dragged = false;
                dragTarget = null;
                resizing = null;
                if (e.getButton() == MouseEvent.BUTTON1 && editable()) {
                    int k = handleAt(e.getX(), e.getY());
                    if (k >= 0) { resizing = startResize(selectedObstacle, k); return; }
                    Point2D w = vt.toWorld(e.getX(), e.getY());
                    RobotUnit r = robotAt(w);
                    int o = r == null ? obstacleAt(w) : -1;
                    dragTarget = r != null ? r : (o >= 0 ? obstacles.get(o) : null);
                    if (dragTarget != null) {
                        Position c = centerOf(dragTarget);
                        grabDx = w.getX() - c.x * Const.cellSize;
                        grabDy = w.getY() - c.y * Const.cellSize;
                        if (r != null) select(r); else selectObstacle(o);
                    }
                }
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (minimapDrag) { centerFromMinimap(e.getX(), e.getY()); return; }
                if (last == null) return;
                dragged = true;
                boolean snap = (e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
                if (resizing != null && editable()) {
                    applyResize(resizing, vt.toWorld(e.getX(), e.getY()), snap);
                    notifyChanged(false);
                } else if (dragTarget != null && editable()) {
                    Point2D w = vt.toWorld(e.getX(), e.getY());
                    double x = (w.getX() - grabDx) / Const.cellSize, y = (w.getY() - grabDy) / Const.cellSize;
                    if (snap) {
                        x = Math.round(x / SNAP_CELLS) * SNAP_CELLS;
                        y = Math.round(y / SNAP_CELLS) * SNAP_CELLS;
                    }
                    moveTo(dragTarget, x, y);
                    notifyChanged(false);
                } else {
                    vt.pan(e.getX() - last.getX(), e.getY() - last.getY());
                    clampView();
                    followed = null;                 // panning by hand stops following
                    autoFit = false;
                    viewListener.run();
                    repaint();
                }
                last = e.getPoint();
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (minimapDrag) { minimapDrag = false; return; }
                if (dragged && (dragTarget != null || resizing != null)) notifyChanged(true);
                if (!dragged && resizing == null && e.getButton() == MouseEvent.BUTTON1) {
                    Point2D w = vt.toWorld(e.getX(), e.getY());
                    RobotUnit r = robotAt(w);
                    int o = r == null ? obstacleAt(w) : -1;
                    if (r != null) { select(r); if (e.getClickCount() >= 2) follow(r); }
                    else if (o >= 0) selectObstacle(o);
                    else if (e.getClickCount() >= 2) fitToWindow();
                    else { select(null); selectObstacle(-1); }
                }
                last = null;
                dragTarget = null;
                resizing = null;
            }

            @Override public void mouseExited(MouseEvent e) { edgePointer = null; }

            @Override public void mouseMoved(MouseEvent e) {
                if (overMinimap(e.getX(), e.getY())) {
                    edgePointer = null;
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    return;
                }
                trackEdge(e.getX(), e.getY());
                boolean over = false;
                int k = editable() ? handleAt(e.getX(), e.getY()) : -1;
                if (k >= 0) { setCursor(resizeCursor(selectedObstacle, k)); return; }
                if (editable()) {
                    Point2D w = vt.toWorld(e.getX(), e.getY());
                    over = robotAt(w) != null || obstacleAt(w) >= 0;
                }
                setCursor(Cursor.getPredefinedCursor(over ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isShiftDown() && editable()) {
                    Point2D w = vt.toWorld(e.getX(), e.getY());
                    RobotUnit r = robotAt(w);
                    int o = r == null ? obstacleAt(w) : -1;
                    Object t = r != null ? r : (o >= 0 ? obstacles.get(o) : selectedTarget());
                    if (t != null) {
                        rotate(t, Math.signum(e.getPreciseWheelRotation()) * ROTATION_STEP);
                        notifyChanged(true);
                    }
                    return;
                }
                vt.zoomAt(Math.pow(1.15, -e.getPreciseWheelRotation()), e.getX(), e.getY());
                clampView();
                autoFit = false;
                viewListener.run();
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
    }

    private void installKeys() {
        bindRotation(KeyEvent.VK_Q, "pianura-rot-ccw", -ROTATION_STEP);
        bindRotation(KeyEvent.VK_E, "pianura-rot-cw", ROTATION_STEP);
        bindPan(KeyEvent.VK_W, 0);
        bindPan(KeyEvent.VK_A, 1);
        bindPan(KeyEvent.VK_S, 2);
        bindPan(KeyEvent.VK_D, 3);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "pianura-zoom-in", () -> zoom(1.25));
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), "pianura-zoom-in", () -> zoom(1.25));
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.SHIFT_DOWN_MASK), "pianura-zoom-in", () -> zoom(1.25));
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "pianura-zoom-in", () -> zoom(1.25));
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "pianura-zoom-out", () -> zoom(1 / 1.25));
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "pianura-zoom-out", () -> zoom(1 / 1.25));
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "pianura-fit", this::fitToWindow);
    }

    private void bindKey(KeyStroke ks, String name, Runnable r) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        });
    }

    /** W A S D: held down = the view moves while the key stays down (Shift too). */
    private void bindPan(int key, int dir) {
        for (int mod : new int[] { 0, InputEvent.SHIFT_DOWN_MASK }) {
            boolean fast = mod != 0;
            bindKey(KeyStroke.getKeyStroke(key, mod, false), "pianura-pan-" + dir + "-on-" + fast,
                () -> setPanKey(dir, true, fast));
            bindKey(KeyStroke.getKeyStroke(key, mod, true), "pianura-pan-" + dir + "-off-" + fast,
                () -> setPanKey(dir, false, fast));
        }
    }

    // ------------------------------------------------------------------
    //  Navigation
    // ------------------------------------------------------------------

    /** Follows the robot (null = stop): the view keeps it in the centre while it moves. */
    public void follow(RobotUnit r) {
        followed = r;
        if (r != null) { autoFit = false; startNav(); }
        viewListener.run();
    }

    public RobotUnit getFollowed() { return followed; }

    /** Brings the world point (pixels) to the centre of the view, without changing the zoom. */
    public void centerOn(double wx, double wy) {
        vt.centerOn(wx, wy, getWidth(), getHeight());
        clampView();
        followed = null;
        autoFit = false;
        viewListener.run();
        repaint();
    }

    // ------------------------------------------------------------------
    //  Minimap
    // ------------------------------------------------------------------

    /** The minimap shows when it is enabled and the arena does not already fit in the view. */
    public boolean minimapVisible() {
        if (!options.minimap || getWidth() < 3 * Minimap.MAX_SIDE / 2 || getHeight() < Minimap.MAX_SIDE) return false;
        Rectangle2D v = visibleWorld();
        return !(v.getMinX() <= 0 && v.getMinY() <= 0 && v.getMaxX() >= arenaW && v.getMaxY() >= arenaH);
    }

    /** Screen rectangle of the minimap. */
    public java.awt.Rectangle minimapBounds() { return Minimap.bounds(getWidth(), getHeight(), arenaW, arenaH); }

    private boolean overMinimap(double x, double y) {
        if (!minimapVisible()) return false;
        java.awt.Rectangle b = minimapBounds();
        b.grow(4, 4);
        return b.contains(x, y);
    }

    /** Click or drag on the minimap: the view goes there (same zoom). */
    void centerFromMinimap(double x, double y) {
        Point2D w = Minimap.toWorld(minimapBounds(), x, y, arenaW, arenaH);
        centerOn(w.getX(), w.getY());
    }

    private void paintMinimap(Graphics2D g, List<RobotUnit> rs, Position[] pos) {
        List<Point2D> pts = new ArrayList<>(pos.length);
        int hl = -1;
        RobotUnit mark = followed != null ? followed : selected;
        for (int i = 0; i < pos.length; i++) {
            pts.add(new Point2D.Double(pos[i].x * Const.cellSize, pos[i].y * Const.cellSize));
            if (rs.get(i) == mark) hl = i;
        }
        minimap.paint(g, minimapBounds(), arenaW, arenaH, floor, outlines, pts, hl, visibleWorld());
    }

    /** The centre of the view stays inside the arena. */
    private void clampView() {
        if (getWidth() > 0 && getHeight() > 0) vt.clampCenter(new Rectangle2D.Double(0, 0, arenaW, arenaH), getWidth(), getHeight());
    }

    private void trackEdge(double x, double y) {
        int[] d = ViewTransform.edgeDirection(x, y, getWidth(), getHeight(), EDGE_MARGIN_PX);
        if (d[0] == 0 && d[1] == 0) { edgePointer = null; return; }
        Point2D p = new Point2D.Double(x, y);
        if (edgePointer == null) edgeSince = System.nanoTime();
        edgePointer = p;
        if (options.edgeScroll) startNav();
    }

    private void startNav() {
        if (navTimerEnabled && !navTimer.isRunning()) { lastNavTick = 0; navTimer.start(); }
    }

    /** One navigation step (~60 per second) while something moves the view. */
    private void navTick() {
        long now = System.nanoTime();
        double dt = lastNavTick == 0 ? 0.016 : Math.min(0.1, (now - lastNavTick) / 1e9);
        lastNavTick = now;
        if (!navigate(now, dt)) { navTimer.stop(); lastNavTick = 0; }
    }

    /** Panning key W/A/S/D (0 = up, 1 = left, 2 = down, 3 = right) pressed or released. */
    void setPanKey(int dir, boolean down, boolean fast) {
        panKeys[dir] = down;
        panFast = fast;
        if (down) startNav();
    }

    /**
     * Moves the view by {@code dt} seconds (keys, edge, followed robot).
     * @return true if something is still moving it
     */
    boolean navigate(long now, double dt) {
        int kx = (panKeys[3] ? 1 : 0) - (panKeys[1] ? 1 : 0);
        int ky = (panKeys[2] ? 1 : 0) - (panKeys[0] ? 1 : 0);
        boolean keys = kx != 0 || ky != 0;
        boolean fast = keys && panFast;
        Point2D ep = edgePointer;
        boolean edge = false;
        if (!keys && ep != null && options.edgeScroll && now - edgeSince >= EDGE_DWELL_NS) {
            int[] d = ViewTransform.edgeDirection(ep.getX(), ep.getY(), getWidth(), getHeight(), EDGE_MARGIN_PX);
            kx = d[0];
            ky = d[1];
            edge = kx != 0 || ky != 0;
        }
        boolean moved = false;
        if (keys || edge) {
            // speed in screen pixels per second: crossing the view in little more than a second
            double speed = 0.8 * Math.max(getWidth(), getHeight()) * (fast ? 3 : 1);
            double norm = (kx != 0 && ky != 0) ? Math.sqrt(0.5) : 1;
            vt.pan(-kx * speed * norm * dt, -ky * speed * norm * dt);
            followed = null;
            moved = true;
        }
        RobotUnit f = followed;
        if (f != null) {
            Position p = f.getPosition();
            vt.moveCenterToward(p.x * Const.cellSize, p.y * Const.cellSize, getWidth(), getHeight(), 1 - Math.exp(-8 * dt));
            moved = true;
        }
        if (moved) {
            clampView();
            autoFit = false;
            viewListener.run();
            repaint();
        }
        boolean waitingEdge = ep != null && options.edgeScroll;
        return keys || edge || f != null || waitingEdge;
    }

    private void bindRotation(int key, String name, double delta) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                Object t = selectedTarget();
                if (t != null && editable()) { rotate(t, delta); notifyChanged(true); }
            }
        });
    }

    // ------------------------------------------------------------------
    //  Drawing
    // ------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        AffineTransform screen = g.getTransform();
        g.transform(vt.asAffine());
        double px = 1 / vt.getScale();   // one screen pixel in world units

        // positions read once per frame: every layer draws the same instant
        List<RobotUnit> rs = robots;
        Position[] pos = new Position[rs.size()];
        for (int i = 0; i < pos.length; i++) pos[i] = rs.get(i).getPosition();
        recordTrails(rs, pos);

        paintFloor(g);
        if (options.grid) paintGrid(g, px);
        paintWalls(g);
        paintObstacles(g, px);
        if (options.commRange) paintCommRange(g, rs, pos, px);
        if (options.commLinks) paintCommLinks(g, rs, pos, px);
        if (options.trails) paintTrails(g, rs, px);
        if (options.sensorRange) paintSensorRanges(g, rs, pos, px);
        if (options.sensorRays) paintRays(g, rs, pos, px);
        List<RobotUnit> drawers = options.drawings ? drawingRobots(rs) : List.of();
        for (RobotUnit r : drawers) paintDrawings(g, r, px);
        for (int i = 0; i < rs.size(); i++) paintRobot(g, rs.get(i), pos[i], px);
        if (options.floorSensors) paintFloorSensors(g, rs, px);

        g.setTransform(screen);
        for (RobotUnit r : drawers) paintDrawingTexts(g, r);
        if (options.ids) paintLabels(g, rs, pos);
        if (editable()) paintEditHint(g);
        if (minimapVisible()) paintMinimap(g, rs, pos);
        g.dispose();
    }

    /**
     * Floor: first the overview (always ready), then, if the zoom calls for it, the visible tiles
     * of the suitable level. Tiles not ready yet are drawn in the background and appear at the
     * next frame: meanwhile the overview shows.
     */
    private void paintFloor(Graphics2D g) {
        FloorMap fm = floor;
        if (fm == null) return;
        int top = fm.levels() - 1;
        double so = fm.levelScale(top);
        g.drawImage(fm.overview(), AffineTransform.getScaleInstance(1 / so, 1 / so), null);
        int k = fm.levelFor(vt.getScale());
        fm.cancelPending();                       // only this frame's tiles are needed
        if (k == top) return;
        double s = fm.levelScale(k);
        Rectangle2D v = visibleWorld();
        fm.forEachTile(k, v.getMinX(), v.getMinY(), v.getMaxX(), v.getMaxY(), t -> {
            BufferedImage img = fm.tileIfReady(k, t[0], t[1], this::repaint);
            if (img == null) return;
            AffineTransform at = AffineTransform.getTranslateInstance(t[0] * FloorMap.TILE / s, t[1] * FloorMap.TILE / s);
            at.scale(1 / s, 1 / s);
            g.drawImage(img, at, null);
        });
    }

    private void paintWalls(Graphics2D g) {
        double t = CollisionDetector.WALL_THICKNESS_PX;
        Area walls = new Area(new Rectangle2D.Double(-t, -t, arenaW + 2 * t, arenaH + 2 * t));
        walls.subtract(new Area(new Rectangle2D.Double(0, 0, arenaW, arenaH)));
        g.setColor(Const.arenaBorderColor);
        g.fill(walls);
    }

    private void paintObstacles(Graphics2D g, double px) {
        List<Shape> ol = outlines;
        Set<Integer> bad = badObstacles;
        Rectangle2D v = visibleWorld();
        for (int i = 0; i < ol.size() && i < obstacles.size(); i++) {
            Shape o = ol.get(i);
            if (o != null && !o.getBounds2D().intersects(v)) continue;   // outside the view
            ArenaFloorPanel.paintObstacle(g, obstacles.get(i), o);
        }
        BasicStroke dashed = new BasicStroke((float) (2.5 * px), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10f, new float[] { (float) (7 * px), (float) (4 * px) }, 0f);
        g.setStroke(dashed);
        for (int i : bad) {
            if (i < ol.size() && ol.get(i) != null) { g.setColor(INVALID); g.draw(ol.get(i)); }
        }
        int s = selectedObstacle;
        if (s >= 0 && s < ol.size() && ol.get(s) != null) {
            g.setColor(SELECTION);
            g.draw(ol.get(s));
            if (editable()) paintHandles(g, s, px);
        }
    }

    private void paintHandles(Graphics2D g, int i, double px) {
        double hs = HANDLE_PX * px;   // constant size on screen
        g.setStroke(new BasicStroke((float) px));
        for (int k = 0; k < HANDLES.length; k++) {
            Point2D h = handlePos(i, k);
            Rectangle2D r = new Rectangle2D.Double(h.getX() - hs / 2, h.getY() - hs / 2, hs, hs);
            g.setColor(Color.WHITE);
            g.fill(r);
            g.setColor(new Color(40, 40, 40));
            g.draw(r);
        }
    }

    /** Colour of a connected component (same palette as the connectivity window). */
    public static Color componentColor(int c) {
        return Color.getHSBColor((0.58f + c * 0.381966f) % 1f, 0.75f, 0.85f);
    }

    private void paintCommRange(Graphics2D g, List<RobotUnit> rs, Position[] pos, double px) {
        io.github.raffaelegrandi.pianura.comm.CommNetwork net = network;
        if (net == null) return;
        g.setColor(new Color(40, 120, 220, 130));
        g.setStroke(new BasicStroke((float) (1.2 * px), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[] { (float) (6 * px), (float) (4 * px) }, 0f));
        for (int i = 0; i < rs.size(); i++) {
            if (!rs.get(i).isActive()) continue;
            double r = net.rangeOf(rs.get(i)) * Const.cellSize;
            double cx = pos[i].x * Const.cellSize, cy = pos[i].y * Const.cellSize;
            g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        }
    }

    /** Links of the last network step, and a dot travelling along the link for each recent delivery. */
    private void paintCommLinks(Graphics2D g, List<RobotUnit> rs, Position[] pos, double px) {
        io.github.raffaelegrandi.pianura.comm.CommNetwork net = network;
        if (net == null) return;
        java.util.Map<Integer, Point2D> at = new java.util.HashMap<>();
        for (int i = 0; i < rs.size(); i++) at.put(rs.get(i).getIDnum(), new Point2D.Double(pos[i].x * Const.cellSize, pos[i].y * Const.cellSize));
        io.github.raffaelegrandi.pianura.comm.ConnectivityGraph gr = net.getGraph();
        BasicStroke solid = new BasicStroke((float) (1.8 * px), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        BasicStroke dashed = new BasicStroke((float) (1.5 * px), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[] { (float) (5 * px), (float) (4 * px) }, 0f);
        for (io.github.raffaelegrandi.pianura.comm.ConnectivityGraph.Link l : gr.links()) {
            Point2D a = at.get(gr.id(l.a())), b = at.get(gr.id(l.b()));
            if (a == null || b == null) continue;
            Color c = componentColor(gr.component(l.a()));
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 200));
            if (l.bidirectional()) {
                g.setStroke(solid);
                g.draw(new Line2D.Double(a, b));
            } else {
                Point2D from = l.aToB() ? a : b, to = l.aToB() ? b : a;
                g.setStroke(dashed);
                g.draw(new Line2D.Double(from, to));
                paintArrowHead(g, from, to, Const.unitRadius + 2, 7 * px);
            }
        }
        io.github.raffaelegrandi.pianura.sim.SimClock clk = clock;
        if (clk == null) return;
        double now = clk.now(), window = 0.3;
        for (io.github.raffaelegrandi.pianura.comm.CommNetwork.Delivery d : net.getRecentDeliveries(now - window)) {
            Point2D a = at.get(d.from()), b = at.get(d.to());
            if (a == null || b == null) continue;
            double f = Math.max(0, Math.min(1, (now - d.deliveredAt()) / window));
            double x = a.getX() + (b.getX() - a.getX()) * f, y = a.getY() + (b.getY() - a.getY()) * f;
            double r = Math.max(2.2, 3.5 * px);
            g.setColor(new Color(255, 255, 255, (int) (235 * (1 - f * 0.6))));
            g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
            g.setColor(new Color(30, 90, 200, (int) (235 * (1 - f * 0.6))));
            g.setStroke(new BasicStroke((float) (1.2 * px)));
            g.draw(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
    }

    private static void paintArrowHead(Graphics2D g, Point2D from, Point2D to, double back, double size) {
        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), d = Math.hypot(dx, dy);
        if (d < back + size) return;
        double ux = dx / d, uy = dy / d;
        double tx = to.getX() - ux * back, ty = to.getY() - uy * back;
        java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
        p.moveTo(tx, ty);
        p.lineTo(tx - ux * size - uy * size * 0.5, ty - uy * size + ux * size * 0.5);
        p.lineTo(tx - ux * size + uy * size * 0.5, ty - uy * size - ux * size * 0.5);
        p.closePath();
        g.fill(p);
    }

    private void paintGrid(Graphics2D g, double px) {
        double c = Const.cellSize;
        if (c / px < 5) return;   // cells too small on screen: the grid would be unreadable
        g.setColor(new Color(0, 0, 0, 45));
        g.setStroke(new BasicStroke((float) px));
        Rectangle2D v = visibleWorld();   // visible lines only
        double y0 = Math.max(0, v.getMinY()), y1 = Math.min(arenaH, v.getMaxY());
        double x0 = Math.max(0, v.getMinX()), x1 = Math.min(arenaW, v.getMaxX());
        for (double x = Math.max(1, Math.ceil(x0 / c)) * c; x < x1; x += c) g.draw(new Line2D.Double(x, y0, x, y1));
        for (double y = Math.max(1, Math.ceil(y0 / c)) * c; y < y1; y += c) g.draw(new Line2D.Double(x0, y, x1, y));
    }

    private void recordTrails(List<RobotUnit> rs, Position[] pos) {
        for (int i = 0; i < rs.size(); i++) {
            ArrayDeque<Point2D> tr = trails.computeIfAbsent(rs.get(i), k -> new ArrayDeque<>());
            Point2D p = new Point2D.Double(pos[i].x * Const.cellSize, pos[i].y * Const.cellSize);
            Point2D lastP = tr.peekLast();
            if (lastP != null && lastP.distance(p) > 3 * Const.cellSize) tr.clear();   // robot moved by hand
            if (lastP == null || lastP.distance(p) >= 1.5) {
                tr.addLast(p);
                if (tr.size() > TRAIL_MAX) tr.removeFirst();
            }
        }
    }

    private void paintTrails(Graphics2D g, List<RobotUnit> rs, double px) {
        g.setStroke(new BasicStroke((float) (1.5 * px), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (RobotUnit r : rs) {
            ArrayDeque<Point2D> tr = trails.get(r);
            if (tr == null || tr.size() < 2) continue;
            Path2D path = new Path2D.Double();
            boolean first = true;
            for (Point2D p : tr) {
                if (first) { path.moveTo(p.getX(), p.getY()); first = false; } else path.lineTo(p.getX(), p.getY());
            }
            Color c = trailColor(r);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 150));
            g.draw(path);
        }
    }

    private static Color trailColor(RobotUnit r) {
        Color c = r.getUnitObject().getID().getColor();
        if (c == null || (c.getRed() + c.getGreen() + c.getBlue()) < 120) {
            // black robots: a distinct color per robot, so the trails can be told apart
            return Color.getHSBColor((r.getIDnum() * 0.137f) % 1f, 0.75f, 0.85f);
        }
        return c;
    }

    private void paintSensorRanges(Graphics2D g, List<RobotUnit> rs, Position[] pos, double px) {
        g.setColor(Const.sensorRangeColor);
        g.setStroke(new BasicStroke((float) px));
        for (int i = 0; i < rs.size(); i++) {
            double r = Const.unitRadius + Const.cellSize * rs.get(i).getUnitObject().getSensorRange();
            double cx = pos[i].x * Const.cellSize, cy = pos[i].y * Const.cellSize;
            g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        }
    }

    private void paintRays(Graphics2D g, List<RobotUnit> rs, Position[] pos, double px) {
        g.setStroke(new BasicStroke((float) px));
        Color none = new Color(90, 90, 90, 90), hit = new Color(255, 140, 0), bump = new Color(220, 20, 20);
        for (int i = 0; i < rs.size(); i++) {
            UnitObject u = rs.get(i).getUnitObject();
            SensorArray ir = u.getIrSensors(), bs = u.getBumpSensors();
            double cx = pos[i].x * Const.cellSize, cy = pos[i].y * Const.cellSize;
            double max = Const.unitRadius + Const.cellSize * u.getSensorRange();
            for (int k = 0; k < ir.length(); k++) {
                double a = pos[i].theta + ir.getSensorAngle(k);
                double sx = cx + Const.unitRadius * Math.cos(a), sy = cy + Const.unitRadius * Math.sin(a);
                Point2D cp = ir.getCheckPoint(k);
                if (cp != null) {
                    double ex = cp.getX() * Const.cellSize, ey = cp.getY() * Const.cellSize;
                    g.setColor(bs.getSensorValue(k) != bs.getInitValue() ? bump : hit);
                    g.draw(new Line2D.Double(sx, sy, ex, ey));
                    double d = 2.5 * px;
                    g.fill(new Ellipse2D.Double(ex - d, ey - d, 2 * d, 2 * d));
                } else {
                    g.setColor(none);
                    g.draw(new Line2D.Double(sx, sy, cx + max * Math.cos(a), cy + max * Math.sin(a)));
                }
            }
        }
    }

    // ------------------------------------------------------------------ debug drawings (robot.viz)

    private List<RobotUnit> drawingRobots(List<RobotUnit> rs) {
        if (!options.drawingsSelectedOnly) return rs;
        RobotUnit s = selected;
        return s == null ? List.of() : List.of(s);
    }

    private static final Font DRAWING_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

    /** Shapes of every layer of the robot, in world coordinates (text apart: see paintDrawingTexts). */
    private void paintDrawings(Graphics2D g, RobotUnit r, double px) {
        double k = Const.cellSize;
        for (List<io.github.raffaelegrandi.pianura.viz.Shape> layer : r.getDrawings().values()) {
            for (io.github.raffaelegrandi.pianura.viz.Shape s : layer) {
                g.setColor(s.color());
                g.setStroke(new BasicStroke((float) (s.width() * px), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (s.kind()) {
                    case POINT -> {
                        double d = Math.max(2.5 * px, s.width() * 1.5 * px);
                        g.fill(new Ellipse2D.Double(s.c(0) * k - d, s.c(1) * k - d, 2 * d, 2 * d));
                    }
                    case LINE -> g.draw(new Line2D.Double(s.c(0) * k, s.c(1) * k, s.c(2) * k, s.c(3) * k));
                    case ARROW -> {
                        Point2D a = new Point2D.Double(s.c(0) * k, s.c(1) * k), b = new Point2D.Double(s.c(2) * k, s.c(3) * k);
                        double head = Math.max(7, 4 * s.width()) * px;
                        double len = a.distance(b);
                        if (len > head) {   // the line stops at the base of the head, so the tip stays sharp
                            double f = (len - head * 0.8) / len;
                            g.draw(new Line2D.Double(a.getX(), a.getY(), a.getX() + (b.getX() - a.getX()) * f, a.getY() + (b.getY() - a.getY()) * f));
                        }
                        paintArrowHead(g, a, b, 0, Math.min(head, len));
                    }
                    case CIRCLE -> g.draw(new Ellipse2D.Double((s.c(0) - s.c(2)) * k, (s.c(1) - s.c(2)) * k, 2 * s.c(2) * k, 2 * s.c(2) * k));
                    case DISC -> g.fill(new Ellipse2D.Double((s.c(0) - s.c(2)) * k, (s.c(1) - s.c(2)) * k, 2 * s.c(2) * k, 2 * s.c(2) * k));
                    case RECT -> g.fill(new Rectangle2D.Double(s.c(0) * k, s.c(1) * k, s.c(2) * k, s.c(3) * k));
                    case POLYLINE, POLYGON -> {
                        Path2D p = new Path2D.Double();
                        p.moveTo(s.c(0) * k, s.c(1) * k);
                        for (int i = 2; i + 1 < s.size(); i += 2) p.lineTo(s.c(i) * k, s.c(i + 1) * k);
                        if (s.kind() == io.github.raffaelegrandi.pianura.viz.Shape.Kind.POLYGON) p.closePath();
                        g.draw(p);
                    }
                    case TEXT -> {}   // in screen coordinates, after the robots
                }
            }
        }
    }

    /** Text of the drawings, in screen coordinates: readable at any zoom, with a light halo. */
    private void paintDrawingTexts(Graphics2D g, RobotUnit r) {
        g.setFont(DRAWING_FONT);
        for (List<io.github.raffaelegrandi.pianura.viz.Shape> layer : r.getDrawings().values()) {
            for (io.github.raffaelegrandi.pianura.viz.Shape s : layer) {
                if (s.kind() != io.github.raffaelegrandi.pianura.viz.Shape.Kind.TEXT) continue;
                Point2D p = vt.toScreen(s.c(0) * Const.cellSize, s.c(1) * Const.cellSize);
                float x = (float) p.getX(), y = (float) p.getY();
                g.setColor(new Color(255, 255, 255, 170));
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) g.drawString(s.text(), x + dx, y + dy);
                g.setColor(s.color());
                g.drawString(s.text(), x, y);
            }
        }
    }

    /** Robot: disc, ring and heading marker, placed with translation + rotation. */
    private void paintRobot(Graphics2D g, RobotUnit r, Position p, double px) {
        double R = Const.unitRadius;
        AffineTransform saved = g.getTransform();
        g.translate(p.x * Const.cellSize, p.y * Const.cellSize);
        g.rotate(p.theta);

        Color body = r.getUnitObject().getID().getColor();
        if (!r.isActive()) body = blend(body, Color.GRAY, 0.6);   // stopped / faulty
        if (r == selected || badRobots.contains(r.getUnitObject())) {
            g.setColor(r == selected ? SELECTION : INVALID);
            g.setStroke(new BasicStroke((float) Math.max(2.5 * px, 1.2)));
            g.draw(new Ellipse2D.Double(-R - 4, -R - 4, 2 * R + 8, 2 * R + 8));
        }
        RobotGlyph.paintBody(g, R, px, body,
            r.getLeftWheelTravel() * Const.cellSize, r.getRightWheelTravel() * Const.cellSize);

        g.setTransform(saved);
    }

    /** Floor sensors: a dot with the color read, white and black outline to see it on any floor. */
    private void paintFloorSensors(Graphics2D g, List<RobotUnit> rs, double px) {
        for (RobotUnit r : rs) {
            io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray fs = r.getUnitObject().getFloorSensors();
            double d = Math.max(fs.getLayout().footprintCells() * Const.cellSize, 1.0);   // dot = real sensitive area
            for (int k = 0; k < fs.count(); k++) {
                Point2D p = fs.getPosition(k);
                if (p == null) continue;
                Ellipse2D e = new Ellipse2D.Double(p.getX() * Const.cellSize - d, p.getY() * Const.cellSize - d, 2 * d, 2 * d);
                g.setColor(fs.getColor(k));
                g.fill(e);
                g.setStroke(new BasicStroke((float) (1.6 * px)));
                g.setColor(Color.WHITE);
                g.draw(e);
                g.setStroke(new BasicStroke((float) (0.8 * px)));
                g.setColor(Color.BLACK);
                g.draw(e);
            }
        }
    }

    /**
     * Robot labels in screen coordinates: upright and readable at any zoom. They sit at the
     * top left at 45°, {@code ViewOptions.labelDistanceFromCenter} radii from the centre.
     */
    private void paintLabels(Graphics2D g, List<RobotUnit> rs, Position[] pos) {
        double rScreen = Const.unitRadius * vt.getScale();
        if (rScreen < 4) return;   // robots too small: the labels would cover everything
        double dist = options.labelDistanceFromCenter;
        for (int i = 0; i < rs.size(); i++) {
            double cx = pos[i].x * Const.cellSize, cy = pos[i].y * Const.cellSize;
            Point2D lw = RobotGlyph.labelCenter(cx, cy, Const.unitRadius, dist);
            RobotUnit r = rs.get(i);
            Color accent = r == selected ? SELECTION : badRobots.contains(r.getUnitObject()) ? INVALID : null;
            RobotGlyph.paintLabel(g, String.valueOf(r.getIDnum()), vt.toScreen(cx, cy),
                vt.toScreen(lw.getX(), lw.getY()), rScreen, accent);
        }
    }

    private void paintEditHint(Graphics2D g) {
        String txt = "Edit: drag obstacles and robots · handles = size · Shift+wheel or Q/E = rotate · Ctrl = snap";
        g.setFont(HINT_FONT);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(txt) + 14, h = fm.getHeight() + 6;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(8, 8, w, h, 8, 8);
        g.setColor(Color.WHITE);
        g.drawString(txt, 15, 8 + 3 + fm.getAscent());
    }

    private static Color blend(Color a, Color b, double t) {
        return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t),
                         (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                         (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    /** Tests only: state of the view. */
    Set<Integer> badObstaclesForTest() { return Collections.unmodifiableSet(badObstacles); }

}
