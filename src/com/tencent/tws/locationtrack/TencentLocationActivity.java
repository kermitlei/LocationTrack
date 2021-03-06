package com.tencent.tws.locationtrack;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.tencent.map.geolocation.TencentLocation;
import com.tencent.mapsdk.raster.model.GeoPoint;
import com.tencent.mapsdk.raster.model.LatLng;
import com.tencent.mapsdk.raster.model.Polyline;
import com.tencent.mapsdk.raster.model.PolylineOptions;
import com.tencent.tencentmap.mapsdk.map.MapView;
import com.tencent.tws.locationtrack.activity.LocationService;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.database.MyContentProvider;
import com.tencent.tws.locationtrack.database.SPUtils;
import com.tencent.tws.locationtrack.util.LocationUtil;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TencentLocationActivity extends Activity {

    private static final String TAG = "TencentLocationActivity";

    protected LocationManager locationManager;
    protected Context context;

    private MapView mMapView;
    private LocationOverlay mLocationOverlay;
    private List<Object> Overlays;

    WakeLock mWakeLock;

    List<LatLng> points = new ArrayList<LatLng>();
    List<LatLng> points_tem = new ArrayList<LatLng>();

    int mSatelliteNum;
    private ArrayList<GpsSatellite> numSatelliteList = new ArrayList<GpsSatellite>();

    private final static int ACCURACY = 3;
    private BigDecimal lastLatitude;
    private BigDecimal lastLongitude;
    private long lastLocationTime = (long) 0;

    private TextView tvAveSpeed;
    private TextView tvInsSpeed;
    private Button startButton;
    private Button exitButton;

    private TextView tvKal;
    private TextView tvGPSStatus;
    private TextView tvLocation;
    private TextView getIntervalTime;
    private TextView allDis;
    private DBContentObserver mDBContentObserver;

    private boolean isFinishDBDraw = true;
    protected Queue<LatLng> resumeLocations = new LinkedList<LatLng>();

    Intent tencentLocationServiceIntent;

    private ExecutorService fixedThreadExecutor = Executors.newFixedThreadPool(2);

    private static final int UPDATE_TEXT_VIEWS = 1;
    private static final int UPDATE_DRAW_LINES = 2;
    private static final int DRAW_RESUME = 3;

    private static final int RESUME_ONCE_DRAW_POINTS = 500;

    private static final int ZOOM_LEVER = 18;
    //临时未用的
    protected double topBoundary;
    protected double leftBoundary;
    protected double rightBoundary;
    protected double bottomBoundary;
    protected Location locationTopLeft;
    protected Location locationBottomRight;
    protected float maxDistance;
    protected GeoPoint mapCenterPoint;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tencent_geolocation);

        //不灭屏
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        mWakeLock.acquire();

        tvLocation = (TextView) findViewById(R.id.tencent_tvLocation);
        tvGPSStatus = (TextView) findViewById(R.id.tencent_tvGPSStatus);
        tvAveSpeed = (TextView) findViewById(R.id.tencent_ave_speed);
        tvInsSpeed = (TextView) findViewById(R.id.tencent_ins_speed);
        tvKal = (TextView) findViewById(R.id.tencent_kal);
        allDis = (TextView) findViewById(R.id.tencent_all_dis);
        getIntervalTime = (TextView) findViewById(R.id.tencent_getIntervalTime);

        //地图
        initMapView();
        //初始化观察者
        initContentObserver();

        //按钮
        startButton = (Button) findViewById(R.id.tencent_startButton);
        exitButton = (Button) findViewById(R.id.tencent_exitButton);


        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SPUtils.writeTencentExitFlag(getApplicationContext(), false);
                //按钮状态改变
                startButton.setEnabled(false);
                exitButton.setEnabled(true);

                //启动后台服务获取地理信息
                tencentLocationServiceIntent = new Intent(getApplicationContext(), TencentLocationService.class);
                startService(tencentLocationServiceIntent);
                Log.i(TAG, "TencentLocationActivity 启动");
            }
        });

        //activity被杀掉重新进来的时候不需要再次点击开始按钮,自动重新启动服务
        if (SPUtils.readExitFlag(getApplicationContext()) == false) {
            tencentLocationServiceIntent = new Intent(getApplicationContext(), TencentLocationService.class);
            startService(tencentLocationServiceIntent);

            //改变按钮状态
            startButton.setEnabled(false);
            exitButton.setEnabled(true);
        }

        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SPUtils.clearDBName(getApplicationContext());

                if (tencentLocationServiceIntent != null) {
                    stopService(tencentLocationServiceIntent);
                }

                //设置退出标志位
                SPUtils.writeTencentExitFlag(getApplicationContext(), true);
                //退出当前Activity
                finish();

