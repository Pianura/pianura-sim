# Copyright (c) 2008-2026 Raffaele Grandi
# SPDX-License-Identifier: MIT

"""
Conformance check for anything that speaks Pianura's protocol: the simulator today, a physical
robot tomorrow. It connects as a client, reads the welcome message and a few sensor readings,
drives a little, and checks that what the robot declares is what it does.

    1. start a Pianura with a remote robot (./gradlew run -Pexample=remote), or switch a robot on
    2. python3 conformance.py                 (robot 1 on 127.0.0.1:5555; --robot, --host, --port)
    3. press Run in Pianura, if it is paused

Every check prints a line; the exit status is 0 only if none failed. The robot moves: about
half a cell forwards, then it turns a little, then it stops.

What is checked:
  - welcome: protocol, mode, period, units, body
  - descriptor: kind, description, capabilities (sensors, actuators, features)
  - sensors: every sensor declared is in the readings, with the declared size and range, and
    nothing undeclared is there
  - driving: every declared drive mode is accepted, time and sequence go forward, the wheels turn
  - a physical robot (kind "bot") is real time and does not claim a true pose
"""

import argparse
import math
import sys

from pianura_client import PROTOCOL_VERSION, PianuraError, PianuraRobot, RobotStopped

KNOWN_SENSORS = ("pose", "ir", "bump", "contact", "floor", "wheels")
KNOWN_FEATURES = ("radio", "draw", "inspector")
KNOWN_MODES = ("v_w", "wheels")


class Report:
    def __init__(self):
        self.failed = 0
        self.passed = 0

    def check(self, ok, what, detail=""):
        if ok:
            self.passed += 1
            print(f"  ok    {what}")
        else:
            self.failed += 1
            print(f"  FAIL  {what}" + (f": {detail}" if detail else ""))
        return ok

    def note(self, text):
        print(f"  note  {text}")


def number(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v)


def check_welcome(rep, w):
    print("welcome")
    rep.check(w.get("protocol") == PROTOCOL_VERSION, f"protocol is {PROTOCOL_VERSION}", repr(w.get("protocol")))
    rep.check(w.get("mode") in ("lockstep", "realtime"), "mode is lockstep or realtime", repr(w.get("mode")))
    rep.check(number(w.get("period")) and w["period"] > 0, "period is a positive number of seconds", repr(w.get("period")))
    u = w.get("units") or {}
    rep.check(u.get("length") == "cell" and u.get("time") == "s" and u.get("angle") == "rad",
              "units are cells, seconds and radians", repr(u))
    rep.check(number(u.get("meters_per_cell")) and u["meters_per_cell"] > 0, "meters_per_cell is positive", repr(u.get("meters_per_cell")))
    b = w.get("body") or {}
    for k in ("radius", "wheel_base", "wheel_radius"):
        rep.check(number(b.get(k)) and b[k] > 0, f"body.{k} is positive", repr(b.get(k)))


