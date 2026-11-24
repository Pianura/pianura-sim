# Changelog

All notable changes to Pianura Sim are listed here. Versions follow
[Semantic Versioning](https://semver.org/); before 1.0 the programming interface may still
change between minor versions.

## Unreleased

### Name
- The simulator is now called **Pianura Sim**: Pianura is the project it belongs to. The window,
  the start screen, this file and the citation use the new name, and the runnable jar is
  `pianura-sim.jar`. The Java packages and the client classes keep their names.

### Interface
- A start screen: the scenes and examples opened last, favourites, all the examples with a
  picture each, and the scene files of a folder; a new example, `sandbox`, for scenes of your
  own. `./gradlew run` and the jar start from here; the Home button comes back to it.
- Two views of the simulation window. Studio: a toolbar of icons with tooltips, tools to add
  and delete obstacles, a panel with the view options and the selected robot, a timeline of the
  robots' states, the log. Focus: the arena takes the window, with floating controls, a card
  that follows the selected robot and an optional caption.
- The inspector shows the robot's body as Pianura sees it (sensors, wheels, speeds) and views
  of its insides: bars, heat maps, a neural network whose neurons light up.
- The window fits the screen on small displays.

### Robots and remote control
- The welcome message describes the robot (`kind`, `description`, `capabilities`: sensors with
  their directions, drive modes, features), so that a client can run on another robot that
  speaks the protocol; `conformance.py` checks that a server does what it declares. Protocol
  version unchanged: only fields were added.
- A new example, `brains`: one program per robot over UDP.
- Fixed: a client connecting to a running simulation could receive a sensor reading before the
  welcome, and stall the simulation until the reply timeout.

## [0.1.0] — 2026-11-20

First public release. Pianura is the evolution of JUSE (Java UniBot Simulation Environment,
2008–2011), rewritten around a simulated clock and extended for teaching and research on
mobile robots and swarms.

### Simulation
- Simulated clock with a fixed physics step: pause, single step and speed control;
  reproducible runs.
- Differential-drive kinematics integrated exactly on the instantaneous centre of curvature;
  contacts with a sliding response, so a robot never enters an obstacle and can move away.
- Lengths in cells, with a scale (`metersPerCell`, 5 cm by default) to read them in meters.
- 18 infrared and 18 bump sensors per robot by geometric ray casting; optical floor sensors
  for line following.
- Radio between robots with range, losses and latency; connectivity window with the graph and
  its matrices.

### Programming robots
- Two styles: a sequential control loop, or a controller called at a fixed period.
- Debug drawings: a robot draws what its algorithm thinks in named layers over the arena.
- Inspector: a separate window with a robot's state, watched values, time plots and log; a
  robot can pause the simulation on a decision.
- Remote control over UDP and JSON (protocol v3), lockstep or real time, with clients for
  Python and MATLAB/Octave; drawings and inspector from the clients too.
- Reference algorithms: obstacle avoidance, Braitenberg vehicles, radio beacons, RRT path
  planning.

### Interface
- Arenas of any size: zoom, pan, fly over, minimap, follow a robot.
- Floors: checkerboard, grid, random tiles, Voronoi tiles, line-following track, any image.
- Scene editing with the mouse before Run or while paused, with live validation; scenes saved
  to and opened from JSON files.
- Modern look with FlatLaf when it is on the classpath (light or dark), Swing's standard look
  otherwise.

### Distribution
- Gradle build with three modules: `core` (JDK only), `algorithms`, `examples`.
- Nine examples, and `pianura.jar` to run them with `java -jar` (Java 21 or later).
- MIT license; `CITATION.cff` for citing the work.

[0.1.0]: https://github.com/Pianura/pianura-sim/releases/tag/v0.1.0
