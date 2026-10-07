package com.mobiled.android;

import android.app.Application;
import android.content.Intent;

import androidx.multidex.MultiDexApplication;

public class MobiLedApp extends MultiDexApplication {
    private static Intent musicProjectionData;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public static void setMusicProjectionData(Intent data) {
        musicProjectionData = data;
    }

    public static Intent getMusicProjectionData() {
        return musicProjectionData;
    }
}
