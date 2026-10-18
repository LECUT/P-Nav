package com.ecut.pnav;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class positiondatabase extends SQLiteOpenHelper {
    // 数据库名称
    private static final String DATABASE_NAME = "my_database.db";
    // 数据库版本
    private static final int DATABASE_VERSION = 2;

    // 表名
    public static final String TABLE_NAME = "my_table";
    public static final String TABLE_FILE_RECORDS = "file_records";
    // 列名
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TIME = "time";
    public static final String COLUMN_LAT = "dsm1";
    public static final String COLUMN_LON = "dsm2";
    public static final String COLUMN_ALTITUDE = "altitude";
    public static final String COLUMN_QR = "qr";
    public static final String COLUMN_STATE = "state";
    // file_records 列
    public static final String COL_FILE_PATH = "file_path";
    public static final String COL_FILE_NAME = "file_name";
    public static final String COL_FILE_TYPE = "file_type";
    public static final String COL_FILE_MODE = "file_mode";
    public static final String COL_FILE_CREATED = "created_at";

    // 创建表的 SQL 语句
    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TIME + " TEXT NOT NULL, " +
                    COLUMN_LAT + " TEXT NOT NULL, " +
                    COLUMN_LON + " TEXT NOT NULL, " +
                    COLUMN_ALTITUDE + " TEXT NOT NULL, " +
                    COLUMN_QR + " TEXT NOT NULL, " +
                    COLUMN_STATE + " TEXT NOT NULL);";

    private static final String TABLE_FILE_RECORDS_CREATE =
            "CREATE TABLE " + TABLE_FILE_RECORDS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_FILE_PATH + " TEXT NOT NULL, " +
                    COL_FILE_NAME + " TEXT NOT NULL, " +
                    COL_FILE_TYPE + " TEXT NOT NULL, " +
                    COL_FILE_MODE + " TEXT NOT NULL, " +
                    COL_FILE_CREATED + " TEXT NOT NULL);";
    public positiondatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    public positiondatabase(@Nullable Context context, @Nullable String name, @Nullable SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
        db.execSQL(TABLE_FILE_RECORDS_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL(TABLE_FILE_RECORDS_CREATE);
        }
    }
    public static String getLatestData(SQLiteDatabase db) {
        // 查询最新一条数据
        String query = "SELECT * FROM " + positiondatabase.TABLE_NAME + " ORDER BY " +
                positiondatabase.COLUMN_TIME + " DESC LIMIT 1";
        Cursor cursor = db.rawQuery(query, null);

        String result = "No data found"; // 默认返回值

        if (cursor != null && cursor.moveToFirst()) {
            // 获取数据
            @SuppressLint("Range") int id = cursor.getInt(cursor.getColumnIndex(positiondatabase.COLUMN_ID));
            @SuppressLint("Range") String time = cursor.getString(cursor.getColumnIndex(positiondatabase.COLUMN_TIME));
            @SuppressLint("Range") String lat = cursor.getString(cursor.getColumnIndex(positiondatabase.COLUMN_LAT));
            @SuppressLint("Range") String lon = cursor.getString(cursor.getColumnIndex(positiondatabase.COLUMN_LON));
            @SuppressLint("Range") String altitude = cursor.getString(cursor.getColumnIndex(positiondatabase.COLUMN_ALTITUDE));
            @SuppressLint("Range") String qr = cursor.getString(cursor.getColumnIndex(positiondatabase.COLUMN_QR));
            @SuppressLint("Range") String state = cursor.getString(cursor.getColumnIndex(positiondatabase.COLUMN_STATE));
            // 构建返回字符串
            result = time+","+lat+","+lon+","+altitude+","+qr+","+state;
        }
        if (cursor != null) {
            cursor.close();
        }
        return result; // 返回结果字符串
    }
    // 查询数据
    public String getData() {
        StringBuilder result = new StringBuilder();
        try (SQLiteDatabase db = getReadableDatabase();
             Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                int timeIndex = cursor.getColumnIndex("time");
                int dsm1Index = cursor.getColumnIndex("dsm1");
                int dsm2Index = cursor.getColumnIndex("dsm2");
                int heightIndex = cursor.getColumnIndex("altitude");
                int qrIndex = cursor.getColumnIndex("qr");
                int stateIndex = cursor.getColumnIndex("state");

                if (timeIndex >= 0 && dsm1Index >= 0 && dsm2Index >= 0 && heightIndex >= 0 && qrIndex >= 0 && stateIndex >= 0) {
                    do {
                        appendData(result, cursor, timeIndex, dsm1Index, dsm2Index, heightIndex, qrIndex, stateIndex);
                    } while (cursor.moveToNext());
                } else {
                    // 处理索引为负数的情况
                    result.append("Error: Invalid column index.\n");
                }
            } else {
                // 处理 Cursor 未初始化的情况
                result.append("Error: Cursor is null or empty.\n");
            }
        } catch (Exception e) {
            result.append("Error: ").append(e.getMessage()).append("\n");
        }
        return result.toString();
    }

    private void appendData(StringBuilder result, Cursor cursor, int timeIndex, int dsm1Index, int dsm2Index, int heightIndex, int qrIndex, int stateIndex) {
        String time = cursor.getString(timeIndex);
        String dsm1 = cursor.getString(dsm1Index);
        String dsm2 = cursor.getString(dsm2Index);
        String height = cursor.getString(heightIndex);
        String qr = cursor.getString(qrIndex);
        String state = cursor.getString(stateIndex);
        result.append("Time：").append(time).append("\n");
        result.append("Longitude：").append(dsm1).append("\n");
        result.append("Latitude：").append(dsm2).append("\n");
        result.append("Elevation：").append(height).append("\n");
        result.append("Variance and covariance(x,y,h)：").append(qr).append("\n");
        result.append("Status(1:RTK Fix 2:RTK Float 4：DGNSS 5：SPP 0：Fail)：").append(state).append("\n\n");
    }
    public void clearDatabase() {
        SQLiteDatabase db = getWritableDatabase(); // dbHelper 是您的数据库帮助类对象
        // 清空数据表
        db.delete(TABLE_NAME, null, null);
        db.close();
    }
    public static void insertData(SQLiteDatabase db, String time, String lat, String lon, String altitude, String qr, String state) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_TIME, time);
        values.put(COLUMN_LAT, lat);
        values.put(COLUMN_LON, lon);
        values.put(COLUMN_ALTITUDE, altitude);
        values.put(COLUMN_QR, qr);
        values.put(COLUMN_STATE, state);
        db.insert(TABLE_NAME, null, values);
    }

    /** 异步写入定位数据，避免阻塞 GNSS 回调线程 */
    private static final ExecutorService DB_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "position-db");
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });

    public static void insertDataAsync(SQLiteDatabase db, String time, String lat, String lon, String altitude, String qr, String state) {
        DB_EXECUTOR.execute(() -> insertData(db, time, lat, lon, altitude, qr, state));
    }

    /** 异步写入文件记录 */
    public static void insertFileRecordAsync(SQLiteDatabase db, String filePath, String fileName, String fileType, String fileMode, String createdAt) {
        DB_EXECUTOR.execute(() -> {
            ContentValues values = new ContentValues();
            values.put(COL_FILE_PATH, filePath);
            values.put(COL_FILE_NAME, fileName);
            values.put(COL_FILE_TYPE, fileType);
            values.put(COL_FILE_MODE, fileMode);
            values.put(COL_FILE_CREATED, createdAt);
            db.insert(TABLE_FILE_RECORDS, null, values);
        });
    }

    /** 查询文件记录（按时间倒序，最新50条） */
    public static Cursor queryFileRecords(SQLiteDatabase db) {
        return db.query(TABLE_FILE_RECORDS, null, null, null, null, null,
                COL_FILE_CREATED + " DESC", "50");
    }

    /** 清空文件记录表 */
    public void clearFileRecords() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_FILE_RECORDS, null, null);
        db.close();
    }
}
