package dv106.pp222es.georienteering2;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

public class FileUtils {	
	
	private static final String LOG = "FileUtils";	
	
	/**	
	 * Writes all the strings in a list to file, where strings are encoded in modified UTF-8
	 * @param context
	 * @param text_lines
	 * @param file_name
	 * @return true if saving was successful
	 * REF: https://mymoodle.lnu.se/mod/resource/view.php?id=420382
	 */
	public static boolean writeListOfStringToFile(Context context, List<String> text_lines, String file_name) {
		
		DataOutputStream dout = null;
		boolean result = true;
		
		try {			 
			FileOutputStream output = context.openFileOutput(file_name, Context.MODE_PRIVATE); //Modes: MODE_PRIVATE, MODE_APPEND 
			dout = new DataOutputStream(output);
			
			dout.writeInt(text_lines.size()); // Save line count 
			for (String line : text_lines) // Save lines 
				dout.writeUTF(line);

			dout.flush(); // Flush stream ... 			
		} 
		catch (FileNotFoundException e) {
			e.printStackTrace();	
			result = false;
		}		
		catch (IOException exc) { 
			exc.printStackTrace();	
			result = false;
		}		
		finally {
			try {
				// Close stream
				if (dout != null) dout.close();
			} catch (IOException e) {				
				e.printStackTrace();
				result = false;
			} 
		}
		return result;
	}
	
	/**
	 * Reads strings encoded in modified UTF-8 into a list from a file
	 * @param context
	 * @param file_name
	 * @return text_lines
	 * REF: https://mymoodle.lnu.se/mod/resource/view.php?id=420382
	 */
   	public static List<String> readFromFileToListOfString(Context context, String file_name) { 
   		
   		List<String> text_lines = new ArrayList<String>();
   		DataInputStream din = null;   		
   		
   		try {
   			FileInputStream input = context.openFileInput(file_name); 
   			din = new DataInputStream(input);
   			
   			int size = din.readInt(); // Read line count
   			for (int i=0; i<size; i++) { // Read lines
   				String line = din.readUTF();
   				text_lines.add(line); // Add to list
   			}   			
   		}
   		catch (IOException exc) { 
   			exc.printStackTrace(); 
   		}
   		finally {
   			try {
   				// Close stream   			
   				if (din != null) din.close();
   			} catch (IOException e) {   				
   				e.printStackTrace();				
   			} 
   		}   		
   		return text_lines;
   	} 
   	
   	/**
   	 * Reads lines from raw resource file into a list
   	 * @param resources
   	 * @param resId
   	 * @return text_lines
   	 */
   	public static List<String> readFromRawResourceToListOfString(Resources resources, int resId) {
		
   		String line;
		List<String> text_lines = new ArrayList<String>();
		
		InputStream rawResource = resources.openRawResource(resId);		
		BufferedReader reader = new BufferedReader(new InputStreamReader(rawResource));
						
		try {
			while ((line = reader.readLine()) != null) {
//				Log.v(LOG, "line: " + line);				
				text_lines.add(line);				
			}
		} 
		catch (IOException e) {
			e.printStackTrace();
		} 
		finally {
   			try {
   				rawResource.close();
   			}
   			catch (IOException e) {
   				e.printStackTrace();
   			}
   		}
   
		return text_lines;
	}

}
