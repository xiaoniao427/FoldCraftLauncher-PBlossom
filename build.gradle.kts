// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

dependencies {
    // 引入插件库
    classpath("com.baidu.mobstat:mtj-circle-plugin:latest.integration")
}
