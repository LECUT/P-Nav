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

/** 解算日志行适配器 — 按行前缀着色，支持过滤、清空、导出 */
public class LogLineAdapter extends RecyclerView.Adapter<LogLineAdapter.VH> {

    private final List<String> lines = new ArrayList<>();
    private boolean autoScroll = true;

    private static final int COLOR_OK = Color.parseColor("#2E7D32");
    private static final int COLOR_FAIL = Color.parseColor("#C62828");
    private static final int COLOR_SKIP = Color.parseColor("#E65100");
    private static final int COLOR_SYS = Color.parseColor("#757575");
    private static final int COLOR_DEFAULT = Color.parseColor("#616161");

    /** 追加新行（始终显示全部，无过滤） */
    public void addLine(String line) {
        if (line.isEmpty()) return;
        lines.add(line);
        notifyItemInserted(lines.size() - 1);
    }

    public int getLineCount() { return lines.size(); }
    public boolean isAutoScroll() { return autoScroll; }
    public void setAutoScroll(boolean v) { this.autoScroll = v; }

    /** 重置所有数据 */
    public void clear() {
        int size = lines.size();
        lines.clear();
        if (size > 0) notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_line, parent, false);
        return new VH(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String line = lines.get(position);
        holder.textView.setText(line);

        if (line.contains("CRASH") || line.contains("FAIL"))
            holder.textView.setTextColor(COLOR_FAIL);
        else if (line.contains("SKIP"))
            holder.textView.setTextColor(COLOR_SKIP);
        else if (line.contains("OK") && !line.contains("BOOK"))
            holder.textView.setTextColor(COLOR_OK);
        else if (line.contains("SESSION") || line.contains("Start") || line.contains("Eph")
                || line.contains("CORS") || line.contains("NRT") || line.contains("Star")
                || line.contains("星历") || line.contains("══════"))
            holder.textView.setTextColor(COLOR_SYS);
        else
            holder.textView.setTextColor(COLOR_DEFAULT);
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView textView;
        VH(@NonNull TextView itemView) {
            super(itemView);
            textView = itemView;
        }
    }
}
