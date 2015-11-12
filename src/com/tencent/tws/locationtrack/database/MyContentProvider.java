package com.tencent.tws.locationtrack.database;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

/**
 * Created by microzhang on 2015/10/30 at 14:27.
 */
public class MyContentProvider extends ContentProvider {

    private static final String TAG = "MyContentProvider";

    public static final String AUTHORITY = "com.tencent.tws.locationtrack.database.MyContentProvider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/locations");
    public static final String CONTENT_TYPE = "vnd.android.cursor.dir/locations";
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/location";

    private static LocationDbHelper dbHelper;
    private static String dbName;

    private static HashMap<String, String> locationMaps;

    private static final int LOCATION = 1;
    private static final int LOCATION_ID = 2;

    private static final UriMatcher uriMatcher;
    public static final String LAST_DATABASE_NAME = "_location.db";

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(MyContentProvider.AUTHORITY, "locations", LOCATION);
        uriMatcher.addURI(MyContentProvider.AUTHORITY, "locations/#", LOCATION_ID);

        //定义别名
        locationMaps = new HashMap<String, String>();
        locationMaps.put(LocationDbHelper.ID, LocationDbHelper.ID);
        locationMaps.put(LocationDbHelper.LATITUDE, LocationDbHelper.LATITUDE);
        locationMaps.put(LocationDbHelper.LONGITUDE, LocationDbHelper.LONGITUDE);
        locationMaps.put(LocationDbHelper.INS_SPEED, LocationDbHelper.INS_SPEED);
        locationMaps.put(LocationDbHelper.BEARING, LocationDbHelper.BEARING);
        locationMaps.put(LocationDbHelper.ALTITUDE, LocationDbHelper.ALTITUDE);
        locationMaps.put(LocationDbHelper.ACCURACY, LocationDbHelper.ACCURACY);
        locationMaps.put(LocationDbHelper.TIME, LocationDbHelper.TIME);
        locationMaps.put(LocationDbHelper.DISTANCE, LocationDbHelper.DISTANCE);
        locationMaps.put(LocationDbHelper.AVG_SPEED, LocationDbHelper.AVG_SPEED);
        locationMaps.put(LocationDbHelper.KCAL, LocationDbHelper.KCAL);
    }

    @Override
    public boolean onCreate() {
        Log.i(TAG, "MyContentProvider onCreate");

        String databaseName = System.currentTimeMillis() + LAST_DATABASE_NAME;
        if (SPUtils.readSp(getContext()) != "") {
            Log.i(TAG, "open database");
            dbHelper = new LocationDbHelper(getContext(), SPUtils.readSp(getContext()));
        } else {
            Log.i(TAG, "new database");
            dbHelper = new LocationDbHelper(getContext(), databaseName);
            SPUtils.writeSp(getContext(), databaseName);
        }

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteDatabase sqLiteDatabase = dbHelper.getWritableDatabase();
        SQLiteQueryBuilder sqLiteQueryBuilder = new SQLiteQueryBuilder();
        switch (uriMatcher.match(uri)) {
            case LOCATION_ID:
                sqLiteQueryBuilder.setTables(LocationDbHelper.TABLE_NAME);
                sqLiteQueryBuilder.setProjectionMap(locationMaps);
                sqLiteQueryBuilder.appendWhere(LocationDbHelper.ID + "=" + uri.getPathSegments().get(1));
                break;
            case LOCATION:
                sqLiteQueryBuilder.setTables(LocationDbHelper.TABLE_NAME);
                sqLiteQueryBuilder.setProjectionMap(locationMaps);
                break;
        }

        String orderBy;
        if (sortOrder == null || sortOrder.equals("")) {
            orderBy = LocationDbHelper.DEFAULT_ORDERBY;
        } else {
            orderBy = sortOrder;
        }
        Cursor cursor = sqLiteQueryBuilder.query(sqLiteDatabase, projection, selection, selectionArgs, null, null, orderBy);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)) {
            case LOCATION:
                return MyContentProvider.CONTENT_TYPE;
            case LOCATION_ID:
                return MyContentProvider.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI" + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long rowId = db.insert(LocationDbHelper.TABLE_NAME, null, values);
        Uri resultUri = ContentUris.withAppendedId(MyContentProvider.CONTENT_URI, rowId);
//		getContext().getContentResolver().notifyChange(resultUri, null);
        return resultUri;

//		Cursor cursor = getLastDataItem(sqLiteDatabase, LocationDbHelper.TABLE_NAME);
//		long rowId = 0;
//
//		if (cursor != null && cursor.moveToLast()) {
//			long oldTimeStamp = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
//			int recordId = cursor.getInt(cursor.getColumnIndex(LocationDbHelper.ID));
//			long newTimeStamp = values.getAsLong(LocationDbHelper.TIME);
//			if (isSameMinute(oldTimeStamp, newTimeStamp)) {
//				String whereString = LocationDbHelper.ID + "=" + recordId;
//				rowId = sqLiteDatabase.update(LocationDbHelper.TABLE_NAME, values, whereString, null);
//			} else {
//				rowId = sqLiteDatabase.insert(LocationDbHelper.TABLE_NAME, null, values);
//			}
//		}
//
//		if (rowId > 0) {
//			Uri insertUserUri = ContentUris.withAppendedId(MyContentProvider.CONTENT_URI, rowId);
//			getContext().getContentResolver().notifyChange(insertUserUri, null);
//			return insertUserUri;
//		} else {
//			throw new IllegalArgumentException("Failed to insert row into" + uri);
//		}
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count = 0;
        switch (uriMatcher.match(uri)) {
            case LOCATION_ID:
                String locId = uri.getPathSegments().get(1);
                count = db.delete(LocationDbHelper.TABLE_NAME, LocationDbHelper.ID + "=" + locId + (!TextUtils.isEmpty(selection) ? " and (" + selection + ')' : ""), selectionArgs);
                break;
            case LOCATION:
                count = db.delete(LocationDbHelper.TABLE_NAME, selection, selectionArgs);
                break;
        }
//		getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase sqLiteDatabase = dbHelper.getWritableDatabase();
        int count = 0;
        switch (uriMatcher.match(uri)) {
            case LOCATION_ID:
                String id = uri.getLastPathSegment();
                count = sqLiteDatabase.update(LocationDbHelper.TABLE_NAME, values, LocationDbHelper.ID + " = " + id + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
                break;
            case LOCATION:
                count = sqLiteDatabase.update(LocationDbHelper.TABLE_NAME, values, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI " + uri);
        }
//		getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    protected Cursor getLastDataItem(SQLiteDatabase mDatabase, String tableName) {
        String SQLString = String.format("select * from %s ORDER BY id ASC;", tableName);
        Cursor cursor = mDatabase.rawQuery(SQLString, null);
        if (cursor.moveToLast()) {
            return cursor;
        }
        return null;
    }

    public boolean isSameMinute(long timeA, long timeB) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(new Date(timeA));
        Calendar cb = Calendar.getInstance();
        cb.setTime(new Date(timeB));
        boolean result = ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR) && ca.get(Calendar.HOUR_OF_DAY) == cb.get(Calendar.HOUR_OF_DAY) && ca.get(Calendar.MINUTE) == cb.get(Calendar.MINUTE);
        return result;
    }
}
