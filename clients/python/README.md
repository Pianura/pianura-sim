# Driving Pianura robots from Python

A ready-made Python client (`pianura_client.py`, Python 3.8 or later, standard library only) and
an example (`wander.py`). The protocol, the modes and the Pianura side are described in
[`../README.md`](../README.md).

## Quick start

1. Run the example `remote` from the project root: `./gradlew run -Pexample=remote`. It opens
   paused, with two remote robots waiting on UDP port 5555.
2. Start the client: `python3 wander.py 1 2`
3. Press **Run** (or Space) in Pianura.

## Writing your own client

```python
from pianura_client import PianuraRobot

with PianuraRobot(robot_id=1) as robot:
    while True:
        s = robot.sensors()          # blocks until the next reading
        robot.drive(1.0, 0.0)        # answer every reading exactly once (drive or wheels)
```

Radio messages:

```python
        for m in s.inbox:                    # Message(sender, to, t, data); to is None for a broadcast
            print(f"from {m.sender}: {m.data}")
        robot.broadcast({"x": s.x, "y": s.y})
        robot.send(2, "only for robot 2")
        robot.drive(1.0, 0.0)                # the messages leave with this command
```

Debug drawings over the arena, in named layers (coordinates in cells; colors by name,
`"#rrggbb"` or `(r, g, b)`):

```python
        robot.layer("plan").clear().polyline(xs, ys, color="blue").disc(gx, gy, 0.3, color="red")
        robot.layer("trail").point(s.x, s.y)            # never cleared: it grows
        robot.drive(1.0, 0.0)                           # the drawings leave with this command
```

Shapes: `point`, `line`, `arrow`, `circle`, `disc`, `rect`, `polyline`, `polygon`, `text`;
`robot.clear_drawings()` clears every layer.

Pianura's **Inspector** window shows the algorithm's state, values, time plots and log:

```python
        robot.inspect.state("searching").watch("nodes", n).plot("error", e).log("goal seen")
```

`wander.py` sends a beacon every second with its position, prints which robots it can hear and
draws a line to where it believes each neighbour is (layer `radio`); in the Inspector it shows
whether it is cruising or turning away, its front IR reading and how many neighbours it hears.
