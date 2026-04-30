package com.tungsten.fclauncher.utils;

import android.content.Context;
import android.util.Log;
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

public class DeviceInfoUploader {
    private static final String TAG = "DeviceInfoUploader";
    private static final String UPLOAD_URL = "https://your-worker-url.dev/upload";
    private static final String BAN_CHECK_URL = "https://your-worker-url.dev/ban-check";
    
    // 上传设备信息
    public static void uploadDeviceInfo(String username, String deviceId, Context context) {
        // 确保用户名不为空
        if (username == null || username.isEmpty()) {
            // 尝试从SharedPreferences获取
            username = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                             .getString("username", "UnknownPlayer");
        }
        
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
    
    // 检查黑名单状态
    public static void checkBanStatus(String deviceId, Context context) {
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
                    showBanDialog(context);
                }
            }
        });
    }
    
    // 显示封禁对话框
    private static void showBanDialog(Context context) {
        // 在主线程显示对话框
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            builder.setTitle("账户封禁")
                   .setMessage("您已被服务器封禁，如有疑问请联系管理员")
                   .setCancelable(false)
                   .setPositiveButton("确定", (dialog, id) -> {
                       // 点击确定后退出应用
                       System.exit(0);
                   });
            
            android.app.AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        });
    }
}