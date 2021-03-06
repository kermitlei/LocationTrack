package com.tencent.tws.locationtrack;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import com.tencent.tws.locationtrack.util.DoublePoint;
import com.tencent.tws.locationtrack.util.LocationUtil;
import com.tencent.tws.locationtrack.util.MySurfaceRenderer;
import com.tencent.tws.locationtrack.util.SensorUtil;

import java.util.Timer;
import java.util.TimerTask;


public class TrackModeActivity extends Activity implements SensorEventListener {
    private static String TAG = "TrackModeActivity";

    private MySurfaceView GLView;
    private boolean sysOk;
    private SensorUtil SU;

    WakeLock mWakeLock;
    Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        mWakeLock.acquire();
        SU = new SensorUtil(this);
        LocationUtil.setSensorUtil(SU);
        LocationUtil.init(false);
        if (SU.systemMeetsRequirements()) {

            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            setContentView(R.layout.trackmode);
            GLView = (MySurfaceView) findViewById(R.id.glSurfaceViewID);
//        	GLView = new MySurfaceView(this);
//        	setContentView(GLView);
            sysOk = true;

            Button btnEndTrack = (Button) this.findViewById(R.id.btnEndTrack);
            Button btnStartNavigate = (Button) this.findViewById(R.id.btnStartNavigate);

            btnEndTrack.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    SU.unregisterListeners();
                    LocationUtil.setEndTrack(true);
                }
            });

            btnStartNavigate.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    LocationUtil.cloneTrail();
                    DoublePoint dPoint = LocationUtil.getCurrentLocation();

                    LocationUtil.reset(true);
                    LocationUtil.setStartLocation(dPoint);
                    LocationUtil.getBreadCrumbs().add(new DoublePoint(dPoint.getX(), dPoint.getY()));
                    SU.registerListeners();
                    LocationUtil.setEndTrack(false);
                }
            });

        } else {

            //setContentView(R.layout.main);
            sysOk = false;
        }


        Timer timer = new Timer();
        timer.schedule(task, 30000);

//		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
//		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);//鎸囨槑涓�釜涓庤繙绋嬭澶囧缓绔嬬殑浣庣骇鍒紙ACL锛夎繛鎺ャ�
//		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);//鎸囨槑涓�釜鏉ヨ嚜浜庤繙绋嬭澶囩殑浣庣骇鍒紙ACL锛夎繛鎺ョ殑鏂紑
//		this.registerReceiver(BluetoothReciever, filter); // 涓嶈蹇樹簡涔嬪悗瑙ｉ櫎缁戝畾
    }

    TimerTask task = new TimerTask() {
        @Override
        public void run() {
            // TODO Auto-generated method stub
            SU.unregisterListeners();
            LocationUtil.setEndTrack(true);
            Vibrator vibrator = (Vibrator) mContext
                    .getSystemService(Context.VIBRATOR_SERVICE);

            vibrator.vibrate(500);
        }

    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sysOk) {
            SU.registerListeners();
            GLView.onResume();
            LocationUtil.setEndTrack(false);
        }
        if (mWakeLock != null) {
            mWakeLock.acquire();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sysOk) {
            SU.unregisterListeners();
            GLView.onPause();
        }
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //LocationUtil.reset();
        return true;
    }

    public void onSensorChanged(SensorEvent event) {
        SU.routeEvent(event);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

//	private BroadcastReceiver BluetoothReciever = new BroadcastReceiver() {
//		@Override
//		public void onReceive(Context context, Intent intent) {
//			
//			Log.d(TAG, "=========蓝牙的状态为======== "+intent.getAction());
//			if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
//				NotifyUtil.vibrate(GlobalObj.g_appContext, 50);
//				SU.registerListeners();
//			}
//		}
//	};
}

class MySurfaceView extends GLSurfaceView {
    public MySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setRenderer(new MySurfaceRenderer());
    }
}
