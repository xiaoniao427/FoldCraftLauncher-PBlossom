package com.tungsten.fcl;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;

/**
 * 友盟工具类（官方要求实现）
 */
public class UMUtils {
    
    /**
     * 判断是否主进程（用于多进程初始化）
     */
    public static boolean isMainProgress(Context context) {
        try {
            int pid = android.os.Process.myPid();
            String processName = "";
            android.app.ActivityManager manager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            for (android.app.ActivityManager.RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
                if (processInfo.pid == pid) {
                    processName = processInfo.processName;
                }
            }
            String mainProcessName = context.getApplicationInfo().processName;
            return TextUtils.isEmpty(mainProcessName) || processName.equals(mainProcessName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }
}