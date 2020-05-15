package dv106.pp222es.georienteering2;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.database.SQLException;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
//import android.view.Menu;
//import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class GameDataActivity extends Activity {
	
	private static String LOG = "GameDataActivity";
	
	private ListView myListView = null;
	private GameDataSource dataSource = null;	

	private List<GameData> gameDataItems = null;
	private IconMultiAdapter adapter = null; // This extends ArrayAdapter
	
	private TextView heading;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_game_data);
		
		myListView = (ListView) findViewById(R.id.mylistview);
		
		heading = (TextView) findViewById(R.id.heading);
		heading.setText(R.string.game_data_heading);
		
		// Create array list for game data items
        gameDataItems = new ArrayList<GameData>();        
        
        // Open database        
        dataSource = new GameDataSource(this);         
       
        try {
        	dataSource.open();
        } catch (SQLException sqe) {
        	Log.e(LOG, "Could not open database");
        	Toast.makeText(getApplicationContext(), R.string.database_open_error, Toast.LENGTH_LONG).show();
        	finish(); // This activity can not show any data, so finish it
        	return;
        } 
        
        gameDataItems = dataSource.getAllItems(); 
        
        // Create an IconMultiAdapter for dynamic list view       
        adapter = new IconMultiAdapter(getApplicationContext(), R.layout.row, gameDataItems);        
               
        // Configure the list view adapter 
        myListView.setAdapter(adapter); 
        
        // Notify adapter that data set changed
//        adapter.notifyDataSetChanged();
	}  
	
	// Called at the end of the full lifetime.
	/*
	 * Note: do not count on this method being called as a place for saving data! 
	 * For example, if an activity is editing data in a content provider, those edits should be committed in either onPause or onSaveInstanceState, not here.
	 * @see android.app.Activity#onDestroy()
	 */	  
	@Override
	public void onDestroy(){
		// Clean up any resources including ending threads, 
		// closing database connections etc.
		super.onDestroy();

		Log.i(LOG, "onDestroy for activity. Will close database.");
		
		// NOTE: MUST ALWAYS CLOSE DB AT LEAST HERE!!!
		dataSource.close();
	}
	
	/**
     * Inner class IconMultiAdapter
     *  to handle both icons and multiple rows in list view
     *  and populate contents for each row with data obtained from array list with WeatherForecast items 
     * 
     */
    class IconMultiAdapter  extends ArrayAdapter<GameData> {
    	private int resource;
    	
    	public IconMultiAdapter(Context _context, int _resource, List<GameData> _items) { 
    		super(_context, _resource, _items);
    		resource = _resource;
    	}
    	
    	@Override   // Called when updating the ListView
    	public View getView(int position, View convertView, ViewGroup parent) {    		
    		
    		GameData item = getItem(position);    		
    	
    		View row;
    		if (convertView == null) {	// Create new row view object   		
    			LayoutInflater inflater = getLayoutInflater();
    			row = inflater.inflate(resource, parent, false);
    		}
    		else    // Reuse old row view to save time/battery
    			row = convertView;    		    		
    		   		
    		TextView date = (TextView) row.findViewById(R.id.date);    		
    		TextView time = (TextView) row.findViewById(R.id.time);    
    		TextView distance = (TextView) row.findViewById(R.id.distance);
//    		TextView speed = (TextView) row.findViewById(R.id.speed);
    		TextView found_controls = (TextView) row.findViewById(R.id.found_controls);    		   		
    	    	
    		ImageView icon = (ImageView) row.findViewById(R.id.game_icon);
    		
    		if (item.getNumFoundControlPoints() >= item.getNumTotalControlPoints()) {
    			icon.setImageResource(R.drawable.ok);
    		}
    		else {
    			icon.setImageResource(R.drawable.not_ok);
    		}     		
    		
    		date.setText(item.getDate());
    		time.setText(item.getPlaytime());
    		distance.setText(Integer.valueOf(item.getDistanceInMeters()).toString() + " m");
    		
    		//FIXME: Does not calculate correctly:
//    		float speed_km_per_h = item.calculateAverageSpeed_KMperH();    		    		
//    		speed.setText(Float.valueOf(item.calculateAverageSpeed_KMperH()).toString() + " km/h");
//Disabled in v 1
/*
    		float speed_m_per_s = item.calculateAverageSpeed_MperS();    		
    		speed.setText(Float.valueOf(speed_m_per_s).toString()); // + " m/s");
*/    		
    		found_controls.setText(Integer.valueOf(item.getNumFoundControlPoints()).toString() + " " +  				
    			getString(R.string.found_controls_text_part2) + " " + Integer.valueOf(item.getNumTotalControlPoints()).toString());    		
 
    		return row;		
    	}
    }

//	@Override
//	public boolean onCreateOptionsMenu(Menu menu) {
//		// Inflate the menu; this adds items to the action bar if it is present.
////		getMenuInflater().inflate(R.menu.game_data, menu);
//		return true;
//	}
//
//	@Override
//	public boolean onOptionsItemSelected(MenuItem item) {
//		// Handle action bar item clicks here. The action bar will
//		// automatically handle clicks on the Home/Up button, so long
//		// as you specify a parent activity in AndroidManifest.xml.
//		int id = item.getItemId();
//		if (id == R.id.action_settings) {
//			
//			// TEST
//			// Save the new item to the database
//    		GameData newItem = dataSource.createItem("New3",77,59,3,5,26905);
//    		    			
//			assert(newItem != null);
//
//			try {    			
//				adapter.add(newItem);    				
//			}
//			catch (NullPointerException npe) {
//				System.out.println("NullPointerException for adapter.add(newItem)");
//				return false;
//			}    		
//
//			// Notify adapter that data set changed
//			adapter.notifyDataSetChanged();
//			
//			return true;
//		}
//		return super.onOptionsItemSelected(item);
//	}
}
