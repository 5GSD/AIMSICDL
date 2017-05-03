/* Android IMSI-Catcher Detector | (c) AIMSICD Privacy Project
 * -----------------------------------------------------------
 * LICENSE:  http://git.io/vki47 | TERMS:  http://git.io/vki4o
 * -----------------------------------------------------------
 */
package zz.aimsicd.lite.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import zz.aimsicd.lite.R;

import static java.lang.String.valueOf;

/**
 * Inflater class used in DB viewer (for Measured cell strength measurements)
 *
 * Template:    SilentSmsCardInflater.java
 *
 */
public class MeasuredCellStrengthCardInflater implements IAdapterViewInflater<MeasuredCellStrengthCardData> {

    @Override
    public View inflate(final BaseInflaterAdapter<MeasuredCellStrengthCardData> adapter, final int pos,
                        View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            convertView = inflater.inflate(R.layout.measured_signal_str, parent, false);
            holder = new ViewHolder(convertView);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final MeasuredCellStrengthCardData item = adapter.getTItem(pos);
        holder.updateDisplay(item);

        return convertView;
    }

    private class ViewHolder {

        private final View mRootView;

        private final TextView cid;
        private final TextView rss;
        private final TextView time;

        ViewHolder(View rootView) {
            mRootView = rootView;

            cid =   (TextView) mRootView.findViewById(R.id.tv_measure_cid);
            rss =   (TextView) mRootView.findViewById(R.id.tv_measure_rss);
            time =  (TextView) mRootView.findViewById(R.id.tv_measure_time);

            rootView.setTag(this);
        }

        public void updateDisplay(MeasuredCellStrengthCardData item) {
            cid.setText(valueOf(item.getCellID()));
            rss.setText(valueOf(item.getSignal()));
            time.setText(item.getTimestamp());
        }
    }
}
