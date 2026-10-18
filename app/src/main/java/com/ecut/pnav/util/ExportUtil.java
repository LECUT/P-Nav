package com.ecut.pnav.util;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.ecut.pnav.positiondatabase;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 定位结果导出工具 — 从数据库读取记录，生成 KML/GPX/CSV 文件
 */
public class ExportUtil {

    private static final String TAG = "ExportUtil";

    /**
     * 定位记录数据类
     */
    public static class ExportRecord {
        public final String time;
        public final double lat;
        public final double lon;
        public final double altitude;
        public final String qr;
        public final int state;

        public ExportRecord(String time, double lat, double lon, double altitude, String qr, int state) {
            this.time = time;
            this.lat = lat;
            this.lon = lon;
            this.altitude = altitude;
            this.qr = qr;
            this.state = state;
        }

        public String stateLabel() {
            switch (state) {
                case 1: return "RTK Fix";
                case 2: return "RTK Float";
                case 4: return "DGNSS";
                case 5: return "SPP";
                case 0: return "Fail";
                default: return "Unknown";
            }
        }
    }

    /**
     * 从数据库读取全部定位记录
     */
    public static List<ExportRecord> fetchAllRecords(Context context) {
        List<ExportRecord> list = new ArrayList<>();
        positiondatabase dbHelper = new positiondatabase(context);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = dbHelper.getReadableDatabase();
            cursor = db.query(positiondatabase.TABLE_NAME, null, null, null, null, null,
                    positiondatabase.COLUMN_ID + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                int idxTime = cursor.getColumnIndex(positiondatabase.COLUMN_TIME);
                int idxLat  = cursor.getColumnIndex(positiondatabase.COLUMN_LAT);
                int idxLon  = cursor.getColumnIndex(positiondatabase.COLUMN_LON);
                int idxAlt  = cursor.getColumnIndex(positiondatabase.COLUMN_ALTITUDE);
                int idxQr   = cursor.getColumnIndex(positiondatabase.COLUMN_QR);
                int idxState= cursor.getColumnIndex(positiondatabase.COLUMN_STATE);
                do {
                    String time = cursor.getString(idxTime);
                    double lat = parseDouble(cursor.getString(idxLat));
                    double lon = parseDouble(cursor.getString(idxLon));
                    double alt = parseDouble(cursor.getString(idxAlt));
                    String qr  = cursor.getString(idxQr);
                    int state  = parseInt(cursor.getString(idxState));
                    list.add(new ExportRecord(time, lat, lon, alt, qr, state));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read position database", e);
        } finally {
            if (cursor != null) cursor.close();
            if (db != null && db.isOpen()) db.close();
        }
        return list;
    }

    /**
     * 生成 KML 内容
     */
    public static String generateKml(List<ExportRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
        sb.append("  <Document>\n");
        sb.append("    <name>P-Nav Track</name>\n");
        sb.append("    <Style id=\"track\"><LineStyle><color>ff0000ff</color><width>3</width></LineStyle></Style>\n");
        sb.append("    <Placemark>\n");
        sb.append("      <name>Position Track</name>\n");
        sb.append("      <styleUrl>#track</styleUrl>\n");
        sb.append("      <LineString>\n");
        sb.append("        <coordinates>\n");

        for (ExportRecord r : records) {
            if (r.lat != 0 || r.lon != 0) {
                sb.append(String.format(Locale.US, "          %.8f,%.8f,%.3f\n", r.lon, r.lat, r.altitude));
            }
        }

        sb.append("        </coordinates>\n");
        sb.append("      </LineString>\n");
        sb.append("    </Placemark>\n");

        // 每个点一个 Placemark
        for (ExportRecord r : records) {
            if (r.lat == 0 && r.lon == 0) continue;
            sb.append("    <Placemark>\n");
            sb.append("      <name>").append(r.stateLabel()).append("</name>\n");
            sb.append("      <description>")
              .append("Time: ").append(r.time).append("\\n")
              .append("Alt: ").append(String.format(Locale.US, "%.3f", r.altitude)).append(" m\\n")
              .append("QR: ").append(r.qr)
              .append("</description>\n");
            sb.append("      <Point><coordinates>")
              .append(String.format(Locale.US, "%.8f,%.8f,%.3f", r.lon, r.lat, r.altitude))
              .append("</coordinates></Point>\n");
            sb.append("    </Placemark>\n");
        }

        sb.append("  </Document>\n");
        sb.append("</kml>\n");
        return sb.toString();
    }

    /**
     * 生成 GPX 内容
     */
    public static String generateGpx(List<ExportRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"P-Nav\"\n");
        sb.append("  xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        sb.append("  <trk><name>P-Nav Track</name><trkseg>\n");

        for (ExportRecord r : records) {
            if (r.lat == 0 && r.lon == 0) continue;
            sb.append(String.format(Locale.US,
                    "    <trkpt lat=\"%.8f\" lon=\"%.8f\"><ele>%.3f</ele><time>%s</time></trkpt>\n",
                    r.lat, r.lon, r.altitude, r.time));
        }

        sb.append("  </trkseg></trk>\n");
        sb.append("</gpx>\n");
        return sb.toString();
    }

    /**
     * 生成 CSV 内容
     */
    public static String generateCsv(List<ExportRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("Time,Latitude,Longitude,Altitude(m),QR,State\n");
        for (ExportRecord r : records) {
            sb.append(String.format(Locale.US, "%s,%.8f,%.8f,%.3f,%s,%s\n",
                    r.time, r.lat, r.lon, r.altitude, r.qr, r.stateLabel()));
        }
        return sb.toString();
    }

    /**
     * 写入临时文件并返回
     */
    public static File writeToTempFile(Context context, String content, String filename) {
        File exportDir = new File(StorageUtil.getDataDir(context), "export");
        if (!exportDir.exists()) exportDir.mkdirs();
        File file = new File(exportDir, filename);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write(content);
            w.flush();
        } catch (Exception e) {
            Log.e(TAG, "Failed to write export file: " + filename, e);
            return null;
        }
        return file;
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }
}
