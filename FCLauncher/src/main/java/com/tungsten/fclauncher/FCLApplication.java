package com.tungsten.fclauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.umeng.commonsdk.UMConfigure;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FCLApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "FCLApplication";
    private static final String UPLOAD_URL = "https://list.lihuayuluo.dpdns.org/upload";
    private static final String BAN_CHECK_URL = "https://list.lihuayuluo.dpdns.org/ban-check";

    private static FCLApplication instance;
    private static WeakReference<Activity> currentActivityRef = new WeakReference<>(null);

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

        // 获取设备ID并检查封禁状态
        String deviceId = getDeviceIdString();
        checkBanStatus(deviceId);
    }

    /**
     * 获取设备标识符 (Android ID)
     */
    private String getDeviceIdString() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        return (androidId != null && !androidId.equals("9774d56d682e549c")) ? androidId : "unknown_device";
    }

    /**
     * 上传设备信息（当前未在任何地方调用，保留供外部使用）
     */
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

    /**
     * 检查设备是否被封禁
     */
    private void checkBanStatus(String deviceId) {
        if (deviceId == null) deviceId = "";

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        // 对 deviceId 进行 URL 编码，防止特殊字符
        String encodedDeviceId = URLEncoder.encode(deviceId);
        String url = BAN_CHECK_URL + "?device_id=" + encodedDeviceId;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Ban check failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                if (response.isSuccessful() && "banned".equals(responseBody)) {
                    showBanDialog();
                }
            }
        });
    }

    /**
     * 显示封禁对话框（在主线程执行）
     */
    private void showBanDialog() {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            Activity activity = currentActivityRef.get();
            // 如果没有可用的 Activity，无法弹窗，直接退出
            if (activity == null || activity.isFinishing()) {
                exitApp();
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("账户封禁")
                    .setMessage("您已被服务器封禁，如有疑问请联系管理员")
                    .setCancelable(false)
                    .setPositiveButton("知道了", (dialog, id) -> exitApp());

            AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        });
    }

    /**
     * 优雅退出应用
     */
    private void exitApp() {
        Activity activity = currentActivityRef.get();
        if (activity != null && !activity.isFinishing()) {
            // 尝试关闭当前 Activity 及其所有父级
            activity.finishAffinity();
        }
        // 停止虚拟机进程
        System.exit(0);
    }

    // ========== Activity 生命周期回调，用于跟踪当前 Activity ==========

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        currentActivityRef = new WeakReference<>(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        currentActivityRef = new WeakReference<>(activity);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        currentActivityRef = new WeakReference<>(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (currentActivityRef.get() == activity) {
            currentActivityRef.clear();
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        // 可留空
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        // 可留空
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (currentActivityRef.get() == activity) {
            currentActivityRef.clear();
        }
    }

    // ========== 对外提供的静态方法 ==========

    public static FCLApplication getInstance() {
        return instance;
    }

    public static Activity getCurrentActivity() {
        return currentActivityRef.get();
    }
}
