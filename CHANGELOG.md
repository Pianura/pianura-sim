# Changelog

All notable changes to Pianura are listed here. Versions follow
[Semantic Versioning](https://semver.org/); before 1.0 the programming interface may still
change between minor versions.

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
