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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 友盟基础组件库（所有友盟业务SDK都依赖基础组件库
    implementation("com.umeng.umsdk:common:+")// 必选
    implementation("com.umeng.umsdk:asms:+")// 必选
    implementation("com.umeng.umsdk:uyumao:+") // 高级运营分析功能依赖库，使用U-App卸载分析、开启反作弊能力请务必集成。common需搭配v9.6.3及以上版本，asms需搭配v1.7.0及以上版本。需更新隐私声明。
    //U-Push依赖
    implementation("com.umeng.umsdk:push:+")
}
