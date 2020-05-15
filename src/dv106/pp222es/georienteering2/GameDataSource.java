package dv106.pp222es.georienteering2;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * GameDataSource
 * @author Pertti Palokangas
 * Handling of SQLite database and storing/deleting/retrieving items from DB using GameData class.
 * 
 * TODO: Check for memory leaks! (usage of cursor)
 */

public class GameDataSource {
	
	private static String LOG = "GameDataSource";

	// Database fields
	private SQLiteDatabase database;
	private DbOpenHelper dbHelper;
	private String[] allColumns = { DbOpenHelper.COLUMN_ID,
									DbOpenHelper.COLUMN_NAME,
									DbOpenHelper.COLUMN_DATE,
									DbOpenHelper.COLUMN_PLAYTIME_MINUTES,
									DbOpenHelper.COLUMN_PLAYTIME_SECONDS,
									DbOpenHelper.COLUMN_NUM_FOUND_CONTROLS,
									DbOpenHelper.COLUMN_NUM_TOTAL_CONTROLS,
									DbOpenHelper.COLUMN_DISTANCE_METERS
								  };

	public GameDataSource(Context context) {
		dbHelper = new DbOpenHelper(context);
	}

	public void open() throws SQLException {
		
		// Opens the database object in "write" mode
		database = dbHelper.getWritableDatabase();
		
		// Opens the database object in "read" mode, if no writes need to be done.
	    //database = dbHelper.getReadableDatabase();
		
		if (database == null) {
			Log.e(LOG, "Failed to open database (database == null)");
		}
	}

	public void close() {
		dbHelper.close();
	}
	
	public GameData createItem(String item_name,
							   int timeMinutes,
							   int timeSeconds,
							   int numFoundControls,
							   int numTotalControls,
							   int distanceMeters) {
		
		// Add column values to the data set
		ContentValues values = new ContentValues();
		
		java.sql.Date sqlDate = new java.sql.Date(new java.util.Date().getTime());		
//		java.sql.Date sqlDate2 = new java.sql.Date(System.currentTimeMillis());
		
//		Log.i(LOG, "sqlDate: " + sqlDate.toString());
//		Log.i(LOG, "sqlDate2: " + sqlDate2.toString());
		
		values.put(DbOpenHelper.COLUMN_NAME, item_name);
		values.put(DbOpenHelper.COLUMN_DATE, sqlDate.toString()); //NOTE: date stored as text (NOT date)!
		values.put(DbOpenHelper.COLUMN_PLAYTIME_MINUTES, Integer.valueOf(timeMinutes));
		values.put(DbOpenHelper.COLUMN_PLAYTIME_SECONDS, Integer.valueOf(timeSeconds));
		values.put(DbOpenHelper.COLUMN_NUM_FOUND_CONTROLS, Integer.valueOf(numFoundControls));
		values.put(DbOpenHelper.COLUMN_NUM_TOTAL_CONTROLS, Integer.valueOf(numTotalControls));
		values.put(DbOpenHelper.COLUMN_DISTANCE_METERS, Integer.valueOf(distanceMeters));
		
		assert(values != null);
		
		long insertRowId; 		
				
		try {
			// Performs the insert and returns the ID of the new row
			insertRowId = database.insert(DbOpenHelper.MY_TABLE_NAME, 
										  null,    // SQLite sets this column value to null if values is empty.
										  values); // A map of column names, and the values to insert into the columns.
		}		
		catch (SQLException sqe) {
			sqe.printStackTrace();
			Log.e(LOG, "SQLException in createItem() for database.insert");
			return null;
		}
		
		// If the insert succeeded, the insert row ID exists.
        if (insertRowId < 0) {    	
        	
        	// If the insert didn't succeed, then the rowID is <= 0. Throws an exception.
            throw new SQLException("Failed to insert new item into SQL table: " + DbOpenHelper.MY_TABLE_NAME);
        }
        else {
        	Log.i(LOG, "Item created with id: " + insertRowId);
        }
		
		// Run query, returning a Cursor over the result set.
		Cursor cursor = database.query(DbOpenHelper.MY_TABLE_NAME,
									   allColumns, 
									   DbOpenHelper.COLUMN_ID + " = " + insertRowId, 
									   null, null, null, null);
				
		// Move the cursor to the first row
		cursor.moveToFirst();
		
		// Create a new item using the cursor
		GameData newItem = cursorToItem(cursor);
		
		// Make sure to close the cursor
		cursor.close();
		
		return newItem;
	}

	public boolean deleteItem(GameData item) {
		long id = item.getId();		
		
		int numRows = database.delete(DbOpenHelper.MY_TABLE_NAME, DbOpenHelper.COLUMN_ID
				+ " = " + id, null);
		
		if (numRows < 1) {
			// If no row(s) were deleted then numRows is < 1
			Log.e(LOG, "Failed to delete item with id: " + id + " in SQL table " + DbOpenHelper.MY_TABLE_NAME);
			return false;
		}
		else {
			Log.i(LOG, "Item deleted with id: " + id);
			return true;
		}
	}

	public GameData getItem(long itemId) {
		String restrict = DbOpenHelper.COLUMN_ID + "=" + itemId;
		
		// Run query, returning a Cursor over the result set.
		Cursor cursor = database.query(true, DbOpenHelper.MY_TABLE_NAME, 
									         allColumns, 
									         restrict,
									         null, null, null, null, null);
		
		GameData item = null;
		if (cursor != null && cursor.getCount() > 0) {
			cursor.moveToFirst();
			item = cursorToItem(cursor);			
		}
		
		// Make sure to close the cursor
		cursor.close();
		
		return item;
	}

	public List<GameData> getAllItems() {
		List<GameData> items = new ArrayList<GameData>();

		Cursor cursor = database.query(DbOpenHelper.MY_TABLE_NAME,
									   allColumns, 
									   null, null, null, null, null);

		cursor.moveToFirst();
		
		// While not cursor is pointing to the position after the last row
		while (!cursor.isAfterLast()) {
			GameData item = cursorToItem(cursor);
			items.add(item);
			cursor.moveToNext();
		}
		
		// Make sure to close the cursor
		cursor.close();
		
		return items;
	}	
	
	private GameData cursorToItem(Cursor cursor) {
		GameData item = new GameData();
		
		item.setId(cursor.getLong(0)); //This is the primary key ID
		item.setName(cursor.getString(1));		
		item.setDate(cursor.getString(2)); //NOTE: date stored as text (NOT date)!
		item.setPlaytimeInMinutes(cursor.getInt(3));
		item.setPlaytimeInSeconds(cursor.getInt(4));		
		item.setNumFoundControlPoints(cursor.getInt(5));
		item.setNumTotalControlPoints(cursor.getInt(6));
		item.setDistanceInMeters(cursor.getInt(7));

		return item;
	}
	
} 
