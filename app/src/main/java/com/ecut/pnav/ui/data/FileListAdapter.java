package com.ecut.pnav.ui.data;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.ecut.pnav.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.ViewHolder> {

    private List<File> files;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final OnFileActionListener listener;

    public interface OnFileActionListener {
        void onFileDeleted();
    }

    public FileListAdapter(List<File> files, OnFileActionListener listener) {
        this.files = files;
        this.listener = listener;
    }

    public void updateFiles(List<File> newFiles) {
        this.files = newFiles;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(
                LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = files.get(position);
        String name = file.getName();

        holder.tvFileName.setText(name);

        // 文件类型判断
        boolean isRinex = name.startsWith("PNAV");
        String typeLabel = isRinex ? "RINEX 3" : "CSV";
        int iconTint = isRinex ? R.color.gps_stats : R.color.galileo_stats;
        holder.ivFileIcon.setColorFilter(ContextCompat.getColor(
                holder.itemView.getContext(), iconTint));

        String fSize;
        if (file.length() < 1024) fSize = file.length() + " B";
        else if (file.length() < 1024 * 1024) fSize = String.format("%.1f KB", file.length() / 1024.0);
        else fSize = String.format("%.1f MB", file.length() / (1024.0 * 1024.0));
        holder.tvFileInfo.setText(typeLabel + "  ·  " + sdf.format(new Date(file.lastModified())) + "  ·  " + fSize);

        holder.btnShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM,
                    FileProvider.getUriForFile(v.getContext(),
                            v.getContext().getPackageName() + ".fileprovider", file));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            v.getContext().startActivity(Intent.createChooser(shareIntent,
                    v.getContext().getString(R.string.select_file_manager)));
        });

        holder.btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(v.getContext())
                    .setTitle(R.string.file_delete_confirm)
                    .setMessage(file.getName())
                    .setPositiveButton(R.string.yes, (d, w) -> {
                        file.delete();
                        Toast.makeText(v.getContext(), R.string.file_deleted, Toast.LENGTH_SHORT).show();
                        listener.onFileDeleted();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    @Override
    public int getItemCount() {
        return files != null ? files.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivFileIcon;
        final TextView tvFileName;
        final TextView tvFileInfo;
        final ImageButton btnShare;
        final ImageButton btnDelete;

        ViewHolder(@NonNull android.view.View itemView) {
            super(itemView);
            ivFileIcon = itemView.findViewById(R.id.iv_file_icon);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileInfo = itemView.findViewById(R.id.tv_file_info);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
