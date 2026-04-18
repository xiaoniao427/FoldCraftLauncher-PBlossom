// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google()
        jcenter()
        maven { url = uri("https://repo1.maven.org/maven2/") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.4.0")
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

allprojects {
    repositories {
        google()
        jcenter()
        maven { url = uri("https://repo1.maven.org/maven2/") }
    }
}
