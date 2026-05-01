package com.tungsten.fclcore.launch;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class DeviceInfoUploader {

    private static final String TAG = "DeviceInfoUploader";
    private static final String UPLOAD_URL = "https://list.lihuayuluo.dpdns.org/upload";
    private static final String BAN_CHECK_URL = "https://list.lihuayuluo.dpdns.org/ban-check";

    private DeviceInfoUploader() {}

    private static String getDeviceId(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return (androidId != null && !androidId.equals("9774d56d682e549c")) ? androidId : "unknown_device";
    }

    public static void uploadDeviceInfo(String username, String deviceId, Context context) {
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = getDeviceId(context);
        }
        final String finalDeviceId = deviceId;
        long timestamp = System.currentTimeMillis();

        JSONObject data = new JSONObject();
        try {
            data.put("username", username);
            data.put("device_id", finalDeviceId);
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

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), data.toString());
        Request request = new Request.Builder().url(UPLOAD_URL).post(body).build();

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

    public static void checkBanStatus(String deviceId, Context context) {
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = getDeviceId(context);
        }
        final String finalDeviceId = deviceId;

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        String encodedDeviceId = URLEncoder.encode(finalDeviceId);
        String url = BAN_CHECK_URL + "?device_id=" + encodedDeviceId;

        Request request = new Request.Builder().url(url).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Ban check failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                if (response.isSuccessful() && "banned".equals(responseBody)) {
                    showBanDialog(context);
                }
            }
        });
    }

    private static void showBanDialog(Context context) {
        new Handler(Looper.getMainLooper()).post(() -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            builder.setTitle("账户封禁")
                    .setMessage("您已被服务器封禁，如有疑问请联系管理员")
                    .setCancelable(false)
                    .setPositiveButton("知道了", (dialog, which) -> {
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).finishAffinity();
                        }
                        System.exit(0);
                    });
            android.app.AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        });
    }
}
