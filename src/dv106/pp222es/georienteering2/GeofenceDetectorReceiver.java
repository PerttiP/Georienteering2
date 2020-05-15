package dv106.pp222es.georienteering2;

/**
 * GeofenceDetectorReceiver
 * 
 * A broadcast receiver to receive and handle an intent sent from Googles Location Services
 * when a geofence transition (enter) has been detected.
 * If client (main activity) is not alive or not visible then a notification is displayed.
 * 
 * @author Pertti Palokangas
 * 
 * TODOs:
 * 1. Need to refactor to use the new API Client Model (if required?)
 *    https://developer.android.com/reference/com/google/android/gms/location/GeofencingApi.html 
 *    
 * NOTE: 
 *   Using LocalBroadcastManager:
 *   Helper to register for and send broadcasts of Intents to local objects within your process. This has a number of advantages over sending global broadcasts with sendBroadcast(Intent):
 *    You know that the data you are broadcasting won't leave your app, so don't need to worry about leaking private data.
 *    It is not possible for other applications to send these broadcasts to your app, so you don't need to worry about having security holes they can exploit.
 *    It is more efficient than sending a global broadcast through the system.
 */

import java.util.List;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationClient; //NOTE: Deprecated (but works for API 19)

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

public class GeofenceDetectorReceiver extends BroadcastReceiver {

	Context context;

    Intent broadcastIntent = new Intent();
    
    private static final String LOG = "GeofenceDetectorReceiver";
    
    public static final int GEOFENCE_NOTIFICATION_ID = 154439; // Unique ID

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;

        broadcastIntent.addCategory(GeofenceUtils.CATEGORY_LOCATION_SERVICES);

