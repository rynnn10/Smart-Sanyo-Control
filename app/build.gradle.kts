plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.smartsanyocontrol.jhfxqa"
    minSdk = 24
    targetSdk = 36
    // Update: Sel 01/07/2026 20:00 - v2.4.0
    // Fix JSN-SR04T phantom echo + kalibrasi DIST_FULL_CM=25; canvas wave air 3D gravitasi
    versionCode = 10
    versionName = "2.4.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "BUILD_TIMESTAMP", "\"${System.currentTimeMillis()}\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD") ?: "android123"
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD") ?: "android123"
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.paho.client)
}

// Custom task: uninstall any previous app (release or debug) and reinstall the freshly assembled debug APK.
// Usage: `./gradlew :app:reinstallDebug` (device must be reachable via `adb`)
val baseAppId = "com.aistudio.smartsanyocontrol.jhfxqa"
val debugAppId = "$baseAppId.debug"

// Optional deviceId property: pass -PdeviceId=192.168.1.7:5555 to target a specific device
val deviceId: String? = if (project.hasProperty("deviceId")) project.property("deviceId").toString() else null

fun adbCommand(vararg args: String): List<String> {
  return if (deviceId.isNullOrBlank()) listOf("adb", *args) else listOf("adb", "-s", deviceId, *args)
}

val uninstallBase = tasks.register<Exec>("adbUninstallBase") {
  isIgnoreExitValue = true
  commandLine = adbCommand("uninstall", baseAppId)
}

val uninstallDebug = tasks.register<Exec>("adbUninstallDebug") {
  isIgnoreExitValue = true
  commandLine = adbCommand("uninstall", debugAppId)
}

val installApk = tasks.register<Exec>("adbInstallDebugApk") {
  dependsOn("assembleDebug")
  val apkPath = "${layout.buildDirectory.get().asFile}/outputs/apk/debug/app-debug.apk"
  doFirst {
    val apk = file(apkPath)
    if (!apk.exists()) throw GradleException("Debug APK not found: ${apk.absolutePath}")
  }
  commandLine = adbCommand("install", "-r", "${layout.buildDirectory.get().asFile}/outputs/apk/debug/app-debug.apk")
}

tasks.register("reinstallDebug") {
  dependsOn(uninstallBase, uninstallDebug, installApk)
}