package com.ecut.pnav.ui.data;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.ecut.pnav.R;
import com.ecut.pnav.util.ExportUtil;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * 导出格式选择弹窗 — 用户在 KML/GPX/CSV 中选择，异步生成文件后通过系统分享
 */
public class ExportDialogUtil {

    private static final String[] FORMATS = {"KML", "GPX", "CSV"};

    public static void showExportDialog(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.export_dialog_title)
                .setItems(FORMATS, (dialog, which) -> {
                    String fmt = FORMATS[which];
                    dialog.dismiss();
                    exportAsync(activity, fmt);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void exportAsync(Activity activity, String format) {
        Toast.makeText(activity, R.string.export_generating, Toast.LENGTH_SHORT).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            List<ExportUtil.ExportRecord> records = ExportUtil.fetchAllRecords(activity);
            if (records.isEmpty()) {
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, R.string.export_no_data, Toast.LENGTH_SHORT).show());
                return;
            }

            String content;
            String filename;
            switch (format) {
                case "KML":
                    content = ExportUtil.generateKml(records);
                    filename = "pnav_track_" + com.ecut.pnav.util.StorageUtil.getTimestamp() + ".kml";
                    break;
                case "GPX":
                    content = ExportUtil.generateGpx(records);
                    filename = "pnav_track_" + com.ecut.pnav.util.StorageUtil.getTimestamp() + ".gpx";
                    break;
                case "CSV":
                    content = ExportUtil.generateCsv(records);
                    filename = "pnav_data_" + com.ecut.pnav.util.StorageUtil.getTimestamp() + ".csv";
                    break;
                default:
                    return;
            }

            File file = ExportUtil.writeToTempFile(activity, content, filename);
            if (file == null) {
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, R.string.export_failed, Toast.LENGTH_SHORT).show());
                return;
            }

            activity.runOnUiThread(() -> shareFile(activity, file));
        });
    }

    private static void shareFile(Activity activity, File file) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        String ext = file.getName().substring(file.getName().lastIndexOf('.') + 1).toLowerCase();
        switch (ext) {
            case "kml": shareIntent.setType("application/vnd.google-earth.kml+xml"); break;
            case "gpx": shareIntent.setType("application/gpx+xml"); break;
            default:    shareIntent.setType("text/csv"); break;
        }
        shareIntent.putExtra(Intent.EXTRA_STREAM,
                FileProvider.getUriForFile(activity,
                        activity.getPackageName() + ".fileprovider", file));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(shareIntent,
                activity.getString(R.string.select_file_manager)));
    }
}
