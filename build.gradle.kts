buildscript {
    repositories {
        google()
        mavenCentral()
        // 移除已弃用的 jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.2")  // 请根据项目实际情况调整版本
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
        classpath("com.baidu.mobstat:mtj-circle-plugin:latest.integration")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
