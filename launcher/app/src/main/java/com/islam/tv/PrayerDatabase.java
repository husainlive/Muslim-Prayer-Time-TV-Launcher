package com.islam.tv;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class PrayerDatabase extends SQLiteOpenHelper {

    private static final String TAG = "PrayerDatabase";
    private static final String DATABASE_NAME = "prayer_times.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_NAME = "prayer_calendar";
    public static final String COLUMN_DATE = "date"; // YYYY-MM-DD
    public static final String COLUMN_CITY = "city";
    public static final String COLUMN_COUNTRY = "country";
    public static final String COLUMN_FAJR = "fajr";
    public static final String COLUMN_SUNRISE = "sunrise";
    public static final String COLUMN_DHUHR = "dhuhr";
    public static final String COLUMN_ASR = "asr";
    public static final String COLUMN_MAGHRIB = "maghrib";
    public static final String COLUMN_ISHA = "isha";

    public static class PrayerDayTimes {
        public final String date;
        public final String fajr;
        public final String sunrise;
        public final String dhuhr;
        public final String asr;
        public final String maghrib;
        public final String isha;

        public PrayerDayTimes(String date, String fajr, String sunrise, String dhuhr,
                              String asr, String maghrib, String isha) {
            this.date = date;
            this.fajr = fajr;
            this.sunrise = sunrise;
            this.dhuhr = dhuhr;
            this.asr = asr;
            this.maghrib = maghrib;
            this.isha = isha;
        }
    }

    public PrayerDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " ("
                + COLUMN_DATE + " TEXT, "
                + COLUMN_CITY + " TEXT, "
                + COLUMN_COUNTRY + " TEXT, "
                + COLUMN_FAJR + " TEXT, "
                + COLUMN_SUNRISE + " TEXT, "
                + COLUMN_DHUHR + " TEXT, "
                + COLUMN_ASR + " TEXT, "
                + COLUMN_MAGHRIB + " TEXT, "
                + COLUMN_ISHA + " TEXT, "
                + "PRIMARY KEY (" + COLUMN_DATE + ", " + COLUMN_CITY + ", " + COLUMN_COUNTRY + "))";
        db.execSQL(createTable);
        Log.d(TAG, "PrayerDatabase table created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void insertPrayerTimes(String date, String city, String country,
                                  String fajr, String sunrise, String dhuhr,
                                  String asr, String maghrib, String isha) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_DATE, date);
        values.put(COLUMN_CITY, city.toLowerCase().trim());
        values.put(COLUMN_COUNTRY, country.toLowerCase().trim());
        values.put(COLUMN_FAJR, fajr);
        values.put(COLUMN_SUNRISE, sunrise);
        values.put(COLUMN_DHUHR, dhuhr);
        values.put(COLUMN_ASR, asr);
        values.put(COLUMN_MAGHRIB, maghrib);
        values.put(COLUMN_ISHA, isha);
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public PrayerDayTimes getPrayerTimes(String date, String city, String country) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_NAME,
                null,
                COLUMN_DATE + " = ? AND " + COLUMN_CITY + " = ? AND " + COLUMN_COUNTRY + " = ?",
                new String[]{date, city.toLowerCase().trim(), country.toLowerCase().trim()},
                null, null, null
        );

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    String fajr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FAJR));
                    String sunrise = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SUNRISE));
                    String dhuhr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DHUHR));
                    String asr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ASR));
                    String maghrib = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MAGHRIB));
                    String isha = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ISHA));
                    return new PrayerDayTimes(date, fajr, sunrise, dhuhr, asr, maghrib, isha);
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }
}
