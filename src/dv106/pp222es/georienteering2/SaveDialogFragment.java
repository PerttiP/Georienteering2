package dv106.pp222es.georienteering2;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Defines a SaveDialogFragment to display the save dialog generated. 
 * 
 */
public class SaveDialogFragment extends DialogFragment {
	
	/**
	 * The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. 
     */
    public interface SaveDialogListener {
        public void onDialogPositiveClick(DialogFragment dialog);
        public void onDialogNegativeClick(DialogFragment dialog);
    }

    // Use this instance of the interface to deliver action events
    SaveDialogListener mListener;

    // Override the Fragment.onAttach() method to instantiate the SaveDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the SaveDialogListener so we can send events to the host
            mListener = (SaveDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement SaveDialogListener");
        }
    }
    
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.save_dialog_save_game_question)
               .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                	   // Send the positive button event back to the host activity
                       if (mListener != null) mListener.onDialogPositiveClick(SaveDialogFragment.this);
                   }
               })
               .setNegativeButton(R.string.discard, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                	   // Send the negative button event back to the host activity
                	   if (mListener != null) mListener.onDialogNegativeClick(SaveDialogFragment.this);
                   }
               });
        // Create the AlertDialog object and return it
        return builder.create();
    }

}
