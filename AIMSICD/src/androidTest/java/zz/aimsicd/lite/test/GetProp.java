/* Android IMSI-Catcher Detector | (c) AIMSICD Privacy Project
 * -----------------------------------------------------------
 * LICENSE:  http://git.io/vki47 | TERMS:  http://git.io/vki4o
 * -----------------------------------------------------------
 */
package zz.aimsicd.lite.test;

import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import zz.aimsicd.lite.activities.DebugLogs;

import java.io.IOException;

/**
 * DebugLogs test
 */
public class GetProp extends ActivityInstrumentationTestCase2<DebugLogs> {

    private final String TAG = "AIMSICD";

    public GetProp() {
        super(DebugLogs.class);
    }

    public void testGetPropsReturnsValue() throws IOException {
        DebugLogs activity = (DebugLogs) getActivity();
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            Log.e(TAG, "Exception in test: getprop: " + e);
        }
        String props = activity.getProp();
        Log.i(TAG, "getprop: " + props);
        assertTrue(props.trim().length() > 0);
    }
}
