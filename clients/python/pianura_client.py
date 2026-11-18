"""
Pianura remote-control client (Python 3.8+, standard library only).

Drive a Pianura robot from Python over UDP:

    from pianura_client import PianuraRobot

    with PianuraRobot(robot_id=1) as robot:       # default: 127.0.0.1:5555
        while True:
            s = robot.sensors()                   # blocks until the next control period
            if min(s.ir[i] for i in (0, 1, 17)) < 1.0:  # obstacle less than 1 cell from the body
                robot.drive(0.0, 1.5)             # obstacle ahead: turn in place
            else:
                robot.drive(1.0, 0.0)             # go straight (cells/s, rad/s)

Every call to sensors() must be followed by exactly one drive() or wheels() call:
in lockstep mode the simulation waits for that answer before going on.

Radio messages (since protocol version 2) go through Pianura's simulated network, with the same range,
losses and latency as Java robots:

    for m in s.inbox:                             # received since the previous reading
        print(m.sender, m.data)
    robot.broadcast({"seen": s.t})                # to every robot in range
    robot.send(3, "hello 3")                      # to robot 3, if it is in range
    robot.drive(1.0, 0.0)                         # messages leave with this command

Message data must be JSON data (dict, list, str, number, bool, None); numbers arrive as floats.

Debug drawings (protocol version 3) appear over the arena in Pianura, in named layers, to show
what the algorithm thinks: a path, a target, an estimated map. Coordinates are in cells:

    robot.layer("plan").clear().polyline(xs, ys, color="blue").disc(gx, gy, 0.3, color="red")
    robot.drive(1.0, 0.0)                         # drawings leave with this command

A layer keeps its shapes until clear(): redraw it at every step, or let it grow (a trail, a map).

Units (protocol version 3): lengths in cells, time in seconds, angles in radians. One cell is
robot.meters_per_cell meters (0.05 by default: the robot is 2 cells, 10 cm, across).
Screen convention: y grows downwards, positive angles turn clockwise on screen.
"""

import json
import socket
from dataclasses import dataclass, field
from typing import Any, List, Optional, Sequence, Union

PROTOCOL_VERSION = 3


class PianuraError(Exception):
    """An error message sent back by Pianura (unknown robot, malformed command, ...)."""


class RobotStopped(Exception):
    """The simulation was stopped, or the robot was disabled (e.g. simulated fault)."""


@dataclass
class Message:
    sender: int                 # id of the robot that sent it
    to: Optional[int]           # receiver id, or None for a broadcast
    t: float                    # simulated time when it was sent (s)
    data: Any                   # the content, as sent (JSON data)


@dataclass
class Sensors:
    t: float                    # simulated time (s)
    seq: int                    # sequence number, echoed back by drive()/wheels()
    x: float                    # pose: position in cells ...
    y: float
    theta: float                # ... and heading in radians (clockwise on screen)
    ir: List[float]             # 18 IR sensors: cells from the body to the nearest hit, robot.ir_range if nothing
                                # in range (index 0 = front, then clockwise every 20 degrees)
    bump: List[bool]            # 18 bump sensors, True = contact in that direction
    contact: bool               # the body is pushing against something
    floor: List[int]            # floor sensors, 0 = black, 255 = white (0 = left, 1 = right)
    wheel_left: float           # distance travelled by each wheel (cells, signed)
    wheel_right: float
    active: bool
    inbox: List[Message] = field(default_factory=list)   # radio messages received since the previous reading
    raw: dict = field(repr=False, default_factory=dict)


_NAMED_COLORS = {
    "red": "#e02020", "green": "#20a040", "blue": "#2060ff", "orange": "#ff7800", "yellow": "#ffd000",
    "magenta": "#d020d0", "cyan": "#00b0d0", "black": "#000000", "white": "#ffffff", "gray": "#808080",
}

Color = Union[str, Sequence[int]]


