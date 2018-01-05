package routing.chitchat.decay;

import core.Settings;
import routing.chitchat.decay.AbstractDecay;

public class LogisticDecay extends AbstractDecay{
	private double beta;
	private double sigma;
	
	public LogisticDecay(Settings s) {
		super(s); // This does nothing!
		beta = s.getDouble("beta");
		sigma = s.getDouble("sigma");
		
		//System.out.printf("beta = %f, sigma = %f\n", beta, sigma);
	}

	@Override
	public double eval(double x) {
		double bts = beta*(x - sigma);
		double denom = 1 + Math.pow(Math.E, bts);
		return 1/denom;
	}	
}