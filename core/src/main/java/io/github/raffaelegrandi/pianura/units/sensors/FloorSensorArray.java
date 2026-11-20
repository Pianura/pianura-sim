package io.github.raffaelegrandi.pianura.units.sensors;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.Arrays;

/**
 * Optical sensors facing the floor (reflectance / color), under the robot.
 *
 * Each sensor sits at {@code radiusFraction} of the robot's radius from the centre, at an
 * angle from the direction of travel (degrees, positive = to the right, i.e. clockwise on
 * screen). It reads the average color of the floor in a circular area of radius
 * {@code footprintCells} (cells): with a larger area the crossing of a line's edge is more
 * gradual, as for a real reflective sensor.
 *
 * Default: two sensors at 97% of the radius, at −20° (left, index 0) and +20° (right,
 * index 1), about 2/3 of a cell apart; footprint radius 0.1 cells (5 mm at the default scale).
 *
 * Thread-safety: as for {@code SensorArray}, the readings are published as a whole
 * (immutable snapshot) by the engine and read by the robot's thread.
 */
public class FloorSensorArray {

    public static final double DEFAULT_RADIUS_FRACTION = 0.97;
    public static final double[] DEFAULT_ANGLES_DEG = { -20, 20 };
    public static final double DEFAULT_FOOTPRINT_CELLS = 0.1;

    /** Configuration: positions and sensitive area. */
    public record Layout(double radiusFraction, double footprintCells, double[] anglesDeg) {
        public Layout {
            if (radiusFraction < 0) throw new IllegalArgumentException("radiusFraction must be >= 0");
            if (footprintCells < 0) throw new IllegalArgumentException("footprintCells must be >= 0");
            anglesDeg = anglesDeg.clone();
        }
        public int count() { return anglesDeg.length; }
        public double angleRad(int i) { return Math.toRadians(anglesDeg[i]); }
    }

    private record Reading(int[] rgb, Point2D[] positionsCells) {}

    private volatile Layout layout;
    private volatile Reading reading;

    public FloorSensorArray() {
        this(new Layout(DEFAULT_RADIUS_FRACTION, DEFAULT_FOOTPRINT_CELLS, DEFAULT_ANGLES_DEG));
    }

    public FloorSensorArray(Layout layout) {
        setLayout(layout);
    }

    /** Changes number, position or area of the sensors (the readings start again from zero). */
    public void setLayout(Layout l) {
        layout = l;
        reading = new Reading(new int[l.count()], new Point2D[l.count()]);
    }

    public Layout getLayout() { return layout; }

    public int count() { return layout.count(); }

    /** Publishes the new readings as a whole (called by the engine). */
    public void publish(int[] rgb, Point2D[] positionsCells) {
        reading = new Reading(Arrays.copyOf(rgb, rgb.length), Arrays.copyOf(positionsCells, positionsCells.length));
    }

    /** Color read by sensor i. */
    public Color getColor(int i) { return FloorMap.color(reading.rgb()[i]); }

    /** Packed RGB (0xRRGGBB) read by sensor i. */
    public int getRGB(int i) { return reading.rgb()[i]; }

    /** Brightness read by sensor i: 0 = black (line), 255 = white. */
    public int getGray(int i) { return FloorMap.gray(reading.rgb()[i]); }

    /** Brightness of all the sensors (copy). */
    public int[] getGrays() {
        int[] rgb = reading.rgb();
        int[] out = new int[rgb.length];
        for (int i = 0; i < rgb.length; i++) out[i] = FloorMap.gray(rgb[i]);
        return out;
    }

    /** Position (cells) of sensor i at the last reading, or null if not read yet. */
    public Point2D getPosition(int i) { return reading.positionsCells()[i]; }

}
