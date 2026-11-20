package io.github.raffaelegrandi.pianura.arena;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Point2D;

import io.github.raffaelegrandi.pianura.utils.Position;

class ObjectIDTest {

    private ObjectID makeObstacle() {
        return new ObjectID(
            ArenaObjectType.OBSTACLE_RECT,
            Color.RED,
            new Position(10.0, 20.0, Math.PI / 4),
            new Dimension(3, 3)
        );
    }

    @Test
    void getTypeReturnsConstructorValue() {
        ObjectID id = makeObstacle();
        assertEquals(ArenaObjectType.OBSTACLE_RECT, id.getType());
    }

    @Test
    void getColorReturnsConstructorValue() {
        ObjectID id = makeObstacle();
        assertEquals(Color.RED, id.getColor());
    }

    @Test
    void getPosReturnsPosition() {
        ObjectID id = makeObstacle();
        assertEquals(10.0, id.getPos().getX());
        assertEquals(20.0, id.getPos().getY());
    }

    @Test
    void getAngleReturnsTheta() {
        ObjectID id = makeObstacle();
        assertEquals(Math.PI / 4, id.getAngle(), 1e-10);
    }

    @Test
    void setAngleNormalizesTo2Pi() {
        ObjectID id = makeObstacle();
        id.setAngle(3 * Math.PI); // 3π mod 2π = π
        assertEquals(Math.PI, id.getAngle(), 1e-10);
    }

    @Test
    void setAndGetIDnum() {
        ObjectID id = makeObstacle();
        id.setIDnum(42);
        assertEquals(42, id.getIDnum());
    }

    @Test
    void setLocUpdatesXY() {
        ObjectID id = makeObstacle();
        id.setLoc(new Point2D.Double(5.0, 7.0));
        assertEquals(5.0, id.getPos().getX());
        assertEquals(7.0, id.getPos().getY());
    }

    @Test
    void getDimReturnsConstructorValue() {
        ObjectID id = makeObstacle();
        assertEquals(3, id.getDim().width);
        assertEquals(3, id.getDim().height);
    }

    @Test
    void aPublishedPoseIsNeverChangedAfterwards() {
        Position mine = new Position(4, 4, 0);
        ObjectID id = new ObjectID(ArenaObjectType.OBSTACLE_RECT, java.awt.Color.RED, mine, new java.awt.Dimension(1, 1));
        mine.setPosition(9, 9, 9);                       // the caller reuses its object
        assertEquals(4, id.getPos().x, "the object keeps its own copy");
        Position seen = id.getPos();
        id.setLoc(new Point2D.Double(5, 6));
        id.setAngle(1);
        id.setPos(new Position(7, 8, 2));
        assertEquals(new Position(4, 4, 0).toString(), seen.toString(), "a reader's snapshot does not move");
        assertEquals(new Position(7, 8, 2).toString(), id.getPos().toString());
    }

    @Test
    void setPosReplacesPosition() {
        ObjectID id = makeObstacle();
        id.setPos(new Position(1.0, 2.0, 0.0));
        assertEquals(1.0, id.getPos().getX());
        assertEquals(2.0, id.getPos().getY());
    }
}
