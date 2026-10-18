package com.ecut.pnav.ui.data;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ecut.pnav.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * RecyclerView adapter for the live satellite measurement list.
 * Sorts satellites by constellation (GPS → BDS → GLO → GAL → QZSS),
 * then by PRN within each group.
 */
public class LiveSatAdapter extends RecyclerView.Adapter<LiveSatAdapter.ViewHolder> {

    private List<SatMeasurement> measurements = new ArrayList<>();
    private static final float CN0_MAX = 50f; // 50 dB-Hz = full bar

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sat_measurement, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SatMeasurement m = measurements.get(position);
        holder.bind(m);
    }

    @Override
    public int getItemCount() {
        return measurements.size();
    }

    /**
     * Update the list with fresh measurements. Sorted by constellation group.
     */
    public void updateMeasurements(List<SatMeasurement> newList) {
        measurements.clear();
        if (newList != null) {
            measurements.addAll(newList);
            Collections.sort(measurements, COMPARATOR);
        }
        notifyDataSetChanged();
    }

    /**
     * Comparator: GPS first, then BDS, GLO, GAL, QZSS, others.
     * Within same constellation, sort by PRN numerically.
     */
    private static final Comparator<SatMeasurement> COMPARATOR = (a, b) -> {
        int ca = constellationOrder(a.constellation);
        int cb = constellationOrder(b.constellation);
        if (ca != cb) return Integer.compare(ca, cb);
        // Sort by PRN number (strip leading letter)
        String pA = a.prn.substring(1);
        String pB = b.prn.substring(1);
        try {
            return Integer.compare(Integer.parseInt(pA), Integer.parseInt(pB));
        } catch (NumberFormatException e) {
            return a.prn.compareTo(b.prn);
        }
    };

    private static int constellationOrder(int constellation) {
        switch (constellation) {
            case 1: return 0; // GPS
            case 5: return 1; // BDS
            case 3: return 2; // GLONASS
            case 6: return 3; // GALILEO
            case 4: return 4; // QZSS
            default: return 5;
        }
    }

    /**
     * Resolve constellation color from GnssStatus constant.
     */
    private static int constellationColor(int constellation) {
        switch (constellation) {
            case 1:  return Color.parseColor("#007AFF"); // GPS blue
            case 5:  return Color.parseColor("#FF3B30"); // BDS red
            case 3:  return Color.parseColor("#AF52DE"); // GLONASS purple
            case 6:  return Color.parseColor("#34C759"); // GALILEO green
            case 4:  return Color.parseColor("#FF9500"); // QZSS orange
            case 2:  return Color.parseColor("#8E8E93"); // SBAS gray
            default: return Color.parseColor("#8E8E93"); // Unknown gray
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvPrn, tvCn0;
        final TextView tvElevation, tvCarrier;
        final View cn0Bar;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPrn = itemView.findViewById(R.id.tv_prn);
            tvCn0 = itemView.findViewById(R.id.tv_cn0);
            tvElevation = itemView.findViewById(R.id.tv_elevation);
            tvCarrier = itemView.findViewById(R.id.tv_carrier);
            cn0Bar = itemView.findViewById(R.id.cn0_bar);
        }

        void bind(SatMeasurement m) {
            // PRN badge color
            int color = constellationColor(m.constellation);
            GradientDrawable bg = (GradientDrawable) tvPrn.getBackground().mutate();
            bg.setColor(color);
            tvPrn.setText(m.prn);

            // CN0 value
            tvCn0.setText(String.valueOf(Math.round(m.cn0DbHz)));

            // CN0 bar width (weight-based inside horizontal LinearLayout)
            float fraction = Math.min(m.cn0DbHz / CN0_MAX, 1.0f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, cn0Bar.getLayoutParams().height);
            lp.weight = fraction;
            cn0Bar.setLayoutParams(lp);

            // Elevation — green for high angle (≥30°), orange for low, gray for unknown
            if (m.elevationDeg >= 0) {
                tvElevation.setText(String.format("%.0f°", m.elevationDeg));
                tvElevation.setTextColor(m.elevationDeg >= 30
                        ? Color.parseColor("#34C759")
                        : Color.parseColor("#FF9500"));
            } else {
                tvElevation.setText("--");
                tvElevation.setTextColor(Color.parseColor("#8E8E93"));
            }

            // Carrier phase indicator
            if (m.hasCarrier) {
                tvCarrier.setVisibility(View.VISIBLE);
            } else {
                tvCarrier.setVisibility(View.GONE);
            }
        }
    }
}
