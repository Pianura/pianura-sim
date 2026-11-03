package io.github.raffaelegrandi.pianura.examples.rooms;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.Simulator;

public class Main {

	public static void main(String[] args) {
		Simulator sim = new Simulator(new Point(30, 30), "Pianura - arena 80x50", new RoomsScenario());
		sim.startSimPaused();	// parte in pausa: sistemare la scena, poi Run (o Spazio)
	}

}
