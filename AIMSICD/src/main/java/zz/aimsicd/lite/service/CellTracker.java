/* Android IMSI-Catcher Detector | (c) AIMSICD Privacy Project
 * -----------------------------------------------------------
 * LICENSE:  http://git.io/vki47 | TERMS:  http://git.io/vki4o
 * -----------------------------------------------------------
 */
package zz.aimsicd.lite.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Vibrator;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import zz.aimsicd.lite.AppAIMSICD;
import zz.aimsicd.lite.BuildConfig;
import zz.aimsicd.lite.MainActivity;
import zz.aimsicd.lite.R;
import zz.aimsicd.lite.adapters.AIMSICDDbAdapter;
import zz.aimsicd.lite.enums.Status;
import zz.aimsicd.lite.utils.Cell;
import zz.aimsicd.lite.utils.Device;
import zz.aimsicd.lite.utils.DeviceApi18;
import zz.aimsicd.lite.utils.Helpers;
import zz.aimsicd.lite.utils.Icon;
import zz.aimsicd.lite.utils.TinyDB;

/**
 * Description:     Class to handle tracking of cell information
 *
 * Dependencies:
 *
 * Issues:
 *
 *
 * Note:            The refresh rate is set in two different places:
 *                      onSharedPreferenceChanged()
 *                      loadPreferences()
 *
 *              For proper TinyDB implementation use something like:
 *              https://github.com/kcochibili/TinyDB--Android-Shared-Preferences-Turbo/issues/6
 *
 *
 *
 *  ToDo:       Currently the automatic refresh rate is hard-coded to 15 seconds,
 *              in 2 different places above. We may consider have this more transparent
 *              in a static variable. It is also used in timerRunnable where it
 *              defaults to 25 seconds.
 *
 *              [x] Use TinyDB.java to simplify Shared Preferences usage
 */

