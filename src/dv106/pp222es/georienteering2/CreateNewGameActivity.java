package dv106.pp222es.georienteering2;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import dv106.pp222es.georienteering2.GeofenceUtils; //NOTE: Changed to georienteering2 for Supernova

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * CreateNewGameActivity
 * @author Pertti
 * 
 */
public class CreateNewGameActivity extends Activity 
	implements
		GooglePlayServicesClient.ConnectionCallbacks,
		GooglePlayServicesClient.OnConnectionFailedListener,
		LocationListener {
	
	private static final String LOG = "CreateNewGameActivity";
	
	// Location update frequency in seconds
    public static final int UPDATE_INTERVAL_IN_SECONDS = 2; 

    // The fastest location update frequency, in seconds
    private static final int FASTEST_INTERVAL_IN_SECONDS = 1;
	
	// An object that holds accuracy and frequency parameters for the location client	
	private LocationRequest locationRequest;
		
	// An object that holds the current instantiation of the location client
	private LocationClient locationClient;
	    
	private Location currentLocation;
	
	private EditText latitude;	
	private EditText longitude;
	
	private TextView label_latitude;
	private TextView label_longitude;
	
	// Default location coordinates
//	private double dLatitude = 0.0d; //LocationUtils.DEFAULT_LOCATION_LATITUDE;
//	private double dLongitude = 0.0d; //LocationUtils.DEFAULT_LOCATION_LONGITUDE;
// UPDATED 20150105: This is better than the 0.0d,0.0d, however we would like to update them from prefs
	private double dLatitude = LocationUtils.DEFAULT_LOCATION_LATITUDE;
	private double dLongitude = LocationUtils.DEFAULT_LOCATION_LONGITUDE;
	
	// Current location coordinates (if a location from location services was retrieved)
	private double dCurrentLatitude = 0.0d;
	private double dCurrentLongitude = 0.0d;
	private boolean currentLocationWasObtained = false; //True if either the getLastLocation OR the location update one-shot request succeeded	
	
	private Spinner number_controls;
	private Button ok_button, cancel_button;
	
	private boolean doValidateLocationInputData = true; //MUST be default true, since 2 := rb_use_edit_fields is default!
	
	/**
	 * Data read and saved in preferences
	 */
	private int intSelectedRadioButtonForStartPoint; //the id of the radio button that was selected in rg_startpoint Radio Group
	
	private static final int RADIO_BUTTON_START_POINT_USE_FLAG = 0;
	private static final int RADIO_BUTTON_START_POINT_USE_CURRENT_LOCATION = 1;
	private static final int RADIO_BUTTON_START_POINT_USE_EDIT_FIELD = 2;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_create_new_game);
		
		// Display Home/Up button (since not a main activity) 
		ActionBar actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		
		if (!isGooglePlayServicesAvailable()) {
			Log.e(LOG, "Unable to get location - Google Play services unavailable.");
			
			// Disable radio button 'rb_use_current_location'
			RadioButton rbUseCurrentLocation = (RadioButton) findViewById(R.id.rb_use_current_location);
			rbUseCurrentLocation.setEnabled(false);			
		}
		else {
			// Create new location client, using this class to handle callbacks
    		locationClient = new LocationClient(this, this, this);    		
    		
    		if (!locationClient.isConnected() || !locationClient.isConnecting()) {
    			// Connect the client
    			locationClient.connect();
    		}
		}

		// Assign listener to buttons 
		ok_button = (Button)findViewById(R.id.ok_button);
		ok_button.setOnClickListener(new ButtonClick());
		cancel_button = (Button)findViewById(R.id.cancel_button);
		cancel_button.setOnClickListener(new ButtonClick());		

		label_latitude = (TextView) findViewById(R.id.label_latitude );
		label_longitude = (TextView) findViewById(R.id.label_longitude );
		latitude = (EditText) findViewById(R.id.value_latitude );
		longitude = (EditText) findViewById(R.id.value_longitude );
		number_controls = (Spinner) findViewById(R.id.value_num_controls);
				
		// Get default shared preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this); //TODO: or use getApplicationContext() ?
        intSelectedRadioButtonForStartPoint = prefs.getInt("selected_radio_button_start_point", 2); //default 2 := rb_use_edit_fields		
		Integer num_controls = prefs.getInt("num_control_points", 4);
		
		// Set default location values in edit fields (will be updated if user chooses radio button 'rb_use_current_location'
