package routing.chitchat.decay;

import core.Settings;
import routing.chitchat.decay.AbstractDecay;

public class LinearDecay extends AbstractDecay{
	private double x_intersection;
	
	public LinearDecay(Settings s) {
		super(s); // This does nothing!
		x_intersection = s.getDouble("secondsToZero");
	}

	@Override
	public double eval(double x) {
		if (x >= x_intersection) {
			return 0;
		}

		return 1 - (x/x_intersection);
	}	
}
