import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tungsten.fclcore"
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

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":FCLauncher"))
    implementation(libs.gson)
    implementation(libs.opennbt)
    implementation(libs.nanohttpd)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.commons.io)
    implementation(libs.toml4j)
    implementation(libs.jsoup)
    implementation(libs.constant.pool.scanner)
    implementation(libs.appcompat)
}