//		latitude.setText(prefs.getString("latitude", Double.valueOf(LocationUtils.DEFAULT_LOCATION_LATITUDE).toString()));
//		longitude.setText(prefs.getString("longitude", Double.valueOf(LocationUtils.DEFAULT_LOCATION_LONGITUDE).toString()));
		
		// Georienteering2: Set default values from PreferenceActivity instead!
		latitude.setText(prefs.getString("latitude_pref_key", Double.valueOf(LocationUtils.DEFAULT_LOCATION_LATITUDE).toString()));
		longitude.setText(prefs.getString("longitude_pref_key", Double.valueOf(LocationUtils.DEFAULT_LOCATION_LONGITUDE).toString()));

		// Georienteering2: UPDATED 20150105 for default values when 'rb_use_flag_on_map' is selected
		dLatitude = Double.valueOf(latitude.getText().toString());
		dLongitude = Double.valueOf(longitude.getText().toString());
		
		// Populate spinner values from string-array in arrays.xml using adapter
		// NOTE: integer-array will NOT work (NullPointerException)!
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
				this, R.array.num_controls_string_values, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		number_controls.setAdapter(adapter);
		
		// Set spinner value
		int item_position = adapter.getPosition(num_controls.toString());
		number_controls.setSelection(item_position);

		// ADDED 20150113:
		switch (intSelectedRadioButtonForStartPoint) {
		case RADIO_BUTTON_START_POINT_USE_FLAG:
			latitude.setEnabled(false);
	    	longitude.setEnabled(false);
	    	latitude.setVisibility(View.INVISIBLE);
	    	longitude.setVisibility(View.INVISIBLE);
	    	label_latitude.setVisibility(View.INVISIBLE);
	    	label_longitude.setVisibility(View.INVISIBLE);
			break;
		case RADIO_BUTTON_START_POINT_USE_CURRENT_LOCATION:
			// Disable editing of coordinates
	    	latitude.setEnabled(false);
	    	longitude.setEnabled(false); 
			break;
		case RADIO_BUTTON_START_POINT_USE_EDIT_FIELD:
			latitude.setEnabled(true);
	    	longitude.setEnabled(true);
	    	latitude.setVisibility(View.VISIBLE);
	    	longitude.setVisibility(View.VISIBLE);
	    	label_latitude.setVisibility(View.VISIBLE);
	    	label_longitude.setVisibility(View.VISIBLE);
			break;
		}
	}
	
	/**
	 * Button click handler set in XML layout
	 * @param view
	 */
	public void onRadioButtonClicked(View view) {
	    // Is the button now checked?
//	    boolean checked = ((RadioButton) view).isChecked();
	    
	    // Check which radio button was clicked
	    switch(view.getId()) {
	    case R.id.rb_use_flag_on_map:
	    	latitude.setEnabled(false);
	    	longitude.setEnabled(false);
	    	latitude.setVisibility(View.INVISIBLE);
	    	longitude.setVisibility(View.INVISIBLE);
	    	label_latitude.setVisibility(View.INVISIBLE);
	    	label_longitude.setVisibility(View.INVISIBLE);	    	
	    	doValidateLocationInputData = false;
	    	intSelectedRadioButtonForStartPoint = RADIO_BUTTON_START_POINT_USE_FLAG;
	    	break;		    
	    case R.id.rb_use_current_location:
	    	// Disable editing of coordinates
	    	latitude.setEnabled(false);
	    	longitude.setEnabled(false); 	
	    	
	    	if (currentLocationWasObtained) {
	    		
	    		if (currentLocation != null) {
	    			dCurrentLatitude = currentLocation.getLatitude();
	    			dCurrentLongitude = currentLocation.getLongitude();
	    		}	    		
	    		latitude.setText(Double.valueOf(dCurrentLatitude).toString());
	    		longitude.setText(Double.valueOf(dCurrentLongitude).toString());
	    		latitude.setVisibility(View.VISIBLE);
		    	longitude.setVisibility(View.VISIBLE);
		    	label_latitude.setVisibility(View.VISIBLE);
		    	label_longitude.setVisibility(View.VISIBLE);
		    	doValidateLocationInputData = true;
	    	}
	    	else {
	    		String msg = getString(R.string.get_last_known_location_failed) + " " + getString(R.string.please_select_other_startpoint_location_method);	    		
	    		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
	    		latitude.setVisibility(View.INVISIBLE);
		    	longitude.setVisibility(View.INVISIBLE);
		    	label_latitude.setVisibility(View.INVISIBLE);
		    	label_longitude.setVisibility(View.INVISIBLE);
		    	doValidateLocationInputData = false;
	    	}
	    	intSelectedRadioButtonForStartPoint = RADIO_BUTTON_START_POINT_USE_CURRENT_LOCATION;
	    	break;	    	
	    case R.id.rb_use_edit_fields:
	    	latitude.setEnabled(true);
	    	longitude.setEnabled(true);
	    	latitude.setVisibility(View.VISIBLE);
	    	longitude.setVisibility(View.VISIBLE);
	    	label_latitude.setVisibility(View.VISIBLE);
	    	label_longitude.setVisibility(View.VISIBLE);
	    	doValidateLocationInputData = true;
	    	intSelectedRadioButtonForStartPoint = RADIO_BUTTON_START_POINT_USE_EDIT_FIELD;
	    	break;	        
	    }
	}
	
	private class ButtonClick implements View.OnClickListener {
		public void onClick(View v) {	

			if (v == ok_button) {
			
				// Validate input values
				if (!validateInputFields()) {
					Log.d("CreateNewGameActivity", "Validation of input values failed!");
					return;
				}

//				int item_position = number_controls.getSelectedItemPosition();				
				String num_controls = number_controls.getSelectedItem().toString();
												
				Log.d("CreateNewGameActivity", "num_controls: " + num_controls);
				
				int intNumControls = 4;
				try {
					intNumControls = Integer.parseInt(num_controls);
				}
				catch (NumberFormatException e) {
					e.printStackTrace();					
				}						
				
				// Create new reply intent 
				Intent reply = new Intent();				
								
				reply.putExtra("result_selected_radio_button_start_point", intSelectedRadioButtonForStartPoint);				
				reply.putExtra("result_latitude", dLatitude);
				reply.putExtra("result_longitude", dLongitude);				
				reply.putExtra("result_num_controls", intNumControls);
				
				setResult(RESULT_OK, reply);    		
				finish(); // Close this activity and return to caller
			}
			else if (v == cancel_button) {				
				// Create new reply intent 
				Intent reply = new Intent();				
				setResult(RESULT_CANCELED, reply); 
				finish(); // Close this activity and return to caller
			}			
		}
	}
	
	 /**
     * Validate the input values and mark those that are incorrect with red.
     * @return true if all the widget values are correct.
     */
    private boolean validateInputFields() {
        // Start with the input validity flag set to true
        boolean inputOK = true;

        if (doValidateLocationInputData) { // Only need to validate if radiobutton 'rb_use_edit_fields' is selected!

        	/*
        	 * Latitude, longitude can't be empty, when radio button 'rb_use_edit_fields' is selected.
        	 * If they are, highlight the input field in RED and put a Toast message in the UI. 
        	 * Otherwise set the input field highlight to GREEN, ensuring that a field that was formerly wrong is reset.
        	 */
        	if (TextUtils.isEmpty(latitude.getText()) || (latitude.getText().toString() == "" )) {
        		latitude.setBackgroundColor(Color.RED);
        		Toast.makeText(this, R.string.location_input_error_missing, Toast.LENGTH_LONG).show();
        		// Set the input validity to false
        		inputOK = false;
        	} 
        	else {
        		latitude.setBackgroundColor(Color.GREEN);
        	}
        	if (TextUtils.isEmpty(longitude.getText()) || (longitude.getText().toString() == "" )   ) {
        		longitude.setBackgroundColor(Color.RED);
        		Toast.makeText(this, R.string.location_input_error_missing, Toast.LENGTH_LONG).show();
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
        		 * First try to convert the values from String to Double.
        		 * Highlight if converting failed and set a Toast in the UI.
        		 */
        		try {
        			dLatitude = Double.valueOf(latitude.getText().toString());        			
        		}	
        		catch (NumberFormatException e) {
        			e.printStackTrace();
        			latitude.setBackgroundColor(Color.RED);
        			Toast.makeText(
        					this,
        					R.string.location_input_error_latitude_invalid,
        					Toast.LENGTH_LONG).show();
        			// Set the input validity to false
        			inputOK = false;
        			return inputOK;
        		}
        		
        		try {        			
        			dLongitude = Double.valueOf(longitude.getText().toString());
        		}	
        		catch (NumberFormatException e) {
        			e.printStackTrace();
        			longitude.setBackgroundColor(Color.RED);
        			Toast.makeText(
        					this,
        					R.string.location_input_error_longitude_invalid,
        					Toast.LENGTH_LONG).show();
        			// Set the input validity to false
        			inputOK = false;
        			return inputOK;
        		}        		

        		/*
        		 * Then test latitude and longitude for minimum and maximum values. 
        		 * Highlight incorrect values and set a Toast in the UI.
        		 */

        		if (dLatitude > GeofenceUtils.MAX_LATITUDE || dLatitude < GeofenceUtils.MIN_LATITUDE) {
        			latitude.setBackgroundColor(Color.RED);
        			Toast.makeText(
        					this,
        					R.string.location_input_error_latitude_invalid,
        					Toast.LENGTH_LONG).show();
        			// Set the input validity to false
        			inputOK = false;
        		} 
        		else {
        			latitude.setBackgroundColor(Color.GREEN);
        		}
        		if ((dLongitude > GeofenceUtils.MAX_LONGITUDE) || (dLongitude < GeofenceUtils.MIN_LONGITUDE)) {
        			longitude.setBackgroundColor(Color.RED);
        			Toast.makeText(
        					this,
        					R.string.location_input_error_longitude_invalid,
        					Toast.LENGTH_LONG).show();
        			// Set the input validity to false
        			inputOK = false;
        		} 
        		else {
        			longitude.setBackgroundColor(Color.GREEN);
        		}
        	}
        }        
        
        // If everything passes, the validity flag will still be true
        return inputOK;
    }

	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		
