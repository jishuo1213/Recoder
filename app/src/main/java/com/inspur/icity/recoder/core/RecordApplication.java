package com.inspur.icity.recoder.core;

import android.app.Application;

import com.inspur.icity.recoder.utils.FileUtils;

public class RecordApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FileUtils.init(this);
    }
}