def check_descriptor(rep, w):
    print("descriptor")
    kind = w.get("kind")
    rep.check(kind in ("sim", "bot"), 'kind is "sim" or "bot"', repr(kind))
    d = w.get("description")
    rep.check(isinstance(d, str) and len(d.strip()) >= 10, "description is a sentence", repr(d))
    caps = w.get("capabilities")
    if not rep.check(isinstance(caps, dict), "capabilities is an object", type(caps).__name__):
        return {}
    sensors = caps.get("sensors")
    rep.check(isinstance(sensors, dict) and len(sensors) > 0, "capabilities.sensors lists the sensors")
    drive = (caps.get("actuators") or {}).get("drive") or {}
    modes = drive.get("modes")
    rep.check(isinstance(modes, list) and len(modes) > 0 and all(m in KNOWN_MODES for m in modes),
              "actuators.drive.modes are among v_w and wheels", repr(modes))
    for lim in ("v_max", "w_max", "wheel_max"):
        if lim in drive:
            rep.check(number(drive[lim]) and drive[lim] > 0, f"actuators.drive.{lim} is positive", repr(drive[lim]))
    feats = caps.get("features")
    rep.check(isinstance(feats, list) and all(isinstance(f, str) for f in feats), "features is a list of names", repr(feats))
    for f in feats or []:
        if f not in KNOWN_FEATURES:
            rep.note(f'feature "{f}" is not one this check knows')
    for name in sensors or {}:
        if name not in KNOWN_SENSORS:
            rep.note(f'sensor "{name}" is not one this check knows')

    b = w.get("body") or {}
    ir = (sensors or {}).get("ir")
    if ir is not None:
        rep.check(isinstance(ir.get("count"), int) and ir["count"] > 0, "ir.count is a positive integer", repr(ir.get("count")))
        rep.check(number(ir.get("range")) and ir["range"] > 0, "ir.range is positive", repr(ir.get("range")))
        rep.check(ir.get("unit") == "cell", 'ir.unit is "cell"', repr(ir.get("unit")))
        rep.check(isinstance(ir.get("angles"), list) and len(ir["angles"]) == ir.get("count"),
                  "ir.angles has one direction per sensor")
        if "ir_count" in b:
            rep.check(b["ir_count"] == ir.get("count") and b.get("ir_range") == ir.get("range"),
                      "body.ir_count and body.ir_range agree with capabilities", f'{b.get("ir_count")}, {b.get("ir_range")}')
    bump = (sensors or {}).get("bump")
    if bump is not None:
        rep.check(isinstance(bump.get("count"), int) and bump["count"] > 0, "bump.count is a positive integer", repr(bump.get("count")))
        rep.check(isinstance(bump.get("angles"), list) and len(bump["angles"]) == bump.get("count"),
                  "bump.angles has one direction per sensor")
    floor = (sensors or {}).get("floor")
    if floor is not None:
        rep.check(isinstance(floor.get("count"), int) and floor["count"] >= 0, "floor.count is an integer", repr(floor.get("count")))
        rep.check(number(floor.get("min")) and number(floor.get("max")) and floor["min"] < floor["max"], "floor.min is below floor.max")
        if "floor_count" in b:
            rep.check(b["floor_count"] == floor.get("count"), "body.floor_count agrees with capabilities", repr(b["floor_count"]))
    pose = (sensors or {}).get("pose")
    if pose is not None:
        rep.check(pose.get("source") in ("truth", "odometry", "none"), "pose.source is truth, odometry or none", repr(pose.get("source")))

    if kind == "bot":
        rep.check(w.get("mode") == "realtime", "a physical robot is real time")
        rep.check((pose or {}).get("source") != "truth", "a physical robot does not claim a true pose")
    return caps


def check_reading(rep, caps, raw):
    print("sensor reading")
    sensors = caps.get("sensors") or {}
    for name in KNOWN_SENSORS:
        declared = name in sensors and not (name == "pose" and sensors["pose"].get("source") == "none")
        present = name in raw
        if declared:
            rep.check(present, f'"{name}" is declared and is in the reading')
        else:
            rep.check(not present, f'"{name}" is not declared and is not in the reading')
    if "ir" in sensors and "ir" in raw:
        ir, rng = raw["ir"], sensors["ir"].get("range", 0)
        rep.check(len(ir) == sensors["ir"].get("count"), "ir has the declared number of readings", str(len(ir)))
        rep.check(all(number(v) and -1e-9 <= v <= rng + 1e-9 for v in ir), "every ir reading is between 0 and the range", repr(ir))
    if "bump" in sensors and "bump" in raw:
        rep.check(len(raw["bump"]) == sensors["bump"].get("count") and all(isinstance(v, bool) for v in raw["bump"]),
                  "bump has the declared number of true/false readings")
    if "contact" in sensors and "contact" in raw:
        rep.check(isinstance(raw["contact"], bool), "contact is true or false")
    if "floor" in sensors and "floor" in raw:
        f = sensors["floor"]
        rep.check(len(raw["floor"]) == f.get("count"), "floor has the declared number of readings", str(len(raw["floor"])))
        rep.check(all(number(v) and f.get("min", 0) <= v <= f.get("max", 255) for v in raw["floor"]), "every floor reading is in its range")
    if "wheels" in sensors and "wheels" in raw:
        rep.check(number(raw["wheels"].get("left")) and number(raw["wheels"].get("right")), "wheels has left and right travel")
    if "pose" in raw:
        rep.check(all(number(raw["pose"].get(k)) for k in ("x", "y", "theta")), "pose has x, y and theta")
    if "radio" not in (caps.get("features") or []):
        rep.check(not raw.get("inbox"), "no radio: the inbox is empty or absent")
    rep.check(number(raw.get("t")) and isinstance(raw.get("seq"), int), "the reading has t and seq")


