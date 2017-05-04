/* Android IMSI-Catcher Detector | (c) AIMSICD Privacy Project
 * -----------------------------------------------------------
 * LICENSE:  http://git.io/vki47 | TERMS:  http://git.io/vki4o
 * -----------------------------------------------------------
 */
package zz.aimsicd.lite.ui.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import zz.aimsicd.lite.AppAIMSICD;
import zz.aimsicd.lite.R;
import zz.aimsicd.lite.enums.Status;
import zz.aimsicd.lite.service.AimsicdService;
import zz.aimsicd.lite.utils.Icon;

// TODO: get rid of this!
import io.freefair.android.injection.app.InjectionAppCompatActivity;

// Base activity class, handles code that is shared between all activities
public abstract class BaseActivity extends InjectionAppCompatActivity {

    public static final String TAG = "AICDL";
    public static final String mTAG = "BaseActivity:";

    // Triggered when GUI is opened
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, mTAG + "StatusWatcher starting watching");
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter("StatusChange"));
        updateIcon(this);
    }

    // Message reciever that handles icon update when status changes
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, mTAG + "StatusWatcher received status change to " + ((AppAIMSICD) getApplication()).getStatus().name() + ", updating icon");
            updateIcon(context);
        }
    };

    private void updateIcon(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AimsicdService.SHARED_PREFERENCES_BASENAME, 0);
        //final String iconType = prefs.getString(context.getString(R.string.pref_ui_icons_key), "SENSE").toUpperCase();
        final String iconTypeString = prefs.getString(context.getString(R.string.pref_ui_icons_key), "SENSE").toUpperCase();
        final Icon.Type iconType = Icon.Type.valueOf(iconTypeString);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //if (getActionBar() != null) {
                //    getActionBar().setIcon(Icon.getIcon(Icon.Type.valueOf(iconType), ((AppAIMSICD) getApplication()).getStatus()));
                if(getSupportActionBar() != null) {
                    Status status = ((AppAIMSICD) getApplication()).getStatus();
                    int newIcon = Icon.getIcon(iconType, status);
                    getSupportActionBar().setIcon(newIcon);

                }
            }
        });
    }

    // Triggered when GUI is closed/put to background
    @Override
    protected void onPause() {
        super.onPause();
       Log.d(TAG, mTAG + "StatusWatcher stopped watching");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }
}
