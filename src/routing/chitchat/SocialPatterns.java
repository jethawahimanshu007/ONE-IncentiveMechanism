package routing.chitchat;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import core.SimClock;

import routing.chitchat.decay.AbstractDecay;

public class SocialPatterns {
	private HashMap<String, Double> patternStrengths;
	private AbstractDecay decayModel;
	private HashMap<String, Integer> timeOfLastGrowth;
	
	private HashMap<Integer, Collection<String>> patternsOfConnected;
	private HashMap<Integer, Collection<String>> interestsOfConnected;
	
	private HashMap<String, Double> lockstep_patterns;
	private int lockstep_time;
	
	
	public SocialPatterns() {
		patternStrengths = new HashMap<String, Double>();
		timeOfLastGrowth = new HashMap<String, Integer>(); 
		patternsOfConnected = new HashMap<Integer, Collection<String>>();
		interestsOfConnected = new HashMap<Integer, Collection<String>>();
		lockstep_time = SimClock.getIntTime();
		lockstep_patterns = new HashMap<String, Double>();
	}
	
	public SocialPatterns(HashMap<String, Double> patterns) {
		patternStrengths = new HashMap<String, Double>(patterns);
		timeOfLastGrowth = new HashMap<String, Integer>();
		patternsOfConnected = new HashMap<Integer, Collection<String>>();
		interestsOfConnected = new HashMap<Integer, Collection<String>>();
		lockstep_time = SimClock.getIntTime();
		lockstep_patterns = new HashMap<String, Double>(patterns);
	}

	public double getDecayedStrength(int time_delta, String key) {
		double current_weight = patternStrengths.get(key);
		double decay_weight = decayModel.eval(time_delta);
		double decayed_value = decay_weight*current_weight;
		return decayed_value;
	}
	
	/*
	 * When a new connection is established between two nodes, the social
	 * characteristics of the other node, represented here as c, induce
	 * changes to the social characteristics of this node.
	 * 
	 */
	public void grow(SocialCharacteristics c, int addr) {
		SocialPatterns p = c.getSocialPatterns();
		SocialInterests i = c.getSocialInterests();
		
		// Decay the patterns that are in the intersection of this node and
		//  the social patterns and social interests of the other node.
		decay(p, i);
		
		// If these keys were present in the connected set before, then
		//  decaying has been withheld up to now and thus the order of
		//  connect and decay does not matter.
		// However, if these keys were not present in the connected set before,
		//  then the pattern may not have decayed since the last growth event.
		//  Thus we need to decay them before we connect. If we connect first,
		//  then decay() will not perform the necessary decay because the key
		//  is in the connected set.
		connect(addr, p, i);
		
		// The keys that undergo growth must be "refreshed" in the sense that
		//  the time of their most recent growth must be recorded.
		recordGrowthTime(p, i);
		
		double current_strength;
		double v_pattern_strength;
		double v_interest_strength;
		double growth_delta;
		
		for (String k: p.keys()) {
			// Decay the pattern according to the last time it was grown.
			current_strength = get_actual(k);
			v_pattern_strength = p.get(k);
			v_interest_strength = i.get(k);
			growth_delta = (1-current_strength) * ((v_interest_strength/2)+(v_pattern_strength/4));
			
			patternStrengths.put(k, growth_delta + current_strength);
		}
		for (String k: i.keys()) {
			// Decay the pattern according to the last time it was grown.
			current_strength = get_actual(k);
			v_pattern_strength = p.get(k);
			v_interest_strength = i.get(k);
			growth_delta = (1-current_strength) * ((v_interest_strength/2)+(v_pattern_strength/4));
			
			patternStrengths.put(k, growth_delta + current_strength);
		}
	}

	private void connect(int addr, SocialPatterns p, SocialInterests i) {
		patternsOfConnected.put(addr, p.keys());
		interestsOfConnected.put(addr, i.keys());
	}

