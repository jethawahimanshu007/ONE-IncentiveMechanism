package routing.chitchat.decay;

import core.Settings;

public abstract class AbstractDecay {
	public AbstractDecay(Settings s) {}
	abstract public double eval(double x);
}
