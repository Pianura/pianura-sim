// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.units.collisions;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Dimension;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.utils.Position;

class ObstacleValidationTest {

    private static Obstacle rect(double x, double y, int w, int h, double ang) {
        return new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.WHITE, new Position(x, y, ang), new Dimension(w, h));
    }

    private static Obstacle oval(double x, double y, int w, int h) {
        return new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(x, y, 0), new Dimension(w, h));
    }

    private static List<String> check(Obstacle... obs) {
        return new CollisionDetector(List.of(obs)).validateObstacles();
    }

    @Test
    void separatedObstaclesAreFine() {
        assertTrue(check(rect(10, 10, 4, 4, 0), rect(20, 10, 4, 4, 0), oval(30, 30, 4, 4)).isEmpty());
    }

    @Test
    void touchingEdgesAreNotOverlap() {
        // two 4x4 squares side by side: sides coinciding at x = 12
        assertTrue(check(rect(10, 10, 4, 4, 0), rect(14, 10, 4, 4, 0)).isEmpty());
    }

    @Test
    void overlappingRectanglesAreReportedWithArea() {
        List<String> p = check(rect(10, 10, 4, 4, 0), rect(12, 10, 4, 4, 0));  // overlap 2x4 = 8 cells²
        assertEquals(1, p.size(), p.toString());
        assertTrue(p.get(0).contains("#1") && p.get(0).contains("#2"));
        assertTrue(p.get(0).contains("8.00"), p.get(0));
    }

    @Test
    void rotatedRectangleOverlapUsesSameRotationAsCollisions() {
        // long 10x1 rectangle rotated by +45° (clockwise on screen): its end lands at the bottom
        // right of the centre, where there is a small oval
        double d = 5 / Math.sqrt(2) - 0.5;
        assertEquals(1, check(rect(20, 20, 10, 1, Math.PI / 4), oval(20 + d, 20 + d, 1, 1)).size());
        // in the opposite direction (counter-clockwise) there must be nothing
        assertTrue(check(rect(20, 20, 10, 1, Math.PI / 4), oval(20 + d, 20 - d, 1, 1)).isEmpty());
    }

    @Test
    void ovalOverlap() {
        assertEquals(1, check(oval(10, 10, 4, 4), oval(13, 10, 4, 4)).size());
        assertTrue(check(oval(10, 10, 4, 4), oval(14.1, 10, 4, 4)).isEmpty());
    }

    @Test
    void obstacleOutsideArenaIsReported() {
        List<String> p = check(rect(1, 20, 4, 4, 0));   // from x = -1 to 3: 1x4 cells outside
        assertEquals(1, p.size(), p.toString());
        assertTrue(p.get(0).contains("extends outside the arena"));
        assertTrue(p.get(0).contains("4.00"), p.get(0));
    }

    @Test
    void validationUsesConfiguredArenaSize() {
        Obstacle far = rect(70, 45, 4, 4, 0);   // inside an 80x50 arena, outside a 40x40 one
        assertTrue(new CollisionDetector(List.of(far), 80 * 15, 50 * 15).validateObstacles().isEmpty());
        assertEquals(1, new CollisionDetector(List.of(far)).validateObstacles().size());
    }

}
