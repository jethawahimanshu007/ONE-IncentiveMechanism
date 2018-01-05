package routing.chitchat;

import java.util.ArrayList;
import java.util.Collection;

public class SocialCharacteristics {
	
	private SocialInterests interests;
	private SocialPatterns patterns;
	private ContactStrength contacts;

	public SocialCharacteristics() {
		interests = new SocialInterests();
		patterns = new SocialPatterns();
	}

	public SocialCharacteristics(SocialInterests i) {
		interests = i;
		patterns = new SocialPatterns();
	}

	public SocialCharacteristics(SocialPatterns p) {
		patterns = p;
		interests = new SocialInterests();
	}
	
	/*
	 * A new connection between two nodes has occurred. Thus, the social
	 * characteristics of the other node, represented here as c, will
	 * induce social patterns in this node.
	 */
	public void connect(SocialCharacteristics c, int addr) {
		patterns.grow(c, addr);
	}

	public SocialPatterns getSocialPatterns() {
		return patterns;
	}

	public SocialInterests getSocialInterests() {
		return interests;
	}

	public boolean hasInterest(String interest) {
		return interests.has(interest);
	}

	public boolean hasPattern(String interest) {
		return patterns.has(interest);
	}

	public double getContentStrength(ArrayList<String> metadata_descriptors) {
		double CeS = 0.0;
		for (String k: metadata_descriptors) {
			double pattern_strength = patterns.get(k);
			//System.out.printf("\t%s => %f\n", k, pattern_strength);
			CeS += pattern_strength;
		}
		//System.out.printf("\tCeS := %f\n", CeS);
		//System.out.println("------------------------------------------");
		return CeS;
	}

	/*
	 * The node with address has disconnected. We should no longer withhold decaying
	 * of this nodes characteristics that are shared with that node. 
	 */
	public void disconnect(int address) {
		patterns.disconnect(address);
	}

	public String getStringInfo() {
		// TODO Auto-generated method stub
		return patterns.getStringInfo();
	}
}
