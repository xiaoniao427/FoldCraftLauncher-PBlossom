package com.tungsten.fclauncher;

import android.app.Application;
import com.umeng.commonsdk.UMConfigure;

public class FCLApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化友盟SDK
        UMConfigure.init(
            this, 
            "69e0f1b36f259537c79a2e80", 
            "GitHub", 
            UMConfigure.DEVICE_TYPE_PHONE, 
            "1853c4972a25c98245161c0bc6593e08"
        );
    }
}