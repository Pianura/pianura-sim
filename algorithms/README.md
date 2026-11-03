# Algorithms

Reference robot controllers, one package each under
`io.github.raffaelegrandi.pianura.algorithms`. The scenarios that use them are in
[`../examples`](../examples/README.md).

| Package | Class | Style | Origin | Used by |
|---|---|---|---|---|
| [`timedmotion`](src/main/java/io/github/raffaelegrandi/pianura/algorithms/timedmotion) | `TimedMotion` | control loop | JUSE, 2008–2011 (was `TestDummy03`) | `timedmotion` |
| [`iravoidance`](src/main/java/io/github/raffaelegrandi/pianura/algorithms/iravoidance) | `IrAvoider` | control loop | JUSE, 2008–2011 (was `TestDummy04`) | `obstacles`, `rooms` |
| [`beaconwanderer`](src/main/java/io/github/raffaelegrandi/pianura/algorithms/beaconwanderer) | `BeaconWanderer` | periodic controller | added 2026 | `radio`, `largemap` |
| [`braitenberg`](src/main/java/io/github/raffaelegrandi/pianura/algorithms/braitenberg) | `BraitenbergVehicle` | periodic controller | added 2026 | `braitenberg` |

*Control loop*: a `RobotDummy` subclass whose `executeLoop()` runs in its own thread and waits
with `sleepSim()`. *Periodic controller*: a `RobotController` called by a `ControllerRobot` at a
fixed period of simulated time. Both styles are described in the [main README](../README.md#programming-a-robot).

## timedmotion: `TimedMotion`

*From JUSE.* Open-loop motion: the robot runs a fixed sequence of wheel speeds, each for a fixed
time, then stops. No sensor is read.

| Step | left / right wheel | duration |
|---|---|---|
| 1 | 1.5 / 1.5 | 2 s |
| 2 | 1.5 / 6 | 2 s |
| 3 | 2 / 6 | 1 s |
| 4 | 2 / 2 | 4 s |

In JUSE the durations were real time; here they are simulated time, so the result does not
depend on the computer.

## iravoidance: `IrAvoider`

*From JUSE.* Reactive obstacle avoidance on the infrared sensors, every 0.2 s:

- an obstacle closer than 25 px on sensors 0–4 (front right) makes the robot turn
  counter-clockwise, one on sensors 14–17 (front left) clockwise;
- once a turn has started, it keeps the same direction until the way is clear, so it does not
  hesitate between left and right;
- while turning, the inner wheel stops and the outer one speeds up a little at every step;
- with the way clear both wheels go back to the cruise speed.

It has no memory of where it has been, so in narrow passages it can go back and forth.

## beaconwanderer: `BeaconWanderer`

*Added 2026.* Wanders around avoiding obstacles and, once a second, broadcasts a radio beacon
with its id. It knows its neighbours only from the beacons it hears (there is no oracle), and
forgets a neighbour that has not been heard for 2.5 s. Each robot has its own random seed, so
runs are reproducible.

## braitenberg: `BraitenbergVehicle`

*Added 2026.* A Braitenberg "fear" vehicle (V. Braitenberg, *Vehicles: Experiments in Synthetic
Psychology*, MIT Press, 1984). No map and no plan: every IR sensor is wired to the motors with a
fixed weight, and the behaviour comes from the wiring alone.

- lateral wiring: an obstacle on one side turns the vehicle away from it. The wiring is skewed
  by 25° so that an obstacle straight ahead still makes it turn;
- frontal wiring: an obstacle ahead slows the vehicle down;
- after a contact the vehicle turns on the spot for a moment.

Give it a sensing range of about 4 cells. In the example `braitenberg`, five vehicles run for
120 s without touching anything. The constants at the top of the class (`CRUISE`,
`STEER_GAIN`, `BRAKE_GAIN`, `SKEW`, `ESCAPE_SPEED`, `ESCAPE_TICKS`) can be changed to see how
the behaviour follows the wiring.

First written for the historical edition of [JUSE](https://github.com/raffaeleGrandi/JUSE)
and then adapted to Pianura's periodic controller API.

## Adding an algorithm

1. Create a package under `io.github.raffaelegrandi.pianura.algorithms`, named after what the
   algorithm does.
2. Write the controller as a `RobotController` (preferred) or a `RobotDummy` subclass. Start the
   class with a short comment that says what it does, where the idea comes from and, for classic
   algorithms, a bibliographic reference.
3. Add an example that uses it in [`../examples`](../examples/README.md), and a row to the table
   above.
