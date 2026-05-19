package com.tungsten.fclauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaDrm;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
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

    private static final int WATERMARK_VIEW_ID = 0x7F090001;

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
                    public List<<InetAddress> lookup(String hostname) throws UnknownHostException {
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

    // ---------- 平铺水印 View（基于旋转包围盒精确计算，防重叠，自适应屏幕）----------
    private static class TiledWatermarkView extends View {
        private static final float ROTATION_ANGLE = -45f;
        private static final String OPACITY_HEX = "#08000000";      // ~3% 透明度黑色
        private static final float MIN_TEXT_SP = 12f;
        private static final float MAX_TEXT_SP = 48f;
        private static final float MIN_SPACING_DP = 80f;
        private static final float MAX_SPACING_DP = 300f;
        private static final float SPACING_MARGIN_RATIO = 1.15f;    // 15% 额外边距，彻底防重叠
        private static final float TEXT_SIZE_RATIO = 35f;           // 基准：短边 1/35

        private final String watermarkText;
        private final Paint textPaint;
        private float cellWidth;
        private float cellHeight;
        private float drawRange;

        public TiledWatermarkView(Context context, String ip) {
            super(context);
            this.watermarkText = ip + " " + ip;

            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.parseColor(OPACITY_HEX));
            textPaint.setStyle(Paint.Style.FILL);

            setClickable(false);
            setFocusable(false);
            setEnabled(false);

            calculateDimensions();
        }

        /** 核心：根据当前屏幕尺寸与文字尺寸，计算无重叠的单元格大小 */
        private void calculateDimensions() {
            android.util.DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            int shortSide = Math.min(screenWidth, screenHeight);
            float density = metrics.density;

            // 1. 文字大小：基于短边比例，限制在 12sp ~ 48sp
            float desiredTextSizePx = shortSide / TEXT_SIZE_RATIO;
            float desiredTextSizeSp = desiredTextSizePx / density;
            float clampedTextSizeSp = Math.max(MIN_TEXT_SP, Math.min(MAX_TEXT_SP, desiredTextSizeSp));

            textPaint.setTextSize(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, clampedTextSizeSp, metrics));

            // 2. 测量文字实际像素尺寸
            float textWidth = textPaint.measureText(watermarkText);
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            float textHeight = fontMetrics.descent - fontMetrics.ascent;

            // 3. 计算 -45° 旋转后的包围盒（防止重叠的关键）
            //    旋转后宽 = w*|cos| + h*|sin|
            //    旋转后高 = w*|sin| + h*|cos|
            double rad = Math.toRadians(Math.abs(ROTATION_ANGLE));
            float sin = (float) Math.sin(rad);
            float cos = (float) Math.cos(rad);

            float rotatedWidth = textWidth * cos + textHeight * sin;
            float rotatedHeight = textWidth * sin + textHeight * cos;

            // 4. 取旋转后宽高中的较大值作为最小单元格，追加边距比例
            float baseCellSize = Math.max(rotatedWidth, rotatedHeight) * SPACING_MARGIN_RATIO;

            // 5. 限制在 80dp ~ 300dp 的舒适区间，再转回 px
            float cellSizeDp = Math.max(MIN_SPACING_DP, Math.min(MAX_SPACING_DP, baseCellSize / density));
            float cellSizePx = cellSizeDp * density;

            cellWidth = cellSizePx;
            cellHeight = cellSizePx;
            drawRange = (float) (Math.hypot(screenWidth, screenHeight) * 1.5);
        }

        /** 屏幕旋转、分屏、折叠屏展开时自动重新计算 */
        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            calculateDimensions();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (watermarkText == null || watermarkText.isEmpty() || getWidth() <= 0 || getHeight() <= 0) return;

            canvas.save();
            canvas.rotate(ROTATION_ANGLE, getWidth() / 2f, getHeight() / 2f);

            float x = -drawRange;
            while (x < drawRange) {
                float y = -drawRange;
                while (y < drawRange) {
                    canvas.drawText(watermarkText, x, y, textPaint);
                    y += cellHeight;
                }
                x += cellWidth;
            }

            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }
    }

    private void addWatermark(Activity activity, String ipAddress) {
        if (activity == null || activity.isFinishing()) return;

        ViewGroup rootView = (ViewGroup) activity.getWindow().getDecorView();
        View oldWatermark = rootView.findViewById(WATERMARK_VIEW_ID);
        if (oldWatermark != null) {
            rootView.removeView(oldWatermark);
        }

        TiledWatermarkView watermark = new TiledWatermarkView(activity, ipAddress);
        watermark.setId(WATERMARK_VIEW_ID);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        watermark.setLayoutParams(params);
        rootView.addView(watermark);
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