//                Intent intent = new Intent(Intent.ACTION_MAIN);
//                intent.addCategory(Intent.CATEGORY_HOME);
//                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//                startActivity(intent);
//                android.os.Process.killProcess(android.os.Process.myPid());
//                System.exit(0);
            }
        });


        //判断GPS是否打开
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            tvGPSStatus.setText("GPS已打开");
        } else {
            tvGPSStatus.setText("GPS已关闭");
        }
        //注册GPS监听回调
        locationManager.addGpsStatusListener(statusListener);
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case UPDATE_TEXT_VIEWS:
                    Bundle bundle = (Bundle) msg.obj;
                    if (bundle != null) {
                        updateTextViews(bundle.getDouble("longitude"), bundle.getDouble("latitude"), bundle.getLong("times"), bundle.getDouble("insSpeed"), bundle.getDouble("aveSpeed"), bundle.getDouble("kcal"), bundle.getDouble("allDistance"));
                    }
                    break;

                case UPDATE_DRAW_LINES:
                    Bundle bundle1 = (Bundle) msg.obj;
                    Log.i(TAG, "UPDATE_DRAW_LINES -----> longitude=" + bundle1.getDouble("longitude") + " latitude=" + bundle1.getDouble("latitude") + " accuracy=" + bundle1.getFloat("accuracy"));
                    drawLines(bundle1.getDouble("longitude"), bundle1.getDouble("latitude"), bundle1.getFloat("accuracy"));
                    break;

                case DRAW_RESUME:
                    //locations 中包含了所有需要绘制的点信息
                    onResumeDrawImp();
                    isFinishDBDraw = true;
                    break;
                default:
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
                if (SPUtils.readTencentExitFlag(getApplicationContext()) == false) { //已经开始轨迹定位且没有退出，只能通过点击按钮退出
                    Toast.makeText(getApplicationContext(), "正在记录轨迹，点击退出按钮结束轨迹记录", Toast.LENGTH_SHORT).show();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart");
        isFinishDBDraw = false;
    }

    private void initContentObserver() {
        mDBContentObserver = new DBContentObserver(new Handler());
        getContentResolver().registerContentObserver(MyContentProvider.CONTENT_URI, true, mDBContentObserver);
    }

    @Override
    protected void onResume() {
        mMapView.onResume();
        super.onResume();

        Log.i(TAG, "onResume");
        if (SPUtils.readDBName(getApplicationContext()) != "") {//数据库是存在的
            if (mMapView != null) {
                Log.i(TAG, "clearAllOverlays");
                mMapView.clearAllOverlays();
            }

            //将数据库文件读取到locations队列中，为真正的绘制流程做准备
            Log.i(TAG, "readDbforDraw");
            readDbforDraw();
        }

        if (mWakeLock != null) {
            mWakeLock.acquire();
        }
    }

    //读取数据库，绘制数据库中所有数据
    private void readDbforDraw() {
        fixedThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Cursor cursor = null;
                try {
                    String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
                    cursor = getApplicationContext().getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);

//                    douglasPoints.clear();
                    resumeLocations.clear();

                    if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                        while (cursor.moveToNext()) {
                            double latitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
                            double longitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
                            LatLng latLng = new LatLng(latitude, longitude);
                            //Gps gps = PositionUtil.gps84_To_Gcj02(latitude, longitude);
                            //if (gps != null) {
                            //所有数据进入队列
                            resumeLocations.offer(latLng);
                            //}
                        }

                        Log.i(TAG, "resumeLocations.size()=" + resumeLocations.size());
                    }

                    handler.sendEmptyMessage(DRAW_RESUME);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (cursor != null) {
                    cursor.close();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        mMapView.onPause();
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    protected void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();

        if (locationManager != null) {
            locationManager.removeGpsStatusListener(statusListener);
        }

        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    protected void onStop() {
        mMapView.onStop();
        super.onStop();
    }


    private boolean filter(TencentLocation location) {
        BigDecimal longitude = (new BigDecimal(location.getLongitude())).setScale(ACCURACY, BigDecimal.ROUND_HALF_UP);

        BigDecimal latitude = (new BigDecimal(location.getLatitude())).setScale(ACCURACY, BigDecimal.ROUND_HALF_UP);

        if (latitude.equals(lastLatitude) && longitude.equals(lastLongitude)) {
            return false;
        }

        lastLatitude = latitude;
        lastLongitude = longitude;
        return true;
    }

    private boolean filter(Location location) {
        BigDecimal longitude = (new BigDecimal(location.getLongitude())).setScale(ACCURACY, BigDecimal.ROUND_HALF_UP);

        BigDecimal latitude = (new BigDecimal(location.getLatitude())).setScale(ACCURACY, BigDecimal.ROUND_HALF_UP);

        if (latitude.equals(lastLatitude) && longitude.equals(lastLongitude)) {
            return false;
        }

        lastLatitude = latitude;
        lastLongitude = longitude;
        return true;
    }

    private void initMapView() {
        mMapView = (MapView) findViewById(R.id.tencent_mapviewOverlay);
        // mMapView.setBuiltInZoomControls(true);
        mMapView.getController().setZoom(ZOOM_LEVER);

        Bitmap bmpMarker = BitmapFactory.decodeResource(getResources(), R.drawable.mark_location);
        mLocationOverlay = new LocationOverlay(bmpMarker);
        mMapView.addOverlay(mLocationOverlay);

        Overlays = new ArrayList<Object>();
    }

    private static GeoPoint of(double latitude, double longitude) {
        GeoPoint ge = new GeoPoint((int) (latitude * 1E6), (int) (longitude * 1E6));
        return ge;
    }

    /**
     * onResume的时候真正的绘制流程
     * 每次绘制 RESUME_ONCE_DRAW_POINTS 个点 分批绘制
     */
    private void onResumeDrawImp() {
        Log.i(TAG, "onResumeDrawImp");

        int count = resumeLocations.size();

        if (count < RESUME_ONCE_DRAW_POINTS) {
            PolylineOptions lineOpt = new PolylineOptions();
            lineOpt.color(0xAAFF0000);

            //绘制全部数据
            LatLng t1Points[] = new LatLng[count];
            for (int i = 0; i < count; i++) {
                if (resumeLocations.peek() != null) {
                    //Gps gps = resumeLocations.poll();
                    LatLng latLng = resumeLocations.poll();
                    t1Points[i] = latLng;
                    if (t1Points[i] != null) {
                        lineOpt.add(t1Points[i]);
                    }

                    if (i == (count - 1)) {
                        mMapView.getController().animateTo(of(latLng.getLatitude(), latLng.getLongitude()));
                        mLocationOverlay.setGeoCoords(of(latLng.getLatitude(), latLng.getLongitude()));
                        // mMapView.invalidate();
                    }
                }
            }

            Polyline line = mMapView.getMap().addPolyline(lineOpt);
            Overlays.add(line);

        } else {
            //每次绘制 RESUME_ONCE_DRAW_POINTS 个点，分多次绘制
            LatLng tPoints[] = new LatLng[RESUME_ONCE_DRAW_POINTS];
            PolylineOptions lineOpt2 = new PolylineOptions();
            lineOpt2.color(0xAAFF0000);

            int count2 = (resumeLocations.size() % RESUME_ONCE_DRAW_POINTS == 0) ? (resumeLocations.size() % RESUME_ONCE_DRAW_POINTS) : (resumeLocations.size() % RESUME_ONCE_DRAW_POINTS + 1);
            for (int i = 0; i < count2 - 1; i++) {
                for (int j = 0; j < RESUME_ONCE_DRAW_POINTS; j++) {
                    if (resumeLocations.peek() != null) {
                        //Gps gps = resumeLocations.poll();
                        LatLng latLng = resumeLocations.poll();

                        //绘制最后一个点的时候移动到对应的位置
                        if (i == (count2 - 2)) {
                            mMapView.getController().animateTo(of(latLng.getLatitude(), latLng.getLongitude()));
                            mLocationOverlay.setGeoCoords(of(latLng.getLatitude(), latLng.getLongitude()));
                            mMapView.invalidate();
                        }

                        tPoints[j] = new LatLng(latLng.getLatitude(), latLng.getLongitude());
                        if (tPoints[j] != null) {
                            lineOpt2.add(tPoints[j]);
                        }
                    }
                }
                Polyline line = mMapView.getMap().addPolyline(lineOpt2);
                Overlays.add(line);
            }


            //绘制剩下的 <= 500个点
            PolylineOptions lineOpt3 = new PolylineOptions();
            lineOpt3.color(0xAAFF0000);
            int restCount = resumeLocations.size();
            LatLng t2Points[] = new LatLng[restCount];
            for (int i = 0; i < restCount; i++) {
                if (resumeLocations.peek() != null) {
                    // Gps gps = resumeLocations.poll();
                    LatLng latLng = resumeLocations.poll();

                    t2Points[i] = latLng;
                    if (t2Points[i] != null) {
                        lineOpt3.add(t2Points[i]);
                    }

                    //绘制最后一个点的时候移动到对应的位置
                    if (i == (restCount - 1)) {
                        mMapView.getController().animateTo(of(latLng.getLatitude(), latLng.getLongitude()));
                        mLocationOverlay.setGeoCoords(of(latLng.getLatitude(), latLng.getLongitude()));
                        //mMapView.invalidate();
                    }
                }
            }
            Polyline line = mMapView.getMap().addPolyline(lineOpt3);
            Overlays.add(line);
        }
    }


    private void drawLines(double longitude, double latitude, float accuracy) {

        Log.i(TAG, "douglasPoints size = " + points.size());
        mMapView.getController().animateTo(of(latitude, longitude));

        mLocationOverlay.setAccuracy(accuracy);
        mLocationOverlay.setGeoCoords(of(latitude, longitude));
        mMapView.invalidate();

        if (longitude > 0 && latitude > 0) {
            LatLng latLng = new LatLng(latitude, longitude);
            points.add(latLng);
        }

        if (points.size() == 2) {
            // 这里绘制起点
            PolylineOptions lineOpt = new PolylineOptions();
            lineOpt.color(0xAAFF0000);
            for (LatLng point : points) {
                lineOpt.add(point);
            }
            mMapView.getMap().addPolyline(lineOpt);
            Overlays.add(lineOpt);
        }

        if (points.size() > 2 && points.size() <= 10) {
            PolylineOptions lineOpt = new PolylineOptions();
            lineOpt.color(0xAAFF0000);
            for (LatLng point : points) {
                lineOpt.add(point);
            }
            mMapView.getMap().addPolyline(lineOpt);
            Overlays.add(lineOpt);
        }

        if (points.size() > 10) {
            // 每次绘制10个点，这样应该不会出现明显的折线吧
            points_tem = points.subList(points.size() - 10, points.size());
            // 绘图

            PolylineOptions lineOpt = new PolylineOptions();
            lineOpt.color(0xAAFF0000);
            for (LatLng point : points_tem) {
                lineOpt.add(point);
            }
            Polyline line = mMapView.getMap().addPolyline(lineOpt);
            Overlays.add(line);
        }

        Log.i(TAG, "douglasPoints.size() = " + points.size());

    }

    private void updateTextViews(double longitude, double latitude, long times, double insSpeed, double aveSpeed, double kal, double allDistance) {
        tvLocation.setText("维度:" + latitude + ",经度:" + longitude + ",时间 :" + LocationUtil.convert(times));

        if (lastLocationTime != times && lastLocationTime != 0) {
            Log.i("kermit", "lastLocationTime=" + lastLocationTime);
            Log.i("kermit", "times=" + times);
            Log.i("kermit", "deltTimes=" + (times - lastLocationTime));
            long deltTIme = (times - lastLocationTime) / 1000;
            getIntervalTime.setText("   获取间隔时间：" + deltTIme);
        }

        lastLocationTime = times;

        DecimalFormat myformat = new DecimalFormat("#0.00");
        tvInsSpeed.setText(myformat.format(insSpeed) + " km/h");
        tvAveSpeed.setText(myformat.format(aveSpeed) + " km/h");
        tvKal.setText(myformat.format(kal) + " kal");
        allDis.setText(allDistance + " m");
    }


    private final GpsStatus.Listener statusListener = new GpsStatus.Listener() {
        @Override
        public void onGpsStatusChanged(int event) {
            // TODO Auto-generated method stub
            // GPS状态变化时的回调，获取当前状态
            GpsStatus status = locationManager.getGpsStatus(null);
            // 获取卫星相关数据
            GetGPSStatus(event, status);
        }

    };

    private void GetGPSStatus(int event, GpsStatus status) {
        if (status == null) {
        } else if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
            // 获取最大的卫星数（这个只是一个预设值）
            int maxSatellites = status.getMaxSatellites();
            Iterator<GpsSatellite> it = status.getSatellites().iterator();
            numSatelliteList.clear();
            // 记录实际的卫星数目
            int count = 0;
            while (it.hasNext() && count <= maxSatellites) {
                // 保存卫星的数据到一个队列，用于刷新界面
                GpsSatellite s = it.next();
                numSatelliteList.add(s);
                count++;
            }
            mSatelliteNum = numSatelliteList.size();
            String strSatelliteNum = this.getString(R.string.satellite_num) + mSatelliteNum;
            TextView tv = (TextView) findViewById(R.id.tencent_tvSatelliteNum);
            tv.setText(strSatelliteNum);

        } else if (event == GpsStatus.GPS_EVENT_STARTED) {
            // 定位启动
        } else if (event == GpsStatus.GPS_EVENT_STOPPED) {
            // 定位结束
        }
    }


    private class DBContentObserver extends ContentObserver {

        public DBContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.i(TAG, "onChange~~~~");
            //子线程中去做数据库操作，更新UI
            fixedThreadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
                        long allDistance = 0;

                        Cursor cursor = getApplicationContext().getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);
                        if (cursor != null) {
                            int cursorCount = cursor.getCount();
                            Log.i(TAG, "cursorCount = " + cursorCount);

                            //获取总距离
                            if (cursorCount > 0 && cursor.moveToFirst()) {
                                for (int i = 0; i < cursor.getCount(); i++) {
                                    cursor.moveToPosition(i);
                                    allDistance += cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.DISTANCE));
                                }
                            }

                            //更新UI TextView，每次采集  LocationService.LOCATION_QUEUE_SIZE  个点更新一次UI
                            if (cursor.moveToLast()) {
                                double latitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
                                double longitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
                                long times = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
                                double insSpeed = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.INS_SPEED));
                                double aveSpeed = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.AVG_SPEED));
                                double kcal = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.KCAL));
                                float accuracy = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY));

                                //更新显示UI
                                Message msg = Message.obtain();
                                Bundle bundle = new Bundle();
                                bundle.putDouble("longitude", longitude);
                                bundle.putDouble("latitude", latitude);
                                bundle.putLong("times", times);
                                bundle.putDouble("insSpeed", insSpeed);
                                bundle.putDouble("aveSpeed", aveSpeed);
                                bundle.putDouble("kcal", kcal);
                                bundle.putDouble("allDistance", allDistance);
                                msg.obj = bundle;
                                msg.what = UPDATE_TEXT_VIEWS;
                                handler.sendMessage(msg);


                                if (isFinishDBDraw == false) {
                                    //Gps gps = PositionUtil.gps84_To_Gcj02(latitude, latitude);
                                    //if (gps != null) {
                                    LatLng latLng = new LatLng(latitude, longitude);
                                    points.add(latLng);
                                    //}
                                } else {
                                    if (cursorCount > LocationService.LOCATION_QUEUE_SIZE) {
                                        for (int i = cursorCount - LocationService.LOCATION_QUEUE_SIZE; i < cursorCount; i++) {
                                            Log.i(TAG, "cursorCount - LocationService.LOCATION_QUEUE_SIZE = " + String.valueOf(cursorCount - LocationService.LOCATION_QUEUE_SIZE));
                                            cursor.moveToPosition(i);
                                            Bundle drawLinesBundle = new Bundle();
                                            double tLatitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
                                            double tLongtiude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
                                            Log.i(TAG, "tLatitude=" + tLatitude + " tLongtiude=" + tLongtiude);
                                            //Gps gps = PositionUtil.gps84_To_Gcj02(tLatitude, tLongtiude);
                                            //if (gps != null) {
                                            drawLinesBundle.putDouble("longitude", tLongtiude);
                                            drawLinesBundle.putDouble("latitude", tLatitude);
                                            drawLinesBundle.putFloat("accuracy", cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY)));

                                            Message msg1 = Message.obtain();
                                            msg1.what = UPDATE_DRAW_LINES;
                                            msg1.obj = drawLinesBundle;
                                            handler.sendMessage(msg1);
                                            //}
                                        }
                                    } else {
                                        cursor.moveToLast();

                                        double tLatitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
                                        double tLongtiude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
                                        //Gps gps = PositionUtil.gps84_To_Gcj02(tLatitude, tLongtiude);
                                        //if (gps != null) {
                                        Bundle drawLinesBundle = new Bundle();
                                        drawLinesBundle.putDouble("longitude", tLongtiude);
                                        drawLinesBundle.putDouble("latitude", tLatitude);
                                        drawLinesBundle.putFloat("accuracy", cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY)));

                                        Message msg1 = Message.obtain();
                                        msg1.what = UPDATE_DRAW_LINES;
                                        msg1.obj = drawLinesBundle;
                                        handler.sendMessage(msg1);
                                        //}

                                    }
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
            });
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }
    }
}