//		getMenuInflater().inflate(R.menu.action_create_new_game, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch (item.getItemId()) {
		case android.R.id.home:  // Predefined icon ID
			System.out.println("DEBUG: HOME in action bar was clicked");

			// Back Arrow icon in action bar clicked -> go home        	
			/* NOTE: This would start a new main activity (and reset any item values)             
        	Intent intent = new Intent(this, MyCountriesMainActivity.class);
        	intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        	startActivity(intent);
			 */
			finish(); // Close this activity and return to caller
			return true;

		default:		
			return super.onOptionsItemSelected(item);
		}
	}
	
	
	/**
     * Callbacks from Location Services
     */
    @Override
    public void onLocationChanged(Location location) {
    	if (location != null) {
    		Log.d(LOG, "position: " + location.getLatitude() + ", " + location.getLongitude() + " accuracy: " + location.getAccuracy());
    		
    		currentLocation = location; 
    		
    		currentLocationWasObtained = true;
    		
    		// Remove further updates of location
    		locationClient.removeLocationUpdates(this);
    	}    	
    }
    
    /**
     * Called by Location Services when the request to connect the
     * client finishes successfully. At this point, you can
     * request the current location or start periodic updates.
     */
     @Override
     public void onConnected(Bundle bundle) {
     	Log.d(LOG, "onConnected");
     	
     	// Try to get last known location
     	Location location = locationClient.getLastLocation();
     	
     	if (location == null) {
     	
     		// Create the LocationRequest object and set parameters
         	locationRequest = LocationRequest.create();   	
         	
         	locationRequest.setInterval(UPDATE_INTERVAL_IN_SECONDS*1000); // milliseconds
         	locationRequest.setFastestInterval(FASTEST_INTERVAL_IN_SECONDS*1000); // the fastest rate in milliseconds at which your app can handle location updates
         	locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); // Use high accuracy
     		
     		locationClient.requestLocationUpdates(locationRequest, this);
     	}	
     	else {
     		
     		currentLocation = location;
     		currentLocationWasObtained = true;
     		
     		Toast.makeText(this, "Your last known location: " + location.getLatitude() + ", " + location.getLongitude(), Toast.LENGTH_LONG).show();
     	}	
     }

     /**
      * Called by Location Services if the connection to the
      * location client drops because of an error.
      */
     @Override
     public void onDisconnected() {
     	Log.d(LOG, "onDisconnected. Please try to reconnect.");     	
     }

     /**
      * Called by Location Services if the attempt to connect
      * to the Location Services fails.
      */
     @Override
     public void onConnectionFailed(ConnectionResult connectionResult) {
     	Log.e(LOG, "onConnectionFailed");
     	
     	// Disable radio button 'rb_use_current_location'
     	RadioButton rbUseCurrentLocation = (RadioButton) findViewById(R.id.rb_use_current_location);
		rbUseCurrentLocation.setEnabled(false);
     }
	

     /**
      * Verify that Google Play services is available before making a request.
      * @return true if Google Play services is available
      */
     private boolean isGooglePlayServicesAvailable() {

    	 // Check that Google Play services is available
    	 int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

    	 // If Google Play services is available
    	 if (ConnectionResult.SUCCESS == resultCode) {
    		 Log.d(LOG, "Google Play services is available");            
    		 return true;        
    	 } 
    	 // Google Play services was not available for some reason
    	 else {
    		 Log.e(LOG, "Google Play services is unavailable");

    		 // Display an error dialog
    		 Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
    		 if (dialog != null) {
    			 ErrorDialogFragment errorFragment = new ErrorDialogFragment();
    			 errorFragment.setDialog(dialog);
    			 errorFragment.show(getFragmentManager(), LOG);
    		 }
    		 return false;
    	 }
     }     

}
