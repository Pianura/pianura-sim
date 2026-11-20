# Copyright (c) 2008-2026 Raffaele Grandi
# SPDX-License-Identifier: MIT

"""
Starts one brain process per robot, for the Pianura example brains.

    1. ./gradlew run -Pexample=brains          (8 robots; --args=12 for twelve)
    2. python3 swarm.py 8                      (brains of robots 1..8, one process each)
    3. press Run (or Space) in Pianura

Every brain is a separate program (brain.py) with its own memory: they share nothing except
what travels on Pianura's radio. Ctrl+C stops them all. Other options are passed on to every
brain, e.g. python3 swarm.py 8 --host 192.168.1.20 to drive a Pianura running on another
machine (started there with ./gradlew run -Pexample=brains --args="8 --lan").
"""

import os
import subprocess
import sys


def main():
    if len(sys.argv) < 2 or not sys.argv[1].isdigit():
        print("usage: python3 swarm.py N [brain options]   (brains of robots 1..N)")
        sys.exit(2)
    n = int(sys.argv[1])
    extra = sys.argv[2:]
    brain = os.path.join(os.path.dirname(os.path.abspath(__file__)), "brain.py")
    procs = [subprocess.Popen([sys.executable, brain, str(i)] + extra) for i in range(1, n + 1)]
    try:
        for p in procs:
            p.wait()
    except KeyboardInterrupt:
        for p in procs:
            p.terminate()


if __name__ == "__main__":
    main()
