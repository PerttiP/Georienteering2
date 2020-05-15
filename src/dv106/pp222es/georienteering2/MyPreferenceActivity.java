package dv106.pp222es.georienteering2;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;


/**
 * MyPreferenceActivity
 * @author Pertti Palokangas
 *
 * The PreferenceActivity class is used to host the Preference Fragment hierarchy. 
 * Like all Activities, the Preference Activity must be included in the application manifest.
 * 
 * Note that Preference Fragment is not supported on Android platforms prior to Android 3.0 (API level 11). 
 * 
 * IMPORTANT:
 * If you're using PreferenceFragments, be aware that you should use the findPreference() method 
 * on your PreferenceFragment instance rather than from a PreferenceActivity.
 *				
 */

public class MyPreferenceActivity extends PreferenceActivity {

	SharedPreferences prefs = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getFragmentManager().beginTransaction().replace(android.R.id.content, 
				new MyPreferenceFragment()).commit();
	}	
	
}
