# Copyright (c) 2008-2026 Raffaele Grandi
# SPDX-License-Identifier: MIT

"""
The brain of one robot, as a program of its own: run one per robot (swarm.py does it for you).

    1. run the Pianura example brains (./gradlew run -Pexample=brains): it starts paused
    2. python3 brain.py 3                  (the brain of robot 3; with --host it can run on another
                                          machine, if Pianura was started with --args=--lan)
       or python3 swarm.py 8               (eight brains, one process each)
    3. press Run (or Space) in Pianura

The task is aggregation: the robots look for each other and gather. A brain knows only what
its robot senses and what it hears on the radio. Every half second it broadcasts where it is
and how big its group is (the robots it hears, plus itself). Then:
  - if a neighbour belongs to a bigger group, it goes to that neighbour (groups merge);
  - otherwise it goes to the centre of the robots it hears and stops there;
  - alone, it explores; in a small group that stays small for a while, it leaves to explore,
    so that groups out of radio range of each other can still meet.
Robots out of radio range, or behind a lost message, simply are not there for it.

In Pianura, layer "group" shows where each brain thinks its group is, and the inspector (button
Inspector) shows its state, how many neighbours it hears and how far the centre is.

Replace decide() with your own brain: a neural network, a planner, a language model. In
lockstep mode the simulation waits for every brain at every step, so a slow brain slows the
simulation down but does not change what happens (Pianura waits up to 1 s per answer; start
the example with --args="8 --wait 30" for brains that need longer).
"""

import argparse
import math
import random

from pianura_client import PianuraRobot, RobotStopped

FRONT = (0, 1, 2, 16, 17)          # IR sensors around the heading (index 0 = front, clockwise)
LEFT_SIDE = (13, 14, 15, 16, 17)
RIGHT_SIDE = (1, 2, 3, 4, 5)
NEAR = 1.0                         # IR value (cells from the body) under which an obstacle is "near"
BEACON_PERIOD = 0.5                # s between two radio messages
FORGET_AFTER = 2.0                 # s of silence after which a neighbour is forgotten
CLOSE_ENOUGH = 3.0                 # cells from the centre of the group: stop there
SMALL_GROUP = 3                    # a group of this size or less ...
PATIENCE = 20.0                    # ... that stays so for this many seconds breaks up to explore
EXPLORE_FOR = 12.0                 # s of exploring after leaving a small group


