package com.tungsten.fclauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.umeng.message.UmengNotifyClick;
import com.umeng.message.entity.UMessage;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 厂商通道辅助弹窗 Activity
 * 当 App 进程被杀死后，通过厂商通道拉起此 Activity 接收推送消息
 * 
 * 需要在 AndroidManifest.xml 中注册，并在友盟推送平台填写此 Activity 的完整路径
 */
public class MfrMessageActivity extends Activity {

    private static final String TAG = "MfrMessageActivity";

    // 用于处理通知栏点击回调的代理对象
    private final UmengNotifyClick mNotificationClick = new UmengNotifyClick() {
        @Override
        public void onMessage(UMessage msg) {
            // 获取完整的消息内容（JSON 字符串）
            final String rawMessage = msg.getRaw().toString();
            Log.i(TAG, "Received push message: " + rawMessage);

            if (!TextUtils.isEmpty(rawMessage)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 方法1：显示在预置的 TextView 中（如有）
                        TextView tv = findViewById(R.id.tv_message);
                        if (tv != null) {
                            tv.setText(formatMessageForDisplay(rawMessage));
                        } else {
                            // 方法2：如果没有该 TextView，则用 Toast 提示
                            Toast.makeText(MfrMessageActivity.this, 
                                "收到推送消息，详情见日志", Toast.LENGTH_LONG).show();
                        }

                        // 你可以在这里添加自己的业务逻辑，例如：
                        // - 解析自定义参数，跳转到指定页面
                        // - 发送本地广播给其他组件
                        // - 静默更新数据等
                        handleCustomMessage(msg);
                    }
                });
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置布局文件（你需要创建对应的 layout 文件，或注释掉这行并使用默认背景）
        setContentView(R.layout.activity_mfr_message);
        
        // 必须调用，让代理处理 Intent 中的消息
        mNotificationClick.onCreate(this, getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 当 Activity 为 singleTask 模式时，新 Intent 会走这里
        mNotificationClick.onNewIntent(intent);
        setIntent(intent); // 更新当前 Intent，以便后续获取
    }

    /**
     * 格式化消息以便在 TextView 中显示（仅用于演示，可自行修改）
     */
    private String formatMessageForDisplay(String rawJson) {
        try {
            JSONObject obj = new JSONObject(rawJson);
            // 提取常见的推送字段
            String body = obj.optString("body", "无消息内容");
            String title = obj.optString("title", "无标题");
            return "标题：" + title + "\n内容：" + body;
        } catch (JSONException e) {
            Log.w(TAG, "Message not in standard format, display raw string");
            return rawJson;
        }
    }

    /**
     * 处理消息中的自定义参数（根据你的业务需求修改）
     */
    private void handleCustomMessage(UMessage msg) {
        // 示例：从 extra 字段中获取自定义参数
        String customUrl = msg.extra != null ? msg.extra.get("url") : null;
        if (!TextUtils.isEmpty(customUrl)) {
            Log.i(TAG, "Custom url: " + customUrl);
            // 这里可以启动 WebView 或跳转页面
            // Intent intent = new Intent(this, YourWebActivity.class);
            // intent.putExtra("url", customUrl);
            // startActivity(intent);
        }

        // 你也可以发送本地广播，让应用其他部分（如正在运行的 Service）得知新消息
        // Intent localIntent = new Intent("NEW_PUSH_MESSAGE");
        // localIntent.putExtra("message", msg.getRaw().toString());
        // LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        
        // 如果是静默消息，不需要显示界面，可以在此调用 finish()
        // if (isSilentMessage(msg)) {
        //     finish();
        // }
    }

    /**
     * 可选：判断是否为静默消息（不弹出界面，仅做数据处理）
     */
    private boolean isSilentMessage(UMessage msg) {
        // 根据你发送的消息中的某个字段来判断，例如 msg.extra.get("type") == "silent"
        return false;
    }
}