        if (LocationClient.hasError(intent)) {
            handleError(intent);
        } else {
            handleEnterExit(intent);
        }
    }
    
    private void handleError(Intent intent){
        // Get the error code
        int errorCode = LocationClient.getErrorCode(intent);

        // Get the error message
        String errorMessage = LocationServiceErrorMessages.getErrorString(context, errorCode);

        // Log the error
        Log.e(LOG, context.getString(R.string.geofence_transition_error_detail, errorMessage));

        // Set the action and error message for the broadcast intent
        broadcastIntent
                .setAction(GeofenceUtils.ACTION_GEOFENCE_ERROR)
                .putExtra(GeofenceUtils.EXTRA_GEOFENCE_STATUS, errorMessage);

        // Broadcast the error locally to other components in this app
        LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent);
    }
    
    private void handleEnterExit(Intent intent) {
    	// Get the type of transition (enter or exit)
        int transition = LocationClient.getGeofenceTransition(intent);

        // Check which transition was reported
        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
        	String ids;
        	
        	// Get all triggered geofences in a list
            List<Geofence> geofences = LocationClient.getTriggeringGeofences(intent);
            
            // Set the action for the broadcast intent
            broadcastIntent.setAction(GeofenceUtils.ACTION_GEOFENCE_TRANSITION); 
            
            if (geofences.size() > 1) { // More than one geofence triggered - may happen if user has placed controls very close to each other!
            
            	Log.w(LOG, "More than one geofence triggered!");
            	
            	String[] geofenceIds = new String[geofences.size()];
            	for (int index = 0; index < geofences.size(); index++) {
            		geofenceIds[index] = geofences.get(index).getRequestId();         		
            		
            		
            	}
            	ids = TextUtils.join(GeofenceUtils.GEOFENCE_ID_DELIMITER, geofenceIds);
            }
            else {
            	ids = geofences.get(0).getRequestId();            	
            	
            	
            }             
            
            // Add the id(s) as extra data...
            broadcastIntent.putExtra(GeofenceUtils.EXTRA_GEOFENCE_IDS, ids);

            // Broadcast the intent locally to other components in this app
            LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent); //TODO: Which context? 
            //LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(broadcastIntent);
            
            Log.d(LOG, "Sending ACTION_GEOFENCE_TRANSITION from handleEnterExit");         
            
            if (LocationTrackerService.isStaticTracking()) {
        		Log.i(LOG, "Service is tracking!");
        	}
        	else {
        		Log.i(LOG, "Service is not tracking!");
        	}            
            if (LocationTrackerService.isClientAlive()) {
            	Log.i(LOG, "Client is alive!");
            	
            	if (LocationTrackerService.isClientVisible()) {
                	Log.i(LOG, "Client is visible!");
                }
                else {
                	Log.i(LOG, "Client is not visible!");                	
                }            	
            }
            else {
            	Log.i(LOG, "Client is not alive!");            	
            }                                   
            
            if (!ids.equals("0")) {	
            	
            	if (LocationTrackerService.isStaticTracking()) {            		
     
            		// Update found control info in prefs and show a notification if client is not alive or not visible
            		if (!LocationTrackerService.isClientAlive() ||
            		    !LocationTrackerService.isClientVisible()) {
            			
            			updateControlPointInPreferences(ids); //TODO: Handling of several ids
            			
            			setupNotification(ids);             		
            		}            		
            	}            	
            }           
            
            // Log the transition enter message
            Log.i(LOG, context.getString(R.string.geofence_transition_entered_notification_title) + ids);             
        }             
        else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) { // Will never happen, since set up to only detect entry transitions
        	
        	Log.i(LOG, "Geofence exit");
        	
        	// Do nothing here... just let all geofences be removed from main activity when game finishes
        
        } 
        else { // An invalid transition was reported 
        
            // Always log as an error
            Log.e(LOG, context.getString(R.string.geofence_transition_invalid_type, transition));
        }
    }
    
    /**
	 * Updates control point with found tag in separate preferences file, keeps any existing coordinates as strings.
	 * Only used in PLAY GAME mode to be able to update found control points when app is paused/killed/destroyed unexpectedly.
	 * NOTE: See MainActivity.saveControlPointsInPreferences() for POSSIBLE PROBLEM!
	 * @param id The Id for the control point
	 */
	private void updateControlPointInPreferences(String id) {
		
//		SharedPreferences cpPrefs = context.getApplicationContext().getSharedPreferences("ControlPointPrefs", Context.MODE_PRIVATE);
		// WORKAROUND #2: Testing with Context.MODE_MULTI_PROCESS
		SharedPreferences cpPrefs = context.getApplicationContext().getSharedPreferences("ControlPointPrefs", Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
		
		
		Editor editor = cpPrefs.edit();		
		
		// Get string with coordinates
		String coords = cpPrefs.getString("control_point_" + id, "0.0d,0.0d"); //TODO: Which default value?
				
		// Update string with found tag
		String s = coords + ",Found";			
		
		Log.i(LOG, "Updating info in ControlPointPrefs: id=" + id + " ,s=" + s);
				
		editor.putString("control_point_" + id, s); // Use id in key		
				
		editor.apply();
	}
	
	/**
     * Posts a notification in the notification bar when a geofence transition (enter) is detected.
     * If the user clicks the notification, control goes to the main activity.
     * @param ids The geofence id(s) for which a transition (enter) was detected
     */
	private Notification setupNotification(String ids) {    	
    	
		// 1. Setup Notification Builder, compatible with platform versions >= 4
    	NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(context.getApplicationContext());

		// 2. Configure Notification Status Bar
		notifBuilder.setSmallIcon(R.drawable.control_icon)		
			.setWhen(System.currentTimeMillis())		
			.setAutoCancel(true);
			//.setOngoing(true);		
		
		// 3. Configure Drop-down Action
		String notifTitle = context.getApplicationContext().getString(R.string.geofence_transition_entered_notification_title) + " " + ids;
		notifBuilder.setContentTitle(notifTitle)		
			.setContentText(context.getApplicationContext().getString(R.string.geofence_transition_notification_text)); 
			//.setContentInfo("Click!");

		// 3.5 Create a pending intent that will open main activity if notification is clicked	
		Intent notifIntent = new Intent(context.getApplicationContext(), MainActivity.class);
		
		// UPDATED 20150110
		// If FLAG_ACTIVITY_SINGLE_TOP is set then the activity will not be launched if it is already running at the top of the history stack.
		// NOTE: Also need to set android:launchMode="singleTop" for the given activity in the manifest.
		notifIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		
		PendingIntent pendingNotifIntent = PendingIntent.getActivity(context.getApplicationContext(), 0, notifIntent, 0);
		notifBuilder.setContentIntent(pendingNotifIntent);	
		
		// 3.6 Set sound
		try {
            Uri uri = Uri.parse("android.resource://" + context.getApplicationContext().getPackageName() + "/"
                    + R.raw.tada);
            notifBuilder.setSound(uri);
        } catch (Exception e) {
            e.printStackTrace();
        }		
		
		// 4. Create Notification and use Manager to launch it 
		Notification notification = notifBuilder.build();	
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager notifManager = (NotificationManager) context.getApplicationContext().getSystemService(ns);
		notifManager.notify(GEOFENCE_NOTIFICATION_ID, notification);
		
		return notification;		
	}	
}
