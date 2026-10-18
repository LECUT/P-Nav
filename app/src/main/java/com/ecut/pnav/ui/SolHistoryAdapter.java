package com.ecut.pnav.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ecut.pnav.R;

import java.util.ArrayList;
import java.util.List;

/** 解算历史表格适配器 — 逐行6列显示历元结果 */
public class SolHistoryAdapter extends RecyclerView.Adapter<SolHistoryAdapter.VH> {

    private final List<String> lines = new ArrayList<>();

    private static final int COLOR_SPP     = Color.parseColor("#E65100");
    private static final int COLOR_DGNSS   = Color.parseColor("#1565C0");
    private static final int COLOR_RTK_FIX = Color.parseColor("#2E7D32");
    private static final int COLOR_RTK_FLT = Color.parseColor("#00838F");
    private static final int COLOR_PPP_FIX = Color.parseColor("#6A1B9A");
    private static final int COLOR_PPP_FLT = Color.parseColor("#283593");
    private static final int COLOR_DEFAULT = Color.parseColor("#616161");

    public void addLine(String line) {
        if (line == null || line.isEmpty()) return;
        lines.add(line);
        notifyItemInserted(lines.size() - 1);
    }

    public void clear() {
        int size = lines.size();
        lines.clear();
        if (size > 0) notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sol_history, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String line = lines.get(position);
        // 格式: #N HH:MM:SS Mode lat° lon° height
        String[] parts = line.split(" ");
        if (parts.length < 6) {
            holder.epoch.setText("");
            holder.time.setText(line);
            holder.mode.setText("");
            holder.lat.setText("");
            holder.lon.setText("");
            holder.height.setText("");
            return;
        }
        holder.epoch.setText(parts[0]);
        holder.time.setText(parts[1]);
        String mode = parts[2];
        holder.mode.setText(mode);
        holder.lat.setText(parts[3]);
        holder.lon.setText(parts[4]);
        holder.height.setText(parts[5]);

        int modeColor;
        switch (mode) {
            case "SPP":       modeColor = COLOR_SPP; break;
            case "DGNSS":     modeColor = COLOR_DGNSS; break;
            case "RTK_Fix":   modeColor = COLOR_RTK_FIX; break;
            case "RTK_Float": modeColor = COLOR_RTK_FLT; break;
            case "PPP_Fix":   modeColor = COLOR_PPP_FIX; break;
            case "PPP_Float": modeColor = COLOR_PPP_FLT; break;
            default:          modeColor = COLOR_DEFAULT; break;
        }
        holder.mode.setTextColor(modeColor);

        // 交替行背景
        holder.itemView.setBackgroundColor(position % 2 == 0
                ? Color.TRANSPARENT
                : Color.argb(20, 128, 128, 128));
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView epoch, time, mode, lat, lon, height;
        VH(@NonNull android.view.View itemView) {
            super(itemView);
            epoch  = itemView.findViewById(R.id.solh_epoch);
            time   = itemView.findViewById(R.id.solh_time);
            mode   = itemView.findViewById(R.id.solh_mode);
            lat    = itemView.findViewById(R.id.solh_lat);
            lon    = itemView.findViewById(R.id.solh_lon);
            height = itemView.findViewById(R.id.solh_height);
        }
    }
}
