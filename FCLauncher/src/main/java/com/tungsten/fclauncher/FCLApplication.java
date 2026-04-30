package com.tungsten.fclauncher;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.umeng.commonsdk.UMConfigure;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FCLApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static FCLApplication instance;
    private static Activity currentActivity;
    // 如需修改请参考https://github.com/xiaoniao427/list.lihuayuluo.dpdns.org/
    private static final String TAG = "FCLApplication";
    private static final String UPLOAD_URL = "https://list.lihuayuluo.dpdns.org/upload";
    private static final String BAN_CHECK_URL = "https://list.lihuayuluo.dpdns.org/ban-check";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
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
        
        // 获取设备ID和用户名
        String deviceId = getDeviceId();
        String username = "DefaultPlayer"; // 替换为实际用户名获取逻辑
        
        // 上传设备信息
        uploadDeviceInfo(username, deviceId);
        
        // 检查黑名单状态
        checkBanStatus(deviceId);
    }
    
    private String getDeviceId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }
    
    private void uploadDeviceInfo(String username, String deviceId) {
        long timestamp = System.currentTimeMillis();
        
        JSONObject data = new JSONObject();
        try {
            data.put("username", username);
            data.put("device_id", deviceId);
            data.put("timestamp", timestamp);
        } catch (JSONException e) {
            Log.e(TAG, "JSON error", e);
            return;
        }
        
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), 
                data.toString()
        );
        
        Request request = new Request.Builder()
                .url(UPLOAD_URL)
                .post(body)
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Upload failed", e);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Upload error: " + response.body().string());
                }
            }
        });
    }
    
    private void checkBanStatus(String deviceId) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        
        Request request = new Request.Builder()
                .url(BAN_CHECK_URL + "?device_id=" + deviceId)
                .get()
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Ban check failed", e);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && "banned".equals(response.body().string())) {
                    // 显示无法关闭的封禁窗口
                    showBanDialog();
                }
            }
        });
    }
    
    private void showBanDialog() {
        // 在主线程显示对话框
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
            builder.setTitle("账户封禁")
                   .setMessage("您已被服务器封禁，如有疑问请联系管理员")
                   .setCancelable(false)
                   .setPositiveButton("知道了", (dialog, id) -> {
                       // 点击确定后退出应用
                       System.exit(0);
                   });
            
            AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        });
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

    public static FCLApplication getInstance() {
        return instance;
    }
}