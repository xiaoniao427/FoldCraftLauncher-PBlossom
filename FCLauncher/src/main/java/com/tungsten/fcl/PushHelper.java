package com.tungsten.fcl;

import android.content.Context;

import com.umeng.commonsdk.UMConfigure;
import com.umeng.message.PushAgent;
import com.umeng.message.UPushRegisterCallback;

/**
 * 友盟推送帮助类（官方推荐实现）
 */
public class PushHelper {
    
    /**
     * 预初始化
     */
    public static void preInit(Context context) {
        PushAgent.getInstance(context).register(new UPushRegisterCallback() {
            @Override
            public void onSuccess(String deviceToken) {
                // 注册成功
            }

            @Override
            public void onFailure(String errCode, String errDesc) {
                // 注册失败
            }
        });
    }
    
    /**
     * 正式初始化
     */
    public static void init(Context context) {
        // 获取推送实例
        PushAgent pushAgent = PushAgent.getInstance(context);
        
        // 日志开关
        pushAgent.setDebugMode(true);
        
        // 注册推送服务（官方要求）
        pushAgent.register(new UPushRegisterCallback() {
            @Override
            public void onSuccess(String deviceToken) {
                // 注册成功，deviceToken可用于测试推送
            }

            @Override
            public void onFailure(String errCode, String errDesc) {
                // 注册失败
            }
        });
        
        // 设置推送通知展示时的回调（可选）
        pushAgent.setNotificationClickHandler(new com.umeng.message.UmengMessageHandler() {
            @Override
            public void dealWithCustomMessage(Context context, com.umeng.message.entity.UMessage msg) {
                // 处理自定义消息
            }
            
            @Override
            public void launchApp(Context context, com.umeng.message.entity.UMessage msg) {
                // 点击通知时打开应用
                super.launchApp(context, msg);
            }
        });
    }
}