package com.tungsten.fclauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaDrm;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.umeng.commonsdk.UMConfigure;
import com.umeng.message.PushAgent;
import com.umeng.message.api.UPushRegisterCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dns;
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
    
    // 获取公网 IP 的接口
    private static final String PUBLIC_IP_URL = "https://ipv4.lookup.test-ipv6.com/ip/";

    // 水印相关常量
    private static final int WATERMARK_VIEW_ID = 0x7F090001;

    private static FCLApplication instance;
    private static WeakReference<Activity> currentActivityRef = new WeakReference<>(null);
    private String cachedDeviceId = null;
    private String cachedIpAddress = null;   // 缓存公网 IPv4 地址

    // 全局 OkHttpClient，强制使用 IPv4 地址
    private static final OkHttpClient ipv4Client;

    static {
        ipv4Client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .dns(new Dns() {
                    @Override
                    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                        InetAddress[] all = InetAddress.getAllByName(hostname);
                        List<InetAddress> ipv4List = new ArrayList<>();
                        for (InetAddress addr : all) {
                            if (addr instanceof Inet4Address) {
                                ipv4List.add(addr);
                            }
                        }
                        if (!ipv4List.isEmpty()) {
                            return ipv4List;
                        }
                        Log.w(TAG, "No IPv4 address found for " + hostname + ", falling back to all addresses");
                        return Arrays.asList(all);
                    }
                })
                .build();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 基础初始化（友盟统计基础库）
        UMConfigure.init(
                this,
                "69e0f1b36f259537c79a2e80",
                "GitHub",
                UMConfigure.DEVICE_TYPE_PHONE,
                "1853c4972a25c98245161c0bc6593e08"
        );
        // 开启日志便于调试
        UMConfigure.setLogEnabled(true);

        registerActivityLifecycleCallbacks(this);

        // 将所有网络及推送初始化放入子线程，避免阻塞主线程
        new Thread(() -> {
            String deviceId = getDeviceUniqueId();
            Log.i(TAG, "Device unique ID: " + deviceId);
            checkBanStatus(deviceId);

            // 初始化友盟推送（不需要用户同意协议，直接调用）
            initPush();
        }).start();
    }

    /**
     * 友盟推送注册及 deviceToken 文件生成
     */
    private void initPush() {
        PushAgent pushAgent = PushAgent.getInstance(this);
        pushAgent.register(new UPushRegisterCallback() {
            @Override
            public void onSuccess(String deviceToken) {
                Log.i(TAG, "Push registration success, deviceToken: " + deviceToken);
                writeDeviceTokenToFile(deviceToken);
            }

            @Override
            public void onFailure(String errCode, String errDesc) {
                Log.e(TAG, "Push registration failed! code: " + errCode + ", desc: " + errDesc);
            }
        });
    }

    /**
     * 在应用私有目录根目录生成文件，内容为 deviceToken 信息
     */
    private void writeDeviceTokenToFile(String deviceToken) {
        File file = new File(getFilesDir(), "device_token.txt");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("开发者使用，如果不知道这是什么请不要乱动！\n");
            writer.write("你的deviceToken：" + deviceToken + "\n");
            Log.i(TAG, "Device token written to file: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to write device token to file", e);
        }
    }

    // ---------- 设备ID相关 ----------
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

    // ---------- 服务器交互 ----------
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

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                data.toString()
        );

        Request request = new Request.Builder()
                .url(UPLOAD_URL)
                .post(body)
                .build();

        ipv4Client.newCall(request).enqueue(new Callback() {
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

        String encodedDeviceId = URLEncoder.encode(finalDeviceId);
        String url = BAN_CHECK_URL + "?device_id=" + encodedDeviceId;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        ipv4Client.newCall(request).enqueue(new Callback() {
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

    // ---------- 公网 IP 获取（通过远程接口）----------
    /**
     * 通过 https://ipv4.lookup.test-ipv6.com/ip/ 获取公网 IPv4 地址
     * 响应格式为 JSONP: callback({"ip":"116.141.52.191","type":"ipv4",...})
     * @return 公网 IPv4 地址字符串，失败时返回本地 IP 回退
     */
    private String getPublicIpAddress() {
        Request request = new Request.Builder()
                .url(PUBLIC_IP_URL)
                .get()
                .build();

        try (Response response = ipv4Client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                // 解析 JSONP 格式: callback({...})
                String jsonStr = extractJsonFromJsonp(responseBody);
                JSONObject json = new JSONObject(jsonStr);
                String ip = json.optString("ip", null);
                if (ip != null && !ip.isEmpty()) {
                    Log.i(TAG, "Got public IP: " + ip);
                    return ip;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get public IP", e);
        }
        
        // 降级方案：获取本地局域网 IP 作为回退
        String fallbackIp = getLocalIpAddress();
        Log.w(TAG, "Using fallback local IP: " + fallbackIp);
        return fallbackIp;
    }

    /**
     * 从 JSONP 响应中提取 JSON 字符串
     * @param jsonp 格式例如: callback({"ip":"1.2.3.4",...})
     * @return 纯 JSON 字符串
     */
    private String extractJsonFromJsonp(String jsonp) {
        if (jsonp == null) return "{}";
        int start = jsonp.indexOf('{');
        int end = jsonp.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return jsonp.substring(start, end + 1);
        }
        return "{}";
    }

    /**
     * 获取本地局域网 IPv4 地址（作为降级方案）
     */
    private String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || iface.isVirtual() || !iface.isUp())
                    continue;

                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip != null && !ip.startsWith("169.254")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get local IP", e);
        }
        return "0.0.0.0";
    }

    /**
     * 给当前 Activity 添加隐形水印（不响应点击，不影响输入）
     */
    private void addWatermark(Activity activity, String ipAddress) {
        if (activity == null || activity.isFinishing()) return;

        ViewGroup rootView = activity.getWindow().getDecorView().findViewById(android.R.id.content);
        if (rootView == null) rootView = (ViewGroup) activity.getWindow().getDecorView();

        // 移除已存在的水印，避免重复添加
        View oldWatermark = rootView.findViewById(WATERMARK_VIEW_ID);
        if (oldWatermark != null) {
            rootView.removeView(oldWatermark);
        }

        // 创建水印 TextView
        TextView watermark = new TextView(activity);
        watermark.setId(WATERMARK_VIEW_ID);
        watermark.setText("IP: " + ipAddress);
        watermark.setTextColor(Color.parseColor("#08000000")); // 极淡的黑色（透明度约 3%）
        watermark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        watermark.setRotation(-20f);
        watermark.setClickable(false);
        watermark.setFocusable(false);
        watermark.setEnabled(false);
        watermark.setFocusableInTouchMode(false);
        watermark.setBackgroundColor(Color.TRANSPARENT);

        // 全屏铺开，不干扰触摸事件
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        params.gravity = Gravity.CENTER;
        watermark.setLayoutParams(params);
        watermark.setGravity(Gravity.CENTER);

        rootView.addView(watermark);
    }

    // ---------- Activity 生命周期回调 ----------
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

        // 在子线程中获取公网 IP，避免阻塞 UI
        new Thread(() -> {
            if (cachedIpAddress == null) {
                cachedIpAddress = getPublicIpAddress();
            }
            String ip = cachedIpAddress;
            if (ip == null) ip = "0.0.0.0";
            final String finalIp = ip;

            // 切换回主线程添加水印
            new Handler(Looper.getMainLooper()).post(() -> addWatermark(activity, finalIp));
        }).start();
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

    // ---------- 静态工具方法 ----------
    public static FCLApplication getInstance() {
        return instance;
    }

    public static Activity getCurrentActivity() {
        return currentActivityRef.get();
    }
}
