package com.tungsten.fclauncher;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import com.umeng.commonsdk.UMConfigure;

public class FCLApplication extends Application implements Application.ActivityLifecycleCallbacks {
    
    private static Activity currentActivity;
    
    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化友盟SDK（精简版）
        UMConfigure.init(
            this, 
            "69e0f1b36f259537c79a2e80", 
            "GitHub", 
            UMConfigure.DEVICE_TYPE_PHONE, 
            "1853c4972a25c98245161c0bc6593e08"
        );
        
        // 注册Activity生命周期回调
        registerActivityLifecycleCallbacks(this);
    }
    
    public static Activity getCurrentActivity() {
        return currentActivity;
    }
    
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        currentActivity = activity;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        currentActivity = activity;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        currentActivity = activity;
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }
}