package com.tencent.tws.locationtrack;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.tencent.tws.locationtrack.database.DbNameUtils;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.database.SPUtils;
import com.tencent.tws.widget.BaseActivity;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity";
    WakeLock mWakeLock;

    private Button locationButton;
    private Button historyButton;
    private TextView locationTV;
    private TextView historyTV;

    private ExecutorService fixedThreadExecutor = Executors.newFixedThreadPool(2);
    private DbNameUtils dbNameUtils;
    private static LocationDbHelper dbHelper;
    private static final int POINTS_TO_DELETE = 2;

    public void onCreate(Bundle savedInstanceState) {
        // calls super, sets GUI
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.mainmenu);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        mWakeLock.acquire();

        locationButton = (Button) findViewById(R.id.btn_location);
        locationTV = (TextView) findViewById(R.id.tv_location);
        locationButton.setOnClickListener(new LocationClick());
        locationTV.setOnClickListener(new LocationClick());

        historyButton = (Button) findViewById(R.id.btn_history);
        historyTV = (TextView) findViewById(R.id.tv_history);
        historyButton.setOnClickListener(new HistoryClick());
        historyTV.setOnClickListener(new HistoryClick());

    }

    private Runnable deletNoUseDb = new Runnable() {
        @Override
        public void run() {
            try {
                //1、扫描数据库目录并提前名称
                SQLiteDatabase db;
                Cursor cursor = null;
                dbNameUtils = new DbNameUtils(getApplicationContext());
                ArrayList<String> namesLists = dbNameUtils.getDbNames();

                //2、打开数据库并删除文件
                for (int i = 0; i < namesLists.size(); i++) {
                    String tmpdbName = namesLists.get(i);
                    if (tmpdbName != null && !tmpdbName.equals("")) {
                        String fulldbName = tmpdbName + "_location.db";
                        //打开数据库
                        dbHelper = new LocationDbHelper(getApplicationContext(), fulldbName);
                        db = dbHelper.getReadableDatabase();
                        //查询数据库
                        String SQLString = String.format("select * from %s ORDER BY id ASC;", LocationDbHelper.TABLE_NAME);
                        cursor = db.rawQuery(SQLString, null);
                        //删除空数据库
                        if (cursor != null && cursor.getCount() <= POINTS_TO_DELETE) {
                            Log.i(TAG, "删除数据库文件名称---->" + fulldbName);
                            String fName = "/data/data/com.tencent.tws.locationtrack/databases/" + fulldbName;
                            dbNameUtils.deleteFile(fName);
                        }
                    }

                }

                if (cursor != null) {
                    cursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    class LocationClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Intent i = new Intent(MainActivity.this, LocationActivity.class);
            startActivity(i);
        }
    }

    class HistoryClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Intent i = new Intent(MainActivity.this, HistoryActivity.class);

            //启动的时候检查数据库文件，如果是空数据库直接删除掉
            fixedThreadExecutor.execute(deletNoUseDb);

            startActivity(i);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        Log.i(TAG, "exitFlag=" + SPUtils.readExitFlag(getApplicationContext()));
        if (!SPUtils.readExitFlag(getApplicationContext())) {
            Log.i(TAG, "onResume  start activity");
            Intent i = new Intent(MainActivity.this, LocationActivity.class);
            startActivity(i);
        }

        if (mWakeLock != null) {
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }
}
