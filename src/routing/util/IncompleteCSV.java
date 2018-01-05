package routing.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class IncompleteCSV {
	public static Collection<String> load_from_file(String id, String url){	
		Path path = Paths.get(url);
		List<String> lines = null;
		Collection<String> keys = new HashSet<String>();
		
		try {
			lines = Files.readAllLines(path, StandardCharsets.UTF_8);
			
			String cvsDelimiter = ";";
			for (String line: lines) {
				if (line.isEmpty()) {
					// Do nothing.
				} else {
					boolean line_is_not_comment = (line.charAt(0) != '#');
					if (line_is_not_comment) {
						String[] line_entries = line.split(cvsDelimiter);
						String key_from_file = line_entries[0];

						boolean is_key_of_interest = (key_from_file.equals(id));
						if (is_key_of_interest) {
							keys.add(line_entries[1]);
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return keys;
	}
}
