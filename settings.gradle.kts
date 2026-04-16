pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://repo1.maven.org/maven2/")   // 友盟
    }
}
rootProject.name = "PBlossom"
include(":FCL")
include(":FCLCore")
include(":FCLauncher")
include(":FCLLibrary")
include(":LWJGL-Pojav")
include(":Terracotta")
include(":ZipFileSystem")
//include(":NG-GL4ES")
