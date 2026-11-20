// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.examples.brains;

import java.awt.Point;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import io.github.raffaelegrandi.pianura.Simulator;
import io.github.raffaelegrandi.pianura.remote.RemoteServer;

/**
 * One brain per robot: start this, then {@code python3 clients/python/swarm.py 8} starts one
 * brain process per robot; press Run (or Space).
 *
 * <p>Arguments, all optional ({@code ./gradlew run -Pexample=brains --args="12 --lan --wait 30"}):
 * <ul>
 * <li>the number of robots: 8 by default, up to {@link BrainsScenario#MAX_ROBOTS};</li>
 * <li>{@code --lan}: accept brains from other machines too (otherwise only from this computer);</li>
 * <li>{@code --wait S}: how long Pianura waits for a brain's answer at each step, in seconds
 * (1 by default). After that the robot keeps its last command: give slow brains, such as a
 * language model, the time they need.</li>
 * </ul>
 */
public class Main {

	public static void main(String[] args) throws Exception {
		int n = 8;
		boolean lan = false;
		long waitMs = RemoteServer.DEFAULT_REPLY_TIMEOUT_MS;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--lan")) lan = true;
			else if (args[i].equals("--wait") && i + 1 < args.length) waitMs = Math.round(Double.parseDouble(args[++i]) * 1000);
			else n = Integer.parseInt(args[i]);
		}
		InetSocketAddress bind = lan ? new InetSocketAddress(RemoteServer.DEFAULT_PORT)
			: new InetSocketAddress(InetAddress.getLoopbackAddress(), RemoteServer.DEFAULT_PORT);
		RemoteServer server = RemoteServer.start(bind, RemoteServer.Mode.LOCKSTEP, waitMs);
		Simulator sim = new Simulator(new Point(30, 30), "Pianura - one brain per robot", new BrainsScenario(server, n));
		sim.attach(server);
		sim.getViewOptions().commLinks = true;
		sim.printLogMsg(false, "Waiting for " + n + " brains" + (lan ? " (also from the network)" : "")
			+ ": python3 swarm.py " + n + " in clients/python, or one python3 brain.py <id> per robot - then press Run");
		sim.startSimPaused();
	}

}
