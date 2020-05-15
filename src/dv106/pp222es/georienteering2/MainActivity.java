package dv106.pp222es.georienteering2;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.database.SQLException;
import android.graphics.Color;
import android.graphics.Point;
import android.location.Location;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
//import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


/**
 *  
PROBLEMS:
1. 20141107:
 11-07 06:07:43.118: W/MainActivity(2851): DEBUG:Activity created for the first time
 11-07 06:07:43.248: E/MainActivity(2851): Failed to get GoogleMap handle. Google Play Services APK is not available?
 11-07 06:07:43.308: W/MainActivity(2851): setupMapIfNeeded FAILED in onCreate

2. 20141111:
11-11 09:37:34.620: W/System.err(4042): java.lang.IllegalArgumentException: Receiver not registered: dv106.pp222es.georienteering.MainActivity$4@b1023860
11-11 09:37:34.640: W/System.err(4042): 	at android.app.LoadedApk.forgetReceiverDispatcher(LoadedApk.java:667)
11-11 09:37:34.640: W/System.err(4042): 	at android.app.ContextImpl.unregisterReceiver(ContextImpl.java:1453)
11-11 09:37:34.640: W/System.err(4042): 	at android.content.ContextWrapper.unregisterReceiver(ContextWrapper.java:489)
11-11 09:37:34.650: W/System.err(4042): 	at dv106.pp222es.georienteering.MainActivity.onPause(MainActivity.java:447)
--> Happened when started in INIT_MODE ?

3. 20141122:
11-22 03:45:15.003: E/AndroidRuntime(2420): java.lang.ClassCastException: android.os.BinderProxy cannot be cast to dv106.pp222es.georienteering2.LocationTrackerService$LocationTrackerBinder
11-22 03:45:15.003: E/AndroidRuntime(2420): 	at dv106.pp222es.georienteering2.MainActivity$1.onServiceConnected(MainActivity.java:1380)
--> Seen only once... (when clicking START menu) Hopefully only a temporary issue?
--> 20150102: Seen once more... This trace is seen before the crash:
01-02 12:03:39.537: W/MainActivity(3848): DEBUG:Service NOT running before calling bindService

4. 201501??:
E/SpannableStringBuilder(7770): SPAN_EXCLUSIVE_EXCLUSIVE spans cannot have a zero length.
--> Seen when screen orientation change on JellyBean phone (only?).


SOLUTIONS
1. 20141107: Moved setupMapIfNeeded call to onResume
   20141113: Moved setupMapIfNeeded call to onStart (including workaround just in case)
   
2. Always register in onResume and unregister in onPause of all receivers.
   20141113: Also use LocalBroadcastManager.getInstance(this).unregisterReceiver when unregistering the local broadcast receiver!  
 
4. 20150112 Partial solution: Avoid empty strings in TextViews, like positionDisplay.
 *
 */


