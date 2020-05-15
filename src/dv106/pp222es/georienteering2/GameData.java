package dv106.pp222es.georienteering2;

import java.math.BigDecimal;

//import java.sql.Date;

/**
 * GameData 
 * @author Pertti Palokangas
 * Class for storing game data.
 * Used by GameDataSource to save in SQLite database.
 * 
 */
public class GameData {
	private long id; // Primary key ID used in SQLite database
	private String name;
//	private Date date; //Dates are represented in SQL as yyyy-MM-dd
	private String date;
	private int playtimeInMinutes;
	private int playtimeInSeconds;
	private int distanceInMeters;
	private int numFoundControlPoints;
	private int numTotalControlPoints;
	
//	private int courseId; //For possible future feature with DB table for courses
	
	// Default constructor
	public GameData() {}	

	public long getId() {
	    return id;
	}

	public void setId(long id) {
	    this.id = id;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String _name) {
		name = _name;
	}
	
	public String getDate() {
		return date;
	}
	
	public void setDate(String _date) {
		date = _date;
	}	
	
	public int getPlaytimeInMinutes() {
		return playtimeInMinutes;
	}
	
	public void setPlaytimeInMinutes(int _value) {
		playtimeInMinutes = _value;
	}
	
	public int getPlaytimeInSeconds() {
		return playtimeInSeconds;
	}
	
	public void setPlaytimeInSeconds(int _value) {
		playtimeInSeconds = _value;
	}	
	
	public int getDistanceInMeters() {
		return distanceInMeters;
	}
	
	public void setDistanceInMeters(int _value) {
		distanceInMeters = _value;
	}
	
	public int getNumFoundControlPoints() {
		return numFoundControlPoints;
	}
	
	public void setNumFoundControlPoints(int _value) {
		numFoundControlPoints = _value;
	}
	
	public int getNumTotalControlPoints() {
		return numTotalControlPoints;
	}
	
	public void setNumTotalControlPoints(int _value) {
		numTotalControlPoints = _value;
	}
	
	/**
	 * Calculate average speed in meters per second.
	 * @return speed with 2 decimals scaling
	 */
	public float calculateAverageSpeed_MperS() {
		float timeInSeconds = ((playtimeInMinutes*60) + playtimeInSeconds);		
		if (timeInSeconds > 0.0f) {
			
			float speed_m_per_s = distanceInMeters / timeInSeconds;
			BigDecimal big_decimal = new BigDecimal(speed_m_per_s);  
			// Scale down to two decimals
	        return big_decimal.setScale(2, BigDecimal.ROUND_HALF_DOWN).floatValue(); 			
		}
		else {
			return 0.0f;
		}
	}
	
	//FIXME: Does not calculate correctly
/*
	public float calculateAverageSpeed_KMperH() {			
		float distInKm = distanceInMeters / 1000;	
		// One hour is 60 * 60 = 3600 seconds
		float timeInHours = ((playtimeInMinutes*60) + playtimeInSeconds) / 3600;
		if (timeInHours > 0.0f) {		
			return (distInKm / timeInHours);
		}
		else {
			return 0.0f;
		}
	}
*/
/*
	public float calculateAverageSpeed_MINperKM() {	
		
		float distInKm = distanceInMeters / 1000;
		float timeInSeconds = ((playtimeInMinutes*60) + playtimeInSeconds);
		float num_secs_per_km = timeInSeconds / distInKm;
				
		//TODO:
		return 0.0f;
	}
*/	
	/**
	 * Get game play time
	 * @return Playtime in format H:mm:ss
	 */
	public String getPlaytime() {
		Integer numMinutes = Integer.valueOf(playtimeInMinutes);
		Integer numSeconds = Integer.valueOf(playtimeInSeconds);
		
		Integer numHours = 0;
		if (numMinutes < 60) {
			numHours = 0;
		}
		else {
			numHours = (numMinutes / 60);
			numMinutes = numMinutes - (numHours*60);
		}
		String numMinutesAsString;		
		if (numMinutes < 10) {
			numMinutesAsString = "0" + numMinutes.toString();
		}
		else {
			numMinutesAsString = numMinutes.toString();
		}		
		
		String numSecondsAsString;		
		if (numSeconds < 10) {
			numSecondsAsString = "0" + numSeconds.toString();
		}
		else {
			numSecondsAsString = numSeconds.toString();
		}		
		
		if (numHours > 0) {
			return numHours.toString() + ":" + numMinutesAsString + ":" + numSecondsAsString;
		}
		else {
			return "  " + numMinutesAsString + ":" + numSecondsAsString;
		}
	}
	
	public String toString() {
		return (name + "," + date + "," +
				getPlaytime() + "," +
				distanceInMeters  + "," +
				numFoundControlPoints + "/" +
				numTotalControlPoints
				);
	}		
}