class Brain:
    """What one robot knows and decides. Nothing here is shared with the other brains."""

    def __init__(self, robot_id: int, seed: int = 0):
        self.id = robot_id
        self.rnd = random.Random(seed * 1000 + robot_id)   # deterministic in lockstep mode
        self.neighbours = {}       # id -> (x, y, group size, t heard)
        self.turning = 0           # control periods left in an avoidance turn
        self.turn_dir = 1.0
        self.wander_w = 0.0
        self.next_beacon = 0.0
        self.small_since = None    # when the group became small (None = not small)
        self.explore_until = 0.0

    def listen(self, s):
        for m in s.inbox:
            if isinstance(m.data, dict) and "x" in m.data:
                self.neighbours[m.sender] = (m.data["x"], m.data["y"], m.data.get("size", 1), s.t)
        self.neighbours = {r: n for r, n in self.neighbours.items() if s.t - n[3] <= FORGET_AFTER}

    def group_size(self):
        return len(self.neighbours) + 1

    def decide(self, s):
        """Returns (v, w, state): speed in cells/s, turn rate in rad/s, a word for the inspector."""
        front = min(s.ir[i] for i in FRONT)
        if self.turning > 0:
            self.turning -= 1
            return 0.0, 2.0 * self.turn_dir, "avoiding"
        if front < NEAR or s.contact:
            left = sum(s.ir[i] for i in LEFT_SIDE)
            right = sum(s.ir[i] for i in RIGHT_SIDE)
            self.turn_dir = -1.0 if left > right else 1.0     # negative = counter-clockwise = to the left
            self.turning = self.rnd.randint(3, 8)
            return 0.0, 2.0 * self.turn_dir, "avoiding"
        # a small group that stays small breaks up for a while, to find the others
        if self.neighbours and self.group_size() <= SMALL_GROUP:
            if self.small_since is None:
                self.small_since = s.t
            elif s.t - self.small_since > PATIENCE + self.rnd.uniform(0, 10):
                self.explore_until = s.t + EXPLORE_FOR
                self.small_since = None
        else:
            self.small_since = None
        if self.neighbours and s.t >= self.explore_until:
            bigger = max(self.neighbours.items(), key=lambda kv: (kv[1][2], -kv[0]))
            if bigger[1][2] > self.group_size():
                return self.steer(s, bigger[1][0], bigger[1][1], 1.5) + ("joining a bigger group",)
            cx, cy = self.centre(s)
            if math.hypot(cx - s.x, cy - s.y) < CLOSE_ENOUGH:
                return 0.0, 0.0, "in a group"
            return self.steer(s, cx, cy, 3.0) + ("closing in",)
        # nobody heard, or exploring: a gently changing curve
        self.wander_w = max(-0.8, min(0.8, self.wander_w + self.rnd.uniform(-0.2, 0.2)))
        return 1.5, self.wander_w, "exploring" if s.t < self.explore_until else "looking for others"

    @staticmethod
    def steer(s, x, y, stop_at):
        """Towards (x, y), slowing down near it; stops within stop_at cells."""
        d = math.hypot(x - s.x, y - s.y)
        if d < stop_at:
            return 0.0, 0.0
        e = math.atan2(y - s.y, x - s.x) - s.theta
        e = math.atan2(math.sin(e), math.cos(e))
        return min(1.5, 0.8 * (d - stop_at) + 0.3) * max(0.0, math.cos(e)), 2.5 * e

    def centre(self, s):
        """Centre of the group: this robot and every neighbour heard."""
        xs = [s.x] + [n[0] for n in self.neighbours.values()]
        ys = [s.y] + [n[1] for n in self.neighbours.values()]
        return sum(xs) / len(xs), sum(ys) / len(ys)


def run(robot_id: int, host: str, port: int, seed: int):
    brain = Brain(robot_id, seed)
    t = 0.0
    with PianuraRobot(robot_id, host, port) as robot:
        print(f"brain {robot_id}: connected ({robot.welcome['mode']})", flush=True)
        try:
            while True:
                s = robot.sensors()
                t = s.t
                brain.listen(s)
                if s.t >= brain.next_beacon - 1e-9:
                    robot.broadcast({"x": round(s.x, 2), "y": round(s.y, 2), "size": brain.group_size()})
                    brain.next_beacon += BEACON_PERIOD
                v, w, state = brain.decide(s)
                robot.drive(v, w)

                group = robot.layer("group").clear()
                if brain.neighbours:
                    cx, cy = brain.centre(s)
                    group.line(s.x, s.y, cx, cy, color=(200, 80, 30, 140), width=1)
                    group.circle(cx, cy, 0.4, color=(200, 80, 30))
                    robot.inspect.plot("distance from the group (cells)", math.hypot(cx - s.x, cy - s.y))
                robot.inspect.state(state).watch("neighbours heard", len(brain.neighbours)).watch("group size", brain.group_size())
                robot.inspect.plot("neighbours heard", len(brain.neighbours))
        except RobotStopped:
            print(f"brain {robot_id}: simulation stopped at t={t:.2f} s", flush=True)


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="The brain of one Pianura robot.")
    ap.add_argument("id", type=int, help="robot id")
    ap.add_argument("--host", default="127.0.0.1", help="where Pianura runs")
    ap.add_argument("--port", type=int, default=5555)
    ap.add_argument("--seed", type=int, default=0)
    a = ap.parse_args()
    try:
        run(a.id, a.host, a.port, a.seed)
    except KeyboardInterrupt:
        pass
