package com.tungsten.fclauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaDrm;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.umeng.commonsdk.UMConfigure;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.util.UUID;
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
    private static final String PREF_NAME = "device_identity";
    private static final String KEY_DEVICE_ID = "device_unique_id";

    private static FCLApplication instance;
    private static WeakReference<Activity> currentActivityRef = new WeakReference<>(null);
    private String cachedDeviceId = null;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        UMConfigure.init(
                this,
                "69e0f1b36f259537c79a2e80",
                "GitHub",
                UMConfigure.DEVICE_TYPE_PHONE,
                "1853c4972a25c98245161c0bc6593e08"
        );

        registerActivityLifecycleCallbacks(this);

        new Thread(() -> {
            String deviceId = getDeviceUniqueId();
            Log.i(TAG, "Device unique ID: " + deviceId);
            checkBanStatus(deviceId);
        }).start();
    }

    private String getDeviceUniqueId() {
        if (cachedDeviceId != null) return cachedDeviceId;

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String storedId = prefs.getString(KEY_DEVICE_ID, null);
        if (storedId != null && !storedId.isEmpty()) {
            cachedDeviceId = storedId;
            return cachedDeviceId;
        }

        String widevineId = getWidevineDeviceId();
        if (widevineId != null) {
            saveDeviceId(widevineId);
            cachedDeviceId = widevineId;
            return cachedDeviceId;
        }

        String fallbackId = UUID.randomUUID().toString();
        saveDeviceId(fallbackId);
        cachedDeviceId = fallbackId;
        Log.w(TAG, "Widevine ID unavailable, generated fallback UUID: " + fallbackId);
        return fallbackId;
    }

    private String getWidevineDeviceId() {
        try {
            UUID widevineUuid = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
            MediaDrm mediaDrm = new MediaDrm(widevineUuid);
            byte[] deviceUniqueIdArray = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID);
            mediaDrm.close();
            if (deviceUniqueIdArray != null && deviceUniqueIdArray.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (byte b : deviceUniqueIdArray) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get Widevine device ID", e);
        }
        return null;
    }

    private void saveDeviceId(String deviceId) {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DEVICE_ID, deviceId)
                .apply();
    }

    /**
     * 只上传 device_id 和 timestamp，不再包含 username
     */
    private void uploadDeviceInfo(String deviceId) {
        long timestamp = System.currentTimeMillis();

        JSONObject data = new JSONObject();
        try {
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
                } else {
                    Log.i(TAG, "Upload success for device: " + deviceId);
                }
            }
        });
    }

    private void checkBanStatus(String deviceId) {
    final String finalDeviceId = (deviceId == null) ? "" : deviceId;
    
    OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    String encodedDeviceId = URLEncoder.encode(finalDeviceId);
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
            } else {
                uploadDeviceInfo(finalDeviceId);
            }
        }
    });
    }

    private void showBanDialog() {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            Activity activity = currentActivityRef.get();
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

    private void exitApp() {
        Activity activity = currentActivityRef.get();
        if (activity != null && !activity.isFinishing()) {
            activity.finishAffinity();
        }
        System.exit(0);
    }

    // ActivityLifecycleCallbacks 实现（不变）
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
    public void onActivityStopped(Activity activity) { }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (currentActivityRef.get() == activity) {
            currentActivityRef.clear();
        }
    }

    public static FCLApplication getInstance() {
        return instance;
    }

    public static Activity getCurrentActivity() {
        return currentActivityRef.get();
    }
}
