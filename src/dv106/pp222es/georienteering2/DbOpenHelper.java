package dv106.pp222es.georienteering2;

/**
 * @see http://developer.android.com/training/search/search.html
 */

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DbOpenHelper extends SQLiteOpenHelper {

	// String constants for SQL statements
	public static final String MY_TABLE_NAME = "games";
	public static final String COLUMN_ID = "_id";	
	public static final String COLUMN_NAME = "name";
	public static final String COLUMN_DATE = "date";
	public static final String COLUMN_PLAYTIME_MINUTES = "playtime_minutes";
	public static final String COLUMN_PLAYTIME_SECONDS = "playtime_seconds";	
	public static final String COLUMN_NUM_FOUND_CONTROLS = "num_found_controls";
	public static final String COLUMN_NUM_TOTAL_CONTROLS = "num_total_controls";
	public static final String COLUMN_DISTANCE_METERS = "distance_meters";

	private static final String DATABASE_NAME = "georienteering.db";
	private static final int DATABASE_VERSION = 1;
	
	// Prepared SQL create table statement
    private static final String DATABASE_CREATE = "create table " + MY_TABLE_NAME 
    		+ " (" + COLUMN_ID + " integer primary key autoincrement, "
            	 + COLUMN_NAME + " text null, "
            	 + COLUMN_DATE + " text not null, " //NOTE: date stored as text (NOT date)!
                 + COLUMN_PLAYTIME_MINUTES + " integer null, "    
                 + COLUMN_PLAYTIME_SECONDS + " integer null, "
                 + COLUMN_NUM_FOUND_CONTROLS + " integer null, "
                 + COLUMN_NUM_TOTAL_CONTROLS + " integer null, "
                 + COLUMN_DISTANCE_METERS + " integer null "
            	 + ");" ;
	
    /**
     * Create a helper object to create, open, and/or manage a database.
     * This method always returns very quickly.  The database is not actually
     * created or opened until one of {@link #getWritableDatabase} or
     * {@link #getReadableDatabase} is called.
     */
    
	public DbOpenHelper(Context context) {
		
		// Calls the super constructor, requesting the default cursor factory
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	
	/**
     * Called when the database is created for the first time. This is where the
     * creation of tables and the initial population of the tables should happen.
     */
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		
		// Creates the underlying database with table name and column names
		db.execSQL(DATABASE_CREATE);
	}

	
	/**
     * Called when the database needs to be upgraded. The implementation
     * should use this method to drop tables, add tables, or do anything else when it
     * needs to upgrade to the new schema version.
     * NOTE: After a user (or you, as developer) upgrade the application such that the version number in the app 
     * is higher than the version number in the database on "disk", 
     * the SQLiteHelper code notices, and calls the onUpgrade() method with the old and new version numbers.
     * Here the database is upgraded by destroying the existing data.
     * A real application should upgrade the database in place.
     * 
     * Why?:
     * a good app will analyse the old and the new version numbers and act accordingly, 
     * a not-so-good app will take an easy path (dropping all [affected] tables and recreating them anew), 
     * a bad app (which has an empty onUpgrade()) will just crash eventually when querying for non-existent fields.
     */
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	    Log.w(DbOpenHelper.class.getName(), "Upgrading database from version " 
	    		+ oldVersion + " to " + newVersion 
	    		+ ", which will destroy all old data");
	    
	    // Kills the table and existing data
        db.execSQL("DROP TABLE IF EXISTS " + MY_TABLE_NAME);
        
        // Recreates the database with a new version
        onCreate(db);
        
        switch (newVersion) {
        case 1:
        	Log.w(DbOpenHelper.class.getName(), "Initial version");
        	break;
        default:
        	throw new IllegalStateException("onUpgrade() with unknown newVersion" + newVersion);
        }        
	}

}
