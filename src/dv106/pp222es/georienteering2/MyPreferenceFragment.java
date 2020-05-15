package dv106.pp222es.georienteering2;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

/**
 * MyPreferenceFragment
 * @author Pertti Palokangas
 *
 * NOTE: If you're developing for Android 3.0 (API level 11) and higher, you should use a PreferenceFragment 
 * to display your list of Preference objects. 
 * You can add a PreferenceFragment to any activity—you don't need to use PreferenceActivity.
 */

public class MyPreferenceFragment extends PreferenceFragment {
	
	SharedPreferences prefs = null;
	
	private static final String LOG = "MyPreferenceFragment";
	
	private EditText myLatitude;
	private EditText myLongitude;
	
	private Context appContext;	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		
		// Inflate the given XML resource and add the preference hierarchy to the current preference hierarchy
		addPreferencesFromResource(R.xml.preferences);
		
		appContext = getActivity().getApplicationContext();
	}
			
	@Override
	public void onResume() {
		super.onResume();
				
		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

		// Set up a listener that triggers whenever a key changes
		prefs.registerOnSharedPreferenceChangeListener(listener);
	}
	
	@Override
	public void onPause() {	    
		super.onPause();
		
		// Unregister the listener that triggered whenever a key changed
		prefs.unregisterOnSharedPreferenceChangeListener(listener);		    
	}
	
	public void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {		
		default:
			super.onActivityResult(requestCode, resultCode, data);
			
			Log.i(LOG, "onActivityResult, requestCode= " + requestCode + " resultCode=" + resultCode);
			
//			if (resultCode == Activity.RESULT_OK) {
//				
//			}
			
			break;
		}
	}
	
//	@Override
//    protected void onSetInitialValue (boolean restorePersistedValue, Object defaultValue) {
//        //setText(restoreValue ? getPersistedString(mText) : (String) defaultValue);
//    }
	
	OnSharedPreferenceChangeListener listener = new OnSharedPreferenceChangeListener() {
		public void onSharedPreferenceChanged(
			SharedPreferences sharedPreferences, String key) {
			
			/*
			 * If you're using PreferenceFragments, be aware that you should use the findPreference() method 
			 * on your PreferenceFragment instance rather than from a PreferenceActivity.
			 *		
			 */
			
			if (key.equals("latitude_pref_key")) {
				
				Log.i(LOG, "onSharedPreferenceChanged: latitude_pref_key");

				EditTextPreference myLatitudePreference = (EditTextPreference) findPreference("latitude_pref_key");
				myLatitude = (EditText) myLatitudePreference.getEditText();				
							
				if (!validateLatitude(myLatitude)) {
					Log.e(LOG, "Validation of latitude failed in MyPreferenceFragment");
					// Notify user
					Toast.makeText(appContext, R.string.location_input_error_missing, Toast.LENGTH_LONG).show();
				}
			}
			else if (key.equals("longitude_pref_key")) {
				
				Log.i(LOG, "onSharedPreferenceChanged: longitude_pref_key");

				EditTextPreference myLongitudePreference = (EditTextPreference) findPreference("longitude_pref_key");
				myLongitude = (EditText) myLongitudePreference.getEditText();
				if (!validateLongitude(myLongitude)) {
					Log.e(LOG, "Validation of longitude failed in MyPreferenceFragment");
					// Notify user
					Toast.makeText(appContext, R.string.location_input_error_missing, Toast.LENGTH_LONG).show();
				}				
			}		
		}
	};

	/**
	 * Validate the input values and mark those that are incorrect with red.
	 * @return true if all the widget values are correct.
	 */
	private boolean validateLatitude(EditText latitude) {
		// Start with the input validity flag set to true
		boolean inputOK = true;  		 
		
		/*
		 * Latitude, longitude can't be empty.
		 * If they are, highlight the input field in RED and put a Toast message in the UI. 
		 * Otherwise set the input field highlight to GREEN, ensuring that a field that was formerly wrong is reset.
		 */
		if (TextUtils.isEmpty(latitude.getText()) || (latitude.getText().toString() == "" )) {
			latitude.setBackgroundColor(Color.RED);
			Toast.makeText(appContext, R.string.location_input_error_missing, Toast.LENGTH_LONG).show();

			// Set the input validity to false
			inputOK = false;
		} 
		else {
			latitude.setBackgroundColor(Color.GREEN);
		}		

		/*
		 * If all the input fields have been entered, test to ensure that their values are within
		 * the acceptable range. The tests can't be performed until it's confirmed that there are
		 * actual values in the fields.
		 */
		if (inputOK) {

			/*
			 * Get values from the latitude, longitude fields.
			 */
			double lat = Double.valueOf(latitude.getText().toString());			

			/*
			 * Test latitude and longitude for minimum and maximum values. Highlight incorrect
			 * values and set a Toast in the UI.
			 */

			if (lat > GeofenceUtils.MAX_LATITUDE || lat < GeofenceUtils.MIN_LATITUDE) {
				latitude.setBackgroundColor(Color.RED);
				Toast.makeText(
						appContext,
						R.string.location_input_error_latitude_invalid,
						Toast.LENGTH_LONG).show();

				// Set the input validity to false
				inputOK = false;
			} 
			else {
				latitude.setBackgroundColor(Color.GREEN);
			}			
		}

		// If everything passes, the validity flag will still be true
		return inputOK;
	}
	
	/**
	 * Validate the input values and mark those that are incorrect with red.
	 * @return true if all the widget values are correct.
	 */
	private boolean validateLongitude(EditText longitude) {
		// Start with the input validity flag set to true
		boolean inputOK = true;  		 
		
		/*
		 * Latitude, longitude can't be empty.
		 * If they are, highlight the input field in RED and put a Toast message in the UI. 
		 * Otherwise set the input field highlight to GREEN, ensuring that a field that was formerly wrong is reset.
		 */		
		if (TextUtils.isEmpty(longitude.getText()) || (longitude.getText().toString() == "" )   ) {
			longitude.setBackgroundColor(Color.RED);
			Toast.makeText(appContext, R.string.location_input_error_missing, Toast.LENGTH_LONG).show();

			// Set the input validity to false
			inputOK = false;
		} 
		else {
			longitude.setBackgroundColor(Color.GREEN);
		}

		/*
		 * If all the input fields have been entered, test to ensure that their values are within
		 * the acceptable range. The tests can't be performed until it's confirmed that there are
		 * actual values in the fields.
		 */
		if (inputOK) {

			/*
			 * Get values from the latitude, longitude fields.
			 */			
			double lng = Double.valueOf(longitude.getText().toString());

			/*
			 * Test latitude and longitude for minimum and maximum values. Highlight incorrect
			 * values and set a Toast in the UI.
			 */			
			if ((lng > GeofenceUtils.MAX_LONGITUDE) || (lng < GeofenceUtils.MIN_LONGITUDE)) {
				longitude.setBackgroundColor(Color.RED);
				Toast.makeText(
						appContext,
						R.string.location_input_error_longitude_invalid,
						Toast.LENGTH_LONG).show();

				// Set the input validity to false
				inputOK = false;
			} 
			else {
				longitude.setBackgroundColor(Color.GREEN);
			}
		}

		// If everything passes, the validity flag will still be true
		return inputOK;
	}
	
}
