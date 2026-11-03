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

`wander.py` sends a beacon every second and prints which robots it can hear.
