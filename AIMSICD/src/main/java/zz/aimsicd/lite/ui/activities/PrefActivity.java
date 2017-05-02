/* Android IMSI-Catcher Detector | (c) AIMSICD Privacy Project
 * -----------------------------------------------------------
 * LICENSE:  http://git.io/vki47 | TERMS:  http://git.io/vki4o
 * -----------------------------------------------------------
 */
package zz.aimsicd.lite.ui.activities;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;

import zz.aimsicd.lite.ui.fragments.PrefFragment;


public class PrefActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadFragment();
    }

    private void loadFragment() {
        PrefFragment settingsFragment = new PrefFragment();
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(android.R.id.content, settingsFragment);
        fragmentTransaction.commit();
    }
}