public class CellTracker implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "AICDL";
    public static final String mTAG = "CellTracker: ";

    public static Cell mMonitorCell;
    public static String OCID_API_KEY = null;   // see getOcidKey()
    public static int PHONE_TYPE;               //
    public static int LAST_DB_BACKUP_VERSION;   //
    public static long REFRESH_RATE;            // [s] The DeviceInfo refresh rate (arrays.xml)
    public static final String SILENT_SMS = "SILENT_SMS_DETECTED";

    private boolean CELL_TABLE_CLEANSED;        // default is FALSE for "boolean", and NULL for "Boolean".
    private final int NOTIFICATION_ID = 1;
    private final Device mDevice = new Device();

    private static TelephonyManager tm;
    private final SignalStrengthTracker signalStrengthTracker;
    private PhoneStateListener mPhoneStateListener;
    private SharedPreferences prefs;
    private TinyDB tinydb; // Used to simplify SharedPreferences usage above

    //=====================================================
    //  Tracking and Alert Declarations
    //=====================================================
    private boolean mMonitoringCell;
    private boolean mTrackingCell;
    private boolean mChangedLAC;
    private boolean mCellIdNotInOpenDb;
    private boolean mTypeZeroSmsDetected;
    private boolean mVibrateEnabled;
    private int mVibrateMinThreatLevel;
    private LinkedBlockingQueue<NeighboringCellInfo> neighboringCellBlockingQueue;

    private final AIMSICDDbAdapter dbHelper;
    private static Context context;

    public CellTracker(Context context, SignalStrengthTracker sst) {
        this.context = context;
        this.signalStrengthTracker = sst;

        // Creating tinydb here to avoid: "TinyDb tinydb = new TinyDb(context);"
        // every time we need to use tinydb in this class.
        tinydb = TinyDB.getInstance();

        // TelephonyManager provides system details
        tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        // Shared Preferences
        prefs = context.getSharedPreferences(AimsicdService.SHARED_PREFERENCES_BASENAME, 0);
        prefs.registerOnSharedPreferenceChangeListener(this);
        loadPreferences();
        setNotification();

        PHONE_TYPE = tm.getPhoneType(); // PHONE_TYPE_GSM/CDMA/SIP/NONE

        dbHelper = new AIMSICDDbAdapter(context);

        // Remove all but the last DBi_bts entry, after:
        //   (a) starting CellTracker for the first time or
        //   (b) having cleared the preferences.
        // Subsequent runs are prevented by a hidden boolean preference. See: loadPreferences()
        if (!CELL_TABLE_CLEANSED) {
            dbHelper.cleanseCellTable();
            SharedPreferences.Editor prefsEditor;
            prefsEditor = prefs.edit();
            prefsEditor.putBoolean(context.getString(R.string.pref_cell_table_cleansed), true); // set to true
            prefsEditor.apply();
        }
        mDevice.refreshDeviceInfo(tm, context); // Telephony Manager
        mMonitorCell = new Cell();
    }

    public boolean isTrackingCell() {
        return mTrackingCell;
    }

    public boolean isMonitoringCell() {
        return mMonitoringCell;
    }


    /**
     * Description:     Cell Information Monitoring
     *                  TODO: What exactly are we monitoring here??
     *                  TODO: Is this related to the Tracking/Monitoring in the menu drawer?
     *
     * @param monitor Enable/Disable monitoring
     */
    public void setCellMonitoring(boolean monitor) {
        if (monitor) {
            mMonitoringCell = true;
            Helpers.msgShort(context, context.getString(R.string.monitoring_cell_information));
        } else {
            mMonitoringCell = false;
            Helpers.msgShort(context, context.getString(R.string.stopped_monitoring_cell_information));
        }
        setNotification();
    }

    public Device getDevice() {
        return mDevice;
    }



    public void stop() {
        if (isMonitoringCell()) {
            setCellMonitoring(false);
        }
        if (isTrackingCell()) {
            setCellTracking(false);
        }

        cancelNotification();
        tm.listen(mCellSignalListener, PhoneStateListener.LISTEN_NONE);
        prefs.unregisterOnSharedPreferenceChangeListener(this);

    }

    /**
     *  Description:    Cell Information Tracking and database logging
     *
     *          TODO: update this!!
     *
     *          If the "tracking" option is enabled (as it is by default) then we are keeping
     *          a record (tracking) of the device location "gpsd_lat/lon", the connection
     *          signal strength (rx_signal) and data activity (?) and data connection state (?).
     *
     *          The items included in these are stored in the "DBi_measure" table.
     *
     *          DATA_ACTIVITY:
     *          DATA_CONNECTION_STATE:
     *
     *
     *  UI/function:        Drawer:  "Toggle Cell Tracking"
     *
     *  Issues:
     *
     *  Notes:              TODO:   We also need to listen and log for:
     *
     *      [ ]     LISTEN_CALL_STATE:
     *                  CALL_STATE_IDLE
     *                  CALL_STATE_OFFHOOK
     *                  CALL_STATE_RINGING
     *
     *      [ ]     LISTEN_SERVICE_STATE:
     *                  STATE_EMERGENCY_ONLY
     *                  STATE_IN_SERVICE
     *                  STATE_OUT_OF_SERVICE
     *                  STATE_POWER_OFF
     *
     * @param track Enable/Disable tracking
     */
    public void setCellTracking(boolean track) {
        if (track) {
            tm.listen(mCellSignalListener,
                    PhoneStateListener.LISTEN_CELL_LOCATION |         // gpsd_lat/lon ?
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |      // rx_signal
                    PhoneStateListener.LISTEN_DATA_ACTIVITY |         // No,In,Ou,IO,Do
                    PhoneStateListener.LISTEN_DATA_CONNECTION_STATE | // Di,Ct,Cd,Su
                    PhoneStateListener.LISTEN_CELL_INFO               // !? (Need API 17)
            );
            mTrackingCell = true;
            Helpers.msgShort(context, context.getString(R.string.tracking_cell_information));
        } else {
            tm.listen(mCellSignalListener, PhoneStateListener.LISTEN_NONE);
            mDevice.mCell.setLon(0.0);
            mDevice.mCell.setLat(0.0);
            mDevice.setCellInfo("[0,0]|nn|nn|"); //default entries into "locationinfo"::Connection
            mTrackingCell = false;
            Helpers.msgShort(context, context.getString(R.string.stopped_tracking_cell_information));
        }
        setNotification();
    }

    /**
     *  Description:    This handles the settings/choices and default preferences, when changed.
     *                  From the default file:
     *                          preferences.xml
     *                  And saved in the file:
     *                          /data/data/com.SecUpwN.AIMSICD/shared_prefs/zz.aimsicd.lite_preferences.xml
     *
     *  NOTE:           - For more code transparency we have added TinyDB.java as a
     *                    wrapper to SharedPreferences usage. Please try to use this instead.
     *
     * @param sharedPreferences
     * @param key
     */
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        final String KEY_UI_ICONS = context.getString(R.string.pref_ui_icons_key);
        final String REFRESH = context.getString(R.string.pref_refresh_key);
        final String DB_VERSION = context.getString(R.string.pref_last_database_backup_version);
        final String OCID_KEY = context.getString(R.string.pref_ocid_key);
        final String VIBRATE_ENABLE = context.getString(R.string.pref_notification_vibrate_enable);
        final String VIBRATE_MIN_LEVEL = context.getString(R.string.pref_notification_vibrate_min_level);

        if (key.equals(KEY_UI_ICONS)) {
            // Update Notification to display selected icon type
            setNotification();
        } else if (key.equals(REFRESH)) {
            String refreshRate = sharedPreferences.getString(REFRESH, "1");
            if (refreshRate.isEmpty()) {
                refreshRate = "1"; // Set default to: 1 second
            }

            int rate = Integer.parseInt(refreshRate);
            long t;
            switch (rate) {
                case 1:
                    t = 15L; // Automatic refresh rate is 15 seconds
                    break;
                default:
                    t = (long) rate; // Default is 1 sec (from above)
                    break;
            }
            REFRESH_RATE = TimeUnit.SECONDS.toMillis(t);
        } else if (key.equals(DB_VERSION)) {
            LAST_DB_BACKUP_VERSION = sharedPreferences.getInt(DB_VERSION, 1);
        } else if (key.equals(OCID_KEY)) {
            getOcidKey();
        } else if (key.equals(VIBRATE_ENABLE)) {
            mVibrateEnabled = sharedPreferences.getBoolean(VIBRATE_ENABLE, true);
        } else if (key.equals(VIBRATE_MIN_LEVEL)) {
            mVibrateMinThreatLevel = Integer.valueOf(sharedPreferences.getString(VIBRATE_MIN_LEVEL, String.valueOf(Status.MEDIUM.ordinal())));
        }
    }

    public void getOcidKey() {
        final String OCID_KEY = context.getString(R.string.pref_ocid_key);
        OCID_API_KEY = prefs.getString(OCID_KEY, BuildConfig.OPEN_CELLID_API_KEY);
        //if (OCID_API_KEY == null) {
        if (OCID_API_KEY.isEmpty()) {
            OCID_API_KEY = "NA";
        }
    }

    /**
     *  Description:    Updates Neighbouring Cell details
     *
     *                  TODO: add more details...
     *
     *
     */
    public List<Cell> updateNeighbouringCells() {
        List<Cell> neighboringCells = new ArrayList<>();
        List<NeighboringCellInfo> neighboringCellInfo = tm.getNeighboringCellInfo();

        if (neighboringCellInfo == null) {
            neighboringCellInfo = new ArrayList<>();
        }

        Boolean nclp = tinydb.getBoolean("nc_list_present"); // Is NC list present? (default is false)

        //if nclp = true then check for neighboringCellInfo
        //if (neighboringCellInfo != null && neighboringCellInfo.size() == 0 && nclp) {
        //if (!neighboringCellInfo.isEmpty() && neighboringCellInfo.size() == 0 && nclp) {
        if (neighboringCellInfo.size() == 0 && nclp) {

            Log.i(TAG, mTAG + "NeighbouringCellInfo is empty: start polling...");

            // Try to poll the neighboring cells for a few seconds
            neighboringCellBlockingQueue = new LinkedBlockingQueue<>(100); // Max 100 NC's before blocking (?)

            // ToDo: rename... (We don't use API's <18 anymore.)
            DeviceApi18.startListening(tm, phoneStatelistener);

            // ToDo: Move or remove. This should now be done by getAllCellInfo() in new RadioAPI "collector" module.
            for (int i = 0; i < 10 && neighboringCellInfo.size() == 0; i++) {
                try {
                    Log.d(TAG, mTAG + "NeighbouringCellInfo empty: trying " + i);
                    NeighboringCellInfo info = neighboringCellBlockingQueue.poll(1, TimeUnit.SECONDS);
                    if (info == null) {
                        neighboringCellInfo = tm.getNeighboringCellInfo();
                        if (neighboringCellInfo != null) {
                            if (neighboringCellInfo.size() > 0) {
                                // Can we think of a better log message here?
                                Log.d(TAG, mTAG + "NeighbouringCellInfo found on " + i + " try. (time based)");
                                break;
                            } else {
                                continue;
                            }
                        }
                    }
                    List<NeighboringCellInfo> cellInfoList =
                            new ArrayList<>(neighboringCellBlockingQueue.size() + 1);
                    while (info != null) {
                        cellInfoList.add(info);
                        info = neighboringCellBlockingQueue.poll(1, TimeUnit.SECONDS);
                    }
                    neighboringCellInfo = cellInfoList;
                } catch (InterruptedException e) {
                    Log.e(TAG, mTAG + "Interrupted BlockingQueue Exception: " + e);
                }
            }
        }

        //log.debug(mTAG + ": neighbouringCellInfo size: " + neighboringCellInfo.size());

        // Add NC list to DBi_measure:nc_list
        for (NeighboringCellInfo neighbourCell : neighboringCellInfo) {
            Log.i(TAG, mTAG + "NeighbouringCellInfo -" +
                    " LAC:" + neighbourCell.getLac() +
                    " CID:" + neighbourCell.getCid() +
                    " PSC:" + neighbourCell.getPsc() +
                    " RSSI:" + neighbourCell.getRssi());

            final Cell cell = new Cell(
                    neighbourCell.getCid(),
                    neighbourCell.getLac(),
                    neighbourCell.getRssi(),
                    neighbourCell.getPsc(),
                    neighbourCell.getNetworkType(), false);
            neighboringCells.add(cell);
        }
        return neighboringCells;
    }


    /**
     *  Description:    This snippet sets a global variable (SharedPreference) to indicate
     *                  if Neighboring cells info CAN be obtained or has been obtained
     *                  previously. If it has been and suddenly there are none, we can
     *                  raise a flag of CID being suspicious.
     *
     *                  The logic is:
     *
     *                      IF NC has never been seen on device:
     *                          - NC list is NOT supported on this AOS/HW, so we do nothing.
     *                      IF NC has been seen before,
     *                          - NC list IS supported on this AOS/HW, so we set:
     *                              nc_list_present : "true"
     *                      IF NC list has been seen before AND current CID doesn't provide
     *                      one, we raise an alarm or flag.
     *
     *
     *  Notes:      a)  Not sure where to place this test, but let's try it here..
     *              b)  In TinyDB, the getBoolean() returns "false" by default, if empty.
     *
     *              c)   This will be called on every cell change (ref:  issue #346)
     *              d)  *** https://github.com/SecUpwN/Android-IMSI-Catcher-Detector/issues/383
     *
     *  Issue:
     *          [ ] We need a timer or "something" to reverse a positive detection once
     *              we're out and away from the fake BTS cell.
     *          [ ] We need to add this to EventLog
     *          [ ] We need to add detection tickers etc...
     *          [ ] Attention to the spelling of "neighbor" (USA) Vs. "neighbour" (Eng world)
     *          [x] We need to use a global and persistent variable and not a system property
     *
     */
    public void checkForNeighbourCount(CellLocation location) {
        Log.i(TAG, mTAG + "CheckForNeighbourCount()");

        Integer ncls = 0;                                        // NC list size
        if (tm != null && tm.getNeighboringCellInfo() != null) { // See # 383
            ncls = tm.getNeighboringCellInfo().size();
        }
        Boolean nclp = tinydb.getBoolean("nc_list_present");    // NC list present? (default is false)

        if (ncls > 0) {
           Log.d(TAG, mTAG + "NeighbouringCellInfo size: " + ncls);
            if (!nclp) {
               Log.d(TAG, mTAG + "Setting nc_list_present to: true");
                tinydb.putBoolean("nc_list_present", true);
            }
        } else if (ncls == 0 && nclp) {
            // Detection 7a
            Log.i(TAG, mTAG + "ALERT: No neighboring cells detected for CID: " + mDevice.mCell.getCID());
            vibrate(100, Status.MEDIUM);
            dbHelper.toEventLog(4, "No neighboring cells detected"); // (DF_id, DF_desc)
        } else  {
            // Todo: remove cid string when working.
           Log.d(TAG, mTAG + "NC list not supported by AOS on this device. Nothing to do.");
           Log.d(TAG, mTAG + ": Setting nc_list_present to: false");
            tinydb.putBoolean("nc_list_present", false);
        }
    }

    /**
     *          I removed the timer that activated this code and now the code will be run when
     *          the cell changes so it will detect faster rather than using a timer that might
     *          miss an imsi catcher, also says cpu rather than refreshing every x seconds.
     *
     *          original comments below from xLaMbChOpSx
     *
     *
     *  Description:    (From xLaMbChOpSx commit comment)
     *
     *      Initial implementation for detection method 1 to compare the CID & LAC with the Cell
     *      Information Table contents as an initial implementation for detection of a changed LAC,
     *      once OCID issues (API key use etc) have been finalised this detection method can be
     *      extended to include checking of external data.
     *
     *      REMOVED: refresh timer info
     *
     *      As I have no real way of testing this I require testing by other project members who
     *      do have access to equipment or an environment where a changing LAC can be simulated
     *      thus confirming the accuracy of this implementation.
     *
     *      Presently this will only invoke the MEDIUM threat level through the notification and
     *      does not fully implement the capturing and score based method as per the issue details
     *      once further testing is complete the alert and tracking of information can be refined.
     *
     *      See:
     *        https://github.com/xLaMbChOpSx/Android-IMSI-Catcher-Detector/commit/43ae77e2a0cad10dfd50f92da5a998f9ece95b38
     *        https://github.com/SecUpwN/Android-IMSI-Catcher-Detector/issues/91#issuecomment-64391732
     *
     *  Short explanation:
     *
     *                  This is a polling mechanism for getting the LAC/CID and location
     *                  info for the currently connected cell.
     *
     *  Variables:
     *                  FIXED: now updates on cell change rather than a timer
     *                  There is a "timer" here (REFRESH_RATE), what exactly is it timing?
     *                  "Every REFRESH_RATE seconds, get connected cell details."
     *
     *  Notes:
     *              a) Check if CellID (CID) is in DBe_import (OpenCell) database (issue #91)
     *                 See news in: issue #290 and compare to AIMSICDDbAdapter.java
     *
     *  Issues:     [ ] We shouldn't do any detection here!
     *              [ ] We might wanna use a listener to do this?
     *                  Are there any reasons why not using a listener?
     *
     *  ChangeLog:
     *              2015-03-03  E:V:A           Changed getProp() to use TinyDB (SharedPreferences)
     *              2015-0x-xx  banjaxbanjo     Update: ??? (hey dude what did you do?)
     *
     */
    public void compareLac(CellLocation location) {
        switch (mDevice.getPhoneID()) {

            case TelephonyManager.PHONE_TYPE_NONE:
            case TelephonyManager.PHONE_TYPE_SIP:
            case TelephonyManager.PHONE_TYPE_GSM:
                GsmCellLocation gsmCellLocation = (GsmCellLocation) location;
                if (gsmCellLocation != null) {
                    mMonitorCell.setLAC(gsmCellLocation.getLac());
                    mMonitorCell.setCID(gsmCellLocation.getCid());

                    // Check if LAC is ok
                    boolean lacOK = dbHelper.checkLAC(mMonitorCell);
                    if (!lacOK) {
                        mChangedLAC = true;
                        dbHelper.toEventLog(1, "Changing LAC");

                        // Detection Logs are made in checkLAC()
                        vibrate(100, Status.MEDIUM);
                    } else {
                        mChangedLAC = false;
                    }

                    // Check if CID is in DBe_import DB (issue #91)
                    if (tinydb.getBoolean("ocid_downloaded")) {
                        if (!dbHelper.openCellExists(mMonitorCell.getCID())) {
                            dbHelper.toEventLog(2, "CID not in DBe_import");

                            Log.i(TAG, mTAG + "ALERT: Connected to unknown CID not in DBe_import: " + mMonitorCell.getCID());
                            vibrate(100, Status.MEDIUM);

                            mCellIdNotInOpenDb = true;
                        } else {
                            mCellIdNotInOpenDb = false;
                        }
                    }
                }
                break;

            case TelephonyManager.PHONE_TYPE_CDMA:
                CdmaCellLocation cdmaCellLocation = (CdmaCellLocation) location;
                if (cdmaCellLocation != null) {
                    mMonitorCell.setLAC(cdmaCellLocation.getNetworkId());
                    mMonitorCell.setCID(cdmaCellLocation.getBaseStationId());

                    boolean lacOK = dbHelper.checkLAC(mMonitorCell);
                    if (!lacOK) {
                        mChangedLAC = true;
                        /*dbHelper.insertEventLog(
                                MiscUtils.getCurrentTimeStamp(),
                                mMonitorCell.getLAC(),
                                mMonitorCell.getCID(),
                                mMonitorCell.getPSC(),//This is giving weird values like 21478364... is this right?
                                String.valueOf(mMonitorCell.getLat()),
                                String.valueOf(mMonitorCell.getLon()),
                                (int)mMonitorCell.getAccuracy(),
                                1,
                                "Changing LAC"
                        );*/
                        dbHelper.toEventLog(1, "Changing LAC");
                    } else {
                        mChangedLAC = false;
                    }
                }
        }
        setNotification();
    }

    /**
     * Check device's current cell location's LAC against local database AND verify cell's CID
     * exists in the OCID database.
     *
     * @see #compareLac(CellLocation)
     */
    public void compareLacAndOpenDb() {
        compareLac(tm.getCellLocation());
    }

    // Where is this used?
    private void handlePhoneStateChange() {
        List<NeighboringCellInfo> neighboringCellInfo = tm.getNeighboringCellInfo();
        if (neighboringCellInfo == null || neighboringCellInfo.size() == 0) {
            return;
        }

        Log.i(TAG, mTAG + "NeighbouringCellInfo empty - event based polling succeeded!");
        tm.listen(phoneStatelistener, PhoneStateListener.LISTEN_NONE);
        if (neighboringCellInfo == null) {
            neighboringCellInfo = new ArrayList<>();
        }
        neighboringCellBlockingQueue.addAll(neighboringCellInfo);
    }

    public void refreshDevice() {
        mDevice.refreshDeviceInfo(tm, context);
    }

    /**
     * Description:     Process User Preferences
     *                  This loads the default Settings/Preferences as set in:
     *                      preferences.xml
     *                  and:
     *                      /data/data/com.SecUpwN.AIMSICD/shared_prefs/com.SecUpwN.AIMSICD_preferences.xml
     *
     *                  TODO: Please add more info
     *
     */
    private void loadPreferences() {
        // defaults are given by:  getBoolean(key, default if not exist)
        boolean trackFemtoPref  = prefs.getBoolean(context.getString(R.string.pref_femto_detection_key), false);
        boolean trackCellPref   = prefs.getBoolean(context.getString(R.string.pref_enable_cell_key), true);
        boolean monitorCellPref = prefs.getBoolean(context.getString(R.string.pref_enable_cell_monitoring_key), true);

        LAST_DB_BACKUP_VERSION      = prefs.getInt(context.getString(R.string.pref_last_database_backup_version), 1);
        CELL_TABLE_CLEANSED         = prefs.getBoolean(context.getString(R.string.pref_cell_table_cleansed), false);
        String refreshRate = prefs.getString(context.getString(R.string.pref_refresh_key), "1");
        this.mVibrateEnabled = prefs.getBoolean(context.getString(R.string.pref_notification_vibrate_enable), true);
        this.mVibrateMinThreatLevel = Integer.valueOf(prefs.getString(context.getString(R.string.pref_notification_vibrate_min_level), String.valueOf(Status.MEDIUM.ordinal())));

        // Default to Automatic ("1")
        if (refreshRate.isEmpty()) {
            refreshRate = "1";
        }

        int rate = Integer.parseInt(refreshRate);
        long t;
        if (rate == 1) {
            t = 15L; // Automatic refresh rate is 15 seconds
        } else {
            t = ((long) rate); // Default is 1 sec (from above)
        }

        REFRESH_RATE = TimeUnit.SECONDS.toMillis(t);
        getOcidKey();

        if (trackCellPref) {
            setCellTracking(true);
        }
        if (monitorCellPref) {
            setCellMonitoring(true);
        }
    }


    /**
     * Description:  TODO: add more info
     *
     *    This SEEM TO add entries to the "locationinfo" DB table in the ??
     *
     *    Issues:
     *
     *    [ ] We see that "Connection" items are messed up. What is the purpose of these?
     *
     *    $ sqlite3.exe -header -csv aimsicd.db 'select * from locationinfo;'
     *      _id,Lac,CellID,Net,Lat,Lng,Signal,Connection,Timestamp
     *      1,10401,6828111,10, 54.6787,25.2869, 24, "[10401,6828111,126]No|Di|HSPA|", "2015-01-21 20:45:10"
     *
     *    [ ] TODO: CDMA has to extract the MCC and MNC using something like:
     *
     *      String mccMnc = phoneMgr.getNetworkOperator();
     *      String cdmaMcc = "";
     *      String cdmaMnc = "";
     *      if (mccMnc != null && mccMnc.length() >= 5) {
     *          cdmaMcc = mccMnc.substring(0, 3);
     *          cdmaMnc = mccMnc.substring(3, 5);
     }      }
     *
     *    ChangeLog:
     *
     *          2015-01-22  E:V:A   Changed what appears to be a typo in the character
     *                              following getNetworkTypeName(), "|" to "]"
     *          2015-01-24  E:V:A   FC WTF!? Changed back ^ to "|". (Where is this info parsed?)
     *
     */
    private final PhoneStateListener mCellSignalListener = new PhoneStateListener() {
        public void onCellLocationChanged(CellLocation location) {

            checkForNeighbourCount(location);
            compareLac(location);
            refreshDevice();
            mDevice.setNetID(tm);
            mDevice.getNetworkTypeName();

            switch (mDevice.getPhoneID()) {

                case TelephonyManager.PHONE_TYPE_NONE:
                case TelephonyManager.PHONE_TYPE_SIP:
                case TelephonyManager.PHONE_TYPE_GSM:
                    GsmCellLocation gsmCellLocation = (GsmCellLocation) location;
                    if (gsmCellLocation != null) {
                        //TODO @EVA where are we sending this setCellInfo data?

                        //TODO
                        /*@EVA
                            Is it a good idea to dump all cells to db because if we spot a known cell
                            with different lac then this will also be dump to db.

                        */
                        mDevice.setCellInfo(
                                gsmCellLocation.toString() +                // ??
                                mDevice.getDataActivityTypeShort() + "|" +  // No,In,Ou,IO,Do
                                mDevice.getDataStateShort() + "|" +         // Di,Ct,Cd,Su
                                mDevice.getNetworkTypeName() + "|"          // HSPA,LTE etc
                        );

                        mDevice.mCell.setLAC(gsmCellLocation.getLac());     // LAC
                        mDevice.mCell.setCID(gsmCellLocation.getCid());     // CID
                        if (gsmCellLocation.getPsc() != -1) {
                            mDevice.mCell.setPSC(gsmCellLocation.getPsc()); // PSC
                        }

                        /*
                            Add cell if gps is not enabled
                            when gps enabled lat lon will be updated
                            by function below

                         */
                    }
                    break;

                case TelephonyManager.PHONE_TYPE_CDMA:
                    CdmaCellLocation cdmaCellLocation = (CdmaCellLocation) location;
                    if (cdmaCellLocation != null) {
                        mDevice.setCellInfo(
                                cdmaCellLocation.toString() +                       // ??
                                mDevice.getDataActivityTypeShort() + "|" +  // No,In,Ou,IO,Do
                                mDevice.getDataStateShort() + "|" +         // Di,Ct,Cd,Su
                                mDevice.getNetworkTypeName() + "|"          // HSPA,LTE etc
                        );
                        mDevice.mCell.setLAC(cdmaCellLocation.getNetworkId());      // NID
                        mDevice.mCell.setCID(cdmaCellLocation.getBaseStationId());  // BID
                        mDevice.mCell.setSID(cdmaCellLocation.getSystemId());       // SID
                        mDevice.mCell.setMNC(cdmaCellLocation.getSystemId());       // MNC <== BUG!??
                        mDevice.setNetworkName(tm.getNetworkOperatorName());        // ??
                    }
            }

        }

        /**
         *  Description:  TODO: add more info
         *
         *  Issues:
         *
         *      [ ]     Getting and comparing signal strengths between different RATs can be very
         *              tricky, since they all return different ranges of values. AOS doesn't
         *              specify very clearly what exactly is returned, even though people have
         *              a good idea, by trial and error.
         *
         *              See note in : SignalStrengthTracker.java
         *
         *  Notes:
         *
         *
         *
         */
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            // Update Signal Strength
            if (signalStrength.isGsm()) {
                int dbm;
                if (signalStrength.getGsmSignalStrength() <= 2 ||
                        signalStrength.getGsmSignalStrength() == NeighboringCellInfo.UNKNOWN_RSSI) {
                    // Unknown signal strength, get it another way
                    String[] bits = signalStrength.toString().split(" ");
                    dbm = Integer.parseInt(bits[9]);
                } else {
                    dbm = signalStrength.getGsmSignalStrength();
                }
                mDevice.setSignalDbm(dbm);
            } else {
                int evdoDbm = signalStrength.getEvdoDbm();
                int cdmaDbm = signalStrength.getCdmaDbm();

                // Use lowest signal to be conservative
                mDevice.setSignalDbm((cdmaDbm < evdoDbm) ? cdmaDbm : evdoDbm);
            }
            // Send it to signal tracker
            signalStrengthTracker.registerSignalStrength(mDevice.mCell.getCID(), mDevice.getSignalDBm());
            //signalStrengthTracker.isMysterious(mDevice.mCell.getCID(), mDevice.getSignalDBm());
        }

        // In DB:   No,In,Ou,IO,Do
        public void onDataActivity(int direction) {
            switch (direction) {
                case TelephonyManager.DATA_ACTIVITY_NONE:
                    mDevice.setDataActivityTypeShort("No");
                    mDevice.setDataActivityType("None");
                    break;
                case TelephonyManager.DATA_ACTIVITY_IN:
                    mDevice.setDataActivityTypeShort("In");
                    mDevice.setDataActivityType("In");
                    break;
                case TelephonyManager.DATA_ACTIVITY_OUT:
                    mDevice.setDataActivityTypeShort("Ou");
                    mDevice.setDataActivityType("Out");
                    break;
                case TelephonyManager.DATA_ACTIVITY_INOUT:
                    mDevice.setDataActivityTypeShort("IO");
                    mDevice.setDataActivityType("In-Out");
                    break;
                case TelephonyManager.DATA_ACTIVITY_DORMANT:
                    mDevice.setDataActivityTypeShort("Do");
                    mDevice.setDataActivityType("Dormant");
                    break;
            }
        }

        // In DB:   Di,Ct,Cd,Su
        public void onDataConnectionStateChanged(int state) {
            switch (state) {
                case TelephonyManager.DATA_DISCONNECTED:
                    mDevice.setDataState("Disconnected");
                    mDevice.setDataStateShort("Di");
                    break;
                case TelephonyManager.DATA_CONNECTING:
                    mDevice.setDataState("Connecting");
                    mDevice.setDataStateShort("Ct");
                    break;
                case TelephonyManager.DATA_CONNECTED:
                    mDevice.setDataState("Connected");
                    mDevice.setDataStateShort("Cd");
                    break;
                case TelephonyManager.DATA_SUSPENDED:
                    mDevice.setDataState("Suspended");
                    mDevice.setDataStateShort("Su");
                    break;
            }
        }

    };

    /**
     * Description:    Add entries to the "DBi_measure" DB table
     *
     * Issues:
     *                  [ ]
     *
     * Notes:           (a)
     *
     *
     * TODO:  Remove OLD notes below, once we have new ones relevant to our new table
     *
     *  From "locationinfo":
     *
     *      $ sqlite3.exe -header aimsicd.db 'select * from locationinfo;'
     *      _id|Lac|CellID|Net|Lat|Lng|Signal|Connection|Timestamp
     *      1|10401|6828xxx|10|54.67874392|25.28693531|24|[10401,6828320,126]No|Di|HSPA||2015-01-21 20:45:10
     *
     *  From "cellinfo":
     *
     *      $ sqlite3.exe -header aimsicd.db 'select * from cellinfo;'
     *      _id|Lac|CellID|Net|Lat|Lng|Signal|Mcc|Mnc|Accuracy|Speed|Direction|NetworkType|MeasurementTaken|OCID_SUBMITTED|Timestamp
     *      1|10401|6828xxx|10|54.67874392|25.28693531|24|246|2|69.0|0.0|0.0|HSPA|82964|0|2015-01-21 20:45:10
     *
     *  Issues:
     *
     */
    public void onLocationChanged(Location loc) {

        DeviceApi18.loadCellInfo(tm, mDevice);

        if (!mDevice.mCell.isValid()) {
            CellLocation cellLocation = tm.getCellLocation();
            if (cellLocation != null) {
                switch (mDevice.getPhoneID()) {

                    case TelephonyManager.PHONE_TYPE_NONE:
                    case TelephonyManager.PHONE_TYPE_SIP:
                    case TelephonyManager.PHONE_TYPE_GSM:
                        GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
                        mDevice.mCell.setCID(gsmCellLocation.getCid()); // CID
                        mDevice.mCell.setLAC(gsmCellLocation.getLac()); // LAC
                        mDevice.mCell.setPSC(gsmCellLocation.getPsc()); // PSC
                        break;

                    case TelephonyManager.PHONE_TYPE_CDMA:
                        CdmaCellLocation cdmaCellLocation = (CdmaCellLocation) cellLocation;
                        mDevice.mCell.setCID(cdmaCellLocation.getBaseStationId()); // BSID ??
                        mDevice.mCell.setLAC(cdmaCellLocation.getNetworkId());     // NID
                        mDevice.mCell.setSID(cdmaCellLocation.getSystemId());      // SID
                        mDevice.mCell.setMNC(cdmaCellLocation.getSystemId());      // MNC <== BUG!??

                        break;
                }
            }
        }

        if (loc != null &&  (Double.doubleToRawLongBits(loc.getLatitude()) != 0  &&  Double.doubleToRawLongBits(loc.getLongitude()) != 0)) {

            mDevice.mCell.setLon(loc.getLongitude());       // gpsd_lon
            mDevice.mCell.setLat(loc.getLatitude());        // gpsd_lat
            mDevice.mCell.setSpeed(loc.getSpeed());         // speed        // TODO: Remove, we're not using it!
            mDevice.mCell.setAccuracy(loc.getAccuracy());   // gpsd_accu
            mDevice.mCell.setBearing(loc.getBearing());     // -- [deg]??   // TODO: Remove, we're not using it!
            mDevice.setLastLocation(loc);                   //

            // Store last known location in preference
            SharedPreferences.Editor prefsEditor;
            prefsEditor = prefs.edit();
            prefsEditor.putString(context.getString(R.string.data_last_lat_lon),
                    String.valueOf(loc.getLatitude()) + ":" + String.valueOf(loc.getLongitude()));
            prefsEditor.apply();

            // This only logs a BTS if we have GPS lock
            // Test: ~~Is correct behaviour? We should consider logging all cells, even without GPS.~~
            //if (mTrackingCell) {
                // This also checks that the lac are cid are not in DB before inserting
                dbHelper.insertBTS(mDevice.mCell);
            //}
        }
    }

    /**
     * Cancel and remove the persistent notification
     */
    public void cancelNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
    }

    /**
     *  Description:    Set or update the Detection/Status Notification
     *                  TODO: Need to add status HIGH (Orange) and SKULL (Black)
     *
     *  Issues:
     *      See:  https://github.com/SecUpwN/Android-IMSI-Catcher-Detector/wiki/Status-Icons
     *      and:  https://github.com/SecUpwN/Android-IMSI-Catcher-Detector/issues/11#issuecomment-44670204
     *
     *  [ ] We need to standardize the "contentText" and "tickerText" format
     *
     *  [ ] From #91: https://github.com/SecUpwN/Android-IMSI-Catcher-Detector/issues/91
     *
     *      Problem:
     *          Having multiple notifications will cause an issue with
     *      notifications themselves AND tickerText.  It seems that the
     *      most recent notification raised would overwrite any previous,
     *      notification or tickerText.  This results in loss of information
     *      for any notification before the last one.
     *
     *      Possible Solution:
     *          Perhaps arranging a queue implementation to deal with text
     *      being passed into tickerText only when any previous text has
     *      been entirely displayed.
     *
     *  Dependencies:    Status.java, CellTracker.java, Icon.java ( + others?)
     *
     */
    private void setNotification() {
        String tickerText;
        String contentText = "Phone Type " + mDevice.getPhoneType();

        if (mTypeZeroSmsDetected) {
            getApplication().setCurrentStatus(Status.DANGER, mVibrateEnabled, mVibrateMinThreatLevel);
        } else if (mChangedLAC) {
            getApplication().setCurrentStatus(Status.MEDIUM, mVibrateEnabled, mVibrateMinThreatLevel);
            contentText = context.getString(R.string.hostile_service_area_changing_lac_detected);
        } else if (mCellIdNotInOpenDb) {
            getApplication().setCurrentStatus(Status.MEDIUM, mVibrateEnabled, mVibrateMinThreatLevel);
            contentText = context.getString(R.string.cell_id_doesnt_exist_in_db);
        } else if (mTrackingCell || mMonitoringCell) {
            getApplication().setCurrentStatus(Status.OK, mVibrateEnabled, mVibrateMinThreatLevel);
            if (mTrackingCell) {
                contentText = context.getString(R.string.cell_tracking_active);
            } 
            if (mMonitoringCell) {
                contentText = context.getString(R.string.cell_monitoring_active);
            } else {
                getApplication().setCurrentStatus(Status.IDLE, mVibrateEnabled, mVibrateMinThreatLevel);
            }
        } else {
            getApplication().setCurrentStatus(Status.IDLE, mVibrateEnabled, mVibrateMinThreatLevel);
        }


            Status status = getApplication().getStatus();
            switch (status) {
                case IDLE: // GRAY
                    contentText = context.getString(R.string.phone_type) + mDevice.getPhoneType();
                    tickerText = context.getResources().getString(R.string.app_name_short) + " " + context.getString(R.string.status_idle_description);
                    break;

                case OK: // GREEN
                    tickerText = context.getResources().getString(R.string.app_name_short) + " " + context.getString(R.string.status_ok_description);
                    break;

                case MEDIUM: // YELLOW
                    // Initialize tickerText as the app name string
                    // See multiple detection comments above.
                    tickerText = context.getResources().getString(R.string.app_name_short);
                    if (mChangedLAC) {
                        //Append changing LAC text
                        contentText = context.getString(R.string.hostile_service_area_changing_lac_detected);
                        tickerText += " - " + contentText;
                        // See #264 and ask He3556
                        //} else if (mNoNCList)  {
                        //    tickerText += " - BTS doesn't provide any neighbors!";
                        //    contentText = "CID: " + cellid + " is not providing a neighboring cell list!";

                    } else if (mCellIdNotInOpenDb) {
                        //Append Cell ID not existing in external db text
                        contentText = context.getString(R.string.cell_id_doesnt_exist_in_db);
                        tickerText += " - " + contentText;
                    }
                    break;

                case DANGER: // RED
                    tickerText = context.getResources().getString(R.string.app_name_short) + " - " + context.getString(R.string.alert_threat_detected); // Hmm, this is vague!
                    if (mTypeZeroSmsDetected) {
                        contentText = context.getString(R.string.alert_silent_sms_detected);
                    }

                    break;
                default:
                    tickerText = context.getResources().getString(R.string.main_app_name);
                    break;
            }

            // TODO: Explanation (see above)
            Intent notificationIntent = new Intent(context, MainActivity.class);
            notificationIntent.putExtra("silent_sms", mTypeZeroSmsDetected);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_FROM_BACKGROUND);

            PendingIntent contentIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

            String iconType = prefs.getString(context.getString(R.string.pref_ui_icons_key), "SENSE").toUpperCase(Locale.US);
            int iconResId = Icon.getIcon(Icon.Type.valueOf(iconType), status);
            Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), iconResId);

            //int color = context.getResources().getColor(status.getColor());
            int color = ContextCompat.getColor(context, status.getColor());

            Notification mBuilder = new NotificationCompat.Builder(context)
                    .setSmallIcon(R.drawable.tower48)
                    .setColor(color)
                    .setLargeIcon(largeIcon)
                    .setTicker(tickerText)
                    .setContentTitle(context.getString(R.string.status) + " " + context.getString(status.getName()))
                    .setContentInfo(context.getResources().getString(R.string.app_name_short))
                    .setContentText(contentText)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setContentIntent(contentIntent)
                    .build();

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, mBuilder);

    }

    private AppAIMSICD getApplication() {
        return AppAIMSICD.getInstance();
    }

    /**
     * Vibrator helper method, will check current preferences (vibrator enabled, min threat level to vibrate)
     * and act appropriately
     */
    private void vibrate(int msec, Status threatLevel) {
        if (mVibrateEnabled && (threatLevel == null || threatLevel.ordinal() >= mVibrateMinThreatLevel)) {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(msec);
        }
    }

    //=================================================================================================
    // See issues: #6, #457, #489
    //
    // Summary:     We can detect femtocells by other means, using network data that we already have!
    //              The below code section was copied and modified with permission from
    //              Femtocatcher at:  https://github.com/iSECPartners/femtocatcher
    //
    //=================================================================================================

    // Removed!

    //=================================================================================================
    // END Femtocatcher code
    //=================================================================================================

    final PhoneStateListener phoneStatelistener = new PhoneStateListener() {
        private void handle() {
            handlePhoneStateChange();
        }
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            handle();
        }
        @Override
        public void onDataConnectionStateChanged(int state) {
            handle();
        }
        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            handle();
        }
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            handle();
        }
        @Override
        public void onCellInfoChanged(List<CellInfo> cellInfo) {
            handle();
        }

        @Override
        public void onCellLocationChanged(CellLocation location) {
            handle();
        }

    };

    /**
     * Used in SmsDetector.java
     */
    public Cell getMonitorCell() {
        return mMonitorCell;
    }


}
