package com.tungsten.fclauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.media.MediaDrm;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.FrameLayout;

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
    private static final String PUBLIC_IP_URL = "https://ipv4.lookup.test-ipv6.com/ip/";
    private static final String WATERMARK_TAG = "WATERMARK_LAYOUT";

    private static FCLApplication instance;
    private static WeakReference<Activity> currentActivityRef = new WeakReference<>(null);
    private String cachedDeviceId = null;
    private String cachedIpAddress = null;

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

        UMConfigure.init(
                this,
                "69e0f1b36f259537c79a2e80",
                "GitHub",
                UMConfigure.DEVICE_TYPE_PHONE,
                "1853c4972a25c98245161c0bc6593e08"
        );
        UMConfigure.setLogEnabled(true);

        registerActivityLifecycleCallbacks(this);

        new Thread(() -> {
            String deviceId = getDeviceUniqueId();
            Log.i(TAG, "Device unique ID: " + deviceId);
            checkBanStatus(deviceId);
            initPush();
        }).start();
    }

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
        return cachedDeviceId;
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

    // ---------- 公网 IP 获取 ----------
    private String getPublicIpAddress() {
        Request request = new Request.Builder()
                .url(PUBLIC_IP_URL)
                .get()
                .build();

        try (Response response = ipv4Client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
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

        String fallbackIp = getLocalIpAddress();
        Log.w(TAG, "Using fallback local IP: " + fallbackIp);
        return fallbackIp;
    }

    private String extractJsonFromJsonp(String jsonp) {
        if (jsonp == null) return "{}";
        int start = jsonp.indexOf('{');
        int end = jsonp.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return jsonp.substring(start, end + 1);
        }
        return "{}";
    }

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

    // ---------- 水印实现（Drawable 背景，防重叠） ----------
    private void addWatermark(Activity activity, String ipAddress) {
        if (activity == null || activity.isFinishing()) return;

        ViewGroup rootView = activity.findViewById(android.R.id.content);
        // 移除旧水印，避免重复添加
        FrameLayout oldLayout = rootView.findViewWithTag(WATERMARK_TAG);
        if (oldLayout != null) {
            rootView.removeView(oldLayout);
        }

        // 创建水印 Drawable
        WatermarkDrawable drawable = new WatermarkDrawable(ipAddress + " " + ipAddress,
                Color.parseColor("#08000000"), -45f);

        FrameLayout layout = new FrameLayout(activity);
        layout.setTag(WATERMARK_TAG);
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        layout.setBackground(drawable);
        rootView.addView(layout);
    }

    /**
     * 水印 Drawable：旋转平铺，自适应尺寸，彻底防重叠
     */
    private static class WatermarkDrawable extends Drawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String mText;
        private final int mTextColor;
        private final float mRotation;

        // 自适应参数
        private static final float MIN_TEXT_SP = 12f;
        private static final float MAX_TEXT_SP = 48f;
        private static final float TEXT_SIZE_RATIO = 35f;
        private static final float SPACING_FACTOR = 1.4f;       // 额外40%间距
        private static final float MIN_CELL_DP = 60f;

        private float cellSize;      // 正方形步进（px）
        private float drawRange;     // 绘制范围

        WatermarkDrawable(String text, int textColor, float rotation) {
            this.mText = text;
            this.mTextColor = textColor;
            this.mRotation = rotation;
        }

        private void calculateDimensions(Canvas canvas) {
            DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            int shortSide = Math.min(screenWidth, screenHeight);
            float density = metrics.density;

            // 动态文字大小：短边/比例，限制范围
            float desiredSp = (shortSide / TEXT_SIZE_RATIO) / density;
            float finalSp = Math.max(MIN_TEXT_SP, Math.min(MAX_TEXT_SP, desiredSp));
            mPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, finalSp, metrics));
            mPaint.setColor(mTextColor);

            // 测量文字实际宽高
            float textWidth = mPaint.measureText(mText);
            Paint.FontMetrics fm = mPaint.getFontMetrics();
            float textHeight = fm.descent - fm.ascent;

            // 计算旋转后包围盒（关键：旋转后占据的矩形区域）
            double rad = Math.toRadians(Math.abs(mRotation));
            float sin = (float) Math.sin(rad);
            float cos = (float) Math.cos(rad);
            float rotatedWidth = textWidth * cos + textHeight * sin;
            float rotatedHeight = textWidth * sin + textHeight * cos;

            // 单元格尺寸 = max(旋转宽, 旋转高) * 间距系数，并保证最小尺寸
            float baseCell = Math.max(rotatedWidth, rotatedHeight) * SPACING_FACTOR;
            float minCellPx = MIN_CELL_DP * density;
            cellSize = Math.max(baseCell, minCellPx);

            // 绘制范围：对角线 1.5 倍，确保旋转后覆盖全屏
            drawRange = (float) (Math.hypot(screenWidth, screenHeight) * 1.5);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (mText == null || mText.isEmpty()) return;

            // 每次重绘重新计算，适应分屏/折叠屏变化
            calculateDimensions(canvas);

            canvas.save();
            // 绕画布中心旋转
            canvas.rotate(mRotation, getBounds().centerX(), getBounds().centerY());

            // 平铺水印，步进 cellSize，确保无重叠
            float x = -drawRange;
            while (x < drawRange) {
                float y = -drawRange;
                while (y < drawRange) {
                    canvas.drawText(mText, x, y, mPaint);
                    y += cellSize;
                }
                x += cellSize;
            }

            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ---------- Activity 生命周期 ----------
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

        new Thread(() -> {
            if (cachedIpAddress == null) {
                cachedIpAddress = getPublicIpAddress();
            }
            String ip = cachedIpAddress;
            if (ip == null) ip = "0.0.0.0";
            final String finalIp = ip;

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

    public static FCLApplication getInstance() {
        return instance;
    }

    public static Activity getCurrentActivity() {
        return currentActivityRef.get();
    }
}
