package dv106.pp222es.georienteering2;

/**
 * LocationTrackerService
 * 
 * Using Googles FusedLocationProviderApi
 * @link http://developer.android.com/reference/com/google/android/gms/location/FusedLocationProviderApi.html
 * 
 * The Google Location Services API, part of Google Play Services, provides a more powerful, high-level framework that 
 * automatically handles location providers, user movement, and location accuracy. 
 * It also handles location update scheduling based on power consumption parameters you provide. 
 * In most cases, you'll get better battery performance, as well as more appropriate accuracy, by using the Location Services API.
 * To learn more about the Location Services API, see Google Location Services for Android. 
 * 
 * @author Pertti Palokangas
 * 
 * NOTES:
 * If you put android:process=:myservicename attribute to the service tag of your service in your manifest.xml, like: <service android:name="sname" android:process=":myservicename" />, 
 * then it will run your service as a different process, thus in a different thread. This means, that any heavy calculation done/long request by the service wont hang your UI thread.
 * @link http://developer.android.com/guide/topics/manifest/service-element.html 
 * BUT THEN NOTICE THAT:
 *  LocalBinder can only be used whenever the service runs in the same (local) process as the application. 
 * 
 * TODOs:
 * 1. For forward-compatibility, use Googles new API Client Model
 	Example: http://www.boards.ie/vbulletin/showthread.php?p=91990419
 		mGoogleApiClient = new GoogleApiClient.Builder(this)		
		.addApi(LocationServices.API)
		.addConnectionCallbacks((GoogleApiClient.ConnectionCallbacks) this)
		.addOnConnectionFailedListener(this)
		.build();

* PROBLEMS:
* 1. When using mock test app 'LocationProvider' with continuos location updates
* 11-08 06:54:59.331: E/LocationClientHelper(4353): Received a location in client after calling removeLocationUpdates.
*
*/

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.model.LatLng;

