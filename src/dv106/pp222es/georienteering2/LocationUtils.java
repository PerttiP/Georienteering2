package dv106.pp222es.georienteering2;

import java.util.List;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import android.widget.Toast;

public class LocationUtils {	
	
	// Intent actions
    public static final String LOCATION_DATA_BROADCAST =
            "dv106.pp222es.georienteering.action.LOCATION_DATA_BROADCAST";
    
    // Constants    
    
    // Coordinates according to decimal format WGS := World Geodetic System 1984
    // PRIME MERIDIAN OF THE WORLD, CENTRE OF THE TRANSIT CIRCLE (GREENWICH, LONDON): LATTITUDE 51° 28' 38", LONGITUDE 0° 00' 00".    
    public static final double GREENWICH_LOCATION_LATITUDE = 51.477862d;
    public static final double GREENWICH_LOCATION_LONGITUDE = 0.0d;
    
    // LINNAEUS UNIVERSITY, TRACK AND FIELD ARENA, WGS84 decimal (lat, lon): 56.882341, 14.822005
    public static final double LINNAEUS_UNIVERSITY_LOCATION_LATITUDE = 56.88234d;
    public static final double LINNAEUS_UNIVERSITY_LOCATION_LONGITUDE = 14.8220d;
    
    // GLOBEN ARENA, STOCKHOLM: 59.293611°, 18.083056°
    public static final double GLOBEN_LOCATION_LATITUDE = 59.293611d;
    public static final double GLOBEN_LOCATION_LONGITUDE = 18.083056d;
    
    // KLASMOSSEN, KARLSTAD: 59.409222, 13.57496
    public static final double KLASMOSSEN_LOCATION_LATITUDE = 59.409222d;
    public static final double KLASMOSSEN_LOCATION_LONGITUDE = 13.57496d;
        
    //TODO: Use these when handing in project to Växjö Linneuniversitetet:  
    public static final double DEFAULT_LOCATION_LATITUDE = LINNAEUS_UNIVERSITY_LOCATION_LATITUDE;
    public static final double DEFAULT_LOCATION_LONGITUDE = LINNAEUS_UNIVERSITY_LOCATION_LONGITUDE;

/*
    public static final double DEFAULT_LOCATION_LATITUDE = KLASMOSSEN_LOCATION_LATITUDE;
    public static final double DEFAULT_LOCATION_LONGITUDE = KLASMOSSEN_LOCATION_LONGITUDE;
*/  
    
    private static final String LOG = "LocationUtils";	
    
    /**
     * Checks that at least one location provider is available and enabled.
     * Displays a toast if no location provider is enabled.
     * @param context
     * @return true if at least one provider (gps, network, passive) was found.
     */    
    public boolean isLocationProviderAvailableAndEnabled(Context context) {
		
	    LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
	    if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && 
	    	!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) &&
	    	!manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) ) {
	    	
	    	Toast.makeText(context, R.string.location_provider_not_enabled, Toast.LENGTH_LONG).show();
	    	Log.d(LOG, "No location provider enabled!");
	    	return false;
	    }
	    else {	    	    	
	    	
	    	List<String> providers = manager.getProviders(true); //Only enabled providers
	    	for (String s : providers)
	    		Log.v(LOG, "Enabled Location Providers: " + s);
	    	return true;
	    }	    	
	}
	
    /**
     * Get last known location, provided that a location provider is available and enabled.
     * Uses criteria ACCURACY_FINE, a finer location accuracy requirement.
     * @param context
     * @return Location object, null if no location could be determined.
     * NOTE: Does not seem to work... Use LocationClient instead!
     */
	public Location getLastKnownLocation(Context context) {
		
		if (!isLocationProviderAvailableAndEnabled(context)) return null;
		
		LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		Criteria criteria = new Criteria();
//		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		
    	String bestLocationProvider = manager.getBestProvider(criteria, true); //Only enabled provider
    	
    	Log.v(LOG, "Best Provider: " + bestLocationProvider);
		
		return manager.getLastKnownLocation(bestLocationProvider);		
	}    
    
	/**
     * Get the latitude and longitude from the Location object returned by
     * Location Services.
     *
     * @param currentLocation A Location object containing the current location
     * @return The latitude and longitude of the current location, or null if no
     * location is available.
     */
    public static String getLatLng(Context context, Location currentLocation) {
        // If the location is valid
        if (currentLocation != null) {

            // Return the latitude and longitude as strings, with formating from resources
            return context.getString(
                    R.string.latitude_longitude,
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude());
        } 
        else {

            // Otherwise, return null
            return null;
        }
    }
    
    /**
	 * Calculates distance (in meters) between two LatLng coordinates.
	 * 
	 * @param point1 starting point
	 * @param point2 ending point
	 * @return distance (in meters), 0.0f if calculation failed.
	 */
	public float getDistanceBetweenPoints(LatLng point1, LatLng point2) {
		float[] distance = new float[3];
		
		try {
			Location.distanceBetween(point1.latitude, point1.longitude,
									 point2.latitude, point2.longitude,
									 distance);
		}
		catch (IllegalArgumentException ile) {
			ile.printStackTrace();
			return 0.0f;
		}
		// The computed distance is stored in results[0]. If results has length 2 or greater, 
		// the initial bearing is stored in results[1]. If results has length 3 or greater, the final bearing is stored in results[2].
		return distance[0];
	}
	
	/**
	 * Creates a LatLngBounds, using specified LatLng coordinates as bounds (a rectangle).
	 * 
	 * @param coordinates
	 * @return a LatLngBounds object for the rectangle.
	 */	
	public LatLngBounds getBoundsForPoints(List<LatLng> coordinates) {
		LatLngBounds.Builder builder = LatLngBounds.builder();
		for (LatLng coordinate : coordinates) {
			builder.include(coordinate);
		}
		return builder.build();
	}

	/**
	 * Checks if a LatLng coordinate is within the bounds (a rectangle).
	 * 
	 * @param latLng
	 * @param bounds
	 * @return true if LatLng coordinate is within the bounds.
	 */
	public boolean isWithinBound(LatLng latLng, LatLngBounds bounds) {
		return bounds.contains(latLng);
	}

}
