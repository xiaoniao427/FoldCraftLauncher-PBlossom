package com.tungsten.fcl;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.StrictMode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tungsten.fclauncher.utils.FCLPath;
import com.umeng.analytics.MobclickAgent;
import com.umeng.commonsdk.UMConfigure;
import com.umeng.message.PushAgent;
import com.umeng.message.UPushSettingCallback; // 导入回调接口

import java.lang.ref.WeakReference;

public class FCLApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static WeakReference<Activity> currentActivity;
    private static Application INSTANCE;
    private static boolean umengInitialized = false;

    @Override
    public void onCreate() {
        // enabledStrictMode();
        super.onCreate();
        this.registerActivityLifecycleCallbacks(this);
//        PerfUtil.install();
        FCLPath.loadPaths(getApplicationContext());
        INSTANCE = this;
        
        // Umeng 预初始化
        initializeUmeng();
    }
    
    /**
     * 初始化友盟统计和推送
     */
    private void initializeUmeng() {
        try {
            String appKey = "69e0f1b36f259537c79a2e80";
            String channel = "GitHub";
            
            // 统计分析初始化
            UMConfigure.init(this, appKey, channel, UMConfigure.DEVICE_TYPE_PHONE, "Umeng");
            UMConfigure.setLogEnabled(true);
            
            // 正式初始化
            MobclickAgent.onPageStart("SplashActivity");
            MobclickAgent.onEvent(this, "app_launch");
            
            // 推送服务初始化 - 添加回调
            PushAgent mPushAgent = PushAgent.getInstance(this);
            mPushAgent.enable(new UPushSettingCallback() {
                @Override
                public void onSuccess() {
                    // 推送服务开启成功
                }
                @Override
                public void onFailure(String code, String message) {
                    // 推送服务开启失败
                }
            });
            mPushAgent.setDebugMode(true);
            
            umengInitialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 用户同意隐私协议后调用此方法进行正式初始化
     */
    public static void onUserConsent() {
        if (umengInitialized) {
            return;
        }
        
        try {
            // 正式初始化统计分析
            MobclickAgent.onPageStart("MainActivity");
            
            // 推送服务正式启用 - 添加回调
            PushAgent.getInstance(INSTANCE()).enable(new UPushSettingCallback() {
                @Override
                public void onSuccess() {
                    // 推送服务开启成功
                }
                @Override
                public void onFailure(String code, String message) {
                    // 推送服务开启失败
                }
            });
            
            umengInitialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
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
