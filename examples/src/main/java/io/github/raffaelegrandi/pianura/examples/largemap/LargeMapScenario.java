package io.github.raffaelegrandi.pianura.examples.largemap;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.units.ControllerRobot;
import io.github.raffaelegrandi.pianura.utils.Position;
import io.github.raffaelegrandi.pianura.algorithms.beaconwanderer.BeaconWanderer;

/**
 * A large map to fly over: a building of rooms and corridors, crates and columns scattered
 * inside, and a swarm of explorers exchanging radio beacons.
 *
 * Everything is generated from a seed, so the map is always the same. Change the size, the
 * number of robots or the seed to try larger maps (e.g. new LargeMapScenario(1000, 1000, 400, 1)).
 */
public class LargeMapScenario extends ScenarioManager {

	public LargeMapScenario() { this(300, 200, 60, 9); }

	/**
	 * @param widthCells  arena width in cells
	 * @param heightCells arena height in cells
	 * @param robots      number of robots
	 * @param seed        generator seed (same seed, same map)
	 */
	public LargeMapScenario(int widthCells, int heightCells, int robots, long seed) {
		setArenaSize(widthCells, heightCells);
		setFloor(FloorStyle.voronoiTiles(new Color(196, 186, 168), 3, seed));
		getCommSettings().setRange(8);
		Random rnd = new Random(seed);
		List<Rectangle2D> taken = new ArrayList<>();      // bounds already taken (cells)

		// --- building: rooms of 40 x 40 cells separated by walls with 6-cell doors ---
		Color wall = new Color(112, 104, 96);
		int room = 40, door = 6;
		for (int x = room; x < widthCells; x += room) wallWithDoors(true, x, heightCells, room, door, rnd, wall, taken);
		for (int y = room; y < heightCells; y += room) wallWithDoors(false, y, widthCells, room, door, rnd, wall, taken);

		// --- scattered boxes and columns, never overlapping ---
		Color[] crate = { new Color(70, 110, 160), new Color(150, 95, 60), new Color(95, 130, 85) };
		int items = widthCells * heightCells / 220;
		for (int i = 0, tries = 0; i < items && tries < items * 30; tries++) {
			double x = 3 + rnd.nextDouble() * (widthCells - 6), y = 3 + rnd.nextDouble() * (heightCells - 6);
			boolean oval = rnd.nextDouble() < 0.3;
			double w = oval ? 2 + rnd.nextDouble() * 2 : 1.5 + rnd.nextDouble() * 4;
			double h = oval ? w : 1.5 + rnd.nextDouble() * 3;
			double reach = Math.hypot(w, h) / 2 + 1.2;          // rotated too, plus a margin to pass by
			Rectangle2D box = new Rectangle2D.Double(x - reach, y - reach, 2 * reach, 2 * reach);
			if (overlaps(box, taken)) continue;
			taken.add(box);
			addObstacle(new Obstacle(oval ? ArenaObjectType.OBSTACLE_OVAL : ArenaObjectType.OBSTACLE_RECT,
				oval ? new Color(232, 230, 224) : crate[rnd.nextInt(crate.length)],
				new Position(x, y, oval ? 0 : rnd.nextDouble() * Math.PI), w, h));
			i++;
		}

		// --- robot in posizioni libere ---
		for (int id = 1, tries = 0; id <= robots && tries < robots * 200; tries++) {
			double x = 3 + rnd.nextDouble() * (widthCells - 6), y = 3 + rnd.nextDouble() * (heightCells - 6);
			Rectangle2D box = new Rectangle2D.Double(x - 2, y - 2, 4, 4);
			if (overlaps(box, taken)) continue;
			taken.add(box);
			addRobot(new ControllerRobot(id, new Position(x, y, rnd.nextDouble() * 2 * Math.PI), 2, 0.1, new BeaconWanderer(seed * 1000 + id)));
			id++;
		}
	}

	/** Wall across the whole arena with a door in every stretch between two crossings. */
	private void wallWithDoors(boolean vertical, double at, double length, int room, int door, Random rnd, Color c, List<Rectangle2D> taken) {
		double t = 1;
		for (double start = 0; start < length; start += room) {
			double end = Math.min(length, start + room);
			double d0 = start + 4 + rnd.nextDouble() * Math.max(0, end - start - door - 8);   // porta
			// horizontal walls stop against the vertical ones (they touch, they do not overlap)
			double a = !vertical && start > 0 ? start + t / 2 : start;
			double b = !vertical && end < length ? end - t / 2 : end;
			addSegment(vertical, at, a, d0, t, c, taken);
			addSegment(vertical, at, d0 + door, b, t, c, taken);
		}
	}

	private void addSegment(boolean vertical, double at, double from, double to, double t, Color c, List<Rectangle2D> taken) {
		double len = to - from;
		if (len < 0.5) return;
		double mid = (from + to) / 2;
		Position p = vertical ? new Position(at, mid, 0) : new Position(mid, at, 0);
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, c, p, vertical ? t : len, vertical ? len : t));
		taken.add(vertical ? new Rectangle2D.Double(at - 1.5, from, 3, len) : new Rectangle2D.Double(from, at - 1.5, len, 3));
	}

	private static boolean overlaps(Rectangle2D r, List<Rectangle2D> taken) {
		for (Rectangle2D o : taken) if (o.intersects(r)) return true;
		return false;
	}

}
