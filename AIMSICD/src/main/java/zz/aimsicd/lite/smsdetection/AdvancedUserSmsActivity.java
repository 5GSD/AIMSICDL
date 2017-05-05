/* Android IMSI-Catcher Detector | (c) AIMSICD Privacy Project
 * -----------------------------------------------------------
 * LICENSE:  http://git.io/vki47 | TERMS:  http://git.io/vki4o
 * -----------------------------------------------------------
 */

/* Coded by Paul Kinsella <paulkinsella29@yahoo.ie> */

package zz.aimsicd.lite.smsdetection;

import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.freefair.android.injection.annotation.InjectView;
import io.freefair.android.injection.annotation.XmlLayout;
import io.freefair.android.injection.app.InjectionAppCompatActivity;
import zz.aimsicd.lite.R;
import zz.aimsicd.lite.adapters.AIMSICDDbAdapter;
import zz.aimsicd.lite.constants.DBTableColumnIds;



@XmlLayout(R.layout.activity_advanced_sms_user)
public class AdvancedUserSmsActivity extends InjectionAppCompatActivity {

    public static final String TAG = "AICDL";
    public static final String mTAG = "AdvancedUserSmsActivity";


    @InjectView(R.id.listView_Adv_Sms_Activity)
    ListView listViewAdv;

    AIMSICDDbAdapter dbaccess;
    List<CapturedSmsData> msgitems;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dbaccess = new AIMSICDDbAdapter(getApplicationContext());
        msgitems = new ArrayList<>();

        try {
            Cursor smscur = dbaccess.returnSmsData();
            if (smscur.getCount() > 0) {
                while (smscur.moveToNext()) {
                    CapturedSmsData getdata = new CapturedSmsData();
                    getdata.setId(smscur.getLong(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_ID)));
                    getdata.setSmsTimestamp(smscur.getString(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_TIMESTAMP)));
                    getdata.setSmsType(smscur.getString(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_SMS_TYPE)));
                    getdata.setSenderNumber(smscur.getString(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_SENDER_NUMBER)));
                    getdata.setSenderMsg(smscur.getString(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_SENDER_MSG)));
                    getdata.setCurrent_lac(smscur.getInt(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_LAC)));
                    getdata.setCurrent_cid(smscur.getInt(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_CID)));
                    getdata.setCurrent_nettype(smscur.getString(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_RAT)));
                    getdata.setCurrent_roam_status(smscur.getInt(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_ROAM_STATE)));
                    getdata.setCurrent_gps_lat(smscur.getDouble(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_GPS_LAT)));
                    getdata.setCurrent_gps_lon(smscur.getDouble(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_GPS_LON)));
                    msgitems.add(getdata);
                }
            }
            smscur.close();

        } catch (Exception ee) {
           Log.e(TAG, mTAG + "DB ERROR", ee);
        }



        listViewAdv.setAdapter(new AdvanceUserBaseSmsAdapter(getApplicationContext(), msgitems));

        listViewAdv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> a, View v, int position, long id) {
                Object o = listViewAdv.getItemAtPosition(position);
                CapturedSmsData obj_itemDetails = (CapturedSmsData) o;

                if (dbaccess.deleteDetectedSms(obj_itemDetails.getId())) {
                    Toast.makeText(getApplicationContext(), "Deleted Sms Id = \n" + obj_itemDetails.getId(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Failed to Delete", Toast.LENGTH_SHORT).show();
                }

                try {
                    loadDbString();
                } catch (Exception ee) {
                   Log.d(TAG, mTAG + "", ee);
                }
                return false;
            }

        });

    }
    public void loadDbString() {
        List<CapturedSmsData> newmsglist = new ArrayList<>();

        try {
            Cursor smscur = dbaccess.returnSmsData();

            if (smscur.getCount() > 0) {
                while (smscur.moveToNext()) {
                    CapturedSmsData getdata = new CapturedSmsData();
                    getdata.setId(smscur.getLong(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_ID)));
                    getdata.setSmsTimestamp(smscur.getString(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_TIMESTAMP)));
                    getdata.setSmsType(smscur.getString(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_SMS_TYPE)));
                    getdata.setSenderNumber(smscur.getString(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_SENDER_NUMBER)));
                    getdata.setSenderMsg(smscur.getString(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_SENDER_MSG)));
                    getdata.setCurrent_lac(smscur.getInt(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_LAC)));
                    getdata.setCurrent_cid(smscur.getInt(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_CID)));
                    getdata.setCurrent_nettype(smscur.getString(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_RAT)));
                    getdata.setCurrent_roam_status(smscur.getInt(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_ROAM_STATE)));
                    getdata.setCurrent_gps_lat(smscur.getDouble(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_GPS_LAT)));
                    getdata.setCurrent_gps_lon(smscur.getDouble(smscur.getColumnIndex(DBTableColumnIds.SMS_DATA_GPS_LON)));
                    newmsglist.add(getdata);
                }
            }

            smscur.close();
            listViewAdv.setAdapter(new AdvanceUserBaseSmsAdapter(getApplicationContext(), newmsglist));
        } catch (Exception ee) {
           Log.e(TAG, mTAG + "DB ERROR", ee);
        }


    }
}