def check_driving(rep, robot, caps, steps):
    print("driving")
    drive = (caps.get("actuators") or {}).get("drive") or {}
    modes = [m for m in drive.get("modes", []) if m in KNOWN_MODES]
    v = min(0.5, drive.get("v_max", 0.5))
    wheel = min(0.5, drive.get("wheel_max", 0.5))
    first = robot.sensors()
    last = first
    monotonic = True
    try:
        for mode in modes:
            for _ in range(steps):
                if mode == "v_w":
                    robot.drive(v, 0.0)
                else:
                    robot.wheels(wheel, 0.6 * wheel)          # a gentle turn to the right
                s = robot.sensors()
                monotonic &= s.t >= last.t and s.seq > last.seq
                last = s
            rep.check(True, f'drive mode "{mode}" is accepted')
    except PianuraError as e:
        rep.check(False, "every declared drive mode is accepted", str(e))
    finally:
        robot.drive(0.0, 0.0)                                 # always leave the robot still
    rep.check(monotonic, "time does not go back and seq grows")
    if "wheels" in (caps.get("sensors") or {}) and modes and not math.isnan(last.wheel_left):
        rep.check(last.wheel_left > first.wheel_left and last.wheel_right > first.wheel_right,
                  "the wheels turned forwards while driving forwards",
                  f"left {first.wheel_left:.3f} -> {last.wheel_left:.3f}, right {first.wheel_right:.3f} -> {last.wheel_right:.3f}")
    if not modes:
        rep.check(False, "at least one known drive mode to try")


def run(robot_id, host, port, steps, timeout):
    rep = Report()
    with PianuraRobot(robot_id, host, port, timeout=timeout) as robot:
        w = robot.welcome
        print(f'robot {robot_id} at {host}:{port}: {w.get("kind", "?")}, {w.get("mode", "?")}')
        check_welcome(rep, w)
        caps = check_descriptor(rep, w)
        if caps:
            print("waiting for the first reading (press Run in Pianura if it is paused)", flush=True)
            first = robot.sensors()
            check_reading(rep, caps, first.raw)
            robot.drive(0.0, 0.0)
            check_driving(rep, robot, caps, steps)
    print(f"{rep.passed} passed, {rep.failed} failed: " + ("CONFORMS" if rep.failed == 0 else "DOES NOT CONFORM"))
    return rep.failed == 0


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="Checks that a simulator or a robot does what its welcome message declares.")
    ap.add_argument("--robot", type=int, default=1, help="robot id (default 1)")
    ap.add_argument("--host", default="127.0.0.1", help="where Pianura, or the robot, is")
    ap.add_argument("--port", type=int, default=5555)
    ap.add_argument("--steps", type=int, default=10, help="control periods per drive mode (default 10)")
    ap.add_argument("--timeout", type=float, default=30.0, help="seconds to wait for a message")
    a = ap.parse_args()
    try:
        sys.exit(0 if run(a.robot, a.host, a.port, a.steps, a.timeout) else 1)
    except RobotStopped:
        print("the robot stopped before the check was over")
        sys.exit(1)
    except (PianuraError, OSError) as e:
        print(f"cannot run the check: {e}")
        sys.exit(1)
