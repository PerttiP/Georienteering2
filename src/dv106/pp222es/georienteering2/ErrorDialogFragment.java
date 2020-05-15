package dv106.pp222es.georienteering2;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

/**
 * Defines a DialogFragment to display the error dialog generated in a getErrorDialog call, for example GooglePlayServicesUtil.getErrorDialog.
 */
public class ErrorDialogFragment extends DialogFragment {
	
	// Global field to contain the error dialog
    private Dialog mDialog;

    /**
     * Default constructor. Sets the dialog field to null
     */
    public ErrorDialogFragment() {
        super();
        mDialog = null;
    }

    /**
     * Set the dialog to display
     *
     * @param dialog An error dialog
     */
    public void setDialog(Dialog dialog) {
        mDialog = dialog;
    }

    /*
     * This method must return a Dialog to the DialogFragment.
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return mDialog;
    }

}
