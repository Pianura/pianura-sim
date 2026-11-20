# Driving Pianura robots from outside (UDP + JSON)

Pianura robots can be driven by any program that speaks UDP and JSON. This folder contains
ready-made clients:

| Client | Folder | Needs |
|---|---|---|
| Python | [`python/`](python) | Python 3.8 or later, standard library only |
| MATLAB / GNU Octave | [`matlab/`](matlab) | MATLAB (no toolbox) or GNU Octave 7 or later with Java |

Each folder has its own quick start. This page describes what is common to all of them: the
Pianura side, the two modes and the messages, for anyone who wants to write a client in
another language (C, Julia, another JVM, ...).

## On the Pianura side

```java
RemoteServer server = RemoteServer.start(5555, RemoteServer.Mode.LOCKSTEP);
addRobot(server.createRobot(1, new Position(5, 5, 0), 2, 0.1));   // id, pose, IR range, control period (s)
...
Simulator sim = new Simulator(new Point(30, 30), "Pianura", scenario);
sim.attach(server);        // server messages in the Pianura log, closed by Stop
sim.startSimPaused();
```

By default the server listens on `127.0.0.1` only. To accept clients from other machines use
`RemoteServer.start(new InetSocketAddress(5555), mode, timeoutMs)`.

## Modes

| Mode | What happens every control period | Use it for |
|---|---|---|
| `LOCKSTEP` | Pianura sends the sensors and **waits** for the answer with the same `seq`. The whole simulation waits, so runs are reproducible at any speed. If no answer arrives within the timeout (1 s by default) the robot keeps its last command and a warning is logged. | algorithm development, experiments, debugging |
| `REALTIME` | Pianura sends the sensors and applies the **latest** command received, without waiting — like a real robot on a radio link. | realistic timing, slow or unreliable clients |

A robot whose client has not said `hello` yet stands still, and the simulation is not blocked.
Connect the client before pressing Run, otherwise it misses the first control periods.

## Messages (protocol version 3)

One JSON object per UDP datagram. Lengths are in **cells**, time in **seconds**, angles in
**radians**. The scenario declares how many meters a cell is (`meters_per_cell` in the welcome,
0.05 by default: the robot is 2 cells, 10 cm, across). The y axis grows downwards and positive
angles turn **clockwise** on screen.

### Client → Pianura