def _color(c: Color) -> Union[str, List[int]]:
    """A color for Pianura: a name above, "#rrggbb", "#rrggbbaa", or (r, g, b[, a]) with 0-255 values."""
    if isinstance(c, str):
        if c.lower() in _NAMED_COLORS:
            return _NAMED_COLORS[c.lower()]
        if c.startswith("#") and len(c) in (7, 9):
            return c
        raise ValueError(f"unknown color {c!r}: use a name ({', '.join(_NAMED_COLORS)}), '#rrggbb' or (r, g, b)")
    v = [int(round(x)) for x in c]
    if len(v) not in (3, 4) or not all(0 <= x <= 255 for x in v):
        raise ValueError("a color tuple must be (r, g, b) or (r, g, b, a) with values 0-255")
    return v


class Layer:
    """A named layer of debug drawings. Every method returns the layer, so calls can be chained.

    Coordinates and radii are in cells. `color` applies to that shape; without it the shape takes
    the last color used in the layer. `width` is the line width in screen pixels."""

    def __init__(self, robot: "PianuraRobot", name: str):
        self._robot = robot
        self.name = name

    def _op(self, op: str, color: Optional[Color] = None, width: Optional[float] = None, **fields) -> "Layer":
        d = {"op": op, "layer": self.name, **fields}
        if color is not None:
            d["color"] = _color(color)
        if width is not None:
            d["width"] = float(width)
        self._robot._draw.append(d)
        return self

    def clear(self) -> "Layer":
        """Remove every shape of this layer."""
        return self._op("clear")

    def point(self, x, y, color=None, width=None) -> "Layer":
        return self._op("point", color, width, x=float(x), y=float(y))

    def line(self, x1, y1, x2, y2, color=None, width=None) -> "Layer":
        return self._op("line", color, width, x1=float(x1), y1=float(y1), x2=float(x2), y2=float(y2))

    def arrow(self, x1, y1, x2, y2, color=None, width=None) -> "Layer":
        """A line with an arrowhead at (x2, y2)."""
        return self._op("arrow", color, width, x1=float(x1), y1=float(y1), x2=float(x2), y2=float(y2))

    def circle(self, x, y, r, color=None, width=None) -> "Layer":
        return self._op("circle", color, width, x=float(x), y=float(y), r=float(r))

    def disc(self, x, y, r, color=None) -> "Layer":
        """Filled circle."""
        return self._op("disc", color, None, x=float(x), y=float(y), r=float(r))

    def rect(self, x, y, w, h, color=None) -> "Layer":
        """Filled rectangle, top-left corner at (x, y). One cell of a grid map: rect(i, j, 1, 1)."""
        return self._op("rect", color, None, x=float(x), y=float(y), w=float(w), h=float(h))

    def polyline(self, xs, ys, color=None, width=None) -> "Layer":
        return self._op("polyline", color, width, xs=[float(v) for v in xs], ys=[float(v) for v in ys])

    def polygon(self, xs, ys, color=None, width=None) -> "Layer":
        """Closed outline."""
        return self._op("polygon", color, width, xs=[float(v) for v in xs], ys=[float(v) for v in ys])

    def text(self, x, y, text: str, color=None) -> "Layer":
        return self._op("text", color, None, x=float(x), y=float(y), text=str(text))


