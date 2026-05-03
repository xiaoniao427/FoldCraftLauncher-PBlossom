import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tungsten.fclauncher"
    compileSdk = libs.versions.compileSdk.get().toInt()
    
    // 优化清单合并配置
    packaging {
        resources {
            // 排除所有冲突的清单文件
            excludes.add("AndroidManifest.xml")
            excludes.add("META-INF/**")
            
            // 保留必要的合并
            merges.add("**/R.txt")
            merges.add("**/*.bin")
        }
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
            }
        }
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
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
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
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(libs.bytehook)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("com.umeng.umsdk:common:+")//必选
    implementation("com.umeng.umsdk:asms:+")//必选
    implementation("com.umeng.umsdk:uyumao:+") //高级运营分析功能依赖库（可选）。使用卸载分析、开启反作弊能力请务必集成，以免影响高级功能使用。common需搭配v9.6.3及以上版本，asms需搭配v1.7.0及以上版本。需更新隐私声明。需配置混淆，以避免依赖库无法生效，见本文下方【混淆设置】部分。
    implementation("com.umeng.umsdk:abtest:+")//使用U-App中ABTest能力（可选）
    
    api("com.umeng.umsdk:common:+")
    api("com.umeng.umsdk:asms:+")
    api("com.umeng.umsdk:push:+")
    api("com.umeng.umsdk:uyumao:+")//可选，如要使用地理围栏推送功能则必选
}
