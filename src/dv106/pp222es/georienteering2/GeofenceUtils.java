package dv106.pp222es.georienteering2;

import android.text.format.DateUtils;

/**
 * This class defines constants used by GeofenceDetector service and GeofenceRequester/Remover classes.
 */
public final class GeofenceUtils {

	// Types
    // Used to track what type of Geofence removal request was made
    public enum REMOVE_TYPE {INTENT, LIST}

    // Used to track what type of request is in progress
    public enum REQUEST_TYPE {ADD, REMOVE}    

    // Intent actions
    public static final String ACTION_CONNECTION_ERROR =
            "dv106.pp222es.georienteering.ACTION_CONNECTION_ERROR";

    public static final String ACTION_CONNECTION_SUCCESS =
            "dv106.pp222es.georienteering.ACTION_CONNECTION_SUCCESS";

    public static final String ACTION_GEOFENCES_ADDED =
            "dv106.pp222es.georienteering.ACTION_GEOFENCES_ADDED";

    public static final String ACTION_GEOFENCES_REMOVED =
            "dv106.pp222es.georienteering.ACTION_GEOFENCES_REMOVED"; //Minor Bug fixed here, it stated _DELETED instead

    public static final String ACTION_GEOFENCE_ERROR =
            "dv106.pp222es.georienteering.ACTION_GEOFENCES_ERROR";

    public static final String ACTION_GEOFENCE_TRANSITION =
            "dv106.pp222es.georienteering.ACTION_GEOFENCE_TRANSITION";

    public static final String ACTION_GEOFENCE_TRANSITION_ERROR =
            "dv106.pp222es.georienteering.ACTION_GEOFENCE_TRANSITION_ERROR";

    // The Intent category used by all Location Services
    public static final String CATEGORY_LOCATION_SERVICES =
            "dv106.pp222es.georienteering.CATEGORY_LOCATION_SERVICES";

    // Keys for extended data in Intents
    public static final String EXTRA_CONNECTION_CODE =
            "dv106.pp222es.georienteering.EXTRA_CONNECTION_CODE";

    public static final String EXTRA_CONNECTION_ERROR_CODE =
            "dv106.pp222es.georienteering.EXTRA_CONNECTION_ERROR_CODE";

    public static final String EXTRA_CONNECTION_ERROR_MESSAGE =
            "dv106.pp222es.georienteering.EXTRA_CONNECTION_ERROR_MESSAGE";

    public static final String EXTRA_GEOFENCE_STATUS =
            "dv106.pp222es.georienteering.EXTRA_GEOFENCE_STATUS";
    
    public static final String EXTRA_GEOFENCE_IDS =
            "dv106.pp222es.georienteering.EXTRA_GEOFENCE_IDS";

    /*
     * Keys for flattened geofences stored in SharedPreferences
     */
    public static final String KEY_LATITUDE = "dv106.pp222es.georienteering.KEY_LATITUDE";

    public static final String KEY_LONGITUDE = "dv106.pp222es.georienteering.KEY_LONGITUDE";

    public static final String KEY_RADIUS = "dv106.pp222es.georienteering.KEY_RADIUS";

    public static final String KEY_EXPIRATION_DURATION =
            "dv106.pp222es.georienteering.KEY_EXPIRATION_DURATION";

    public static final String KEY_TRANSITION_TYPE =
            "dv106.pp222es.georienteering.KEY_TRANSITION_TYPE";

    // The prefix for flattened geofence keys
    public static final String KEY_PREFIX =
            "dv106.pp222es.georienteering.KEY";

    // Invalid values, used to test geofence storage when retrieving geofences
    public static final long INVALID_LONG_VALUE = -999l;

    public static final float INVALID_FLOAT_VALUE = -999.0f;

    public static final int INVALID_INT_VALUE = -999;

    /*
     * Constants used in verifying the correctness of input values
     */
    public static final double MAX_LATITUDE = 90.d;

    public static final double MIN_LATITUDE = -90.d;

    public static final double MAX_LONGITUDE = 180.d;

    public static final double MIN_LONGITUDE = -180.d;

    public static final float MIN_RADIUS = 1f;

    /*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    public final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    // A string of length 0, used to clear out input fields
    public static final String EMPTY_STRING = new String();

    public static final CharSequence GEOFENCE_ID_DELIMITER = ",";
    
    /*
     * Used to set an expiration time for a geofence. After this amount
     * of time Location Services will stop tracking the geofence.
     * Remember to unregister a geofence when you're finished with it.
     * Otherwise, your app will use up battery. To continue monitoring
     * a geofence indefinitely, set the expiration time to
     * Geofence#NEVER_EXPIRE.
     */
    public static final long GEOFENCE_EXPIRATION_IN_HOURS = 12; //TODO: Tweaking
    public static final long GEOFENCE_EXPIRATION_IN_MILLISECONDS =
            GEOFENCE_EXPIRATION_IN_HOURS * DateUtils.HOUR_IN_MILLIS; 

}
