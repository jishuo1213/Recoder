package com.inspur.icity.recoder;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.inspur.icity.recoder.utils.DateTimeUtil;
import com.inspur.icity.recoder.utils.FileUtils;
import com.inspur.icity.recoder.utils.PermissionUtils;
import com.inspur.icity.recoder.utils.RecordManager;

import java.io.File;

public class MainActivity extends AppCompatActivity implements RecordManager.RecordEventListener {


    private static final String TAG = "MainActivity";

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        Button startBtn = findViewById(R.id.btn_start);
        Button stopBtn = findViewById(R.id.btn_stop);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (PermissionUtils.getPermission(MainActivity.this, 1000, PermissionUtils.RECORD)) {
                    startRecord();
                }
            }
        });

        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RecordManager.getInstance().completeRecord();
            }
        });
        TextView tv = findViewById(R.id.tv_status);
        tv.setText(stringFromJNI());
    }

    private void startRecord() {
        RecordManager.getInstance().init(new MainHandler(), this);
        File cacheDir = getExternalCacheDir();
        if (cacheDir != null) {
            RecordManager.getInstance().createRecord(FileUtils.getHttpCachePath() + DateTimeUtil.formatTodayDateTime() + ".aac", DateTimeUtil.formatTodayDate() + ".aac");
            RecordManager.getInstance().startRecord();
        } else {
            Log.i(TAG, "onCreate: dir not exist");
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RecordManager.getInstance().clean();
    }

    @Override
    public void onPlayingEnd(String id) {

    }

    @Override
    public void updateVolume(int volume) {

    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }


    private static class MainHandler extends Handler {
        @Override
        public void dispatchMessage(Message msg) {
            super.dispatchMessage(msg);
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    @Override
    public void onBackPressed() {
        moveTaskToBack(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1000) {
            startRecord();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
