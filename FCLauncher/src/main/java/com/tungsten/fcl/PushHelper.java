package com.tungsten.fcl;

import android.content.Context;
import android.util.Log;

import com.umeng.commonsdk.UMConfigure;
import com.umeng.message.PushAgent;
import com.umeng.message.UConfig;
import com.umeng.message.UmengMessageHandler;
import com.umeng.message.UmengMessageCallback;
import com.umeng.message.UmengMessageHandler;
import com.umeng.message.UmengMessageCallback;
import com.umeng.message.UmengMessageHandler;
import com.umeng.message.UmengMessageCallback;
import com.umeng.message.UmengMessageHandler;
import com.umeng.message.UmengMessageCallback;
import com.umeng.message.UmengMessageHandler;
import com.umeng.message.UmengMessageCallback;

import java.util.HashMap;

/**
 * 友盟推送工具类
 * 参考文档：https://developer.umeng.com/docs/67966/detail/98585
 */
public class PushHelper {
    private static final String TAG = "UmengPush";
    private static boolean initialized = false;
    
    /**
     * 预初始化推送SDK
     * 必须在Application的onCreate中调用
     */
    public static void preInit(Context context) {
        try {
            // 日志开关
            UMConfigure.setLogEnabled(true);
            
            // 预初始化
            PushAgent.getInstance(context).preInit(context);
            
            Log.d(TAG, "Umeng Push preInit success");
        } catch (Exception e) {
            Log.e(TAG, "Umeng Push preInit failed: " + e.getMessage());
        }
    }
    
    /**
     * 正式初始化推送SDK
     * 在用户同意隐私政策后调用
     */
    public static void init(Context context) {
        if (initialized) {
            Log.d(TAG, "Umeng Push already initialized");
            return;
        }
        
        try {
            // 获取消息推送实例
            PushAgent pushAgent = PushAgent.getInstance(context);
            
            // 注册推送服务
            pushAgent.register(new UPushRegisterCallback() {
                
                @Override
                public void onSuccess(String deviceToken) {
                    // 注册成功会返回deviceToken
                    Log.i(TAG, "注册成功：deviceToken：--> " + deviceToken);
                }
                
                @Override
                public void onFailure(String errCode, String errDesc) {
                    Log.e(TAG, "注册失败：--> " + "code:" + errCode + ", desc:" + errDesc);
                }
            });
            
            // 设置消息处理回调
            pushAgent.setMessageHandler(new UmengMessageHandler() {
                @Override
                public void dealMessage(Context context, UmengMessageCallback message) {
                    super.dealMessage(context, message);
                    // 自定义消息处理逻辑
                }
            });
            
            // 配置推送参数
            pushAgent.setDebugMode(true);
            
            initialized = true;
            Log.d(TAG, "Umeng Push init success");
        } catch (Exception e) {
            Log.e(TAG, "Umeng Push init failed: " + e.getMessage());
        }
    }
    
    /**
     * 应用活跃统计
     * 必须在SplashActivity或MainActivity的onCreate中调用
     */
    public static void onAppStart(Context context) {
        try {
            PushAgent.getInstance(context).onAppStart();
            Log.d(TAG, "Umeng Push onAppStart success");
        } catch (Exception e) {
            Log.e(TAG, "Umeng Push onAppStart failed: " + e.getMessage());
        }
    }
    
    /**
     * 检查是否是主进程
     */
    public static boolean isMainProcess(Context context) {
        String processName = getProcessName(context);
        return processName == null || processName.equals(context.getPackageName());
    }
    
    /**
     * 获取当前进程名
     */
    private static String getProcessName(Context context) {
        try {
            android.app.ActivityManager activityManager = (android.app.ActivityManager) context
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                return context.getPackageName();
            }
            
            for (android.app.ActivityManager.RunningAppProcessInfo processInfo : 
                    activityManager.getRunningAppProcesses()) {
                if (processInfo.pid == android.os.Process.myPid()) {
                    return processInfo.processName;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get process name: " + e.getMessage());
        }
        return context.getPackageName();
    }
}