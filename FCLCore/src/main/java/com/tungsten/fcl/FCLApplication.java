package com.tungsten.fcl;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tungsten.fclauncher.utils.FCLPath;
import com.umeng.commonsdk.UMConfigure;

import java.lang.ref.WeakReference;

public class FCLApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static WeakReference<Activity> currentActivity;
    private static Application INSTANCE;
    public static final String UMENG_APPKEY = "69e0f1b36f259537c79a2e80";
    public static final String UMENG_CHANNEL = "GitHub";
    
    @Override
    public void onCreate() {
        // enabledStrictMode();
        super.onCreate();

        // 友盟预初始化（合规要求：在 Application.onCreate 中调用，不采集数据）
        // preInit 耗时极少，不会影响冷启动体验
        UMConfigure.preInit(this, UMENG_APPKEY, UMENG_CHANNEL);

        // 如果用户之前已经同意过隐私协议，直接正式初始化友盟 SDK
        // 首次启动时 isAgree 为 false，会在 EulaFragment 中用户点击同意后再初始化
        SharedPreferences sp = getSharedPreferences("launcher", Context.MODE_PRIVATE);
        if (sp.getBoolean("isAgree", false)) {
            UMConfigure.init(this, UMENG_APPKEY, UMENG_CHANNEL, UMConfigure.DEVICE_TYPE_PHONE, "");
        }

        this.registerActivityLifecycleCallbacks(this);
//        PerfUtil.install();
        FCLPath.loadPaths(getApplicationContext());
        INSTANCE = this;
    }

    public static Activity getCurrentActivity() {
        if (currentActivity != null) {
            return currentActivity.get();
        }
        return null;
    }

    private void enabledStrictMode() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectNetwork()
                .detectCustomSlowCalls()
                .detectDiskReads()
                .detectDiskWrites()
                .detectAll()
                .penaltyLog()
                .build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectAll()
                .penaltyLog()
                .build());
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        currentActivity = new WeakReference<>(activity);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        currentActivity = new WeakReference<>(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {

    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (currentActivity != null && currentActivity.get() == activity) {
            currentActivity = null;
        }
    }

    public static Application INSTANCE() {
        return INSTANCE;
    }
}