class PianuraRobot:
    """One remote robot. Use one instance (and one socket) per robot."""

    def __init__(self, robot_id: int, host: str = "127.0.0.1", port: int = 5555, timeout: float = 30.0):
        self.robot_id = robot_id
        self.server = (host, port)
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.settimeout(timeout)
        self.welcome: Optional[dict] = None
        self._last_seq: Optional[int] = None
        self._broadcast: List[Any] = []
        self._send_to: List[dict] = []
        self._draw: List[dict] = []
        self._layers = {}

    # -- connection -------------------------------------------------------------------

    def connect(self) -> dict:
        """Say hello and wait for the welcome message (mode, control period, units)."""
        self._send({"type": "hello", "robot": self.robot_id})
        while True:
            msg = self._receive()
            if msg.get("type") == "welcome":
                if msg.get("protocol") != PROTOCOL_VERSION:
                    raise PianuraError(f"protocol {msg.get('protocol')} not supported (client speaks {PROTOCOL_VERSION})")
                self.welcome = msg
                return msg

    @property
    def meters_per_cell(self) -> float:
        """Scale of the scenario: meters per cell (after connect())."""
        return float(self._welcome()["units"]["meters_per_cell"])

    @property
    def ir_range(self) -> float:
        """Range of the IR sensors in cells: the value they read when nothing is in range."""
        return float(self._welcome()["body"]["ir_range"])

    @property
    def wheel_base(self) -> float:
        """Distance between the wheels in cells (for wheel speeds: w = (left - right) / wheel_base)."""
        return float(self._welcome()["body"]["wheel_base"])

    def _welcome(self) -> dict:
        if self.welcome is None:
            raise PianuraError("not connected: call connect() first (or use 'with PianuraRobot(...)')")
        return self.welcome

    def close(self):
        try:
            self._send({"type": "bye", "robot": self.robot_id})
        except OSError:
            pass
        self.sock.close()

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, *exc):
        self.close()
        return False

    # -- control loop -----------------------------------------------------------------

    def sensors(self) -> Sensors:
        """Wait for the next sensor reading of this robot."""
        while True:
            msg = self._receive()
            kind = msg.get("type")
            if kind == "sensors":
                self._last_seq = int(msg["seq"])
                pose, wheels = msg["pose"], msg["wheels"]
                return Sensors(t=msg["t"], seq=self._last_seq, x=pose["x"], y=pose["y"], theta=pose["theta"],
                               ir=[float(v) for v in msg["ir"]], bump=[bool(v) for v in msg["bump"]],
                               contact=bool(msg["contact"]), floor=[int(v) for v in msg["floor"]],
                               wheel_left=wheels["left"], wheel_right=wheels["right"],
                               active=bool(msg["active"]),
                               inbox=[Message(sender=int(m["from"]), to=None if m["to"] == "all" else int(m["to"]),
                                              t=m["t"], data=m["data"]) for m in msg.get("inbox", [])],
                               raw=msg)
            if kind == "stopped":
                raise RobotStopped(f"robot {self.robot_id} stopped")

    def drive(self, v: float, w: float = 0.0):
        """Linear speed v (cells/s) and angular speed w (rad/s, positive = clockwise on screen)."""
        self._command({"v": float(v), "w": float(w)})

    def wheels(self, left: float, right: float):
        """Wheel speeds in cells/s; left faster than right turns clockwise on screen (to the right)."""
        self._command({"left": float(left), "right": float(right)})

    def broadcast(self, data: Any):
        """Radio message to every robot in range; it leaves with the next drive()/wheels()."""
        json.dumps(data)                                  # fail now if it is not JSON data
        self._broadcast.append(data)

    def send(self, to: int, data: Any):
        """Radio message to robot `to` (delivered only if it is in range); leaves with the next drive()/wheels()."""
        json.dumps(data)
        self._send_to.append({"to": int(to), "data": data})

    def layer(self, name: str = "default") -> Layer:
        """Debug drawing layer `name`; what is drawn leaves with the next drive()/wheels()."""
        if name not in self._layers:
            self._layers[name] = Layer(self, name)
        return self._layers[name]

    def clear_drawings(self):
        """Clear every debug drawing layer of this robot."""
        self._draw.append({"op": "clear_all"})

    # -- low level ----------------------------------------------------------------------

    def _command(self, motion: dict):
        msg = {"type": "cmd", "robot": self.robot_id, "seq": self._last_seq, **motion}
        # one broadcast goes in "broadcast"; any further one becomes a "send" item addressed to "all"
        if self._broadcast:
            msg["broadcast"] = self._broadcast[0]
        sends = [{"to": "all", "data": d} for d in self._broadcast[1:]] + self._send_to
        if sends:
            msg["send"] = sends
        if self._draw:
            msg["draw"] = self._draw
        self._broadcast, self._send_to, self._draw = [], [], []
        self._send(msg)

    def _send(self, msg: dict):
        data = json.dumps(msg, separators=(",", ":")).encode("utf-8")
        if len(data) > 65000:
            raise PianuraError(f"command too large for one UDP datagram ({len(data)} bytes): "
                               "draw less per step (clear and redraw only what changes)")
        self.sock.sendto(data, self.server)

    def _receive(self) -> dict:
        data, _ = self.sock.recvfrom(65535)
        msg = json.loads(data.decode("utf-8"))
        if msg.get("type") == "error":
            raise PianuraError(msg.get("message", "unknown error"))
        return msg
