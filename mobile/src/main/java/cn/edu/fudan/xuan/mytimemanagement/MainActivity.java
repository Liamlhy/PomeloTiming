package cn.edu.fudan.xuan.mytimemanagement;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.drawable.ClipDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.util.Date;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements DataApi.DataListener {

    public static MainActivity instance=null;

    private MainActivity theActivity = this;
    NotificationCompat.Builder mNotifiBuilder;
    private GoogleApiClient mGoogleApiClient;
    private String TAG = "MyTimeManagement";
    private String preference_file_key = "cn.edu.fudan.MTM";
    private Button mButtonL, mButtonR;
    //private ProgressBar mProgress;
    private SQLiteDatabase db;
    ClipDrawable mImageDrawable;
    private EditText textBox1, textBox2;
    private long workTime = 20, restTime = 5;
    private boolean byebyeflag = false;

    public static final String PREFS_NAME = "MyPrefsFile";
    public static final String FIRST_RUN = "first";
    private boolean first;
    public boolean neverseeagain=true;
    public SharedPreferences settings;

    private String Imei;
    private boolean isWatchConnected = false;
    private String ver;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // newly added
            if(msg.what == -1)
                toState01();
            else if(msg.what == -2)
                toState02();
            else if(msg.what == -3)
                toState03();
            //
            else if(msg.what == 1) {
                mButtonL.callOnClick();
            } else if(msg.what == 2) {
                mButtonR.callOnClick();
            } else if(msg.what == 0) {
                mImageDrawable.setLevel(0);
            } else if(msg.what <= 10000){
                mImageDrawable.setLevel(msg.what);
            }
            super.handleMessage(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.out.println("here is create");

        instance=this;

        super.onCreate(savedInstanceState);
        //
        Imei = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
        ver = Build.VERSION.RELEASE;

        //Log.d("PHONE-INFO", Imei);
        //Log.d("PHONE-INFO", String.valueOf(isWatchConnected));
        //Log.d("PHONE-INFO", ver);
        //
        setContentView(R.layout.activity_main);
        Button mButton1 = (Button) findViewById(R.id.button1);
        Button mButton2 = (Button) findViewById(R.id.button2);
        textBox1 = (EditText) findViewById(R.id.editTime1);
        textBox2 = (EditText) findViewById(R.id.editTime2);
        mButtonL = (Button) findViewById(R.id.button_left);
        mButtonR = (Button) findViewById(R.id.button_right);
        //mProgress = (ProgressBar) findViewById(R.id.progressBar);
        //mProgress.setProgress(100);
        ImageView img = (ImageView) findViewById(R.id.imageBarView1);
        mImageDrawable = (ClipDrawable) img.getDrawable();
        mImageDrawable.setLevel(10000);
        //Context mContext;

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "onConnected: " + connectionHint);
                        Wearable.DataApi.addListener(mGoogleApiClient, theActivity);
                    }
                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d(TAG, "onConnectionSuspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(result -> Log.d(TAG, "onConnectionFailed: " + result))
                .addApiIfAvailable(Wearable.API)
                .build();
        mGoogleApiClient.connect();

        mNotifiBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.pomelo)
                .setContentTitle("NOW REST :)")
                .setContentText("Your have just finished a working period.");

        toState01();

        // restore time settings
        SharedPreferences sharedPref = theActivity.getSharedPreferences(preference_file_key, Context.MODE_PRIVATE);
        workTime = sharedPref.getInt("workTime", 1200);
        restTime = sharedPref.getInt("restTime", 300);
        textBox1.setText(Long.toString(workTime / 60));
        textBox2.setText(Long.toString(restTime / 60));

        mButton1.setOnClickListener((view) -> sendTimeSettings());
        mButton2.setOnClickListener((view) -> {
            Intent intent = new Intent(this, StatActivity.class);
            startActivity(intent);
        });

        // database
        db = SQLiteDatabase.openOrCreateDatabase(getFilesDir().getPath() + "/my_db.db", null);
        //Log.d(TAG + "h", getFilesDir().getPath());
        String toCreateTable = "create table records(_id integer primary key autoincrement, year integer, " +
                "month integer, date integer, hour integer, minute integer, length integer)";
        try {
            db.execSQL(toCreateTable);
        } catch (SQLiteException e) {
            // do nothing ...
        }

        settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        first = settings.getBoolean(FIRST_RUN, true);
        // Attention: 临时性屏蔽介绍页面！
        first = false;
        //
        if(first){
            Intent intro_intent = new Intent(this, IntroActivity.class);
            startActivity(intro_intent);

        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop(){
        super.onStop();
       // System.out.println("here's stop");
       // System.out.println(neverseeagain);
        if(!neverseeagain){
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(FIRST_RUN, true);
            editor.commit();
        }else{
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(FIRST_RUN, false);
            editor.commit();
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().equals("/watch-connected")) {
                    isWatchConnected = true;
                    PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/watch-button-state");
                    putDataMapRequest.getDataMap().putInt("watch-button-state", new Random().nextInt());
                    PutDataRequest request = putDataMapRequest.asPutDataRequest();
                    Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                            .setResultCallback(dataItemResult -> {
                                PutDataMapRequest rPutDataMapRequest = PutDataMapRequest.create("/watch-button-state");
                                if(mButtonL.getText().toString().equals("Working"))
                                    rPutDataMapRequest.getDataMap().putInt("watch-button-state", 1);
                                else if(mButtonL.getText().toString().equals("Go Resting"))
                                    rPutDataMapRequest.getDataMap().putInt("watch-button-state", 2);
                                else
                                    rPutDataMapRequest.getDataMap().putInt("watch-button-state", 3);
                                PutDataRequest rRequest = rPutDataMapRequest.asPutDataRequest();
                                Wearable.DataApi.putDataItem(mGoogleApiClient, rRequest);
                            });
                }
                /*
                if (item.getUri().getPath().equals("/set-progress")) {
                    Log.d(TAG, "received set-progress");
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    int progress = (int) (10000.0 * dataMap.getFloat("the-num"));
                    if (progress > 2) {
                        Message msg = mHandler.obtainMessage();
                        msg.what = progress;
                        msg.sendToTarget();
                    }
                }
                */
                if (item.getUri().getPath().equals("/watch-upper-button")) {
                    //mButtonL.callOnClick();
                    Message msg = mHandler.obtainMessage();
                    msg.what = 1;
                    msg.sendToTarget();
                }
                if (item.getUri().getPath().equals("/watch-lower-button")) {
                    //mButtonR.callOnClick();
                    Message msg = mHandler.obtainMessage();
                    msg.what = 2;
                    msg.sendToTarget();
                }
            }
        }
    }

    private void setProgress(float thenum) {
        Log.d(TAG, "received set-progress");
        int progress = (int)(thenum * 10000.0);
        if (progress > 2) {
            Message msg = mHandler.obtainMessage();
            msg.what = progress;
            msg.sendToTarget();
        }
    }

    public void changeWatchButtonState(int num) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/watch-button-state");
        putDataMapRequest.getDataMap().putInt("watch-button-state", num);
        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(dataItemResult -> Log.d("TIME-MNG", "'watch set to the new state"));
    }

    // toStateXX functions only change buttons
    protected void toState01() {
        changeWatchButtonState(1);
        mButtonL.setText("Working");
        mButtonR.setText("Resting");
        mButtonL.setOnClickListener((view) -> {
            byebyeflag = false;
            //mProgress.setProgress(0);
            mImageDrawable.setLevel(0);
            toState02();
            try {
                new newProgressUpdate("work");
            } catch (Exception e) {
                e.printStackTrace();
            }
            // for debug only ...
            //storeRecord(new Date(), 0);
            Runnable ttmmpp = () -> {
                String ip = Utils.getIPAddress(true);
                String t = new Date().toString().replaceAll(" ", "+");
                Log.d("PHONE-INFO", Imei);
                Log.d("PHONE-INFO", String.valueOf(isWatchConnected));
                Log.d("PHONE-INFO", ver);
                Log.d("PHONE-INFO", ip);
                Log.d("PHONE-INFO", t);
                HttpGet httpGet = new HttpGet("http://119.28.64.70:5000/watchapp/"+Imei+"%23"+String.valueOf(isWatchConnected)+"%23"+ver+"%23"+ip+"%23"+t);
                Log.d("PHONE-INFO", "http://119.28.64.70:5000/watchapp/"+Imei+"%23"+String.valueOf(isWatchConnected)+"%23"+ver+"%23"+ip+"%23"+t);
                HttpClient httpClient = new DefaultHttpClient();
                try {
                    httpClient.execute(httpGet);
                } catch (Exception e) {
                    Log.d("PHONE-INFO-FAILURE", "...");
                    e.printStackTrace();
                }
            };
            new Thread (ttmmpp, String.valueOf(new Random().nextInt())).start();
        });
        mButtonR.setOnClickListener((view) -> {
            byebyeflag = false;
            //mProgress.setProgress(0);
            mImageDrawable.setLevel(0);
            toState03();
            try {
                new newProgressUpdate("rest");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    protected void toState02() {
        changeWatchButtonState(2);
        mButtonL.setText("Go Resting");
        mButtonR.setText("Reset");
        mButtonL.setOnClickListener((view) -> {
            byebyeflag = true;
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //mProgress.setProgress(0);
            mImageDrawable.setLevel(0);
            toState03();
            try {
                new newProgressUpdate("rest");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        mButtonR.setOnClickListener((view) -> {
            byebyeflag = true;
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mImageDrawable.setLevel(10000);
            toState01();
        });
    }

    protected void toState03() {
        changeWatchButtonState(3);
        mButtonL.setText("Go Working");
        mButtonR.setText("Reset");
        mButtonL.setOnClickListener((view) -> {
            byebyeflag = true;
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //mProgress.setProgress(0);
            mImageDrawable.setLevel(0);
            toState02();
            try {
                new newProgressUpdate("work");
            } catch (Exception e) {
                e.printStackTrace();
            }

            Runnable ttmmpp = () -> {
                String ip = Utils.getIPAddress(true);
                String t = new Date().toString().replaceAll(" ", "+");
                Log.d("PHONE-INFO", Imei);
                Log.d("PHONE-INFO", String.valueOf(isWatchConnected));
                Log.d("PHONE-INFO", ver);
                Log.d("PHONE-INFO", ip);
                Log.d("PHONE-INFO", t);
                HttpGet httpGet = new HttpGet("http://119.28.64.70:5000/watchapp/"+Imei+"%23"+String.valueOf(isWatchConnected)+"%23"+ver+"%23"+ip+"%23"+t);
                Log.d("PHONE-INFO", "http://119.28.64.70:5000/watchapp/"+Imei+"%23"+String.valueOf(isWatchConnected)+"%23"+ver+"%23"+ip+"%23"+t);
                HttpClient httpClient = new DefaultHttpClient();
                try {
                    httpClient.execute(httpGet);
                } catch (Exception e) {
                    Log.d("PHONE-INFO-FAILURE", "...");
                    e.printStackTrace();
                }
            };
            new Thread (ttmmpp, String.valueOf(new Random().nextInt())).start();

        });
        mButtonR.setOnClickListener((view) -> {
            byebyeflag = true;
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mImageDrawable.setLevel(10000);
            toState01();
        });
    }

    public void sendTimeSettings() {
        int workTime, restTime;
        workTime = Integer.parseInt(textBox1.getText().toString());
        restTime = Integer.parseInt(textBox2.getText().toString());
        //
        workTime = workTime * 60; restTime = restTime * 60;
        SharedPreferences sharedPref = theActivity.getSharedPreferences(preference_file_key, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("workTime", workTime);
        editor.putInt("restTime", restTime);
        editor.apply();
        //
        this.workTime = workTime; this.restTime = restTime;
        //
        String message2show = "Work: " + textBox1.getText().toString() + "\nRest: " + textBox2.getText().toString();
        new AlertDialog.Builder(this).setTitle("Set Successful").setMessage(message2show).setPositiveButton("OK", null).show();
    }

    private boolean storeRecord(Date currDate, int length) {
        try {
            //db.insert("records", null, cValue);
            String insert_sql = String.format("INSERT INTO records(date,month,hour,length,minute,year) VALUES (%d,%d,%d,%d,%d,%d)",
                    currDate.getDate(), currDate.getMonth(), currDate.getHours(), length, currDate.getMinutes(), currDate.getYear());
            Log.d(TAG, insert_sql);
            db.execSQL(insert_sql);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private class newProgressUpdate extends Thread {
        private long startTime = System.nanoTime();
        private long timeInSecond;
        private char currStatus;

        newProgressUpdate(String type) throws Exception {
            switch (type) {
                case "rest":
                    timeInSecond = restTime;
                    currStatus = 'R';
                    break;
                case "work":
                    timeInSecond = workTime;
                    currStatus = 'W';
                    break;
                default:
                    throw new Exception();
            }
            this.start();
        }

        public void run() {
            Message smsg = null;
            long currTime = System.nanoTime();
            while(currTime - startTime < timeInSecond * 1000000000) {
                if(byebyeflag) {
                    byebyeflag = false;
                    return;
                }
                currTime = System.nanoTime();
                // publishProgress
                setProgress( (float)((currTime - startTime) / timeInSecond) / 100000 / 10000 );
                PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/set-progress");
                putDataMapRequest.getDataMap().putFloat("the-num", (float)((currTime - startTime) / timeInSecond) / 100000 / 10000);
                PutDataRequest request = putDataMapRequest.asPutDataRequest();
                Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                        .setResultCallback(dataItemResult -> Log.d(TAG, "set progress"));
                //
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if(currStatus == 'W') {
                storeRecord(new Date(), (int)timeInSecond / 60);
                // was " publishProgress(-2L); "
                Message msg = mHandler.obtainMessage();
                msg.what = 0;
                msg.sendToTarget();
                // toState03
                smsg = mHandler.obtainMessage();
                smsg.what = -3;
                smsg.sendToTarget();
                try {
                    new newProgressUpdate("rest");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //
                Runnable ttmmpp = () -> {
                    String ip = Utils.getIPAddress(true);
                    String t = new Date().toString().replaceAll(" ", "+");
                    Log.d("PHONE-INFO", Imei);
                    Log.d("PHONE-INFO", String.valueOf(isWatchConnected));
                    Log.d("PHONE-INFO", ver);
                    Log.d("PHONE-INFO", ip);
                    Log.d("PHONE-INFO", t);
                    HttpGet httpGet = new HttpGet("http://119.28.64.70:5000/watchapp/"+Imei+"%23"+String.valueOf(isWatchConnected)+"%23"+ver+"%23"+ip+"%23"+t);
                    Log.d("PHONE-INFO", "http://119.28.64.70:5000/watchapp/"+Imei+"%23"+String.valueOf(isWatchConnected)+"%23"+ver+"%23"+ip+"%23"+t);
                    HttpClient httpClient = new DefaultHttpClient();
                    try {
                        httpClient.execute(httpGet);
                    } catch (Exception e) {
                        Log.d("PHONE-INFO-FAILURE", "...");
                        e.printStackTrace();
                    }
                };
                new Thread (ttmmpp, String.valueOf(new Random().nextInt())).start();
                //
                NotificationManager notificationManager =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(new Random().nextInt(), mNotifiBuilder.build());
            } else if(currStatus == 'R') {
                Message msg = mHandler.obtainMessage();
                msg.what = 0;
                msg.sendToTarget();
                // toState02
                smsg = mHandler.obtainMessage();
                smsg.what = -2;
                smsg.sendToTarget();
                try {
                    new newProgressUpdate("work");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                Message msg = mHandler.obtainMessage();
                msg.what = 10000;
                msg.sendToTarget();
                // toState01
                smsg = mHandler.obtainMessage();
                smsg.what = -1;
                smsg.sendToTarget();
            }
        }
    }

}