| Message | Meaning |
|---|---|
| `{"type":"hello","robot":1}` | Start driving robot 1. Answered by `welcome`. |
| `{"type":"cmd","robot":1,"seq":7,"v":1.0,"w":0.3}` | Linear speed `v` (cells/s), angular speed `w` (rad/s). `seq` echoes the sensor message being answered (required in lockstep mode). |
| `{"type":"cmd","robot":1,"seq":7,"left":1.0,"right":0.5}` | Wheel speeds in cells/s (speed of each wheel on the ground). Equivalent to `v = (left + right) / 2`, `w = (left - right) / wheel_base`. |
| `{"type":"cmd","robot":1,"seq":7,"v":1.0,"broadcast":{...},"send":[{"to":3,"data":"hi"}]}` | Optional radio messages, see [Radio messages](#radio-messages). A `cmd` with messages but no speeds keeps the previous speeds. |
| `{"type":"cmd","robot":1,"seq":7,"v":1.0,"draw":[{"op":"line","layer":"plan",...}]}` | Optional debug drawings over the arena, see [Debug drawings](#debug-drawings). A `cmd` with drawings but no speeds keeps the previous speeds. |
| `{"type":"bye","robot":1}` | Stop driving robot 1 (it keeps its last command). |

### Pianura → client

| Message | Meaning |
|---|---|
| `{"type":"welcome","protocol":3,"robot":1,"mode":"lockstep","period":0.1,"units":{...},"body":{...}}` | Connection accepted, see below. |
| `{"type":"sensors", ...}` | One reading per control period, see below. |
| `{"type":"stopped","robot":1}` | The simulation was stopped or the robot was disabled. |
| `{"type":"error","message":"..."}` | Malformed JSON, unknown robot, invalid command, ... |

`welcome` fields:

| Field | Content |
|---|---|
| `mode`, `period` | `"lockstep"` or `"realtime"`; control period in seconds of simulated time |
| `units` | `{"length":"cell","meters_per_cell":0.05,"time":"s","angle":"rad"}` |
| `body` | The robot's geometry, in cells: `radius` (1), `wheel_base` (2), `wheel_radius` (0.25), `ir_count` (18), `ir_range` (the value IR sensors read when nothing is in range), `floor_count` |

`sensors` fields:

| Field | Content |
|---|---|
| `seq`, `t` | Sequence number (echo it in `cmd`) and simulated time |
| `pose` | `{"x","y","theta"}`: true pose of the robot |
| `ir` | 18 IR sensors, index 0 = front then clockwise every 20°: distance in cells from the body to the nearest obstacle or robot, `ir_range` when nothing is in range |
| `bump` | 18 bump sensors, `true` = contact in that direction |
| `contact` | `true` while the body is pushing against an obstacle or robot |
| `floor` | Floor (line-following) sensors, 0 = black … 255 = white; index 0 = left, 1 = right |
| `wheels` | `{"left","right"}`: distance travelled by each wheel (cells, signed; wheels keep turning when the body is blocked) |
| `active` | `false` after a simulated fault |
| `inbox` | Radio messages received since the previous reading: `[{"from":2,"to":"all","t":0.6,"data":...}]` (`to` is `"all"` for a broadcast, else the receiver id; `t` is the sending time) |
| `inbox_dropped` | Present only if the inbox did not fit in one datagram: number of oldest messages dropped |

## Radio messages

Remote robots talk to each other, and to robots written in Java, through Pianura's simulated
radio network: a message reaches only the robots within the sender's range (and in line of
sight, if the scenario enables it), with the scenario's losses and latency. There is no
"list of neighbours": a robot learns who is around from the messages it receives.

- `"broadcast": data` sends `data` to every robot in range.
- `"send": [{"to": 3, "data": ...}, ...]` sends to single robots (delivered only if in range);
  `"to": "all"` is a broadcast, handy to send more than one per command.
- `data` is any JSON value. Numbers arrive as floating point, as in JSON.
- Messages leave at the control instant of the `cmd` that carries them, exactly once; they can
  be read at the receiver's next control period (at the earliest one physics step later).

The radio range is set in the scenario (`getCommSettings().setRange(cells)`, 6 cells by
default) or per robot (`robot.setCommRange(cells)`). The **Connectivity window** in Pianura shows
who hears whom while the simulation runs.

## Debug drawings

A client can draw over the arena what its algorithm thinks (the route, the target, an estimated
map), in named layers that never affect the simulation. `"draw"` is a list of operations,
applied in order at the control instant of the command:

```json
"draw": [
  {"op": "clear", "layer": "plan"},
  {"op": "polyline", "layer": "plan", "xs": [5, 20, 33], "ys": [5, 4, 4], "color": "#2060ff"},
  {"op": "disc", "layer": "plan", "x": 33, "y": 4, "r": 0.3, "color": [255, 0, 0]},
  {"op": "text", "layer": "plan", "x": 33.5, "y": 3.5, "text": "goal"}
]
```

| `op` | Fields (cells) |
|---|---|
| `clear` | empties `layer` |
| `clear_all` | empties every layer of the robot |
| `point` | `x`, `y` |
| `line`, `arrow` | `x1`, `y1`, `x2`, `y2` (the arrow points at `x2`, `y2`) |
| `circle`, `disc` | `x`, `y`, `r` (outline, filled) |
| `rect` | `x`, `y` (top-left corner), `w`, `h`, filled: one cell of a grid map is `x=i, y=j, w=1, h=1` |
| `polyline`, `polygon` | `xs`, `ys` (polygon = closed outline) |
| `text` | `x`, `y` (left end of the baseline), `text` |

Every operation can carry `layer` (default `"default"`), `color` (`"#rrggbb"`, `"#rrggbbaa"`,
or `[r, g, b]` / `[r, g, b, a]` with 0–255 values; without it the shape takes the last color
used in its layer) and `width` (line width in screen pixels). A layer keeps its shapes until it
is cleared: redraw it at every step, or let it grow (a trail, a map). A robot holds at most
50,000 shapes, and a whole command must fit in one UDP datagram (about 64 KB), so draw only
what changes. A malformed operation rejects the whole command with an `error`.

In Pianura, **Robot drawings** in the side panel shows or hides the drawings.

## Inspector

What the client sends here appears in Pianura's **Inspector** window (button on the toolbar),
stamped with the simulated time of the command:

```json
{"type": "cmd", "robot": 1, "seq": 7, "v": 1.0,
 "state": "to waypoint 2",
 "watch": {"v": 1.0, "mode": "fast"},
 "plot": {"distance": 3.2, "heading error": 0.12},
 "log": ["waypoint 1 reached"]}
```

| Field | Content |
|---|---|
| `state` | The algorithm's current state, a string; a change is also logged |
| `watch` | Named values to show, `{name: number or string}` |
| `plot` | Points for the time plots, `{name: number}`: one plot per name |
| `log` | A line, or a list of lines, for the log |

The Python client sends them with `robot.inspect.state(...)`, `.watch(...)`, `.plot(...)`,
`.log(...)`, MATLAB with `robot.inspectState`, `inspectWatch`, `inspectPlot`, `inspectLog`: they
leave with the next `drive`/`wheels`. A `cmd` with only inspector fields keeps the previous speeds.

## Versions

Clients and server must speak the same protocol version, otherwise the client refuses the
welcome: update the clients together with Pianura.

| Version | Changes |
|---|---|
| 3 | Units in cells everywhere: `ir` in cells (was px, 255 = nothing), `bump` as booleans, wheel commands in cells/s (were JUSE's motor units); `meters_per_cell` and `body` in the welcome; debug drawings (`draw` in cmd); inspector (`state`, `watch`, `plot`, `log` in cmd) |
| 2 | Radio messages: `inbox` in sensors, `broadcast` / `send` in cmd |
| 1 | First version |
