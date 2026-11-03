# Driving Pianura robots from MATLAB

A MATLAB client for Pianura's remote control (`PianuraRobot.m`) and two examples:
`gotogoal.m`, a proportional controller that follows waypoints and plots its errors, and
`wander.m`, robots that wander around and talk over the radio. The protocol, the modes and the
Pianura side are described in [`../README.md`](../README.md).

**Requirements:** MATLAB, with **no toolbox**: the client uses the Java runtime that ships with
MATLAB (`java.net.DatagramSocket`) and `jsonencode`/`jsondecode` (R2016b or later). The same
files also run in **GNU Octave** 7 or later, if Octave can use Java.

## Quick start

1. Run the example `remote` from the Pianura folder: `./gradlew run -Pexample=remote`. It opens
   paused, with two remote robots waiting on UDP port 5555.
2. In MATLAB, go to this folder (`clients/matlab`) and run:
   ```matlab
   rec = gotogoal();
   ```
3. Press **Run** (or Space) in Pianura. Robot 1 drives through three waypoints, and at the end
   a figure shows its trajectory and how the distance and heading errors went to zero.

For the radio example, restart the scenario and run `wander([1 2])` instead: two robots wander
around and print which robots they can hear.

## Writing your own controller

```matlab
robot = PianuraRobot(1);          % robot id 1, Pianura on 127.0.0.1:5555
robot.connect();
try
    while true
        s = robot.sensors();      % waits for the next control period
        if min(s.ir([1 2 18])) < 20
            robot.drive(0, 1.5);  % obstacle ahead: turn on the spot (clockwise)
        else
            robot.drive(1, 0);    % straight on: 1 cell/s
        end
    end
catch err
    if ~strcmp(err.identifier, 'Pianura:stopped'), rethrow(err); end
end
robot.close();
```

Answer **every** reading with exactly one `drive` or `wheels`: in lockstep mode the whole
simulation waits for that answer, which makes runs reproducible at any speed. To drive several
robots from one MATLAB session, create one `PianuraRobot` each and serve them one after the
other in the same loop, as `wander.m` does.

## API

| Call | What it does |
|---|---|
| `robot = PianuraRobot(id, host, port, timeout)` | One robot. Defaults: `'127.0.0.1'`, `5555`, `30` s |
| `w = robot.connect()` | Says hello; returns the welcome message (`w.mode`, `w.period`, `w.units`) |
| `s = robot.sensors()` | Waits for the next reading (struct below) |
| `robot.drive(v, w)` | Linear speed `v` (cells/s) and angular speed `w` (rad/s, positive = clockwise on screen) |
| `robot.wheels(left, right)` | Wheel speeds, same units as the Java `setMotorSpeed` (500, 500 = straight on) |
| `robot.broadcast(data)` | Radio message to every robot in range; it leaves with the next `drive`/`wheels` |
| `robot.send(to, data)` | Radio message to robot `to`, delivered only if it is in range |
| `robot.close()` | Stops driving the robot (it keeps its last command) and releases the socket |

The reading `s` is a struct:

| Field | Content |
|---|---|
| `t`, `seq` | Simulated time (s) and sequence number |
| `x`, `y`, `theta` | True pose: cells, cells, radians (clockwise on screen) |
| `ir` | 1×18, distance in px from the body to the nearest obstacle or robot, 255 = nothing in range |
| `bump` | 1×18, 0 = no contact |
| `contact` | `true` while the body is pushing against something |
| `floor` | Floor sensors, 0 = black … 255 = white; `floor(1)` = left, `floor(2)` = right |
| `wheelLeft`, `wheelRight` | Distance travelled by each wheel (cells, signed) |
| `active` | `false` after a simulated fault |
| `inbox` | Radio messages since the previous reading: struct array with `sender`, `to` (0 = broadcast), `t` (sending time), `data` |
| `raw` | The message as received |

**Indices.** MATLAB starts at 1, Pianura's sensors at 0: `s.ir(1)` is sensor 0 (the front one),
`s.ir(k+1)` is sensor k, clockwise every 20°. So `s.ir([1 2 18])` are the front sensor and its
two neighbours.

**Units.** Lengths in cells, time in seconds, angles in radians. The y axis grows downwards and
positive angles turn clockwise, as on screen: `atan2(dy, dx)` gives directions in the same
convention as `s.theta`. In plots, `set(gca, 'YDir', 'reverse')` shows the arena the right way.

**Radio data** can be anything `jsonencode` accepts: numbers, strings, logicals, arrays, structs,
cell arrays. It arrives as `jsondecode` returns it (for example a cell array of mixed values
comes back as a cell array, a numeric vector as a column vector).

## Errors

Catch them with `try`/`catch` and look at `err.identifier`:

| Identifier | When |
|---|---|
| `Pianura:stopped` | The simulation was stopped, or the robot was disabled. The normal way a run ends |
| `Pianura:timeout` | No message within the timeout: is Pianura running, and is the robot id right? |
| `Pianura:error` | Pianura answered with an error (unknown robot, malformed command, ...) |
| `Pianura:protocol` | The server speaks another protocol version: update the client together with Pianura |

## Notes

- The client was tested end to end with GNU Octave 8.4 against Pianura: lockstep runs, radio
  messages, errors, timeouts. Two runs of `wander([1 2])` give identical poses after 60 s.
- Connect before pressing Run in Pianura, otherwise the robot misses the first control periods.
- If MATLAB runs on another computer, the scenario must accept remote clients (see
  [`../README.md`](../README.md#on-the-pianura-side)) and the firewall must let UDP through.
