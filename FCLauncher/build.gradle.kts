import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tungsten.fclauncher"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    lint {
        targetSdk = libs.versions.targetSdk.get().toInt()
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    ndkVersion = "27.0.12077973"

    buildFeatures {
        prefab = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.bytehook)
    implementation(libs.appcompat)
    implementation(libs.material)

    // 友盟统计 SDK（必选）
    implementation("com.umeng.umsdk:common:+")
    implementation("com.umeng.umsdk:asms:+")
    implementation("com.umeng.umsdk:uyumao:+")   // 高级运营分析

    // 友盟可选模块
    implementation("com.umeng.umsdk:apm:+")       // APM 性能监控
    implementation("com.umeng.umsdk:link:+")
    implementation("com.umeng.umsdk:game:+")     // 游戏统计

    // 友盟 Push（必须）
    implementation("com.umeng.umsdk:push:+")

    // 友盟分享核心组件
    implementation("com.umeng.umsdk:share-core:+")
}
