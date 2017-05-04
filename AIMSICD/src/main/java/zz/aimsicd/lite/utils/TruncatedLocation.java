package zz.aimsicd.lite.utils;

import android.location.Location;
import android.util.Log;

import java.text.NumberFormat;
import java.text.ParseException;

/**
 *  Description:
 *      Used to truncate the Lat/Lon coordinates to 5 decimals when shown in DB tables viewer
 *
 * Note:
 *      5 decimals are more than sufficient for most purposes, as its a matter of 10's of meters on normal latitudes.
 */
public class TruncatedLocation extends Location {

    public static final String TAG = "AICDL";
    public static final String mTAG = "TruncatedLocation: ";

    public TruncatedLocation(Location l) {
        super(l);
    }

    @Override
    public double getLatitude() {
        return truncateDouble(super.getLatitude(), 5);
    }

    @Override
    public double getLongitude() {
        return truncateDouble(super.getLongitude(), 5);
    }

    public static double truncateDouble(String d, int numDecimal) {
        return  truncateDouble(Double.parseDouble(d), numDecimal);
    }

    public static double truncateDouble(double d, int numDecimal) {
        double td = 0;
        NumberFormat format = NumberFormat.getInstance();

        // %.<string>f, d <-- this is wrong on so many ways
        String s = String.format("%." + Integer.toString(numDecimal) + "f", d);
        try {
            Number number = format.parse(s);
            td = number.doubleValue();
        } catch (ParseException e) {
           Log.e(TAG, mTAG + "parsing exception", e);
        }
        return td;
    }
}