	/*
	 * Decay only the patterns in this node that are the intersection of
	 * this node's patterns and the connecting node's patterns and interests.
	 */
	private void decay(SocialPatterns p, SocialInterests i) {
		// Build up a set of interest and pattern keys from the connected node
		Collection<String> keysToRetain = new HashSet<String>(p.keys());
		keysToRetain.addAll(i.keys());
		
		// Remove from this set the patterns and interests of nodes that have
		//  previously connected to this node and are still connected.
		for (Entry<Integer, Collection<String>> cursor: patternsOfConnected.entrySet()) {
			keysToRetain.removeAll(cursor.getValue());
		}
		for (Entry<Integer, Collection<String>> cursor: interestsOfConnected.entrySet()) {
			keysToRetain.removeAll(cursor.getValue());
		}
		
		// Compute the intersection between this node's soicla patterns
		//  and the social interests and social patterns of the other node.
		Collection<String> residentKeysToDecay = new HashSet<String>(patternStrengths.keySet());
		residentKeysToDecay.retainAll(keysToRetain);
		keysToRetain.clear();
		
		// Decay patterns based on how long it has been since they underwent growth.
		//  Do not decay patterns that have already been grown this second.
		int t = SimClock.getIntTime();
		for (String k: residentKeysToDecay) {
			int time_of_last_growth = timeOfLastGrowth.get(k);
			if (t > time_of_last_growth) {
				int time_delta = t-time_of_last_growth;
				double decayedStrength = getDecayedStrength(time_delta, k);
				patternStrengths.put(k, decayedStrength);
			}
		}
	}

	private void recordGrowthTime(SocialPatterns p, SocialInterests i) {
		int t = SimClock.getIntTime();
		for(String k: p.keys()) {
			timeOfLastGrowth.put(k, t);
		}
		for(String k: i.keys()) {
			timeOfLastGrowth.put(k, t);
		}
	}

	public Collection<String> keys() {
		return patternStrengths.keySet();
	}

	public boolean has(String k) {
		return patternStrengths.containsKey(k);
	}

	public double get(String k) {
		if (lockstep_time < SimClock.getIntTime()) {
			//System.out.printf("Time has changed from %d to %d\n", lockstep_time, SimClock.getIntTime());
			lockstep_patterns.putAll(patternStrengths);
			lockstep_time = SimClock.getIntTime();
		}
		
		return (lockstep_patterns.containsKey(k) ? lockstep_patterns.get(k) : 0);
	}
	
	private double get_actual(String k) {
		return (patternStrengths.containsKey(k) ? patternStrengths.get(k) : 0);
	}

	public void setDecayModel(AbstractDecay l) {
		decayModel = l;
		//System.out.printf("Social Pattern decay model set to '%s'\n", l.getClass().getName());
	}

	/*
	 * When a node disconnects from this node, the disconnected node's
	 *  social characteristics need to reflect this last time of growth.
	 */
	public void disconnect(int address) {
		int t = SimClock.getIntTime();
		Collection<String> p = patternsOfConnected.remove(address);
		if (p != null) {
			for (String k: p) {
				timeOfLastGrowth.put(k, t);
			}
		}
		
		//interestsOfConnected.remove(address);
		Collection<String> i = interestsOfConnected.remove(address);
		if (i != null) {
			for (String k: i) {
				timeOfLastGrowth.put(k, t);
			}
		}
	}

	public String getStringInfo() {
		return "\n    Patterns:              " + patternStrengths.size() + "\n" +
		       "    Last growth times:     " + timeOfLastGrowth.size() + "\n" +
		       "    Patterns of Connected: " + getInfoOnConnected(patternsOfConnected) + "\n" +
		       "    Interests of Connectd: " + getInfoOnConnected(interestsOfConnected) + "\n" +
		       "    LockStep Patterns:     " + lockstep_patterns.size();
	}

	private String getInfoOnConnected(Map<Integer, Collection<String>> m) {
		// TODO Auto-generated method stub
		Integer size = 0;
		for (int i: m.keySet()) {
			size += m.get(i).size();
		}
		return m.size() + " (" + size.toString() + ")";
	}
}
