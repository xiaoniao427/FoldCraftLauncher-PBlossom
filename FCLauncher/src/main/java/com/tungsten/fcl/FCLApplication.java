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
        
        // 友盟预初始化
        initializeUmeng();
    }
    
    /**
     * 初始化友盟统计和推送
     * 遵循友盟官方文档推荐流程：预初始化 -> 用户同意隐私协议 -> 正式初始化
     */
    private void initializeUmeng() {
        try {
            String appKey = "69e0f1b36f259537c79a2e80";
            String channel = "GitHub";
            
            // 统计分析初始化
            UMConfigure.init(this, appKey, channel, UMConfigure.DEVICE_TYPE_PHONE, "Umeng");
            UMConfigure.setLogEnabled(true);
            
            // 推送服务预初始化
            PushHelper.preInit(this);
            
            // 统计分析页面统计
            MobclickAgent.onPageStart("SplashActivity");
            MobclickAgent.onEvent(this, "app_launch");
            
            // 注册推送服务
            PushAgent.getInstance(this).register(new com.umeng.message.UPushRegisterCallback() {
                @Override
                public void onSuccess(String deviceToken) {
                    android.util.Log.i("Umeng", "推送注册成功，deviceToken: " + deviceToken);
                }

                @Override
                public void onFailure(String errCode, String errDesc) {
                    android.util.Log.e("Umeng", "推送注册失败: " + "code:" + errCode + ", desc:" + errDesc);
                }
            });
            
            umengInitialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 用户同意隐私协议后调用此方法进行正式初始化
     * 必须在用户同意隐私政策后才调用
     */
    public static void onUserConsent() {
        if (umengInitialized) {
            return;
        }
        
        try {
            // 统计分析页面统计
            MobclickAgent.onPageStart("MainActivity");
            
            // 推送服务正式初始化
            PushHelper.init(INSTANCE());
            
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