import dv106.pp222es.georienteering2.MainActivity.GameLevel;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class LocationTrackerService extends Service 
	implements
		GooglePlayServicesClient.ConnectionCallbacks,
		GooglePlayServicesClient.OnConnectionFailedListener,
		LocationListener {
	
	private static final boolean DEBUG_MODE = false; // NOTE: Disabled for RELEASE_MODE !!!
	
	private static final String LOG = "LocationTrackerService";		
	
	// Location update frequencies in seconds, depending on difficulty/movement speed level, //TODO: Tweaking
    public static final int UPDATE_INTERVAL_IN_SECONDS_HARD_LEVEL = 4; //2; 

    public static final int UPDATE_INTERVAL_IN_SECONDS_MEDIUM_LEVEL = 10; //4; //6; 
	
    public static final int UPDATE_INTERVAL_IN_SECONDS_EASY_LEVEL = 20; //10;
	
	// Location update frequency in seconds
    public static int UPDATE_INTERVAL_IN_SECONDS = UPDATE_INTERVAL_IN_SECONDS_EASY_LEVEL; //default EASY

    // The fastest location update frequency, in seconds
    private static int FASTEST_INTERVAL_IN_SECONDS = UPDATE_INTERVAL_IN_SECONDS - 1; //TODO: Tweaking

    // An object that holds accuracy and frequency parameters	
	private LocationRequest locationRequest;
	
	// An object that holds the current instantiation of the location client
	private LocationClient locationClient;
    
//	private Location currentLocation;
	// NOTE: These lists are NOT persistent if the service would be destroyed!
    private ArrayList<Location> savedLocations; // All points that has been detected for user while tracking location changes, EXCLUDING startPoint!
    private static ArrayList<LatLng> route_lat_lng_points; // All route points, INCLUDING startPoint! (for show route)
    
    private float distanceMoved = 0.0f; // Distance in meters that user has moved from start point location
    private Location startPointLocation; // Start and finish point location (for distance calculation)
    
    private static boolean isTracking = false;
    private static boolean isRunning = false;
    private static boolean isClientAlive = false; // flag for if client (main activity) is alive, i.e. it has not been destroyed!
    private static boolean isClientVisible = false; // flag for if client (main activity) is visible, i.e. it has not been stopped!
    
    // A simple Timer
    private Timer timer = new Timer();
    private int timerCounter = 0, incrementTimerStep = 1;
    
    
    @Override
    public void onCreate() {    
    	super.onCreate();
        savedLocations = new ArrayList<Location>();
        route_lat_lng_points = new ArrayList<LatLng>();
        isRunning = true;
        Log.i(LOG, "Service Running... (onCreate)");
    }    
    
    private void onTimerTick() {        
    	if (timerCounter % 10 == 0) {
    		//Log.v("TimerTick", "Timer doing work." + timerCounter);
    	}
        try {
        	timerCounter += incrementTimerStep;  
            // Broadcast Timer update to UI
            Intent intent = new Intent("dv106.pp222es.georienteering.action.TIMER_DATA_BROADCAST");
            intent.putExtra("EXTRA_TIMER_DATA", timerCounter);
            sendBroadcast(intent);
        } catch (Throwable t) { // Always ultimately catch all exceptions in timer tasks.
            Log.e("TimerTick", "Timer Tick Failed.", t);            
        }
    }
    
    /**
	 * Called when we receive an Intent from client sent to us via startService().
	 * Any processing done here will happen on the main GUI thread.
	 * NOTE: Will not be called if using bindService to start a bound service.
	 */    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(LOG, "Service received start id " + startId + ": " + intent);
        isClientAlive = true;
        isClientVisible = true;        
        return START_STICKY; // run until explicitly stopped.        
    }
    
    /**
     * NOTE: onDestroy will be called if service was started via binding only when app is destroyed/killed!
     * If service was started explicitly using an intent, then service will continue to run if app only unbinds from it, 
     * i.e. not explicitly calls stopService!
     */
    @Override
    public void onDestroy() {   
    	super.onDestroy();
    	isRunning = false;
        Log.i(LOG, "Service Destroyed... (onDestroy)");
        if (DEBUG_MODE) Toast.makeText(this, "Service Destroyed...", Toast.LENGTH_LONG).show(); 
        if (isTracking) Log.w(LOG, "Service Destroyed when isTracking!");
    }
    
    /**
     * Start Location Tracking
     * @param level
     * @return
     */
    public boolean startTracking(MainActivity.GameLevel level) {    
    	
    	if (level == GameLevel.HARD) {
    		UPDATE_INTERVAL_IN_SECONDS = UPDATE_INTERVAL_IN_SECONDS_HARD_LEVEL;
    	}
    	else if (level == GameLevel.MEDIUM) {
    		UPDATE_INTERVAL_IN_SECONDS = UPDATE_INTERVAL_IN_SECONDS_MEDIUM_LEVEL;
    	}    	
    	else { //default EASY
    		UPDATE_INTERVAL_IN_SECONDS = UPDATE_INTERVAL_IN_SECONDS_EASY_LEVEL;
    	}
    	
    	FASTEST_INTERVAL_IN_SECONDS = UPDATE_INTERVAL_IN_SECONDS - 1;
    	
    	if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
    		
    		// Create new location client, using this class to handle callbacks
    		locationClient = new LocationClient(this, this, this);    		
    		
    		if (!locationClient.isConnected() || !locationClient.isConnecting()) {
    			// Connect the client
    			locationClient.connect();    			
    			
    			// Schedule a new timer using TimerTask that implements Runnable
    			timer = new Timer();
    			try {
    				timer.scheduleAtFixedRate(new TimerTask(){ public void run() {onTimerTick();}}, 0, 1000L); //1000 msec
    			}
    			catch(IllegalStateException e) {
    				e.printStackTrace();
    				// WORKAROUND: Because timer was canceled in stopTracking
    				Log.e(LOG, "timer.scheduleAtFixedRate failed. Will try to create new Timer instance...");
    				timer = new Timer();
    				try {
        				timer.scheduleAtFixedRate(new TimerTask(){ public void run() {onTimerTick();}}, 0, 1000L); //1000 msec
        			}
        			catch(IllegalStateException e2) {
        				e2.printStackTrace();
        				Log.wtf(LOG, "timer.scheduleAtFixedRate failed again.");
        				return false;
        			}    				
    			}
    			
    			Toast.makeText(this, R.string.location_tracking_start, Toast.LENGTH_SHORT).show(); 
    			Log.i(LOG, getString(R.string.location_tracking_start));
    	        isTracking = true;
    		} 
    		else {
    			Log.w(LOG, "LocationClient was already connected!");
    			if (DEBUG_MODE) Toast.makeText(this, "LocationClient was already connected!", Toast.LENGTH_LONG).show(); 
    		}
    	}	
    	else {
    		Log.e(LOG, "Unable to connect to Google Play Services.");
    		// TODO: Display error dialog, and take actions if possible
    		
    		Toast.makeText(this, R.string.location_tracking_failed_to_start, Toast.LENGTH_LONG).show();   
    	}
    	
    	return (isTracking);
    }
    
    /**
     * Stop Location tracking
     */
    public void stopTracking() {    	

    	if (timer != null) {
    		Log.i(LOG, "Attempting to cancel timer...");
    		timer.cancel();
    		timer.purge(); // Removes all cancelled tasks from this timer's task queue
    	}

    	if (locationClient != null && locationClient.isConnected()) {
    		// Remove location updates for a listener
    		locationClient.removeLocationUpdates(this);
    		// Disconnecting the client invalidates it
    		locationClient.disconnect();
    	}     

    	Toast.makeText(this, R.string.location_tracking_stop, Toast.LENGTH_SHORT).show(); 
    	Log.i(LOG, getString(R.string.location_tracking_stop));
    	isTracking = false;

    	// Reset counters 
    	distanceMoved = 0.0f;
    	timerCounter = 0;
    	
    	// Clear savedLocations and route_lat_lng_points lists. ADDED 20150110
    	if (savedLocations != null) {
    		savedLocations.clear();
    	}
    	if (route_lat_lng_points != null) {
    		route_lat_lng_points.clear();
    	}
    }
    
    public boolean isTracking() {
        return isTracking;
    }   
    
    //NOTE This is a static method to be able to call from GeofenceDetectorReceiver without changing a lot of calls in main activity
    public static boolean isStaticTracking() {
        return isTracking;
    }   
    
    public static boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Set Start point location
     * @param latLng
     */
    public void setStartPoint(LatLng latLng) {
    	// A location can consist of a latitude, longitude, timestamp, and other information such as bearing, altitude and velocity
    	startPointLocation = new Location("No provider"); //TODO: Is this ok?
    	startPointLocation.setLatitude(latLng.latitude);
    	startPointLocation.setLongitude(latLng.longitude);
    	
    	// 20150109: Always save start point first in route points list (for show route)
    	if (route_lat_lng_points.isEmpty()) {
    		route_lat_lng_points.add(latLng);
    	}
    }    
    
    public int getNumLocations() {
        return savedLocations.size();
    }
    
    public ArrayList<Location> getLocations() {
        return savedLocations;
    }
    
    public float getDistanceMoved() {
    	return distanceMoved;
    }
    
    public static ArrayList<LatLng> getRouteLatLngPoints() {
    	return route_lat_lng_points;
    }
    
    /**
	 * LocationTrackerBinder	  
	 * Binder interface given to local client
	 * NOTE: LocalBinder can only be used whenever the service runs in the same (local) process as the application. 
	 */    
    public class LocationTrackerBinder extends Binder {
        public LocationTrackerService getService() { //20141114 added public
            return LocationTrackerService.this;
        }
    }    

    // LocalBinder interface given to local client  
    private final IBinder binder = new LocationTrackerBinder();
    
    /**
     * Called when service is started from client via bindService()
     */    
    @Override
    public IBinder onBind(Intent intent) {
    	Log.i(LOG, "Service starting via binding... (onBind)");
    	isClientAlive = true;
    	isClientVisible = true;
    	return binder;
    }
    
    public void setClientAlive(boolean _alive) {
    	isClientAlive = _alive;
    }
    
    public static boolean isClientAlive() {
    	return isClientAlive;
    }
    
    public static void setClientVisible(boolean _visible) {
    	isClientVisible = _visible;
    }
    
    public static boolean isClientVisible() {
    	return isClientVisible;
    }

    /**
     * Callbacks from Location Services
     */
    @Override
    public void onLocationChanged(Location location) {
    	if (location != null) {
    		Log.v(LOG, "position: " + location.getLatitude() + ", " + location.getLongitude() + " accuracy: " + location.getAccuracy());
    		
//    		currentLocation = location;    		
    		
    		// Report to the UI that the location was updated
    	/*	
            String msg = "Updated Location: " +
                    Double.toString(location.getLatitude()) + "," +
                    Double.toString(location.getLongitude());
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        */    
            // Calculate distance moved (so far)
            //LocationUtils.getDistanceBetweenPoints();
            
            Location previousLocation;            
            if (savedLocations.isEmpty()) {
            	previousLocation = startPointLocation;
            }
            else {
            	previousLocation = savedLocations.get(savedLocations.size()-1);            	
            }
            
            float distance;
            try {
            	distance = location.distanceTo(previousLocation);
            }
            catch (NullPointerException npe) {
            	npe.printStackTrace();
            	distance = 0.0f;
            }
            
            distanceMoved += distance;
            
            // Save location in array list 
    		savedLocations.add(location);
    		
    		// 20150109: Also save route points (for show route)
    		route_lat_lng_points.add(new LatLng(location.getLatitude(), location.getLongitude()));
                        
            // Forward location update to main UI (map), but only if client is alive!
            //-> Using Broadcast receiver and an action intent   
    		
    		// TODO UPDATE: Always send this broadcast?
            if (isClientAlive) {
            	Log.v(LOG, "Sending LOCATION_DATA_BROADCAST from onLocationChanged since client is alive");
            	
//            	Intent intent = new Intent("dv106.pp222es.georienteering.action.LOCATION_DATA_BROADCAST"); //OK!
            	Intent intent = new Intent(LocationUtils.LOCATION_DATA_BROADCAST); 
            	intent.putExtra("EXTRA_LATITUDE", location.getLatitude());
            	intent.putExtra("EXTRA_LONGITUDE", location.getLongitude());
            	intent.putExtra("EXTRA_DISTANCE", distanceMoved);
            	sendBroadcast(intent);
            }
            else {
            	if (DEBUG_MODE) Toast.makeText(this, "isClientAlive is FALSE in onLocationChanged! Not sending any LOCATION_DATA_BROADCAST!", Toast.LENGTH_SHORT).show();
            }
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
    	
    	// Create the LocationRequest object and set parameters
    	locationRequest = LocationRequest.create();   	
    	
    	locationRequest.setInterval(UPDATE_INTERVAL_IN_SECONDS*1000); // milliseconds
    	locationRequest.setFastestInterval(FASTEST_INTERVAL_IN_SECONDS*1000); // the fastest rate in milliseconds at which your app can handle location updates
    	locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); // Use high accuracy
    	
    	locationClient.requestLocationUpdates(locationRequest, this);
    }

    /**
     * Called by Location Services if the connection to the
     * location client drops because of an error.
     */
    @Override
    public void onDisconnected() {
    	Log.d(LOG, "onDisconnected. Please try to reconnect.");
    	if (DEBUG_MODE) Toast.makeText(this, "onDisconnected. Please try to reconnect.", Toast.LENGTH_LONG).show(); 
    	
    	stopTracking();
    	stopSelf();
    }

    /**
     * Called by Location Services if the attempt to connect
     * to the Location Services fails.
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    	Log.e(LOG, "onConnectionFailed");
    	if (DEBUG_MODE) Toast.makeText(this, "onConnectionFailed. Please try to reconnect.", Toast.LENGTH_LONG).show(); 
    	
    	stopTracking();     	
    	stopSelf();
    }    
   
}