public class MainActivity extends Activity 
						  implements OnMarkerClickListener, OnMarkerDragListener,
						   			 OnSharedPreferenceChangeListener,
						   			 SaveDialogFragment.SaveDialogListener {
	
	private static final boolean DEBUG_MODE = false; // NOTE: Disabled for RELEASE_MODE !!!
	
	private static final String LOG = "MainActivity";	

	private static final String MAP_FRAGMENT_TAG = "map";
	private MapFragment mapFragment = null;
	
	/**
	 * Note that this may be null if the Google Play services APK is not available.
	 */
	private GoogleMap map = null;	
	private boolean hasMapTypeChanged = true; //for optimization in setupMap (not redrawing map unnecessarily)
		
	private boolean hasSetupMapIfNeededFailedInOnStart = false; //workaround flag for map setup during startup
	private boolean hasOnCreateBeenCalledInPreparedMode = false; //flag for recreate game map functionality in PREPARED mode
	private boolean hasOnCreateBeenCalledInPlayGameMode = false; //flag for recreate game map functionality in PLAY_GAME mode
	private boolean hasOnCreateBeenCalledInStoppedMode = false; //flag for showMySaveDialog in STOPPED mode
	
	private boolean hasOnResumeBeenCalledInPlayGameMode = false; //flag for updating game map in PLAY_GAME mode
	
	public static LocationTrackerService myService = null;	
	private Intent myServiceIntent = null;
	private boolean isServiceBound = false;	
	
	private Intent intentLocationDataReceiver = null; //Intent if registering location receiver was successful
	private Intent intentTimerDataReceiver = null; //Intent if registering timer receiver was successful
		
	public enum MapMode {INIT_MODE,       //Initial mode
						 CREATE_MODE,     //Mode for creating a new game (placing control points on the map) 
						 PREPARED_MODE,   //Prepared mode, when everything has been set up and is ready for a new game
						 PLAY_GAME_MODE,  //Play mode, when user has started to play the game
						 STOPPED_MODE};   //Stopped mode, when game was stopped either by user or game was won/lost 						
						 
	public enum GameLevel {EASY,   //Walking
						   MEDIUM, //Jogging
						   HARD};  //Running  
  	
	/**
	 * Constants only used in INIT mode	for initial map and camera settings				   
	 */
	private static final LatLng MY_CENTER_OF_MAP = new LatLng(LocationUtils.DEFAULT_LOCATION_LATITUDE, 
 															  LocationUtils.DEFAULT_LOCATION_LONGITUDE); 	
 	private static final float MY_ZOOM_LEVEL = 12.0f; 	
 		
 	/**
 	 * UI view components 	
 	 */
 	private TextView textDisplay, positionDisplay; 	
 	private Button doneSaveButton = null; //button for 'Done' in create mode and 'Save' in stopped mode.
 	
 	/**
 	 * Game map components
 	 */
 	private Marker startAndEndPointMarker = null;
 	private Marker playerAvatarMarker = null; 	
 	private Marker ghostAvatarMarker = null; 	
 	
 	/**
 	 * Game logic data
 	 */
 	private List<Marker> controlPointList = null;
 	private List<String> controlPointCoordsList = null; //list with coordinates in "1.1,2.2" format used for storing only ONE course in internal storage 			
 	 	
 	/**
 	 * Request codes
 	 */
 	private static final int MY_CREATE_NEW_GAME_REQUEST = 1; //Create New Game activity 	
 	private static final int MY_GOOGLE_PLAY_SERVICES_INSTALL_REQUEST = 1000; //Google Play services install request (from isGooglePlayServicesAvailable)
 	
 	/**
 	 * Geofences handling
 	 */ 
 	private static final Float MY_GEOFENCE_RADIUS = 20.0f; //15.0f; // Increased from 10 meters
 	
 	// Store a list of geofence instances to add
    private List<Geofence> myCurrentGeofencesList;    
    // Store a list of geofence IDs (as String) to remove
    private List<String> myGeofenceIdsToRemove;
    
    // Store the current request (ADD, REMOVE)
    private GeofenceUtils.REQUEST_TYPE myRequestType;    
    // Store the current type of removal (INTENT, LIST)
    private GeofenceUtils.REMOVE_TYPE myRemoveType;
    
    // Add geofences handler
    private GeofenceRequester myGeofenceRequester;
    // Remove geofences handler
    private GeofenceRemover myGeofenceRemover;
    
    // Flag saved in prefs, to make sure that no pending geofences left in wrong map mode when app was killed
    private boolean geofencesAreSetup = false; // MUST BE PERSISTENT!  
    
    /**
     * Storage (Preferences, database, internal)
     */
    private SharedPreferences prefs;  
    private GameDataSource dataSource = null;
    private static final String MY_COURSE_FILE_NAME = "my_first_course";
    
    /**
     * Ghost Mover task specific data
     */
    private String  diffDistToGhostAsString; // As string for updatePositionDisplay()
    private Integer intDistanceForGhost = 0;      
    private AsyncTask<Integer, ProgressWrapper, Boolean> ghostMoverTask = null;
    
    private AsyncTask<Integer, Void, Boolean> audioClipPlayerTask = null;
    
    /**
     * Data that will need to be saved in Database via GameDataSource (when game finished)
     */        
    private Integer numMinutes; // Must be persistent between activity destroyed/created!
    private Integer numSeconds;
    private int intDistanceForUser;
    private String timeAsString; // As string for updatePositionDisplay()    
    private String distanceAsString; // As string for updatePositionDisplay()     
    
 	/**
	 * Data that need to be persistent (saved in onStop and read in onCreate)
	 */	
	private MapMode mapMode;	
	private int mapType; //NOTE: Must be a primitive type! (NOT Integer class)
	private float myZoomLevel;
	
	private int numControlPointsFound; 	
	private int numberOfControlPoints;
	
	private int intSelectedStartFlagPlaceMethod;
	private boolean hasGameBeenSaved; // Flag will be false only until the very first save of a course has been done!
	
	private boolean doMoveGhostAvatar; 	
	private Integer ghostMoveIntervalInSeconds; 	
	private int userLocationIndexWhenGhostStartedToMove;

	private int current_route_point_index = 0; // Service saves start point in list at index 0 via setStartPoint
	
 	/**
 	 * Data that need to be persistent and should be customizable via User Preferences
 	 */ 			
	private GameLevel gameLevel;
	
	private LatLng myStartAndEndPoint = MY_CENTER_OF_MAP; // By default the start and end point is at the center of the map 	
	private LatLng myDefaultStartAndEndPoint; //Added 20141123, read from PreferenceActivity! Only used in INIT mode
	
 	private boolean isGhostAvatarEnabled; 	
 	private boolean doShowRoute;
 	
 	/**
 	 * IDs for storing activity state variables in onSaveInstanceState
 	 */
 	private static final String STATE_CURRENT_ROUTE_POINT_INDEX = "current_route_point_index"; 
 	
  	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);	
		
		if (!isGooglePlayServicesAvailable()) {			
			Log.e(LOG, "Google Play services unavailable.");			
			Toast.makeText(getApplicationContext(), 
					R.string.google_play_services_not_available, 
					Toast.LENGTH_LONG).show();
		}		
		
		if (savedInstanceState == null) {
            // Activity created for the first time.
			Log.i(LOG, "Activity created for the first time");	
		}			
		
		// Set the default values from an XML preference file by reading the values defined by each Preference item's android:defaultValue attribute
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false); //read only once
		
		// Get default shared preferences
        prefs =	PreferenceManager.getDefaultSharedPreferences(this);  
        
        // Read persistent preferences
        readPreferences();
        
        // Detect if special handling required in setupGameMap due to app created in a certain map mode
        if (mapMode == MapMode.PREPARED_MODE) {
        	hasOnCreateBeenCalledInPreparedMode = true; // For recreate game map functionality in PREPARED mode        	
        }        
        if (mapMode == MapMode.PLAY_GAME_MODE) {
        	hasOnCreateBeenCalledInPlayGameMode = true; // For recreate game map functionality in PLAY_GAME mode        	
        }     
        if (mapMode == MapMode.STOPPED_MODE) {
        	hasOnCreateBeenCalledInStoppedMode = true; // For showMySaveDialog in STOPPED mode        	
        }
        // Make sure that no geofences have been left active when they should not 
        if ( (mapMode != MapMode.PREPARED_MODE) && (mapMode != MapMode.PLAY_GAME_MODE) ) {
        	if (geofencesAreSetup) {  
        		Log.w(LOG, "Geofences are still setup but NOT in PREPARED or PLAY GAME modes!!! Trying to remove...");
        		       		
        		// Remove the geofences via request to GeofenceRemover
        		if (!removeMyGeofences() ) {
        			Log.e(LOG, "Failed to remove all geofences in onCreate. Retrying...");        			
        		}
        	}        	
        }      
		
		// Instantiate the current list of geofences
        myCurrentGeofencesList = new ArrayList<Geofence>();		        

        // Instantiate a Geofence requester
        myGeofenceRequester = new GeofenceRequester(this);

        // Instantiate a Geofence remover
        myGeofenceRemover = new GeofenceRemover(this);    
        
        // Get UI elements
		textDisplay = (TextView) findViewById(R.id.textdisplay);
		positionDisplay = (TextView) findViewById(R.id.positiondisplay);		
		doneSaveButton = (Button) findViewById(R.id.done_save_btn);
		doneSaveButton.setOnClickListener(DoneSaveButtonClickListener);
		// Hide the 'Done'/'Save' button until the appropriate map mode is active
		doneSaveButton.setVisibility(View.INVISIBLE);
		
		// Try to find a fragment that was identified by the given tag either when inflated from XML or as supplied when added in a transaction
		mapFragment = (MapFragment) getFragmentManager()
				.findFragmentByTag(MAP_FRAGMENT_TAG);
		
		if (mapFragment == null) { 
			// Fragment was not found...
			Log.w(LOG, "mapFragment == null in OnCreate!");		
			
			// Setup map and camera specific initial settings. 			
			//TODO: Only when (savedInstanceState == null)?
        	CameraPosition initialCameraPosition = new CameraPosition.Builder()
			   .target(MY_CENTER_OF_MAP) // Sets the center of the map   
			   .zoom(MY_ZOOM_LEVEL)      // Sets the zoom			 
			   .build();                 // Creates a CameraPosition from the builder
			
			GoogleMapOptions options = new GoogleMapOptions();
	        options.mapType(GoogleMap.MAP_TYPE_NORMAL) //default normal	      
	        .camera(initialCameraPosition);	       

			// Instantiate a MapFragment			
			mapFragment = MapFragment.newInstance(options);
			
			if (mapFragment == null) {				
				// Fragment does not exist...?
				Log.e(LOG, "mapFragment == null still in OnCreate!");
			}

			// Add it to container using a FragmentTransaction
			FragmentTransaction fragmentTransaction =
					getFragmentManager().beginTransaction();

			fragmentTransaction.add(R.id.container, mapFragment, MAP_FRAGMENT_TAG);			 
			fragmentTransaction.commit();
		}		
		
		if (myServiceIntent == null) {
			// Create an intent for starting and binding to service
			myServiceIntent = new Intent(this, LocationTrackerService.class);
		}
		
		//UPDATED 20150113: Only automatically bind when in PLAY GAME mode
		if (mapMode == MapMode.PLAY_GAME_MODE) {
			// If the service is already running when the activity starts, we want to automatically bind to it
			doBindToServiceIfIsRunning(); 
		}
		
		// Check that at least one location provider ("gps","network","passive") is available and enabled
		LocationUtils lu = new LocationUtils();
		lu.isLocationProviderAvailableAndEnabled(this); // If not this will display a Toast
		
		dataSource = new GameDataSource(this);
	}

	/**     
	 * State Monitoring Methods
	 * Each method use System.out.println() to print a message when activated. 
	 */
	// Called after onCreate has finished, use to restore UI state
	// NOTE: This callback allows you to separate creation code from state restoring code.
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		// TEST: Log verbose print to see the automatically saved View state
		// REF: From http://www.intertech.com/Blog/saving-and-retrieving-android-instance-state-part-1/
		/*
		Bundle viewHierarchy = savedInstanceState
				.getBundle("android:viewHierarchyState");

		if (viewHierarchy != null) {
			SparseArray views = viewHierarchy.getSparseParcelableArray("android:views");
			if (views != null) {
				for (int i = 0; i < views.size(); i++) {
					Log.v(LOG, "key -->" + views.get(i));
					Log.v(LOG, "value --> " + views.valueAt(i));
				}
			}
		} else {
			Log.v(LOG, "no view data");
		}	
		*/	
		// END TEST
		// Super class restores all View component data in the Bundle automatically as long as you have provided the View an id property!
		super.onRestoreInstanceState(savedInstanceState); 
		System.out.println("StateMonitoring: RestoreInstanceState");
		// Restore UI state from the savedInstanceState. 
		// This bundle has also been passed to onCreate.
		
		current_route_point_index = savedInstanceState.getInt(STATE_CURRENT_ROUTE_POINT_INDEX);		
	}

	// Called before subsequent visible lifetimes 
	// for an activity process. (activity comes to foreground)
	@Override
	public void onRestart(){
		super.onRestart();
		System.out.println("StateMonitoring: Restart");
		// Load changes knowing that the activity has already
		// been visible within this process.		
	}

	// Called at the start of the visible lifetime.
	@Override
	public void onStart(){
		super.onStart();
		System.out.println("StateMonitoring: Start");
		// Apply any required UI change now that the Activity is visible.		
		
		// Setup the map here instead of in onCreate		
		if (!setupMapIfNeeded()) { //NOTE: there is no guarantee that the map fragment is ready when setUpMapIfNeeded method is executed in onCreate
			Log.w(LOG, "setupMapIfNeeded FAILED in onStart");
			hasSetupMapIfNeededFailedInOnStart = true;
			return;
		}	
		
		if (mapMode != MapMode.PLAY_GAME_MODE) {
			// Now also update the game map UI (for all other modes except PLAY_GAME)
			setupGameMap(mapMode);
		}
		
		// Start the service explicitly here, if not already running (required to make service continue running until explicitly stopped)
		if (!LocationTrackerService.isRunning()) {
			
			ComponentName cn = startService(myServiceIntent);
			if (cn == null) {
				Log.e(LOG, "Failed to start service");
			}	
			else {
				Log.i(LOG, cn.toString() + " was started in onStart");
			}
		}
		// Notify service that client is visible
		/*
		if (myService != null) myService.setClientVisible(true);
		*/
		LocationTrackerService.setClientVisible(true);
	}

	// Called at the start of the active lifetime.
	@Override
	public void onResume(){
		super.onResume();
		System.out.println("StateMonitoring: Resume");
		// Resume any paused UI updates, threads, or processes required
		// by the activity but suspended when it was inactive.

		// Workaround (in case setup map has failed in onStart)
		if (hasSetupMapIfNeededFailedInOnStart) {
			if (!setupMapIfNeeded()) {
				Log.e(LOG, "setupMapIfNeeded FAILED in onResume");
				// A toast is already shown from setupMapIfNeeded				
				return;
			}			
			// Now also update the game map UI
			setupGameMap(mapMode);			
			hasSetupMapIfNeededFailedInOnStart = false;
		}
		
		if (mapMode == MapMode.PLAY_GAME_MODE) {			
			
			//IMPORTANT: Must set this BEFORE setupGameMap!
			hasOnResumeBeenCalledInPlayGameMode = true; // For update of game map in PLAY_GAME mode  
			
			// Now also update the game map UI (for PLAY_GAME mode)
			setupGameMap(mapMode);        	
        }   
		
		// ADDED 20150117
		AsyncTask<Void, Integer, Integer> task = 	
				new CheckInternetTask(getApplicationContext()).execute();
		
		/**
		 * Register receivers
		 */
		// Register Broadcast receiver (within main activity) to listen for location data broadcast from LocationTrackerService
	    IntentFilter iff = new IntentFilter();
        iff.addAction(LocationUtils.LOCATION_DATA_BROADCAST);
        intentLocationDataReceiver = registerReceiver(mainLocationDataReceiver, iff);
		
		// Register Broadcast receiver to listen for Timer update
		IntentFilter iff2 = new IntentFilter();
        iff2.addAction("dv106.pp222es.georienteering.action.TIMER_DATA_BROADCAST");
		intentTimerDataReceiver = registerReceiver(mainTimerDataReceiver, iff2);		
		
		// TODO: Why is "The first sticky intent found that matches filter" null?
		if ((intentLocationDataReceiver == null) || (intentTimerDataReceiver == null)) {
//			debug("(intentLocationDataReceiver == null) || (intentTimerDataReceiver == null)");
		}
		
		// Create intent filters for the Geofence Detector broadcast receiver
		IntentFilter iff3 = new IntentFilter();        
		iff3.addAction(GeofenceUtils.ACTION_GEOFENCES_ADDED);        
		iff3.addAction(GeofenceUtils.ACTION_GEOFENCES_REMOVED);
		iff3.addAction(GeofenceUtils.ACTION_GEOFENCE_ERROR);
		iff3.addAction(GeofenceUtils.ACTION_GEOFENCE_TRANSITION);

        // All Location Services aware apps use this category
		iff3.addCategory(GeofenceUtils.CATEGORY_LOCATION_SERVICES);		
		
		// Register the local broadcast receiver to receive geofence status updates		
		LocalBroadcastManager.getInstance(this).registerReceiver(mainGeofenceDetectorReceiver, iff3);   
		
		// Register to listen for preference changes
        prefs.registerOnSharedPreferenceChangeListener(this);
	}	
	
	/**
	 * Listener to obtain keys and values from preference activity.
	 * 
	 * Text from Google: http://developer.android.com/guide/topics/ui/settings.html
	 * "For proper lifecycle management in the activity, we recommend that you register and unregister your 
	 * SharedPreferences.OnSharedPreferenceChangeListener during the onResume() and onPause() callbacks, respectively"
	 * NOTE: I feel this is a strange text from Google, since onPause will be called when Preference Activity is shown. 
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {		
		
		Log.i(LOG, "onSharedPreferenceChanged, key=" + key);
		if (key.equals("difficulty_level_pref_key")) {
			Integer intGameLevel;
			String strGameLevel;
			try {
//				intGameLevel = prefs.getInt("difficulty_level_pref_key", 0); //default 0 := EASY
				strGameLevel = prefs.getString("difficulty_level_pref_key", "0"); //default "0" := EASY
			}
			catch (ClassCastException cce) {
				cce.printStackTrace();
				Log.e(LOG, "prefs.getInt('difficulty_level_pref_key', 0) FAILED in onSharedPreferenceChanged");
				intGameLevel = 0;
				strGameLevel = "0";
			}			
			intGameLevel = Integer.parseInt(strGameLevel);
			switch (intGameLevel) {
	        case 0:
	        default:	
	        	gameLevel = GameLevel.EASY;
	            break;
	        case 1:
	        	gameLevel = GameLevel.MEDIUM;
	            break;
	        case 2:
	        	gameLevel = GameLevel.HARD;
	            break;      
	        }        
		}
		if (key.equals("ghost_enabled_pref_key")) {
			isGhostAvatarEnabled = prefs.getBoolean(key, true); 
		}
		if (key.equals("show_route_enabled_pref_key")) {
			doShowRoute = prefs.getBoolean(key, true); 
		}			
		
		// Georienteering2: Get default values from PreferenceActivity instead! //Added 20141123        
        double dDefaultLatitude = 0.0d;
        double dDefaultLongitude = 0.0d;
		if (key.equals("latitude_pref_key")) {			
			String sDefaultLatitude = (prefs.getString("latitude_pref_key", Double.valueOf(LocationUtils.DEFAULT_LOCATION_LATITUDE).toString()));
			try {
				dDefaultLatitude = Double.parseDouble(sDefaultLatitude); //Added 20141123       			
			}	
			catch (NumberFormatException e) {
				e.printStackTrace();
				Toast.makeText(
						this,
						R.string.location_input_error_converted_latitude_invalid,
						Toast.LENGTH_LONG).show();
				dDefaultLatitude = LocationUtils.DEFAULT_LOCATION_LATITUDE;
			}
			
			String sDefaultLongitude = (prefs.getString("longitude_pref_key", Double.valueOf(LocationUtils.DEFAULT_LOCATION_LONGITUDE).toString()));
			try {
				dDefaultLongitude = Double.parseDouble(sDefaultLongitude); //Added 20141123        			
			}	
			catch (NumberFormatException e) {
				e.printStackTrace();
				Toast.makeText(
						this,
						R.string.location_input_error_converted_longitude_invalid,
						Toast.LENGTH_LONG).show();
				dDefaultLongitude = LocationUtils.DEFAULT_LOCATION_LONGITUDE;
			}			
		}
		
		if (key.equals("longitude_pref_key")) {
			String sDefaultLatitude = (prefs.getString("latitude_pref_key", Double.valueOf(LocationUtils.DEFAULT_LOCATION_LATITUDE).toString()));
			try {
				dDefaultLatitude = Double.parseDouble(sDefaultLatitude); //Added 20141123       			
			}	
			catch (NumberFormatException e) {
				e.printStackTrace();
				Toast.makeText(
						this,
						R.string.location_input_error_converted_latitude_invalid,
						Toast.LENGTH_LONG).show();
				dDefaultLatitude = LocationUtils.DEFAULT_LOCATION_LATITUDE;
			}
			
			String sDefaultLongitude = (prefs.getString("longitude_pref_key", Double.valueOf(LocationUtils.DEFAULT_LOCATION_LONGITUDE).toString()));
			try {
				dDefaultLongitude = Double.parseDouble(sDefaultLongitude); //Added 20141123        			
			}	
			catch (NumberFormatException e) {
				e.printStackTrace();
				Toast.makeText(
						this,
						R.string.location_input_error_converted_longitude_invalid,
						Toast.LENGTH_LONG).show();
				dDefaultLongitude = LocationUtils.DEFAULT_LOCATION_LONGITUDE;
			}
		}                
        
        myDefaultStartAndEndPoint = new LatLng(dDefaultLatitude, dDefaultLongitude); //Added 20141123		
		
//		try {
//			String prefTextSize = prefs.getString("text_size_pref_key", "medium");
//		}
//		catch (ClassCastException cce) {
//			cce.printStackTrace();
//		}
	}
	
	private void readPreferences() {		
		assert(prefs != null);
		
        // Get and set map mode
        Integer intMapMode = prefs.getInt("map_mode", 0); //default 0 := INIT_MODE        
        debug("MapMode: " + intMapMode.toString());        
        switch (intMapMode) {
        case 0:
        default:	
        	mapMode = MapMode.INIT_MODE;
            break;
        case 1:
        	mapMode = MapMode.CREATE_MODE;
            break;
        case 2:
        	mapMode = MapMode.PREPARED_MODE;
            break;
        case 3:
        	mapMode = MapMode.PLAY_GAME_MODE;
            break;  
        case 4:
        	mapMode = MapMode.STOPPED_MODE;
            break;      
        }        
        
        // Get and set game level, Saved via PreferenceFragment/Activity
        Integer intGameLevel;
        String strGameLevel;
        try {
//			intGameLevel = prefs.getInt("difficulty_level_pref_key", 0); //default 0 := EASY
        	strGameLevel = prefs.getString("difficulty_level_pref_key", "0"); //default "0" := EASY
		}
		catch (ClassCastException cce) {
			cce.printStackTrace();
			Log.e(LOG, "prefs.getInt('difficulty_level_pref_key', 0) FAILED in readPreferences");
			intGameLevel = 0;
			strGameLevel = "0";
		}	      

        intGameLevel = Integer.parseInt(strGameLevel);       
        switch (intGameLevel) {
        case 0:
        default:	
        	gameLevel = GameLevel.EASY;
            break;
        case 1:
        	gameLevel = GameLevel.MEDIUM;
            break;
        case 2:
        	gameLevel = GameLevel.HARD;
            break;      
        }        
        // Get and set map type selection
        mapType = prefs.getInt("map_type", 1); //default 1 := GoogleMap.MAP_TYPE_NORMAL         
        hasMapTypeChanged = true;
        RadioGroup rgMaptypes = (RadioGroup) findViewById(R.id.rg_maptypes);
        if (mapType == GoogleMap.MAP_TYPE_NORMAL) rgMaptypes.check(R.id.rb_normal);
        else if (mapType == GoogleMap.MAP_TYPE_SATELLITE) rgMaptypes.check(R.id.rb_satellite);
        else if (mapType == GoogleMap.MAP_TYPE_TERRAIN) rgMaptypes.check(R.id.rb_terrain);
        
        // Get zoom level
        myZoomLevel = prefs.getFloat("zoom_level", 12.0f);
                
        // Get number of control points, found and total
        numControlPointsFound = prefs.getInt("num_control_points_found", 0);
        numberOfControlPoints = prefs.getInt("num_control_points", 4);
        
        // Get location for start and finish point
        String sLatitude = prefs.getString("latitude", Double.valueOf(LocationUtils.DEFAULT_LOCATION_LATITUDE).toString());
        String sLongitude = prefs.getString("longitude", Double.valueOf(LocationUtils.DEFAULT_LOCATION_LONGITUDE).toString());       
        
        // Georienteering2: Get default values from PreferenceActivity instead! //Added 20141123
        String sDefaultLatitude = (prefs.getString("latitude_pref_key", Double.valueOf(LocationUtils.DEFAULT_LOCATION_LATITUDE).toString()));
        String sDefaultLongitude = (prefs.getString("longitude_pref_key", Double.valueOf(LocationUtils.DEFAULT_LOCATION_LONGITUDE).toString()));
        
        double dLatitude;
        double dLongitude;
        double dDefaultLatitude;
        double dDefaultLongitude;
        
        try {
			dLatitude = Double.parseDouble(sLatitude);        			
		}	
		catch (NumberFormatException e) {
			e.printStackTrace();
			Toast.makeText(
					this,
					R.string.location_input_error_converted_latitude_invalid,
					Toast.LENGTH_LONG).show();
			dLatitude = LocationUtils.DEFAULT_LOCATION_LATITUDE;
		}
        try {
			dLongitude = Double.parseDouble(sLongitude);        			
		}	
		catch (NumberFormatException e) {
			e.printStackTrace();
			Toast.makeText(
					this,
					R.string.location_input_error_converted_longitude_invalid,
					Toast.LENGTH_LONG).show();
			dLongitude = LocationUtils.DEFAULT_LOCATION_LONGITUDE;
		}
        
        try {
			dDefaultLatitude = Double.parseDouble(sDefaultLatitude); //Added 20141123       			
		}	
		catch (NumberFormatException e) {
			e.printStackTrace();
			Toast.makeText(
					this,
					R.string.location_input_error_converted_latitude_invalid,
					Toast.LENGTH_LONG).show();
			dDefaultLatitude = LocationUtils.DEFAULT_LOCATION_LATITUDE;
		}
        try {
			dDefaultLongitude = Double.parseDouble(sDefaultLongitude); //Added 20141123        			
		}	
		catch (NumberFormatException e) {
			e.printStackTrace();
			Toast.makeText(
					this,
					R.string.location_input_error_converted_longitude_invalid,
					Toast.LENGTH_LONG).show();
			dDefaultLongitude = LocationUtils.DEFAULT_LOCATION_LONGITUDE;
		}
                
        myStartAndEndPoint = new LatLng(dLatitude, dLongitude);
        myDefaultStartAndEndPoint = new LatLng(dDefaultLatitude, dDefaultLongitude); //Added 20141123
        
        intSelectedStartFlagPlaceMethod = prefs.getInt("selected_radio_button_start_point", 2); //default 2 := Use edit fields for input of latitude/longitude
        
        geofencesAreSetup = prefs.getBoolean("geofences_are_setup", false);        
        hasGameBeenSaved = prefs.getBoolean("game_been_saved", false);        
        isGhostAvatarEnabled = prefs.getBoolean("ghost_enabled_pref_key", true); //Saved via PreferenceFragment/Activity        
        doShowRoute = prefs.getBoolean("show_route_enabled_pref_key", true); //Saved via PreferenceFragment/Activity
        
        userLocationIndexWhenGhostStartedToMove = prefs.getInt("user_location_when_ghost_started", 100);
        ghostMoveIntervalInSeconds = prefs.getInt("ghost_move_interval", LocationTrackerService.UPDATE_INTERVAL_IN_SECONDS_EASY_LEVEL);
        doMoveGhostAvatar = prefs.getBoolean("move_ghost", false);  
        
        numMinutes = prefs.getInt("num_minutes", 0); //Added 20141203
        
        current_route_point_index = prefs.getInt("current_route_point_index", 0); //Added 20150110
	}
	
	/**
	 * Saves app preferences, that are not saved via PreferenceActivity, see onSharedPreferenceChanged
	 */
	private void savePreferences() {
		assert(prefs != null);
		
		int intMapMode;		
		switch (mapMode) {
        case INIT_MODE:
        default:	
        	intMapMode = 0;
            break;
        case CREATE_MODE:
        	intMapMode = 1;
            break;
        case PREPARED_MODE:
        	intMapMode = 2;
            break;
        case PLAY_GAME_MODE:
        	intMapMode = 3;
            break;
        case STOPPED_MODE:
        	intMapMode = 4;
            break;
        }
	/* Saved via PreferenceFragment/Activity instead	
		int intGameLevel;		
		switch (gameLevel) {
        case EASY:
        default:	
        	intGameLevel = 0;
            break;
        case MEDIUM:
        	intGameLevel = 1;
            break;
        case HARD:
        	intGameLevel = 2;
            break;           
        }		
	*/	
		Editor editor = prefs.edit();		
		editor.putInt("map_mode", intMapMode);		
//		editor.putInt("game_level", intGameLevel); //Saved via PreferenceFragment/Activity
		editor.putInt("map_type", map.getMapType());
		editor.putFloat("zoom_level", map.getCameraPosition().zoom);
		
		if (mapMode == MapMode.PLAY_GAME_MODE)
			editor.putInt("num_control_points_found", numControlPointsFound);
		else 
			editor.putInt("num_control_points_found", 0); // reset value when not ongoing game!		
		
		editor.putInt("num_control_points", numberOfControlPoints);		
		
		String sLatitude  = Double.valueOf(myStartAndEndPoint.latitude).toString();
		String sLongitude  = Double.valueOf(myStartAndEndPoint.longitude).toString();	
		
		editor.putString("latitude", sLatitude);
		editor.putString("longitude", sLongitude);				
		editor.putInt("selected_radio_button_start_point", intSelectedStartFlagPlaceMethod);
		
		editor.putBoolean("geofences_are_setup", geofencesAreSetup);		
		editor.putBoolean("game_been_saved", hasGameBeenSaved);	
		
		editor.putInt("user_location_when_ghost_started", userLocationIndexWhenGhostStartedToMove);
		editor.putInt("ghost_move_interval", ghostMoveIntervalInSeconds);
		editor.putBoolean("move_ghost", doMoveGhostAvatar);
		
		editor.putInt("num_minutes", numMinutes);
		
		editor.putInt("current_route_point_index", current_route_point_index);
		
		// It's safe to replace any instance of commit with apply if you were already ignoring the return value.
		editor.apply();
		
		// If in PLAY GAME mode, also save the control points that user have not yet found
		if (mapMode == MapMode.PLAY_GAME_MODE) {
			Log.d(LOG, "Calling saveControlPointsInPreferences");
			saveControlPointsInPreferences();
		}
	}
	
	/**
	 * Saves start point and control point coordinates in separate preferences file, as strings.
	 * Only used in PLAY GAME mode to be able to save control points when app is killed/destroyed unexpectedly.
	 * FIXME:
	 * POSSIBLE PROBLEM: When GeofenceDetectorReceiver calls updateControlPointInPreferences simultaneously?
	 * NOTE: when two editors are modifying preferences at the same time, the last one to call commit() or apply() wins, as per the SharedPreferences.Editor documentation. 
	 * POSSIBLE WORKAROUND #1: Let GeofenceDetectorReceiver write ALL found control points and DO NOT call this method in main!
	 * BUT THEN NEW PROBLEM: How to store the coordinates for ALL control points?
	 * POSSIBLE WORKAROUND #2: Use Context.MODE_MULTI_PROCESS
	 */
	private void saveControlPointsInPreferences() {
		
//		SharedPreferences cpPrefs = getSharedPreferences("ControlPointPrefs", Context.MODE_PRIVATE);
		// WORKAROUND #2: Testing with Context.MODE_MULTI_PROCESS
		SharedPreferences cpPrefs = getSharedPreferences("ControlPointPrefs", Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
		
		Editor editor = cpPrefs.edit();		
		
		// Store start point coordinates		
    	LatLng startpos = startAndEndPointMarker.getPosition();    	    	
		editor.putString("start_point", String.valueOf(startpos.latitude) + "," + String.valueOf(startpos.longitude));
		
		String s;
		int totalNumControlPoints = 0;
		
		String logBuffer = "";    	    	
		// Get coordinates for all controlPoint markers 		        	
		for (Marker marker : controlPointList) {
			LatLng pos = marker.getPosition();	
			String snippet = marker.getSnippet(); // The snippet contains "Found" for control points that have been found by user
			
			// Add all control points, but append a "Found" string as a third string after lat,lng						
			String id = marker.getTitle(); // title contains id from "1"... NOTE: But the ids for "Found" are not consecutive!
			String sLatitude = Double.valueOf(pos.latitude).toString();
			String sLongitude = Double.valueOf(pos.longitude).toString();		
			
			if (!snippet.contains("Found")) {
				s = sLatitude + "," + sLongitude;
			}
			else {
				s = sLatitude + "," + sLongitude + ",Found";
			}
			editor.putString("control_point_" + id, s); // Use id in key
			logBuffer = logBuffer + s + "\n";
			totalNumControlPoints++;
		}
		
		Log.i(LOG, "Added control points in prefs: " + logBuffer);
		
		// Store the total number of control points saved (excluding start point). (Yes I know, it was already stored also as numberOfControlPoints... but we do like this for consistency)
		editor.putInt("total_num_control_points", totalNumControlPoints);
				
		editor.apply();
	}

	/**
	 * Reads control points from separate preferences file.
	 * Only used in PLAY GAME mode to be able to retrieve control points when app restarts.
	 */
	private List<String> readControlPointsFromPreferences() {
		
//		SharedPreferences cpPrefs = getSharedPreferences("ControlPointPrefs", Context.MODE_PRIVATE);
		// WORKAROUND #2: Testing with Context.MODE_MULTI_PROCESS
		SharedPreferences cpPrefs = getSharedPreferences("ControlPointPrefs", Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
		
		// Get the total number of control points
		int numCPs = cpPrefs.getInt("total_num_control_points", numberOfControlPoints); //default numberOfControlPoints already read in readPreferences!
		
		// Extra check, just in case
		if (numCPs != numberOfControlPoints) {
			Log.e(LOG, "numCP != numberOfControlPoints in readControlPointsFromPreferences");
		}
		
		// Allocate a list for control points AND start point
		List<String> text_lines = new ArrayList<String>(numCPs + 1);
		
		// Get start point (Yes I know, it was already stored also as myStartAndEndPoint... but we do like this for consistency)
		String s = cpPrefs.getString("start_point", Double.valueOf(myStartAndEndPoint.latitude).toString() + "," + Double.valueOf(myStartAndEndPoint.longitude).toString());
		text_lines.add(s);
		
		String logBuffer = "";
		// Get control points, starting with id 1...
		for (int id=1; id<=numCPs; id++) {			
			s = cpPrefs.getString("control_point_" + id, "0.0d,0.0d"); 
			text_lines.add(s);	
			logBuffer = logBuffer + s + "\n";
		}				
		Log.i(LOG, "Read control points from prefs: " + logBuffer);
		
		return text_lines;
	}
	
	// Called to save UI state changes at the end of the active life-cycle.
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {    
		// Save UI state changes to the savedInstanceState. 
		// This bundle will be passed to onCreate if the process is 
		// killed and restarted.		
		
		savedInstanceState.putInt(STATE_CURRENT_ROUTE_POINT_INDEX, current_route_point_index);
		
		// Super class saves all View component data in the Bundle automatically as long as you have provided the View an id property!
		super.onSaveInstanceState(savedInstanceState); 
		System.out.println("StateMonitoring: SaveInstanceState");		
	}

	// Called at the end of the active lifetime.
	// NOTE: onPause will be called when Preference Activity is shown.
	// Therefore DO NOT unregister listener of preference changes here!
	@Override
	public void onPause(){
		// Suspend UI updates, threads, or CPU intensive processes 
		// that don’t need to be updated when the Activity isn’t 
		// the active foreground activity.
		super.onPause();
		System.out.println("StateMonitoring: Pause");	
		
		/** 
		 * Unregister Broadcast receivers
		 */
		try {		
//			if (intentLocationDataReceiver != null) unregisterReceiver(mainLocationDataReceiver);
//			if (intentTimerDataReceiver != null) unregisterReceiver(mainTimerDataReceiver);
			
			unregisterReceiver(mainLocationDataReceiver);
			unregisterReceiver(mainTimerDataReceiver);			
			
			LocalBroadcastManager.getInstance(this).unregisterReceiver(mainGeofenceDetectorReceiver);
		}
		catch (IllegalArgumentException ile) {
			ile.printStackTrace();
		}		
	}

	// Called at the end of the visible lifetime.
	// NOTE: onStop will be called when Preference Activity is shown.
	// Therefore DO NOT unregister listener of preference changes here!
	@Override
	public void onStop(){    
		// Suspend remaining UI updates, threads, or processing 
		// that aren’t required when the Activity isn’t visible. 
		// Persist all edits or state changes 
		// as after this call the process is likely to be killed.
		super.onStop();
		System.out.println("StateMonitoring: Stop");
		
		// Save persistent data in preferences
		savePreferences();		
		
		// Notify service that client is not visible
		/*
		if (myService != null) myService.setClientVisible(false);		
		*/
		LocationTrackerService.setClientVisible(false);
	}

	// Called at the end of the full lifetime.
	/*
	 * Note: Do not count on this method being called as a place for saving data! 
	 * For example, if an activity is editing data in a content provider, those edits should be committed in either onPause or onSaveInstanceState, not here.
	 * @see android.app.Activity#onDestroy()
	 */	  
	@Override
	public void onDestroy(){
		// Clean up any resources including ending threads, 
		// closing database connections etc.
		super.onDestroy();
		System.out.println("StateMonitoring: Destroy");	 		
		
		if (myService != null) {
			
			// Tell service that client is no longer alive
        	myService.setClientAlive(false);
			
        	// Stop service, but only if service is not tracking (during ongoing game)
			if (!myService.isTracking()) {
				stopService(myServiceIntent);
			}
		}
		else {
			Log.w(LOG, "myService == null in onDestroy");
		}
		
		//NOTE: We MUST unbind from service, else we get error "Activity dv106.pp222es.georienteering2.MainActivity has leaked ServiceConnection dv106.pp222es.georienteering2.MainActivity$1@acff22a0 that was originally bound here"

		try {
			doUnbindService();
		} catch (Throwable t) {
			Log.e(LOG, "Failed to unbind from the service", t);
		}	
				
		// Unregister listener of preference changes
		if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
		
		// NOTE: MUST ALWAYS CLOSE DB AT LEAST HERE!!!
		if (dataSource != null) dataSource.close();
	}

	/**
	 * Sets up map if needed
	 * This method can be called from both onCreate and onResume to ensure that the map is always available.
	 * NOTE: However if using a mapFragment (created programmatically in onCreate) the mapFragment handle might not be valid yet!!!
	 * It can take a while for the Google Play Services to initialize everything necessary for the MapFragment and until all that happens the map property will be null!
	 * @return true if map is available
	 */
	private boolean setupMapIfNeeded() {
		// Do a null check to confirm that we have not already instantiated the map.
		if (map == null) {
			// Get a handle to the Map object
			// If the Google Play Services APK is not available on the device,
			// this handle will be null (and this activity will crash if trying to use it)
			
			if (mapFragment != null) {
				map = mapFragment.getMap(); // mapFragment is valid if called from onResume!
			}
			else {
				// mapFragment does not exist yet if called from onCreate!
				Log.w(LOG, "mapFragment == null in setupMapIfNeeded!");
			}
			if (map == null) {
				Log.e(LOG, getString(R.string.map_error_msg));
				Toast.makeText(getApplicationContext(), 
						getString(R.string.map_error_msg), 
						Toast.LENGTH_LONG).show();
				return false;
			}			
			
			UiSettings mapSettings;
			mapSettings = map.getUiSettings();
			mapSettings.setCompassEnabled(true); 
		}	
		
		// Always show map types selection
		RadioGroup rgMaptypes = (RadioGroup) findViewById(R.id.rg_maptypes);
		rgMaptypes.setOnCheckedChangeListener(new OnCheckedChangeListener()  { 
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if (checkedId == R.id.rb_normal){ //default
					map.setMapType(GoogleMap.MAP_TYPE_NORMAL); //1	
					mapType = GoogleMap.MAP_TYPE_NORMAL;
					hasMapTypeChanged = true;
				} else if (checkedId == R.id.rb_satellite){
					map.setMapType(GoogleMap.MAP_TYPE_SATELLITE); //2		
					mapType = GoogleMap.MAP_TYPE_SATELLITE;
					hasMapTypeChanged = true;
				} else if (checkedId == R.id.rb_terrain){
					map.setMapType(GoogleMap.MAP_TYPE_TERRAIN); //3
					mapType = GoogleMap.MAP_TYPE_TERRAIN;
					hasMapTypeChanged = true;
				}
			}
		});		
		
		return true;
	}	
	
	/**
	 * Sets up game map specific UI depending on current map mode
	 * @param _mode The current map mode
	 */
	private void setupGameMap(MapMode _mode) {  
		
		// Optimization, only call this when necessary, ie when map type has changed
		if (hasMapTypeChanged) {
			map.setMapType(mapType);  // moved here from onResume
			hasMapTypeChanged = false;
		}
     
        switch(_mode) {  
        case INIT_MODE:
        	textDisplay.setText(getString(R.string.display_text_init_mode));
        	
        	doneSaveButton.setVisibility(View.INVISIBLE);
        	
        	// Clear map from possible previous game/create map action
        	map.clear();            	
        	clearPositionDisplay();
        	
        	// Clear data from possible previous game (that have been saved in database)
        	clearOngoingGameData();
        	
        	// Setup map and camera specific initial settings.        	
        	CameraPosition initialCameraPosition = new CameraPosition.Builder()
			   	.target(myDefaultStartAndEndPoint) // Sets the center of the map (stored in prefs)
			   	.zoom(myZoomLevel)        // Sets the zoom (stored in prefs)			 
			   	.build();                 // Creates a CameraPosition from the builder
			
			GoogleMapOptions options = new GoogleMapOptions();
	        options.mapType(GoogleMap.MAP_TYPE_NORMAL) //default normal	      
	        	.camera(initialCameraPosition)
	        	.compassEnabled(true) 
	        	.rotateGesturesEnabled(false)
	        	.tiltGesturesEnabled(false);	            	
        	break;        	
        case CREATE_MODE:
        	textDisplay.setText(getString(R.string.display_text_create_mode));   
        	
        	doneSaveButton.setVisibility(View.VISIBLE);
        	
        	// Clear map from possible previous game/create map action
        	map.clear();
        	clearPositionDisplay();
        	
        	// Clear data from possible previous game (that have been saved in database)
        	clearOngoingGameData(); //Added 20141123
        	
            setMyCameraPositionAndZoomLevel();           
        	
        	// Add Marker click listener to the map
         	map.setOnMarkerClickListener(this);
         	
         	// Add Marker drag listener to the map (so we can check that all control point markers have been dragged)
         	map.setOnMarkerDragListener(this);
         	
         	// Create list for controlPoint markers
         	controlPointList = new ArrayList<Marker>();
            
         	// Add all draggable control point markers to map and to a list
//            for (int i = numberOfControlPoints; i > 0; i--) { //start with last id
         	  for (int i = 1; i <= numberOfControlPoints; i++) { //start with id 1
            	addControlPointMarker(myStartAndEndPoint, i, true, false); 
            }
            
            // Add the start/finish point marker (handled separately, not put in control point list)            
            switch (intSelectedStartFlagPlaceMethod) {
            case 0: //Let user drag the flag to desired start position
            	addStartAndFinishPointMarker(myStartAndEndPoint, true); // draggable!
            	break;
            case 1: //Place flag on current location (requires GPS connection)
            	addStartAndFinishPointMarker(myStartAndEndPoint, false); // not draggable!
            	break;
            case 2: //Use edit fields for input of latitude/longitude (default)
            	addStartAndFinishPointMarker(myStartAndEndPoint, false); // not draggable!
            	break;
            }            
            break;
        case PREPARED_MODE:
        	Log.i(LOG, "Entered PREPARED mode in setupGameMap");
        	doneSaveButton.setVisibility(View.INVISIBLE); 
        	
        	if (hasOnCreateBeenCalledInPreparedMode && hasGameBeenSaved) {
        		textDisplay.setText(getString(R.string.display_text_recreate_prepared_mode)); 
        		
        		// Recreate a saved game map when app was started in PREPARED_MODE        		
        		if (geofencesAreSetup) {
        			Log.d(LOG, "Recreating a saved game map when app was started in PREPARED_MODE - Geofences are already setup!");
        			recreatePreparedGameMapFromFile(false); //do not resetup Geofences, since it should have been done already  //TODO: fault check and handling
        		}
        		else {
        			Log.d(LOG, "Recreating a saved game map when app was started in PREPARED_MODE - Will now resetup Geofences!");
        			recreatePreparedGameMapFromFile(true); //do resetup Geofences  //TODO: fault check and handling      			
        		}
    			
    			hasOnCreateBeenCalledInPreparedMode = false;
        	}
        	else {
        		// Get current zoom, and use it as myZoomLevel
        		myZoomLevel = map.getCameraPosition().zoom;
        	}
        	
        	// See actions done before entering PREPARED_MODE in DoneSaveButtonClickListener
        	textDisplay.setText(getString(R.string.display_text_prepared_mode));
        	
        	setMyCameraPositionAndZoomLevel();
        	setupPlayerAvatarMarker(myStartAndEndPoint);  
        	
        	// Save start point as previous location before starting to move (for drawing route)
//        	prev_lat = myStartAndEndPoint.latitude;
//        	prev_lng = myStartAndEndPoint.longitude;        	
        	break;        	
        case PLAY_GAME_MODE:    
        	Log.i(LOG, "Entered PLAY_GAME mode in setupGameMap");
        	doneSaveButton.setVisibility(View.INVISIBLE);
        	
        	// If onCreate has been called in Play Game mode
        	if (hasOnCreateBeenCalledInPlayGameMode) {
        		textDisplay.setText(getString(R.string.display_text_recreate_play_game_mode)); 
        		
        		// Recreate an ongoing game map when app was started in PLAY_GAME_MODE        		
        		Log.d(LOG, "Recreating an ongoing game map when app was started in PLAY_GAME_MODE");        		
        		recreateOngoingGameFromPrefs(); //TODO: fault check and handling
        		
        		// Try to redraw the whole route, using points stored in service's static list
        	/* Probably not working perfectly...?
        		drawWholeRoute();
        	*/       
        		
        		// Update whole route, but will it really work if app was killed?
        		updateRoute(true);
        		
        		hasOnCreateBeenCalledInPlayGameMode = false;
        		
        		// ADDED 20150109: Also need to disable this flag now, since this case is not relevant now
        		hasOnResumeBeenCalledInPlayGameMode = false;
        		
        		setMyCameraPositionAndZoomLevel();
            	setupPlayerAvatarMarker(myStartAndEndPoint);            	
        	}
        	// If ONLY onResume, and NOT onCreate has been called in Play Game mode
        	else if (hasOnResumeBeenCalledInPlayGameMode) {
        		// Update an ongoing game map when app was resumed in PLAY_GAME_MODE        		
        		Log.d(LOG, "Updating an ongoing game map when app was resumed in PLAY_GAME_MODE");
        		updateControlPointMarkersFromPrefs();
        		
        		// Update part of route
        		updateRoute(false); //OK        	
        		
        		hasOnResumeBeenCalledInPlayGameMode = false;
        	}
        	
        	textDisplay.setText(getString(R.string.display_text_play_game_mode));  
        	
        	if (isGhostAvatarEnabled) {
        		setupGhostAvatarMarker(myStartAndEndPoint);
        		
        		// Should only be true if app is restarted in PLAY mode, and we had an ongoing game
        		if (doMoveGhostAvatar) {	
            		ghostAvatarMarker.setVisible(true);
            		
					// Start GhostMover task (if it not already exists)
            		if (ghostMoverTask == null) {
            			
            			Log.i(LOG, "Starting ghostMoverTask with ghostMoveIntervalInSeconds=" + ghostMoveIntervalInSeconds.toString());
            			            			
            			ghostMoverTask = 
	        				new GhostMover().execute(ghostMoveIntervalInSeconds, 
	        										 userLocationIndexWhenGhostStartedToMove);	
            		}            		
				}
        	}        	
        	break;
        case STOPPED_MODE: 	
        	textDisplay.setText(getString(R.string.display_text_stopped_mode));        	
        	Log.i(LOG, "Entered STOPPED mode in setupGameMap");         	
        	
        	doneSaveButton.setVisibility(View.VISIBLE); 
        	
        	// Clear lists etc... just to be sure even if Garbage Collection takes care of it...?        	
        	if (myCurrentGeofencesList != null) {
        		if (!myCurrentGeofencesList.isEmpty()) myCurrentGeofencesList.clear();
        	}	
        	
        	if (controlPointList != null) {	
        	   if (!controlPointList.isEmpty()) controlPointList.clear();
        	}        	
        	
        	// STAY in STOPPED mode!       	
        	// Go back to INIT mode, only after data was saved in database or discarded. 
        	// See OnClickListener Done and SaveDialogFragment.        	
        	if (!hasOnCreateBeenCalledInStoppedMode) {
        		
        		// Ask user if he wants to save game data into database
            	showMySaveDialog();
        	}
        	else {
        		doneSaveButton.setVisibility(View.INVISIBLE);
        		hasOnCreateBeenCalledInStoppedMode = false;
        	}        	
        	break;
        default:
        	break;
        }      
	}    
	
	private void setMyCameraPositionAndZoomLevel() {		  
		
		// Setup map and camera specific settings.
    	CameraPosition myCameraPosition = new CameraPosition.Builder()
		   .target(myStartAndEndPoint) // Sets the center of the map   
		   .zoom(myZoomLevel)          // Sets the zoom			 
		   .build();                   // Creates a CameraPosition from the builder
		
		new GoogleMapOptions()
		   .mapType(mapType)
		   .compassEnabled(true) //ADDED 20150107 
           .camera(myCameraPosition);	
		
		// Could perhaps also have used (the static method):
//		CameraPosition.fromLatLngZoom(myStartAndEndPoint, myZoomLevel);
		
		CameraUpdate cu = CameraUpdateFactory.newCameraPosition(myCameraPosition);
		map.animateCamera(cu);
	}
	
	/**
	 * Recreate a prepared game map, placing start point and control point markers at locations saved in file.
	 * @param resetupGeofences flag telling if geofences should be set up or not.
	 * @return true if recreation was successful.
	 */
	private boolean recreatePreparedGameMapFromFile(boolean resetupGeofences) {
		
		// Only supporting recreate of a saved game in PREPARED_MODE
		if (mapMode != MapMode.PREPARED_MODE) return false;
		
		Log.d(LOG, "Attempting recreate from file...");		   
    	
    	// Clear map from possible previous game/create map action
    	map.clear();
    	clearPositionDisplay();
				
		List<String> coordsList = null;
		
		// Read coordinates from file, NOTE: First item in list is start point
		if (hasGameBeenSaved) {
			coordsList = FileUtils.readFromFileToListOfString(getApplicationContext(), MY_COURSE_FILE_NAME);
		}
		else {
			return false;
		}
    	
    	recreatePointMarkersFromList(coordsList); //TODO: fault check and handling
    
    	// Geofences should have already been created (if in PREPARED_MODE), so should normally not need to make another request to GeofenceRequester...? 
    	if (resetupGeofences) {
    		Log.i(LOG, "Trying to resetup Geofences...");
    		// Create Geofences, and make an add request to GeofenceRequester 
			if (!createMyGeofences()) {
				Log.e(LOG, "createMyGeofences FAILED in recreatePreparedGameMapFromFile");
								
				return false; // disallow further processing
			}    		
    	}
    	return true;
	}
	
	/**
	 * Recreate a prepared game demo map, placing start point and control point markers at locations saved in raw resource file.
	 * @param resetupGeofences flag telling if geofences should be set up or not.
	 * @param doSaveGameDemoInInternalFile flag telling if test game should be saved to internal storage as well.
	 * @param demo_number Number of demo route (currently two routes available).
	 * @return True if everything was fine.
	 * NOTE: Only used for test and demo!
	 * NOTE: Needs the coordinate data in Georienteering2\res\raw\coordinates
	 * NOTE: Requires LocationProvider mock test app with data read from LocationProvider\res\raw\coordinates
	 */	
	private boolean recreatePreparedGameDemoMapFromRawResource(boolean resetupGeofences, boolean doSaveGameDemoInInternalFile, int demo_number) {
		
		// Only supporting recreate of a game when NOT in PLAY_GAME_MODE
		if (mapMode == MapMode.PLAY_GAME_MODE) return false;
		
		Log.d(LOG, "Attempting recreate from raw resource...");		   
    	
    	// Clear map from possible previous game/create map action
    	map.clear();
    	clearPositionDisplay();
				
		List<String> coordsList = null;
		
		// Read coordinates from raw resource file, NOTE: First item in list is start point		
		Resources myRes = getResources();	
		
		switch (demo_number) {
		case 1:
		default:
			coordsList = FileUtils.readFromRawResourceToListOfString(myRes, R.raw.coordinates_demo1);
			break;
		case 2:
			coordsList = FileUtils.readFromRawResourceToListOfString(myRes, R.raw.coordinates_demo2);
			break;
		}			
		
		if (doSaveGameDemoInInternalFile) {
			// Save the start and control points data to internal storage as well (so we can test the 'normal' recreate functionality)
			if (!FileUtils.writeListOfStringToFile(getApplicationContext(), coordsList, MY_COURSE_FILE_NAME)) {
				Log.d(LOG, "FileUtils.writeListOfStringToFile failed!");						
			}
			else {
				hasGameBeenSaved = true; // This flag from now on will ALWAYS be true (saved in prefs)
			}			
		}		
    	
    	recreatePointMarkersFromList(coordsList); //TODO: fault check and handling
    
    	// Geofences need to be setup via a request to GeofenceRequester...? 
    	if (resetupGeofences) {
    		Log.i(LOG, "Trying to setup Geofences...");
    		// Create Geofences, and make an add request to GeofenceRequester 
			if (!createMyGeofences()) {
				Log.e(LOG, "createMyGeofences FAILED in recreatePreparedGameMapFromRawResource");
								
				return false; // disallow further processing
			}    		
    	}
    	return true;
	}
	
	/**
	 * Recreate an ongoing game map, placing start point and control point markers at locations saved in prefs.
	 * Also sets up geofences if the flag 'geofencesAreSetup' saved in prefs indicate that geofences are not active.
	 * Will bind to service if successful.
	 * NOTE: Similar to recreatePreparedGameMapFromFile, but this tries to use 'geofencesAreSetup' read from prefs instead of assuming whether geofences were setup or not.
	 * @return true if recreate (incl. setup of geofences) were successful.
	 */	
	private boolean recreateOngoingGameFromPrefs() {
		
		// Only supporting recreate of an ongoing game in PLAY_GAME_MODE
		if (mapMode != MapMode.PLAY_GAME_MODE) return false;
		
		Log.i(LOG, "Attempting recreate of an ongoing game from prefs...");		   
    	
    	// Clear map from possible previous game/create map action
    	map.clear();
    	clearPositionDisplay();
		
		List<String> coordsList = null;
		
		// Read coordinates from prefs
		Log.d(LOG, "Calling readControlPointsFromPreferences");
		coordsList = readControlPointsFromPreferences();
		
		recreatePointMarkersFromList(coordsList); //TODO: fault check and handling
		
		// If not geofences were setup already, then we need to redo it now...
		if (!geofencesAreSetup) {			
			Log.i(LOG, "Trying to resetup Geofences...");
			// Create Geofences, and make an add request to GeofenceRequester 
			if (!createMyGeofences()) {
				Log.e(LOG, "createMyGeofences FAILED in recreateOngoingGameFromPrefs");
				
				return false; // disallow further processing
			}
		}		
		
		doBindService();		
		
		return true;
   }	
	
	/**
	 * Recreates a game map from a list with coordinates for start and control points
	 * @param coordsList
	 * @return true if recreate was successful
	 */
	private boolean recreatePointMarkersFromList(List<String> coordsList) {
		
		Double lat;
		Double lng;
    	LatLng latLng;
    	boolean result = true;
    	
    	if (coordsList != null) {
    		int id = 0;
    		for (String line : coordsList) {
//    			String[] parts = {"",""}; //REMOVED 20150105
    			String[] parts = line.split(",");
				
				if (id == 0) {
					// First item in list is start point
					try {
						lat = Double.parseDouble(parts[0]);
						lng = Double.parseDouble(parts[1]);
					}
					catch (NumberFormatException nfe){
						nfe.printStackTrace();
						debug("NumberFormatException in recreatePointMarkersFromList (id == 0)");
						
						// TODO: We need to add a draggable start point and tell user to drag this to desired position!
												
						result = false;
						continue; //take next line instead
					}		
					
					latLng = new LatLng(lat, lng);
					addStartAndFinishPointMarker(latLng, false); //not draggable!					
				}
				else {
					
					if (controlPointList == null) {					
						// Create list for controlPoint markers
						controlPointList = new ArrayList<Marker>();
					}	
					
					// This is a control point
					try {
						lat = Double.parseDouble(parts[0]);
						lng = Double.parseDouble(parts[1]);
					}
					catch (NumberFormatException nfe){
						nfe.printStackTrace();
						debug("NumberFormatException in recreatePointMarkersFromList id:" + Integer.valueOf(id).toString());
						
						// TODO: We need to add a draggable control point and tell user to drag this to desired position!
										
						result = false;
						continue; //take next line instead
					}		
					
					latLng = new LatLng(lat, lng);
					addControlPointMarker(latLng, id, false, line.contains("Found")); //not draggable!					
				}			
				id++; //Note: Must increment here to get id starting from 1!
    		}
    		
    		// Update 
    		if (controlPointList.size() != numberOfControlPoints) {
    			Log.i(LOG, "Updating numberOfControlPoints to: " + Integer.valueOf(controlPointList.size()).toString());
    			numberOfControlPoints = controlPointList.size();
    		}
    	}
    	else {
    		Log.e(LOG, "coordsList == null in recreatePointMarkersFromList");
			numberOfControlPoints = 0;
    		return false;
    	}
    	return result;
	}
	
	/**
	 * Updates all control point markers if tag "Found" in prefs
	 */
	private void updateControlPointMarkersFromPrefs() {
		List<String> coordsList = null;
		
		// Read coordinates from prefs (incl. also start point with id "0")
		Log.d(LOG, "Calling readControlPointsFromPreferences");
		coordsList = readControlPointsFromPreferences();		
	
		// Update all found controls (some might have been updated already)
		int numControlsUpdated = 0;		
		if (coordsList != null) {
    		int id = 0; //id "0" is start point, skip it in the loop below!
    		int index = 0; //index is 0 for first control point with id "1"
    		for (String line : coordsList) {
    			
    			if ((id > 0) && (line.contains("Found"))) {
    				index = id - 1; //Decrement index since list starts from index 0 and ends at index controlPointList.size()-1
    				updateControlPointMarkerToFound(index);
    				numControlsUpdated++;
    			}
    			id++; //Note: Must increment here to get id starting from 1!    			
    		}
    		
    		// Update counter if changed
    		if (numControlPointsFound != numControlsUpdated) {
    			Log.i(LOG, "numControlsUpdated: " + Integer.valueOf(numControlsUpdated).toString() + 
    					"Old value for numControlPointsFound: " + Integer.valueOf(numControlPointsFound).toString());
    			numControlPointsFound = numControlsUpdated;    			
    		}
		}		
	}
	
	/**
	 * Updates the control point marker and icon as "Found" for the control with index 0...controlPointList.size()-1
	 * @param index
	 */
	private void updateControlPointMarkerToFound(Integer index) {
		// Update only for a valid index 0...controlPointList.size()-1
		if ( (index >= 0) && (index < controlPointList.size()) ) {
			
			Marker foundMarker = controlPointList.get(index);    			        			
			
			// Create new marker for a found control
			MarkerOptions updatedMarkerOptions = new MarkerOptions()
			.title(foundMarker.getTitle())
			.snippet("Found control") // Add "Found" to snippet string! Used in saveControlPointsInPreferences
			.draggable(false)	
			.position(foundMarker.getPosition())
			.icon(BitmapDescriptorFactory.fromResource(R.drawable.control_icon_green))
			.anchor(0.5f, 0.5f)
			.visible(true) //visible!
			.flat(true); //flat against the map
			
			// Need to update map
			foundMarker.remove();            			
			Marker updatedMarker = map.addMarker(updatedMarkerOptions);   
			
			// Replace with updated marker in list
			try {
				controlPointList.set(index, updatedMarker);
			}
			catch(UnsupportedOperationException |  //- if replacing elements in this List is not supported.
					ClassCastException |           //- if the class of an object is inappropriate for this List.
					IllegalArgumentException |     //- if an object cannot be added to this List.
					IndexOutOfBoundsException e ) { //- if location < 0 || location >= size()
				            					
				e.printStackTrace();
				Log.e(LOG, "controlPointList.set(index, updatedMarker) failed in updateControlPointMarkerToFound");
			}
		}            		
	}	
	
	/**
	 * Draws a whole route on map using static list in LocationTrackerService, if doShowRoute is true.
	 */
	/* NOT USED, see updateRoute
	private void drawWholeRoute() {
		
		if (doShowRoute && map != null) {     
			ArrayList<LatLng> list = null;
			try {
				list = LocationTrackerService.getRouteLatLngPoints();
			}
			catch (NullPointerException e) {
				// This may occur on the emulator if/when the Google maps fatal signal 11 happens
				// since Service has then been killed and the list will be null
				e.printStackTrace();
				
				// Inform user
				Toast.makeText(this, R.string.show_route_failure, Toast.LENGTH_LONG).show();
				
				// Disable show route 
				doShowRoute = false;
			}
			
			if (list != null) {
				PolylineOptions line_options = new PolylineOptions(); 
				
				// Check if first point in list is 0, because then it is faulty
				if ( (list.get(0).latitude == 0.0d) &&
				     (list.get(0).longitude == 0.0d) ) {        					
					
					// Inform user
    				Toast.makeText(this, R.string.show_route_failure, Toast.LENGTH_LONG).show();
    				
    				// Disable show route 
    				doShowRoute = false;        					
				}
				else {
					Log.d(LOG, "Attempting redraw of the whole route");
					
					// Redraw the whole route
					line_options.addAll(list);           	 
					map.addPolyline(line_options.width(5).color(Color.RED).geodesic(true)); 
				}

				// Update index
				current_route_point_index = list.size() - 1;
			}
		} 
	}
*/
	/**
	 * Updates part of route (that were not drawn while activity was in background).
	 * If doUpdateWholeRoute is set to true it will draw whole route by resetting
	 * the current_route_point_index to zero.
	 * The index is used to get points from static list in LocationTrackerService.
	 * 
	 * @param doUpdateWholeRoute If true then draw whole route.
	 */
	private void updateRoute(boolean doUpdateWholeRoute) {
		
		if (doShowRoute && map != null) { 			
			
			if (doUpdateWholeRoute) {
				// Update whole route, starting with start point at index 0
				current_route_point_index = 0;
				Log.i(LOG, "UPDATING WHOLE ROUTE, current_route_point_index=" + current_route_point_index);
			}
			else {
				Log.i(LOG, "NEED TO UPDATE PART OF ROUTE, current_route_point_index=" + current_route_point_index);
			}
			
			ArrayList<LatLng> list = null;
			try {
				list = LocationTrackerService.getRouteLatLngPoints();
			}
			catch (NullPointerException e) {
				// This may occur on the emulator if/when the Google maps fatal signal 11 happens
				// since Service has then been killed and the list will be null
				e.printStackTrace();
				
				// Inform user
				Toast.makeText(this, R.string.show_route_failure, Toast.LENGTH_LONG).show();
				
				// Disable show route 
				doShowRoute = false;
			}
			
			if (list != null) {
				int last_index = list.size() - 1;        				
				while (current_route_point_index < last_index) {
					
					// Add a thin red line from previous location to current location
    				map.addPolyline(new PolylineOptions()            				
    				.add(new LatLng(list.get(current_route_point_index).latitude, list.get(current_route_point_index).longitude), //from this point
    					 new LatLng(list.get(current_route_point_index + 1).latitude, list.get(current_route_point_index + 1).longitude)) //to this point
    				.width(5)        
    				.color(Color.RED)
    				.geodesic(true));
    				
    				// Increment index now
    				current_route_point_index++;        					
				} 
				// Now current_route_point_index equals last_index
				Log.i(LOG, "HAS UPDATED ROUTE, current_route_point_index=" + current_route_point_index);
			}        			
		}		
	}

	private void addControlPointMarker(LatLng latLng, Integer id, boolean isDraggable, boolean isFound) {
		
		Marker cp;
		
		if (isFound) {
			cp = map.addMarker(new MarkerOptions()
			.position(latLng)		
			.title(id.toString())
			.snippet("Found control point")
			.draggable(isDraggable)		
			.anchor(0.5f, 0.5f) //OK!		
			.icon(BitmapDescriptorFactory.fromResource(R.drawable.control_icon_green)));
		}
		else {
			cp = map.addMarker(new MarkerOptions()
			.position(latLng)		
			.title(id.toString())
			.snippet("Control point")
			.draggable(isDraggable)		
			.anchor(0.5f, 0.5f) //OK!		
			.icon(BitmapDescriptorFactory.fromResource(R.drawable.control_icon_doublesize)));
//			.icon(BitmapDescriptorFactory.fromResource(R.drawable.control_icon_quadsize)));
			
		/* WNBD: Larger and better looking control icons with clearer numbers				
			// Image icon resources has the name 'controlX' where X is the control number, example: 'control1' (since file name cannot start with number)   	
    		Integer code = Integer.valueOf(id.toString());    		    		    		
    		String resourceName = "control" + code.toString(); //+ ".png";
    		int imageResourceId = getResources().getIdentifier(resourceName, "drawable", getPackageName());
    		Log.d(LOG, "Image icon resource: " + resourceName + " = " + imageResourceId);
    		if (imageResourceId > 0) {    			
    			cp.setIcon(BitmapDescriptorFactory.fromResource(imageResourceId));
    		}
    		else {
    			cp.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.control_icon)); // default icon without any number
    		}
    	*/
		}
				
		controlPointList.add(cp);
	}
	
	private void addStartAndFinishPointMarker(LatLng latLng, boolean isDraggable) {
		startAndEndPointMarker = map.addMarker(new MarkerOptions()
		.position(latLng)		
		.title("0")	//use id 0 for start and end point
		.snippet("Start and Finish")
		.draggable(isDraggable)		
		.anchor(0.1f, 0.9f) //OK!
		.icon(BitmapDescriptorFactory.fromResource(R.drawable.startflag_blue_transp)));

		myStartAndEndPoint = latLng;
	}
	
	/**
	 * Process clicks on ALL markers
	 * @param marker
	 * @return
	 */
	@Override
	public boolean onMarkerClick(Marker marker) {				
		// Hide the info window
		marker.hideInfoWindow();
		
		// Show a toast with marker title instead		
		Toast.makeText(getApplicationContext(), 
				marker.getTitle(), 
				Toast.LENGTH_SHORT).show();
		
		// Return true to prevent default behavior of opening info window
		return true;		
	}
	
	@Override
	public void onMarkerDragStart(Marker marker) {
		return;
	}
	
	@Override
	public void onMarkerDragEnd(Marker marker) {		
		// Check which control points have been dragged, to make sure ALL have been moved in menu click handler		
		//Idea: Just use snippet parameter... with "dragged" to tell that marker has been moved.
		marker.setSnippet("dragged");		
		return;
	}
	
	@Override
	public void onMarkerDrag(Marker marker) {
		return;
	}	

	public void setupPlayerAvatarMarker(LatLng latLng) {
		
		// Remove previous marker
		if (playerAvatarMarker != null) {
			playerAvatarMarker.remove();
		}		
		
		// Create new marker
		playerAvatarMarker = map.addMarker(new MarkerOptions()
		.title("Me")
		.position(latLng)
		.icon(BitmapDescriptorFactory.fromResource(R.drawable.crosshair_grey_dot))
		.anchor(0.5f, 0.5f)
		.flat(true)); //flat against the map	
	}
	
	public void movePlayerAvatarMarker(LatLng latLng) {
		
		if (mapMode == MapMode.PLAY_GAME_MODE) {		
			if (playerAvatarMarker != null)
				playerAvatarMarker.setPosition(latLng);	//OK!
		}
		else {
			Log.d(LOG, "Wrong mapMode in movePlayerAvatarMarker");
		}
	}
	
	public void setupGhostAvatarMarker(LatLng latLng) {
		
		// Remove previous marker
		if (ghostAvatarMarker != null) {
			ghostAvatarMarker.remove();
		}		
		
		// Create new marker
		ghostAvatarMarker = map.addMarker(new MarkerOptions()
		.title("Ghost")
		.position(latLng)
		.icon(BitmapDescriptorFactory.fromResource(R.drawable.ghost_avatar))
		.anchor(0.5f, 0.5f)
		.visible(false) //not visible from start!
		.flat(true)); //flat against the map	
	}
	
	public void moveGhostAvatarMarker(LatLng latLng) {
		
		if (mapMode == MapMode.PLAY_GAME_MODE && isGhostAvatarEnabled) {		
			if (ghostAvatarMarker != null) {				
				ghostAvatarMarker.setPosition(latLng);	//OK!				
			}
		}
	}

	/**
	 * Service connection handling
	 */	
	private ServiceConnection myServiceConnection = new ServiceConnection() {
    	@Override  
    	// Called only when a connection is made using binding
    	// NOTE: Will NOT be called if service already was bound, even if bindService is called!
    	public void onServiceConnected(ComponentName cName, IBinder binder) {
// Split this single line into two code lines
//    		myService = ((LocationTrackerService.LocationTrackerBinder)binder).getService();
    		
    		LocationTrackerService.LocationTrackerBinder localBinder = null;
    		
/*
01-02 12:03:39.567: E/AndroidRuntime(3848): java.lang.ClassCastException: android.os.BinderProxy cannot be cast to dv106.pp222es.georienteering2.LocationTrackerService$LocationTrackerBinder
 */
    		
    		try {
    			localBinder = (LocationTrackerService.LocationTrackerBinder) binder;
    		}
    		catch (ClassCastException cce) {
    			cce.printStackTrace();
    			
    			Log.e(LOG, "Cast of binder failed! Component: " + cName.flattenToString());
    			
    			if (binder.isBinderAlive())	{
    				Log.i(LOG, "Binder is alive");
    			}
    			else {
    				Log.w(LOG, "Binder is not alive");
    			}
    				    				
    			if (binder.pingBinder()) {
    				Log.i(LOG, "Hosting process exists (ping was ok)");
    			}
    			else {
    				Log.w(LOG, "Hosting process has gone (ping was not ok)");
    			}
    			
    			//binder.queryLocalInterface(descriptor)    			
    			
    			//######################################################################################
    			return; //FIXME: How can we recover from this? Only start service without binding to it?
    		}
    		
    		// Get reference to service
    	    MainActivity.myService = (LocationTrackerService) localBinder.getService();
    		
    		Log.i(LOG, "onServiceConnected");  
    		debug("onServiceConnected");
    		
    		// Set start point as soon as possible
    		if (MainActivity.myService != null) {
    			MainActivity.myService.setStartPoint(myStartAndEndPoint);
    		}
    		else {
    			Log.wtf(LOG, "MainActivity.myService == null in onServiceConnected");
    		}

    		// If in PLAY GAME mode, start location tracking immediately
    		if (mapMode == MapMode.PLAY_GAME_MODE) {	
    			if (MainActivity.myService != null) {
    				if (!MainActivity.myService.isTracking()) MainActivity.myService.startTracking(gameLevel);
    			}
    			else {
    				Log.wtf(LOG, "MainActivity.myService is NULL in onServiceConnected!");
    			}
    		}
    	}    	
    	@Override  
    	// Called when disconnected - Note that tracking in service should continue for an ongoing game!
    	public void onServiceDisconnected(ComponentName cName) {
    		MainActivity.myService = null;    		
    		Log.i(LOG, "onServiceDisconnected");
    	}
    };    
    
    private void doBindService() {
    	// Connect to service, creating it if needed
//    	isServiceBound = bindService(new Intent(this, LocationTrackerService.class), myServiceConnection, Context.BIND_AUTO_CREATE); 
    	    		
    	if (!LocationTrackerService.isRunning()) { //ADDED 20141230    		
    		Log.d(LOG, "Service NOT running before calling bindService");
    	}
    	else {
    		Log.d(LOG, "Service IS ALREADY RUNNING before calling bindService");    		
    	}     	
    	
    	if (isServiceBound) { //ADDED 20150113
    		// NOTE: If service was already bound (after a config change where binding was
    		// automatically done when onCreate called)
    		// then onServiceConnected will now not be called after bindService!
    		Log.wtf(LOG, "Service WAS ALREADY BOUND! in doBindService! Will NOT call bindService.");
    		debug("Service WAS ALREADY BOUND! in doBindService! Will NOT call bindService.");
    		return;
    	}    	
    	
    	isServiceBound = bindService(myServiceIntent, myServiceConnection, Context.BIND_AUTO_CREATE); 
    	
    	if (isServiceBound) {
        	Log.i(LOG, "Binding to LocationTrackerService was successful!");
        }
        else {
        	Log.e(LOG, "Binding to LocationTrackerService failed!");
        	debug("Binding to LocationTrackerService failed!");
        }
    }
    
    private void doUnbindService() {
        if (isServiceBound) {  
        	
//20150102: Moved to onDestroy
//        	// Tell service that client is no longer alive
//        	if (myService != null) myService.setClientAlive(false);
        	
            // Disconnect from service, the service is now allowed to stop at any time...
            unbindService(myServiceConnection); 
            isServiceBound = false;   
            
            Log.d(LOG, "Has done unbinding from LocationTrackerService");
        }
    }  
	
    private void doBindToServiceIfIsRunning() {
        // If the service is already running when the activity starts, we want to automatically bind to it
        if (LocationTrackerService.isRunning()) {
        	
        	if (isServiceBound) {
        		
        		Log.wtf(LOG, "Service WAS ALREADY BOUND! in doBindToServiceIfIsRunning");
        		debug("Service WAS ALREADY BOUND! in doBindToServiceIfIsRunning");
        	}
        	
        	Log.i(LOG, "Attempting to bind to already running service...");
            
        	doBindService();
            
            if (isServiceBound) {
            	Log.d(LOG, "Binding to LocationTrackerService was successful!");
            }
            else {
            	Log.e(LOG, "Binding to LocationTrackerService failed!");
            }
        }
        else {
        	Log.d(LOG, "Could not automatically bind to LocationTrackerService, since service is not running");        	
        }
    }
    
    /**
     * BroadcastReceiver
     * To receive location data from LocationTrackerService
     */
    private BroadcastReceiver mainLocationDataReceiver = new BroadcastReceiver() { 	
    	
		@Override
		public void onReceive(Context context, Intent intent) {
			
			// Validate that we are in Play Game mode, added 20141217
			if (mapMode != MapMode.PLAY_GAME_MODE) return;
			
			// Check the action code and determine what to do
//            String action = intent.getAction();
			
			double lat = intent.getDoubleExtra("EXTRA_LATITUDE", 0.0d);
			double lng = intent.getDoubleExtra("EXTRA_LONGITUDE", 0.0d);
			float dist = intent.getFloatExtra("EXTRA_DISTANCE", 0.0f);
			
			Float fDistance = Float.valueOf(dist);			
			intDistanceForUser = fDistance.intValue();	
			
			if (isGhostAvatarEnabled && doMoveGhostAvatar) {
				Integer diffToGhost = Integer.valueOf(intDistanceForUser) - intDistanceForGhost;
				if (diffToGhost >= 0) diffDistToGhostAsString = diffToGhost.toString();	
			}			
			
			if (intDistanceForUser > 0)
				distanceAsString = Integer.valueOf(intDistanceForUser).toString();			
			
			if ( (lat == 0.0d) && (lng == 0.0d) ) {
				Log.w(LOG, "0.0d in lat and lng in mainLocationDataReceiver");
			}	
			else {
				movePlayerAvatarMarker(new LatLng(lat, lng)); //OK! (Can also use map.setMyLocationEnabled(true) to display a default blue dot)
			}
			
			// Draw part of route using points stored in service's static list
			if (doShowRoute && map != null) {		
				
				ArrayList<LatLng> list = null;
    			try {
    				list = LocationTrackerService.getRouteLatLngPoints();
    			}
    			catch (NullPointerException e) {
    				// This may occur on the emulator if/when the Google maps fatal signal 11 happens
    				// since Service has then been killed and the list will be null
    				e.printStackTrace();
    			}
				
    			if (list != null) { 
    				// Check that there are min two points in the list!
    				if (list.size() > 1) { // We always store start point first in list, so list should contain min 2 points!
    				   current_route_point_index = list.size() - 1;
    				}
    				else {
    					Log.e(LOG, "list.size() < 2 in mainLocationDataReceiver");
    					return; //Bail out
    				}

    				// Add a thin red line from previous location to current location
    				map.addPolyline(new PolylineOptions()    				
    				.add(new LatLng(list.get(current_route_point_index - 1).latitude, list.get(current_route_point_index - 1).longitude), //from this point
    					 new LatLng(lat, lng)) //to this point
    				.width(5)        
    				.color(Color.RED)
    				.geodesic(true));
    			}
			}
		}    	
    };
    
    /**
     * BroadcastReceiver
     * To receive timer data from LocationTrackerService
     */
    private BroadcastReceiver mainTimerDataReceiver = new BroadcastReceiver() {   	
    	
		@Override
		public void onReceive(Context context, Intent intent) {
			
			// Validate that we are in Play Game mode, added 20141217
			if (mapMode != MapMode.PLAY_GAME_MODE) return;
			
			// Check the action code and determine what to do
//            String action = intent.getAction();
			
			Integer timerValue = intent.getIntExtra("EXTRA_TIMER_DATA", 0);			
				
			if (timerValue < 60) {
				numMinutes = 0;
			}
			else {
				numMinutes = (timerValue / 60);
			}
			
			numSeconds = timerValue - (numMinutes*60);
			String numSecondsAsString;
			
			if (numSeconds < 10) {
				numSecondsAsString = "0" + numSeconds.toString();
			}
			else {
				numSecondsAsString = numSeconds.toString();
			}				
			timeAsString = numMinutes.toString() + ":" + numSecondsAsString;			
			updatePositionDisplay();						
			
			if (isGhostAvatarEnabled && !doMoveGhostAvatar) {
				// Start moving ghost after x minutes depending on gameLevel
				switch (gameLevel) {
				case EASY:
				default:	
					if (numMinutes == 3) {					
						doMoveGhostAvatar = true;
						ghostAvatarMarker.setVisible(true);
						userLocationIndexWhenGhostStartedToMove = myService.getNumLocations();
						ghostMoveIntervalInSeconds = LocationTrackerService.UPDATE_INTERVAL_IN_SECONDS_EASY_LEVEL;
					}
					break;
				case MEDIUM:
					if (numMinutes == 2) {					
						doMoveGhostAvatar = true;
						ghostAvatarMarker.setVisible(true);
						userLocationIndexWhenGhostStartedToMove = myService.getNumLocations();
						ghostMoveIntervalInSeconds = LocationTrackerService.UPDATE_INTERVAL_IN_SECONDS_MEDIUM_LEVEL;
					}
					break;
				case HARD:
					if (numMinutes == 1) {
						doMoveGhostAvatar = true;
						ghostAvatarMarker.setVisible(true);
						userLocationIndexWhenGhostStartedToMove = myService.getNumLocations();
						ghostMoveIntervalInSeconds = LocationTrackerService.UPDATE_INTERVAL_IN_SECONDS_HARD_LEVEL;
					}
					break;    
				}
				if (doMoveGhostAvatar) {					
					// Start GhostMover task
					Log.i(LOG, "Starting ghostMoverTask with ghostMoveIntervalInSeconds=" + ghostMoveIntervalInSeconds.toString());				
					ghostMoverTask = 
	        				new GhostMover().execute(ghostMoveIntervalInSeconds, 
	        										 userLocationIndexWhenGhostStartedToMove);	        		
				}
			}
		}    	
    };
	
    /**
     * BroadcastReceiver
     * To receive geofence transition events from the GeofenceDetectorService
     */
    private BroadcastReceiver mainGeofenceDetectorReceiver = new BroadcastReceiver() { 	
    	
    	@Override
		public void onReceive(Context context, Intent intent) {
    		// Check the action code and determine what to do
            String action = intent.getAction();

            // Intent contains information about errors in adding or removing geofences
            if (TextUtils.equals(action, GeofenceUtils.ACTION_GEOFENCE_ERROR)) {
            	String msg = intent.getStringExtra(GeofenceUtils.EXTRA_GEOFENCE_STATUS);
                Log.e(LOG, msg);
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();            
            }            
            else if (TextUtils.equals(action, GeofenceUtils.ACTION_GEOFENCES_ADDED)) {
            	Log.i(LOG, getString(R.string.add_geofences_intent_success));
            	Toast.makeText(context, R.string.add_geofences_intent_success, Toast.LENGTH_LONG).show();
            	geofencesAreSetup = true;
            }
            else if (TextUtils.equals(action, GeofenceUtils.ACTION_GEOFENCES_REMOVED)) {
            	Log.i(LOG, getString(R.string.remove_geofences_intent_success));
            	Toast.makeText(context, R.string.remove_geofences_intent_success, Toast.LENGTH_LONG).show();
            	geofencesAreSetup = false;
            } 
            // Intent contains information about a geofence transition
            else if (TextUtils.equals(action, GeofenceUtils.ACTION_GEOFENCE_TRANSITION)) {             	    	
            	
            	String ids = intent.getStringExtra(GeofenceUtils.EXTRA_GEOFENCE_IDS);
            	
            	if (ids.equals("0")) {            		
            		            		
            		debug("START AND END POINT GEOFENCE TRANSITION! numControlPointsFound=" + numControlPointsFound);            		
            		
            		if ((mapMode == MapMode.PREPARED_MODE) && (numControlPointsFound == 0)) { //Most likely user is about to start the game            			
            			            			
            			textDisplay.setText(getString(R.string.at_start_point_msg));            			
            		}            		     		
            		else if (numControlPointsFound >= numberOfControlPoints) { //User has reached the finish, and found all controls
            			 
            			if (!DEBUG_MODE) { //ADDED 20150112: To debug ghost movement
            				stopOngoingGame(true);
            			}
            		}   		
            		else { //User has reached the finish, but not found all controls
            			
            			if (!DEBUG_MODE) { 
            				stopOngoingGame(false);            				
            			}
            		}
            	}
            	else {
            		
            		Log.i(LOG, "GEOFENCE TRANSITION! ids=" + ids);
            		
            		// Validate that a game is ongoing
                	if (mapMode != MapMode.PLAY_GAME_MODE) {
                		Log.d(LOG, "ACTION_GEOFENCE_TRANSITION, but not in PLAY_GAME_MODE");
                		return;
                	}
            		
            		numControlPointsFound++;
            		
            		//FIXME: If ids would contain several ids (1,2,3) (It is possible if controls are very close to each other)
            	
            		String parts[];   		
            		if (ids.contains(",")) {            			
            			parts = ids.split(",");
            			debug("IDS contain several ids!!! parts[0]=" + parts[0] + "parts[1]=" + parts[1]);
            		}
            		
            		// Notify user
            		String msg = getString(R.string.found_control_point_msg) + " " + ids;
            		Log.d(LOG, msg);
            		Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            		textDisplay.setText(msg);            		
            		
            		int index = -1;
            		try {
            		    index = Integer.parseInt(ids); //index 1...numControlPoints
            		}
            		catch (NumberFormatException e) {
            			e.printStackTrace();
            			Log.e(LOG, "Integer.parseInt(ids) failed in mainGeofenceDetectorReceiver");
            		}
            		index -= 1; //Decrement index since list starts from index 0 and ends at index controlPointList.size()-1
            		            		
            		// This may happen if an attempt to recreate ongoing game failed...?
            		if (controlPointList == null) { //Extra safety check (should not happen if readControlPointsFromPreferences has worked)
            			Log.wtf(LOG, "controlPointList == null in mainGeofenceDetectorReceiver");
            			
            			// Something is really bad now, bail out
            			return;
            		}
            		
            		// Change the icon for the control that was found
            		updateControlPointMarkerToFound(index); //OK!
            	}
            } 
            // The Intent contained an invalid action
            else {            	
                Log.w(LOG, getString(R.string.geofence_invalid_action_detail, action));
                Toast.makeText(context, R.string.geofence_invalid_action, Toast.LENGTH_LONG).show();
            }
    	}
    };  
    
    private boolean stopOngoingGame(boolean hasGameBeenWon) {    	
    	// Validate that app is in Play Game mode
    	if (mapMode != MapMode.PLAY_GAME_MODE) {
    		String msg = getString(R.string.not_in_play_mode_info_msg);
    		Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    		return false;
    	}    	
    	
    	// Unlock screen orientation back to user defined (allowing orientation changes)
    	unlockScreenOrientation(); //ADDED 20141230
    	
    	// Cancel the ghost mover task
    	if (isGhostAvatarEnabled) {
    		if (ghostMoverTask != null) {
    			ghostMoverTask.cancel(true);
    		}
    		doMoveGhostAvatar = false;
    	}    		
    	
    	if (myService != null) {
			if (myService.isTracking()) myService.stopTracking(); // Stop tracking when user actively stops the game			
    	}
    	
    	// Disconnect from LocationTrackerService
		doUnbindService();			

		if (hasGameBeenWon) {   
    		// Show a nice winner Toast
    		showMyToast(getString(R.string.game_was_won_toast_msg));
    		textDisplay.setText(getString(R.string.game_was_won_msg));
    		
    		// Play a winner fanfare
    		audioClipPlayerTask = 
    				new AudioClipPlayerTask(getApplicationContext()).execute(1);
    		
    		hasGameBeenWon = false;
    	}
    	else {    		
    		Toast.makeText(getApplicationContext(), R.string.game_was_lost_msg, Toast.LENGTH_LONG).show();
    		textDisplay.setText(getString(R.string.game_was_lost_msg));  
    		
    		// Play a horrible ghost sound
    		audioClipPlayerTask = 
    				new AudioClipPlayerTask(getApplicationContext()).execute(0);
    	}    
		
		// Remove the geofences via request to GeofenceRemover
		// NOTE: Actually now also controlPointList should be cleared... but we do this in 
		if (!removeMyGeofences() ) {

			debug("Failed to remove all geofences in stopOngoingGame. Will stay in PLAY_GAME_MODE. Retrying...");
			return false;
		}		
		
		mapMode = MapMode.STOPPED_MODE; // We need a STOPPED mode, where data can be saved!
		setupGameMap(mapMode); 
		
		return true;
    }
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		
		MenuItem item_load = menu.findItem(R.id.action_load_last_saved_game);
		item_load.setEnabled(false);
		
		return true;
	}
	
	/**
	 * This is called right before the menu is shown, every time it is shown. 
	 * You can use this method to efficiently enable/disable items or otherwise dynamically modify the contents.
	 * On Android 3.0 and higher, the options menu is considered to always be open when menu items are presented in the action bar. 
	 * When an event occurs and you want to perform a menu update, you must call invalidateOptionsMenu() to request that the system call onPrepareOptionsMenu().
	 */
	@Override
	public  boolean onPrepareOptionsMenu(Menu menu) {
				
		MenuItem item_create = menu.findItem(R.id.action_create_new_game);
		MenuItem item_save = menu.findItem(R.id.action_save_game);
		MenuItem item_load = menu.findItem(R.id.action_load_last_saved_game);		
		MenuItem item_start = menu.findItem(R.id.action_start_game);
		MenuItem item_stop = menu.findItem(R.id.action_stop_game);
		MenuItem item_view_game_data = menu.findItem(R.id.action_view_game_data);
		MenuItem item_settings = menu.findItem(R.id.action_settings);
		
		if (mapMode == null) return true;
		
		// Enable/disable menu items
		switch (mapMode) {
		case INIT_MODE:	
		default:
			item_create.setEnabled(true);
			item_save.setEnabled(false);
			item_load.setEnabled(hasGameBeenSaved); //disabled menu item if not the very first save has been done!
			item_start.setEnabled(false);
			item_stop.setEnabled(false);
			item_view_game_data.setEnabled(true);
			item_settings.setEnabled(true);
			break;
			
		case CREATE_MODE:
			item_create.setEnabled(true);
			item_save.setEnabled(true);
			item_load.setEnabled(hasGameBeenSaved); 
			item_start.setEnabled(false);
			item_stop.setEnabled(false);
			item_view_game_data.setEnabled(false);
			item_settings.setEnabled(true); //Enabled?
			break;		
			
		case PREPARED_MODE:
			item_create.setEnabled(false);
			item_save.setEnabled(false);
			item_load.setEnabled(false);
			item_start.setEnabled(true);
			item_stop.setEnabled(false);
			item_view_game_data.setEnabled(true);
			item_settings.setEnabled(true);
			break;			
			
		case PLAY_GAME_MODE:			
			item_create.setEnabled(false);
			item_save.setEnabled(false);
			item_load.setEnabled(false);
			item_start.setEnabled(false);
			item_stop.setEnabled(true);
			item_view_game_data.setEnabled(false);
			item_settings.setEnabled(false);
			break;		
			
		case STOPPED_MODE:
			item_create.setEnabled(true);
			item_save.setEnabled(true);
			item_load.setEnabled(true);
			item_start.setEnabled(false); //Cannot restart a stopped game
			item_stop.setEnabled(false);
			item_view_game_data.setEnabled(true);
			item_settings.setEnabled(true);
			break;				
		}		
		return true;
	}

	/**
	 * PROBLEM ON JELLYBEAN PHONE (root cause still unknown despite of below "fix"): 
	 * 
01-10 18:57:54.032: I/LocationTrackerService(7770): Service Destroyed...
01-10 18:57:54.042: I/LocationTrackerService(7770): Service Running...
01-10 18:57:54.062: I/libblt_hw(7770): Library opened (handle = 1, fd = 85)
01-10 18:57:54.072: I/MainActivity(7770): onServiceConnected
01-10 18:53:21.620: E/SpannableStringBuilder(7770): SPAN_EXCLUSIVE_EXCLUSIVE spans cannot have a zero length
	 *
	 * NOTE: Should always return super.onOptionsItemSelected(item)!
	 * http://stackoverflow.com/questions/13670374/android-span-exclusive-exclusive-spans-cannot-have-a-zero-length
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		String msg;
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			// Start the preference activity using an intent
			Intent pref_intent;
			pref_intent = new Intent(this, MyPreferenceActivity.class);	
			if (pref_intent != null) {
				//startActivityForResult(pref_intent, SHOW_PREFERENCES); //If this used then a requestID = 3 is sent to ActivityResult which is unnecessary since a OnSharedPreferenceChange listener is used
				startActivity(pref_intent); 		
			}
//			return true;
			return super.onOptionsItemSelected(item);
		}		
		if (id == R.id.action_create_new_game) {
			// Validate that app is not in Play Game mode
			if (mapMode == MapMode.PLAY_GAME_MODE) {
				msg = getString(R.string.play_mode_info_msg);
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); 
//				return true;
				return super.onOptionsItemSelected(item);
			}	
			// Validate that app is not in Prepared mode
			else if (mapMode == MapMode.PREPARED_MODE) {
				msg = getString(R.string.prepared_mode_info_msg);
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); 
//				return true;
				return super.onOptionsItemSelected(item);
			}
			
			// Display create new game activity		
		 	// Create intent for sub-activity
			Intent intent = new Intent(this.getApplicationContext(), CreateNewGameActivity.class);
						
			// Start sub-activity, which may send a result
			startActivityForResult(intent, MY_CREATE_NEW_GAME_REQUEST); //NOTE: This activity will cause main activity to be restarted!

//			return true;
			return super.onOptionsItemSelected(item);
		}
		if (id == R.id.action_save_game) {
			
			handleSaveAction();
			return super.onOptionsItemSelected(item);
		}
		
		if (id == R.id.action_load_last_saved_game) {			
			// Disallow this action in PREPARED_MODE and PLAY_GAME mode (because then Geofences are already activated)!
			// Validate that app is not in Prepared mode
			if (mapMode == MapMode.PREPARED_MODE) {
				msg = getString(R.string.cannot_recreate_game_in_prepared_mode_info_msg);
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); 
//				return true;
				return super.onOptionsItemSelected(item);
			}
			// Validate that app is not in Play Game mode
			else if (mapMode == MapMode.PLAY_GAME_MODE) {
				msg = getString(R.string.cannot_recreate_game_in_play_mode_info_msg);
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); 
//				return true;
				return super.onOptionsItemSelected(item);
			}			
						
			// Recreate a saved game map into PREPARED_MODE
			mapMode = MapMode.PREPARED_MODE;
			recreatePreparedGameMapFromFile(true); //resetup Geofences, since we have not performed PREPARED_MODE actions in Done button handler!			
			setupGameMap(mapMode);			       //and geofences should have been removed in stopOngoingGame for previous game
//			return true;
			return super.onOptionsItemSelected(item);
		}		
		if (id == R.id.action_start_game) {
			// Validate that app is in Prepared mode
			if (mapMode != MapMode.PREPARED_MODE) {
				msg = getString(R.string.not_in_prepared_mode_info_msg);
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); 
//				return true;
				return super.onOptionsItemSelected(item);
			}		

			// Lock screen orientation to the current orientation (during game play)
        	lockScreenOrientation(this); //ADDED 20141230
			
			mapMode = MapMode.PLAY_GAME_MODE; // IMPORTANT: MUST be done BEFORE doBindService()!
			
			//WORKAROUND 20150105: Reset flag to false (Why is it true sometimes here?)
			//20150113: Probably fixed, but keep this check here just in case
			if (hasOnResumeBeenCalledInPlayGameMode) {
				Log.wtf(LOG, "hasOnResumeBeenCalledInPlayGameMode is strangely set to true in onOptionsItemSelected for action_start_game");
				hasOnResumeBeenCalledInPlayGameMode = false;
			}
			
			doBindService();			
			
			setupGameMap(mapMode);
//			return true;
			return super.onOptionsItemSelected(item);
		}
		if (id == R.id.action_stop_game) {
			// Validate that app is in Play Game mode
			if (mapMode != MapMode.PLAY_GAME_MODE) {
				msg = getString(R.string.not_in_play_mode_info_msg);
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); 
//				return true;
				return super.onOptionsItemSelected(item);
			}
			
			stopOngoingGame(false);		
//			return true;
			return super.onOptionsItemSelected(item);
		}
		if (id == R.id.action_view_game_data) {
			// Start the activity using an intent
			Intent view_game_data_intent;
			view_game_data_intent = new Intent(this, GameDataActivity.class);	
			if (view_game_data_intent != null) {							
				startActivity(view_game_data_intent); 		
			}
//			return true;
			return super.onOptionsItemSelected(item);
		}
		if (id == R.id.action_license_info) {
			showGooglePlayServicesLicenseDialog();
			return super.onOptionsItemSelected(item);
		}
		if (id == R.id.action_about_app) {
			showMyToast(getString(R.string.about_app_message));
			return super.onOptionsItemSelected(item);
		}
		
	// START DEBUG menu items	
	/*
		if (id == R.id.action_load_game_demo_one) {			
			// Disallow this action in PREPARED_MODE and PLAY_GAME mode (because then Geofences are already activated)!
			// Validate that app is not in Prepared mode
			if (mapMode == MapMode.PREPARED_MODE) {
				msg = getString(R.string.cannot_recreate_game_in_prepared_mode_info_msg);
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); 
				return super.onOptionsItemSelected(item);
			}
			// Validate that app is not in Play Game mode
			else if (mapMode == MapMode.PLAY_GAME_MODE) {
				msg = getString(R.string.cannot_recreate_game_in_play_mode_info_msg);
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); 
				return super.onOptionsItemSelected(item);
			}			
						
			// Recreate a game demo map into PREPARED_MODE from raw resource file
			mapMode = MapMode.PREPARED_MODE;
			recreatePreparedGameDemoMapFromRawResource(true, true, 1); //resetup Geofences, since we have not performed PREPARED_MODE actions in Done button handler!			
			setupGameMap(mapMode);			       //and geofences should have been removed in stopOngoingGame for previous game
			return super.onOptionsItemSelected(item);
		}	
		if (id == R.id.action_load_game_demo_two) {			
			// Disallow this action in PREPARED_MODE and PLAY_GAME mode (because then Geofences are already activated)!
			// Validate that app is not in Prepared mode
			if (mapMode == MapMode.PREPARED_MODE) {
				msg = getString(R.string.cannot_recreate_game_in_prepared_mode_info_msg);
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); 
				return super.onOptionsItemSelected(item);
			}
			// Validate that app is not in Play Game mode
			else if (mapMode == MapMode.PLAY_GAME_MODE) {
				msg = getString(R.string.cannot_recreate_game_in_play_mode_info_msg);
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); 
				return super.onOptionsItemSelected(item);
			}			
						
			// Recreate a game demo map into PREPARED_MODE from raw resource file
			mapMode = MapMode.PREPARED_MODE;
			recreatePreparedGameDemoMapFromRawResource(true, true, 2); //resetup Geofences, since we have not performed PREPARED_MODE actions in Done button handler!			
			setupGameMap(mapMode);			       //and geofences should have been removed in stopOngoingGame for previous game
			return super.onOptionsItemSelected(item);
		}	
		if (id == R.id.action_test_enable_rotation) {		
			// Unlock screen orientation back to user defined
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
//			return true;
		}
		if (id == R.id.action_test_disable_rotation) {
			// Get overall orientation of the screen
			switch (getResources().getConfiguration().orientation) {
			case Configuration.ORIENTATION_PORTRAIT:
				// Lock screen orientation to portrait
				// NOTE: If the activity is currently in the foreground or otherwise impacting the screen orientation, 
				// the screen will immediately be changed (possibly causing the activity to be restarted). 
				// Otherwise, this will be used the next time the activity is visible.
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				break;
			case Configuration.ORIENTATION_LANDSCAPE:
				// Lock screen orientation to landscape
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				break;
			case Configuration.ORIENTATION_UNDEFINED:
			default:
				// Lock screen orientation to portrait by default
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				break;
			}			
//			return true;
		}
		if (id == R.id.action_test_check_rotation) {
			Display display = getWindowManager().getDefaultDisplay();			
			// Get the rotation of the screen from its "natural" orientation
	        int rotation = display.getRotation();
	        
	        // Get overall orientation of the screen
	        int screenOrientation = getResources().getConfiguration().orientation;
	        
	        debug("rotation:" + rotation + ", screenOrientation:" + screenOrientation);
//			return true;
		}
		
		if (id == R.id.action_test_start_service) {	
			ComponentName cn = startService(myServiceIntent);
			if (cn == null) {
				Log.e(LOG, "Failed to start service");
			}			
//			return true;
		}
		if (id == R.id.action_test_stop_service) {
			if (!stopService(myServiceIntent)) {
				Log.e(LOG, "Failed to stop service");
			}
//			return true;
		}	
		if (id == R.id.action_test_bind_service) {
			doBindService();
//			return true;
		}
		if (id == R.id.action_test_unbind_service) {
			doUnbindService();
//			return true;
		}	
		if (id == R.id.action_test_start_tracking) {
			if (myService != null) myService.startTracking(gameLevel);
//			return true;
		}
		if (id == R.id.action_test_stop_tracking) {
			if (myService != null) myService.stopTracking();
//			return true;
		}
		
		if (id == R.id.action_test_check_service) {
			if (myService != null) {
				if (isServiceBound) {
	            	Log.i(LOG, "Service is bound!");	            	
	            }
	            else {
	            	Log.e(LOG, "Service is not bound!");
	            	debug("Service is not bound!");
	            }
				if (myService.isTracking()) {
            		Log.i(LOG, "Service is tracking!");
            	}
            	else {
            		Log.i(LOG, "Service is not tracking!");
            		debug("Service is not tracking!");
            	}
			}
			else {
				Log.e(LOG, "Service is not available! (myService == null)");	
				debug("Service is not available! (myService == null)");
			}
			
			if (LocationTrackerService.isRunning()) {
				Log.i(LOG, "Service is running!");
				
//				if (LocationTrackerService.isStaticTracking()) {
//            		Log.i(LOG, "Service is tracking!");
//            	}
//            	else {
//            		Log.i(LOG, "Service is not tracking!");
//            	}					
			}
			else {
				Log.i(LOG, "Service is not running!");
				debug("Service is not running!");
			}
			
			// TEST if geofences are setup:
			if (geofencesAreSetup) debug("geofencesAreSetup TRUE");
	        else debug("NOT any geofences setup");
			//return true;
			
			// ADDED 20150113: Test audioClipPlayer task
			audioClipPlayerTask = 
    				new AudioClipPlayerTask(getApplicationContext()).execute(0);
		}
*/			
	// END DEBUG menu items 	
		
		return super.onOptionsItemSelected(item);
	}	
	
	/**
	 * Take care of two possible Save actions:
	 * CASE 1:
	 * In CREATE_MODE: Save the game map into internal storage.
	 * Upon successful processing, the app goes into PREPARED_MODE.
	 * CASE 2:
	 * In STOPPED_MODE: Save the game data into database.
	 * If user selects Save then app goes into INIT mode after saving to DB.
	 * Else app waits in STOPPED mode for a menu action. 
	 */
	private void handleSaveAction() {
		
		switch(mapMode) {
        case CREATE_MODE: //Save button/menu (only visible after 'Create new game' was chosen in menu)
        				  //Will also save coordinates in a file on internal storage
        	
        	boolean hasNotBeenDragged = false;
        	// Check that all control markers really have been moved - see onMarkerDragEnd (OK!)
        	for (Marker marker : controlPointList) { 
        		if (!marker.getSnippet().equalsIgnoreCase("dragged")) {
        			hasNotBeenDragged = true;
        		}
        	}
        	if (hasNotBeenDragged) { 
        		// Tell user that ALL control point markers must have been moved! (OK!)
        		Toast.makeText(getApplicationContext(), 
        				R.string.drag_all_markers_info_msg, 
        				Toast.LENGTH_LONG).show();
        		return; // disallow further processing
        	}		        		
        			      
        	// Create list for storing start and control point coordinates as strings
        	controlPointCoordsList = new ArrayList<String>();
        	
        	// Store start point first in list, at index 0
        	LatLng startpos = startAndEndPointMarker.getPosition();
        	controlPointCoordsList.add(String.valueOf(startpos.latitude) + "," + String.valueOf(startpos.longitude));
        	
        	startAndEndPointMarker.setDraggable(false);
        	
        	// Get coordinates for all controlPoint markers (OK!)		        	
			for (Marker marker : controlPointList) {
				LatLng pos = marker.getPosition();	
				
				marker.setDraggable(false); //disable drag
				
				String textCoords = String.valueOf(pos.latitude) + "," + String.valueOf(pos.longitude);				
				Log.d(LOG, textCoords);					
				
				// Store control point coordinates in list
				controlPointCoordsList.add(textCoords);
			}			 
			
			// Save the start and control points data to internal storage
			if (!FileUtils.writeListOfStringToFile(getApplicationContext(), controlPointCoordsList, MY_COURSE_FILE_NAME)) {
				Log.d(LOG, "FileUtils.writeListOfStringToFile failed!");						
			}
			else {
				hasGameBeenSaved = true; // This flag from now on will ALWAYS be true (saved in prefs)
			}
			
			// Create Geofences, and make an add request to GeofenceRequester 
			if (!createMyGeofences()) {
				Log.e(LOG, "Create geofences failed in OnClickListener");
				return; // disallow further processing
			}
        	
			// Now we are ready to start game when user selects 'Start game' in menu
			mapMode = MapMode.PREPARED_MODE;
			setupGameMap(mapMode);		        	
        	break;
        case STOPPED_MODE: //Save button/menu only visible after a finished game
        	// Save the new game data item to the database
        	// Open database           
            try {
            	dataSource.open();
            } catch (SQLException sqe) {
            	Log.e(LOG, "Could not open database");
            	Toast.makeText(getApplicationContext(), R.string.database_open_error, Toast.LENGTH_LONG).show();		            	
            	mapMode = MapMode.INIT_MODE;
            	return;
            } 
        	
            GameData newItem = null;
            try {
            	//TODO: db item name is not used in first version, empty string "" for now
            	newItem = dataSource.createItem("",numMinutes,numSeconds,numControlPointsFound,numberOfControlPoints,intDistanceForUser);
            }
            catch(SQLException sqe) {
            	sqe.printStackTrace();
            	Log.e(LOG, "dataSource.createItem FAILED");		            	
            }
            if (newItem != null) {
            	Toast.makeText(getApplicationContext(), R.string.database_save_success, Toast.LENGTH_SHORT).show();
            }
    		
            // Go to INIT mode
    		mapMode = MapMode.INIT_MODE;
    		setupGameMap(mapMode);
        	break;		        	
        default:
        	// Do nothing
        	break;
        }
	}
	
	/**
	 * Done/Save button, only used and available in CREATE mode.
	 * Upon successful processing, the app goes into PREPARED_MODE. 
	 * For Georienteering2:
	 * Also used as Save Game Data button, when in STOPPED mode.
	 * If user selects Save then app goes into INIT mode after saving to DB.
	 * Else app waits in STOPPED mode for a menu action.
	 */
	 private OnClickListener DoneSaveButtonClickListener = new OnClickListener() {
		 public void onClick(View v) {			 
			 handleSaveAction();
		 }		
	 };

	private void updatePositionDisplay() {	//TODO: Fix texts	
		
		boolean doShowTime = true;
		boolean doShowDistance = true;
		
		// ADDED 20150112 Validate that strings are not empty
		if (timeAsString == null || timeAsString.isEmpty()) {
			Log.w(LOG, "timeAsString is NULL or EMPTY!");			
			doShowTime = false;
		}
		if (distanceAsString == null || distanceAsString.isEmpty()) {
			Log.w(LOG, "distanceAsString is NULL or EMPTY!");			
			doShowDistance = false;
		}		
		
		if (isGhostAvatarEnabled && doMoveGhostAvatar) {
			
			// ADDED 20150112 Validate that strings are not empty
			if (diffDistToGhostAsString == null || diffDistToGhostAsString.isEmpty()) {
				Log.w(LOG, "diffDistToGhostAsString is NULL or EMPTY!");
				if (doShowTime && doShowDistance)				
					positionDisplay.setText(timeAsString + "   " + distanceAsString + " m");
				else if (doShowTime && !doShowDistance)
					positionDisplay.setText(timeAsString);
				else if (!doShowTime && doShowDistance)
					positionDisplay.setText(distanceAsString + " m");
				else
					positionDisplay.setText(getString(R.string.position_display_no_data_text)); 
			}	
			else {
				//TODO: Use a separate text view?
				positionDisplay.setText(timeAsString + "  " + distanceAsString + " m   Ghost is " +
					diffDistToGhostAsString + " m behind you");
				
				if (doShowTime && doShowDistance)				
					positionDisplay.setText(timeAsString + "  " + distanceAsString + " m   Ghost is " +
							diffDistToGhostAsString + " m behind you");
				else if (doShowTime && !doShowDistance)
					positionDisplay.setText(timeAsString + "  " + "     Ghost is " +
							diffDistToGhostAsString + " m behind you");
				else if (!doShowTime && doShowDistance)
					positionDisplay.setText(distanceAsString + " m   Ghost is " +
							diffDistToGhostAsString + " m behind you");
				else
					positionDisplay.setText(getString(R.string.position_display_no_data_text)); 				
			}			
		}
		else {
			if (doShowTime && doShowDistance)				
				positionDisplay.setText(timeAsString + "   " + distanceAsString + " m");
			else if (doShowTime && !doShowDistance)
				positionDisplay.setText(timeAsString);
			else if (!doShowTime && doShowDistance)
				positionDisplay.setText(distanceAsString + " m");
			else
				positionDisplay.setText(getString(R.string.position_display_no_data_text));
		}
	}
	
	private void clearPositionDisplay() {		
		positionDisplay.setText(" "); //UPDATED 20150112 to one space char		
	}
	
	/**
	 * Clears ONLY the data and counters that are ONLY used during an ongoing game.
	 */
	private void clearOngoingGameData() {
		// Clear data from possible previous game (that have been saved in database)
    	numControlPointsFound = 0;	
		numMinutes = 0;
		numSeconds = 0;
		intDistanceForUser = 0;
		intDistanceForGhost = 0;
		distanceAsString = "0";
		diffDistToGhostAsString = "0";
		timeAsString = " "; //UPDATED 20150113 to one space char
	}	
	
	void showGooglePlayServicesLicenseDialog() {		
		String LicenseInfo = GooglePlayServicesUtil.getOpenSourceSoftwareLicenseInfo(
		        getApplicationContext());
		      AlertDialog.Builder LicenseDialog = new AlertDialog.Builder(MainActivity.this);
		      LicenseDialog.setTitle("Legal Notices");
		      LicenseDialog.setMessage(LicenseInfo);
		      LicenseDialog.show();
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
        	
        	String errorString = GooglePlayServicesUtil.getErrorString(resultCode);
        	Log.e(LOG, "Google Play services is unavailable: " + errorString);
        	
        	if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {        		
        	
        		// Display an error dialog
        		Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, MY_GOOGLE_PLAY_SERVICES_INSTALL_REQUEST); 
        		if (dialog != null) {
        			ErrorDialogFragment errorFragment = new ErrorDialogFragment();
        			errorFragment.setDialog(dialog);

        			//tag The tag for this fragment, as per FragmentTransaction.add
        			errorFragment.show(getFragmentManager(), "MyErrorDialogFragment");
        		}
        		return false;
        	}
        	else {
        		
        		// Display a Toast with error message
        		Toast.makeText(getApplicationContext(), 
        				errorString, 
    					Toast.LENGTH_LONG).show();
        		return false;
        	}        	
        }
    }	
    
    /**      
     * Get geofence parameters for each geofence (control point) and add them to a geofences list.     
     * Create the PendingIntent containing an Intent that Location Services sends to this app's broadcast 
     * receiver when Location Services detects a geofence transition.
     * Send the list and the PendingIntent to Location Services using GeofenceRequester.addGeofences.
     * 
     * @return true if sending add request was ok (but we still do not know if addition will be successful!)
     */
	public boolean createMyGeofences() {	
			
		SimpleGeofence sg = null;
		
		// IMPORTANT: First set request type (needed for retrying the request in case of failure)
		myRequestType = GeofenceUtils.REQUEST_TYPE.ADD;
		
		// Check for Google Play services - 
		// NOTE: if connecting to Google Play services fails, onActivityResult is eventually called (and where a retry of request will be made).
		// Test for Google Play services after setting the request type.
		if (!isGooglePlayServicesAvailable()) {			
			Log.e(LOG, "Unable to add geofences - Google Play services unavailable.");			
			return false;
		}		
		
		// Create a geofence for start/end point
		LatLng startpos;
		if (startAndEndPointMarker != null)
			startpos = startAndEndPointMarker.getPosition();
		else
			startpos = myStartAndEndPoint;

		SimpleGeofence startgeo = new SimpleGeofence(
				"0", // geofenceId, use id "0" for start and end point
				startpos.latitude,
				startpos.longitude,
				MY_GEOFENCE_RADIUS,
				GeofenceUtils.GEOFENCE_EXPIRATION_IN_MILLISECONDS,
				Geofence.GEOFENCE_TRANSITION_ENTER  // Only detect entry transitions 
				);

		if (startgeo != null) {

			// Convert into a Location Services Geofence object and add it to list
			myCurrentGeofencesList.add(startgeo.toGeofence());			
		}			
		
		if (controlPointList == null) { // Should never happen -> What a terrible failure!
			Log.wtf(LOG, "controlPointList == null in createMyGeofences");
			return false;
		}
		
		for (Marker marker : controlPointList) {
			LatLng pos = marker.getPosition();	
			
			// Create a geofence object that is "flattened" into individual fields
			sg = new SimpleGeofence(
					marker.getTitle(), // geofenceId
					pos.latitude,
					pos.longitude,
					MY_GEOFENCE_RADIUS,
					GeofenceUtils.GEOFENCE_EXPIRATION_IN_MILLISECONDS,
					Geofence.GEOFENCE_TRANSITION_ENTER  // Only detect entry transitions 
					);
			
			if (sg != null) {
			
				// Convert into a Location Services Geofence object and add it to list
				myCurrentGeofencesList.add(sg.toGeofence());
			}
		}		
		
		// Start the request to add geofences. Fail if there's already a request in progress
        try {            
            myGeofenceRequester.addGeofences(myCurrentGeofencesList);
        } 
        catch (UnsupportedOperationException e) {        	
        	Log.e(LOG, "UnsupportedOperationException: " + e.getMessage());
        	e.printStackTrace();
            // Notify user that previous request hasn't finished.
            Toast.makeText(this, R.string.add_geofences_already_requested_error,
                        Toast.LENGTH_LONG).show();
            return false;
        }	
		return true;
	}
	
	/**
	 * Remove geofences (control points), using id saved as title for markers in controlPointList.
	 * NOTE: If using remove type intent, it seems that next time when adding new set of geofences, the geofences
	 * will not (always) get activated and thus does not trigger the broadcast receiver. Reason for this
	 * behavior is unknown, but it seems to be a bug with removeGeofencesByIntent and/or getRequestPendingIntent.
	 * 
	 * @return true if sending remove request was ok (but we still do not know if removal will be successful!)
	 */
	public boolean removeMyGeofences() {
		
		// IMPORTANT: First set remove type (needed for retrying the request in case of failure)
//		myRemoveType = GeofenceUtils.REMOVE_TYPE.INTENT;
		myRemoveType = GeofenceUtils.REMOVE_TYPE.LIST;
		
		// Check for Google Play services - 
		// NOTE: if connecting to Google Play services fails, onActivityResult is eventually called (and where a retry of request will be made).
		// Test for Google Play services after setting the remove type.
		if (!isGooglePlayServicesAvailable()) {
			Log.e(LOG, "Unable to remove geofences - Google Play services unavailable.");			
			return false;
		}
		
		if (controlPointList == null) { // Should never happen -> What a terrible failure!
			Log.wtf(LOG, "controlPointList == null in removeMyGeofences");
			return false;
		}
		
		// Put IDs of all geofences to remove in a list	
		myGeofenceIdsToRemove = new ArrayList<String>();
		myGeofenceIdsToRemove.add("0"); // start point with id "0"
		for (Marker marker : controlPointList) {
			
			myGeofenceIdsToRemove.add(marker.getTitle()); // geofenceId
		}
		
		// Start the request to remove geofences. Fail if there's already a request in progress
		try {
			/*
			 * Remove the geofences represented by the currently-active PendingIntent. If the
			 * PendingIntent was removed for some reason, re-create it; since it's always
			 * created with FLAG_UPDATE_CURRENT, an identical PendingIntent is always created.
			 */
// NOTE: There might be a bug with removeGeofencesByIntent and/or getRequestPendingIntent
//			myGeofenceRemover.removeGeofencesByIntent(myGeofenceRequester.getRequestPendingIntent());
	
			// Using removeGeofencesById instead seems to solve the problem!
			myGeofenceRemover.removeGeofencesById(myGeofenceIdsToRemove);		 
		} 
		catch (IllegalArgumentException e) {
			Log.e(LOG, "IllegalArgumentException in removeMyGeofences: " + e.getMessage());
			e.printStackTrace();
			return false;
		} 
		catch (UnsupportedOperationException e) {
			Log.e(LOG, "UnsupportedOperationException in removeMyGeofences: " + e.getMessage());
			e.printStackTrace();
			// Notify user that previous request hasn't finished.
			Toast.makeText(this, R.string.remove_geofences_already_requested_error,
					Toast.LENGTH_LONG).show();
			return false;
		}
        return true;
	}

	/*
     * Handle results returned to this Activity by other Activities started with
     * startActivityForResult(). In particular, the method onConnectionFailed() in
     * GeofenceRemover and GeofenceRequester may call startResolutionForResult() to
     * start an Activity that handles Google Play services problems. The result of this
     * call returns here, to onActivityResult.
     * 
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intentResult) {
        // Choose what to do based on the request code
        switch (requestCode) {
        
        	case MY_CREATE_NEW_GAME_REQUEST :
        		switch (resultCode) {
        			case Activity.RESULT_OK:
        				//NOTE: The main activity will now call onRestart -> onStart -> onResume 
        				Log.d(LOG, "MY_CREATE_NEW_GAME_REQUEST received in onActivityResult with RESULT_OK");
        				
        				// Get received results
            			double dLat = intentResult.getDoubleExtra("result_latitude", 0.0d);
            			double dLong = intentResult.getDoubleExtra("result_longitude", 0.0d);            			        			
            			
            			myStartAndEndPoint = new LatLng(dLat, dLong);            			
            			numberOfControlPoints = intentResult.getIntExtra("result_num_controls", 4);
            			intSelectedStartFlagPlaceMethod = intentResult.getIntExtra("result_selected_radio_button_start_point", 2); //default 2 := use edit fields
            			
            			// Convert location coordinates to string
            			String sLatitude  = Double.valueOf(myStartAndEndPoint.latitude).toString();
            			String sLongitude  = Double.valueOf(myStartAndEndPoint.longitude).toString();
            			
            			Log.i(LOG, "Saving startPoint coordinates to prefs:" + sLatitude + "," + sLongitude);
            			
            			// Save the updated values in preferences
            			Editor editor = prefs.edit();	
            			editor.putString("latitude", sLatitude);
            			editor.putString("longitude", sLongitude);    
            			editor.putInt("num_control_points", numberOfControlPoints);
            			editor.putInt("selected_radio_button_start_point", intSelectedStartFlagPlaceMethod); 
            			
            			editor.apply();
        				
            			mapMode = MapMode.CREATE_MODE;
            			//setupGameMap(mapMode); //Unnecessary since main app now restarts and it will then be called anyway from onStart
        				break;
        				
        			default:
        				// Cancel was clicked -> Go to INIT mode?
        				mapMode = MapMode.INIT_MODE; 
        				break;
        		}//end resultCode        		
        		break;

            // If the request code matches the code sent in onConnectionFailed
            case GeofenceUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST :            	
            	Log.w(LOG, "CONNECTION_FAILURE_RESOLUTION_REQUEST received in onActivityResult");
                switch (resultCode) {
                    // If Google Play services resolved the problem
                    case Activity.RESULT_OK:
                        // If the request was to add geofences
                        if (GeofenceUtils.REQUEST_TYPE.ADD == myRequestType) {

                            // Toggle the request flag and try to send a new request
                            myGeofenceRequester.setInProgressFlag(false);
                            
                            // Restart the process of adding the current geofences
                            myGeofenceRequester.addGeofences(myCurrentGeofencesList);                        
                        } 
                        // If the request was to remove geofences
                        else if (GeofenceUtils.REQUEST_TYPE.REMOVE == myRequestType ) {
                            // Toggle the removal flag and try to send a new removal request
                            myGeofenceRemover.setInProgressFlag(false);

                            // If the removal was by Intent
                            if (GeofenceUtils.REMOVE_TYPE.INTENT == myRemoveType) {
                                // Restart the removal of all geofences for the PendingIntent
                                myGeofenceRemover.removeGeofencesByIntent(
                                    myGeofenceRequester.getRequestPendingIntent());                            
                            } 
                            // If the removal was by a list of geofence IDs
                            else {
                                // Restart the removal of the geofence removal list
                            	if ((myGeofenceIdsToRemove != null) && (!myGeofenceIdsToRemove.isEmpty()))
                            		myGeofenceRemover.removeGeofencesById(myGeofenceIdsToRemove);
                            	else
                            		Log.e(LOG, "myGeofenceIdsToRemove was empty in onActivityResult, case GeofenceUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST");
                            }
                        }
                        break;

                    // If any other result code was returned by Google Play services
                    default:
                        // Report that Google Play services was unable to resolve the problem
                        Log.e(LOG, getString(R.string.no_resolution));
                        Toast.makeText(this, R.string.no_resolution, Toast.LENGTH_LONG).show();
                        break;    
                }//end resultCode
                
            case MY_GOOGLE_PLAY_SERVICES_INSTALL_REQUEST:
            	// Report that a Google Play services install request was received
            	Log.d(LOG, "MY_GOOGLE_PLAY_SERVICES_INSTALL_REQUEST was received in onActivityResult");            	
            	break;

            // If any other request code was received
            default:
               // Report that this Activity received an unknown requestCode
               Log.w(LOG, getString(R.string.unknown_activity_request_code, requestCode));
               break;
        }//end requestCode
    }  
    
    /**
     * UI specific functions, toasts, dialogs etc...
     */
    
    /**
	 * Debug Toast, particularly useful if and when LogCat stops working.
	 * Shows the Toast only when DEBUG_MODE flag is set, but always logs a Warning in LogCat.
	 * @param msg
	 */
	public void debug(String msg) {		
		Log.w(LOG, "DEBUG:" + msg);
		if (DEBUG_MODE) {
			Toast.makeText(getApplicationContext(), 
					msg, 
					Toast.LENGTH_LONG).show();
		}
	}
    
    /**
     * Show my toast
     * @param msg
     */    
    private void showMyToast(String msg) {
		LayoutInflater inflater = getLayoutInflater();
        // Inflate the Layout
        View layout = inflater.inflate(R.layout.my_toast,
                                       (ViewGroup) findViewById(R.id.my_toast_layout));

        TextView text = (TextView) layout.findViewById(R.id.textToShow);
        // Set the Text to show in TextView
        text.setText(msg);

        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
	}
    
    /**
     * Show my save dialog (between mapMode transition STOPPED -> INIT)
     * NOTE: Activity need to implement SaveDialogFragment.SaveDialogListener interface
     */
    public void showMySaveDialog() {
        // Create an instance of the dialog fragment and show it
        DialogFragment dialog = new SaveDialogFragment();
        dialog.show(getFragmentManager(), "SaveDialogFragment");
    }

    // The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the NoticeDialogFragment.NoticeDialogListener interface
    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        // User touched the dialog's positive button, 'Save'
    	
    	// Save the new game data item to the database
    	// Open database           
        try {
        	dataSource.open();
        } catch (SQLException sqe) {
        	Log.e(LOG, "Could not open database");
        	Toast.makeText(getApplicationContext(), R.string.database_open_error, Toast.LENGTH_LONG).show();		            	
        	mapMode = MapMode.INIT_MODE;
        	return;
        } 
    	
        GameData newItem = null;
        try {
        	//TODO: db item name is not used in first version, empty string "" for now
        	newItem = dataSource.createItem("",numMinutes,numSeconds,numControlPointsFound,numberOfControlPoints,intDistanceForUser);
        }
        catch(SQLException sqe) {
        	sqe.printStackTrace();
        	Log.e(LOG, "dataSource.createItem FAILED");		            	
        }
        if (newItem != null) {
        	Toast.makeText(getApplicationContext(), R.string.database_save_success, Toast.LENGTH_SHORT).show();
        }
		
        // Go to INIT mode
		mapMode = MapMode.INIT_MODE; 
		setupGameMap(mapMode);
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {
        // User touched the dialog's negative button, 'Discard'
    	
    	// Go to INIT mode
		mapMode = MapMode.INIT_MODE;   
		setupGameMap(mapMode);
    } 

    /**
     * Unlock screen orientation for main activity (back to user defined)
     */
    private void unlockScreenOrientation() {
    	// Unlock screen orientation back to user defined
    	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
    }
    
    /**
     * Lock screen orientation for an activity
     * From http://stackoverflow.com/questions/3611457/android-temporarily-disable-orientation-changes-in-an-activity
     * @param activity
     */
    @SuppressWarnings("deprecation")
    private void lockScreenOrientation(Activity activity) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        int rotation = display.getRotation();        
        
        int height;
        int width;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR2) {
            height = display.getHeight();
            width = display.getWidth();
        } else {
            Point size = new Point();
            display.getSize(size);
            height = size.y;
            width = size.x;
        }
        switch (rotation) {
        case Surface.ROTATION_90:
            if (width > height)
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            else
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
            break;
        case Surface.ROTATION_180:
            if (height > width)
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
            else
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            break;          
        case Surface.ROTATION_270:
            if (width > height)
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            else
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            break;
        default :
            if (height > width)
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            else
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }
    
    /**
     * Wrapper class for sending several progress variables to onProgressUpdate in GhostMover AsyncTask
     *
     */
    public class ProgressWrapper{
        public final Location mLocation;
        public final Integer mInteger;

        public ProgressWrapper(Location myLocation, Integer myInteger){
            this.mLocation = myLocation;
            this.mInteger = myInteger;
        }
    }
    
    /**
     * Background task
     *  To move ghost avatar in background
     *  and calculate the distance that the ghost has moved.
     */
    private class GhostMover extends AsyncTask<Integer, ProgressWrapper, Boolean> {    	
    	
    	private int ghostLocationIndex = 0;
    	
    	/**
    	 * doInBackground
    	 * NOTE: Do not attempt to access UI objects from here!
    	 * @param value[0] := tick_interval (ghostMoveIntervalInSeconds) 	
    	 * @param value[1] := user_location_at_ghost_move_start (userLocationIndexWhenGhostStartedToMove)
    	 */    	
    	protected Boolean doInBackground(Integer... value) {
    		System.out.println("DEBUG: GhostMover doInBackground started");
    		
    		int tick_interval = value[0]; //(ghostMoveIntervalInSeconds)
    		int user_location_at_ghost_move_start = value[1]; //(userLocationIndexWhenGhostStartedToMove)
    		
        	float distance  = 0.0f;
        	float distanceMoved = 0.0f;
        	
        	// TODO: Use this?
        	/*
        	switch (tick_interval) {
        	case LocationTrackerService.UPDATE_INTERVAL_IN_SECONDS_EASY_LEVEL:
        	default:
        		
        		break;
        	case LocationTrackerService.UPDATE_INTERVAL_IN_SECONDS_MEDIUM_LEVEL:

        		break;
        	case LocationTrackerService.UPDATE_INTERVAL_IN_SECONDS_HARD_LEVEL:

        		break;
        	}
        	*/
    		try {    			
    			
    			ArrayList<Location> locList;
    			
    			while (doMoveGhostAvatar && !isCancelled()) {
    				
    				//Get a point that user has visited previously, start with first point at index 0
    				if (myService == null) {
    					Log.e(LOG, "myService == null in GhostMoverTask");
    					doMoveGhostAvatar = false;
						return false; //End task
    				}
    				
    				locList = myService.getLocations();
    				Location loc = null;    				
    				
//    				System.out.println("DEBUG: locList.size(): " + locList.size());
    				
    				if (locList != null) {
    					if (!locList.isEmpty()) {
    						// Check if ghost location has catched up the user's location
    						if (ghostLocationIndex >= locList.size()-1 ) { 
    							// You have lost the game!	
    							doMoveGhostAvatar = false;
    							return false; //End task   							
    						}
    						else {									
    							// First move the ghost by just one "step"
    							try {
    								loc = locList.get(ghostLocationIndex);    								
    							}
    							catch(IndexOutOfBoundsException e) {
    								e.printStackTrace();
    							}    
    							
    							Integer intDistance = 0;
    							// Calculate distance moved, but only when ghost has moved at least one "step"
    							if (ghostLocationIndex > 0) {
    								try {
    									distance = loc.distanceTo(locList.get(ghostLocationIndex-1)); //-1 for previous location
    								}
    								catch (NullPointerException npe) {
    									npe.printStackTrace();
    									distance = 0.0f;
    								}    	
    								
    								distanceMoved += distance;    				            

    								Float fDistance = Float.valueOf(distanceMoved);			
    								intDistance = fDistance.intValue(); 
    							}
    				            // Increment next "step"
    				            ghostLocationIndex++;
    				            publishProgress(new ProgressWrapper(loc, intDistance));
    						}
    					}
    				}
    				
    				//TODO: Tweaking depending on users speed and location update interval... Need to test "irl"!
    				// Sleep for tick_interval seconds...
           	 		if ((user_location_at_ghost_move_start*256) < ghostLocationIndex) {
           	 			SystemClock.sleep(tick_interval*(1000-200)); //Move really fast (20%)            	 			
           	 		}
           	 		else if ((user_location_at_ghost_move_start*128) < ghostLocationIndex) {
           	 			SystemClock.sleep(tick_interval*(1000-150)); //Move much more faster          	 			
           	 		}
           	 		else if ((user_location_at_ghost_move_start*64) < ghostLocationIndex) {
           	 			SystemClock.sleep(tick_interval*(1000-100)); //Move much faster          	 			
           	 		}
           	 		else if ((user_location_at_ghost_move_start*32) < ghostLocationIndex) {
           	 			SystemClock.sleep(tick_interval*(1000-75)); //Move even quite much faster (7.5%)         	 			
           	 		}
           	 		else if ((user_location_at_ghost_move_start*16) < ghostLocationIndex) {
           	 			SystemClock.sleep(tick_interval*(1000-50)); //Move quite much faster          	 			
           	 		}
           	 		else if ((user_location_at_ghost_move_start*8) < ghostLocationIndex) {
           	 			SystemClock.sleep(tick_interval*(1000-25)); //Move even more slightly faster (2.5%)         	 			
           	 		}
           	 		else if ((user_location_at_ghost_move_start*4) < ghostLocationIndex) {
           	 			SystemClock.sleep(tick_interval*(1000-10)); //Move slightly faster             	 			
           	 		}
           	 		else {
           	 			SystemClock.sleep(tick_interval*1000); //Move at approx. same speed as user has
           	 		}
    			}//end while    			
    			return true;
    			
    		} catch (RuntimeException rte) {
    			System.out.println("EXCEPTION: RuntimeException");
    			throw new RuntimeException(rte);
    		} 
    	}

    	/**
    	 * Handler synchronized with GUI thread (ok to modify UI objects)
    	 * @param progress
    	 */
    	protected void onProgressUpdate(ProgressWrapper... progress) {
    		
    		ProgressWrapper wrapper = progress[0];
    		
    		Location location = wrapper.mLocation;    		
    		moveGhostAvatarMarker(new LatLng(location.getLatitude(), location.getLongitude()));
    		
    		intDistanceForGhost = wrapper.mInteger;
//    		System.out.println("DEBUG: intDistanceForGhost: " + intDistanceForGhost.toString());
    	}

    	/**
    	 * Handler synchronized with GUI thread (ok to modify UI objects)
    	 */    	
    	protected void onPostExecute(Boolean result) {
    		System.out.println("DEBUG: GhostMover onPostExecute.");    
    		
    		if (result) {
    			System.out.println("DEBUG: result was TRUE");
    		}
    		else {
    			System.out.println("DEBUG: result was FALSE");    			
    			stopOngoingGame(false);    			
    		}
    	}
    }
    
    
    private class AudioClipPlayerTask extends AsyncTask<Integer, Void, Boolean> {
    	
    	private MediaPlayer mediaPlayer = null;
    	private boolean hasPlayCompleted = false;
    	private Context context;
    	
    	private AudioClipPlayerTask(Context _context) {
    		context = _context;
    	}
    	
    	/**
    	 * doInBackground
    	 * NOTE: Do not attempt to access UI objects from here!
    	 * @param value[0] := audio clip number    	
    	 */    	
    	protected Boolean doInBackground(Integer... value) {    		

    		// Play an audio
    		int clipNumber = value[0];
    		switch (clipNumber) {
    		case 0:
    			//mediaPlayer = MediaPlayer.create(context, R.raw.ghosts_laughing);
    			mediaPlayer = MediaPlayer.create(context, R.raw.ghost_moaning);
    			break;
    		case 1:
    			mediaPlayer = MediaPlayer.create(context, R.raw.fanfare3);
    			break;
    		}
    		
    		if (mediaPlayer != null) {
    			
    			try {
    				mediaPlayer.start(); // no need to call prepare(); create() does that for you
    			}
    			catch (IllegalStateException e) {
    				hasPlayCompleted = false;
    				return false; //End task
    			}    		

    			mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
    				public void onCompletion(MediaPlayer mp) {
    					// Once audio has completed playing
    					hasPlayCompleted = true;
    				}    			
    			});    		
    		}
    		
    		while (!isCancelled() && !hasPlayCompleted) {
				;
			}
			return hasPlayCompleted; //End task
    	}
    	
    	/**
    	 * Handler synchronized with GUI thread (ok to modify UI objects)
    	 */    	
    	protected void onPostExecute(Boolean result) {
    		System.out.println("DEBUG: AudioClipPlayerTask onPostExecute.");    
    		
    		// Stop playing sound
			if (mediaPlayer != null) {
				mediaPlayer.stop();
				// Release mediaPlayer (will be created again next time we need it)
				mediaPlayer.release();
			}	  
			
			mediaPlayer = null;
    	}
    	
    }
    
    
    /**
     * CheckInternetTask
     * Background task
     *  To check Internet connection
     *  NOTE: Do not attempt to access UI objects from here!
     *  ADDED 20150117
     */    
    private class CheckInternetTask extends AsyncTask<Void, Integer, Integer> {

    	/**
    	 * Checking if a Network is available
    	 * Requires permission.ACCESS_NETWORK_STATE
    	 * NOTE: This does not guarantee that the network found actually is connected to Internet!
    	 * @param context
    	 * @return true if a Network found
    	 */
    	private boolean isNetworkAvailable(Context context) {
    		ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);		
    		NetworkInfo info = cm.getActiveNetworkInfo();	    
    		return (info != null);
    	}

    	/**
    	 * Checking if a network has an active Internet connection, by trying to open 
    	 * a connection to Google web page at http://www.google.com
    	 * Requires permission.ACCESS_NETWORK_STATE
    	 * REF: From http://stackoverflow.com/questions/6493517/detect-if-android-device-has-internet-connection
    	 * NOTE: Make sure you don't run this code on the main thread, otherwise you'll get a NetworkOnMainThread exception (in Android 3.0 or later). 
    	 * Use an AsyncTask or Runnable instead.
    	 * @param context
    	 * @return true if active Internet connection exists
    	 */
    	private boolean hasActiveInternetConnection(Context context) {
    		if (isNetworkAvailable(context)) {
    			HttpURLConnection urlc = null;
    			try {
    				urlc = (HttpURLConnection) (new URL("http://www.google.com").openConnection());
    				urlc.setRequestProperty("User-Agent", "Test");
    				urlc.setRequestProperty("Connection", "close");
    				urlc.setConnectTimeout(1500); 
    				urlc.connect();
    				return (urlc.getResponseCode() == 200);
    			} catch (IOException e) { //| java.net.SocketTimeoutException already handled by IOException
    				Log.e(LOG, "Error checking internet connection", e);
    				return false;
    			} finally {
    				if (urlc != null) urlc.disconnect();
    			}
    		} else {
    			Log.d(LOG, "No network available!");
    		}
    		return false;
    	}

    	static final String LOG = "CheckInternetTask";

    	private Context context;

    	public CheckInternetTask(Context _context) {  		
    		context = _context;
    	}

    	/**
    	 * NOTE: Do not attempt to access UI objects from here!
    	 */	
    	protected Integer doInBackground(Void... none) {

    		try {			
    			if (!hasActiveInternetConnection(context)) {
    				return -1; //NO INTERNET
    			}			
    			return 1; //OK
    		} catch (RuntimeException rte) {
    			Log.e(LOG, "EXCEPTION: RuntimeException");
    			rte.printStackTrace();
    			//throw new RuntimeException(rte);    
    			return 0; //FAIL
    		}    		
    	}

    	/**
    	 * Handler synchronized with GUI thread (ok to modify UI objects)
    	 */    		
    	protected void onPostExecute(Integer result) {
    		if (result > 0) {
    			Log.i(LOG, "onPostExecute. Internet connection is ok.");			
    		}
    		else if (result == 0){
    			Log.e(LOG, "onPostExecute. Task failed.");			
    		}    		
    		else {
    			Log.w(LOG, "onPostExecute. Internet not available.");
    			Toast.makeText(context, R.string.network_error_msg, Toast.LENGTH_LONG).show();
    		}		       
    	}

    }
}
