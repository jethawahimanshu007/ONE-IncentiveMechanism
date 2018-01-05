package routing.chitchat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import routing.util.IncompleteCSV;

public class SocialInterests{
	private static Collection<String> space;
	private Collection<String> interests;
	
	public SocialInterests(Collection<String> i) {
		interests = new HashSet<String>(i);
		//System.out.println("Mobile node has interests:"+interests);
	}


	public SocialInterests() {
		interests = new HashSet<String>();
	}

	public boolean has(String interest) {
		return interests.contains(interest);
	}

	public Collection<String> keys() {
		return interests;
	}
	
	public double get(String k) {
		return interests.contains(k) ? 1.0 : 0.0;
	}
	
	/**
	 * Static factory method for creating SocialInterests object from the
	 *  settings file, given the settings keyword, and the mobile node ID.
	 * @param url
	 * @param id 
	 * @return Social Interests for the given mobile node ID.
	 * @throws IOException Something went wrong with reading the input file.
	 */
	public static SocialInterests buildFromSettingsFile(String url, Integer id) {
		Collection<String> keys = IncompleteCSV.load_from_file(id.toString(), url);
		//System.out.printf("Mobile node #%d has social interests:\n", id);
		///System.out.println(keys);
		//Collection<String> keys=new Collection<String>();
		
		return new SocialInterests(keys);
	}

	public static void generateSpace(int spaceSize) {
		space = new ArrayList<String>(spaceSize);
		for (Integer i = 0; i < spaceSize; i++) {
			space.add(i.toString());
		}
	}

	public static SocialInterests buildFromInterestSpace(int subsetSize) {
		List<String> shuffedSpace = new ArrayList<String>(space);
		Collections.shuffle(shuffedSpace);
		
		Collection<String> keys = new ArrayList<String>(subsetSize);
		for (int i=0; i < subsetSize; i++) {
			keys.add(shuffedSpace.get(i));
		}
		
		//System.out.println("Mobile node has social interests:");
		//System.out.println(keys);
		
		return new SocialInterests(keys);
	}
	
	
}
