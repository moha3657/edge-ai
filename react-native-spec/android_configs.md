# Android Configuration & NDK Specifics for Edge AI (4GB Low-End Devices)

To ensure zero-concurrency GGUF models run smoothly without running into Android OS Out-Of-Memory (LMK) kills or C++ JNI architecture mismatches, apply the following configurations to your Android project files.

---

## 1. AndroidManifest.xml (`android/app/src/main/AndroidManifest.xml`)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Camera permission for physical receipt/document OCR -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <!-- 100% Offline: NO INTERNET PERMISSION IS DECLARED -->

    <application
        android:name=".MainApplication"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:allowBackup="false"
        android:theme="@style/AppTheme"
        
        <!-- CRITICAL FOR EDGE AI: Increases Dalvik/ART virtual heap ceiling (512MB on low-end devices) -->
        android:largeHeap="true"
        
        <!-- Hardware acceleration for smooth Compose/React Native rendering -->
        android:hardwareAccelerated="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:configChanges="keyboard|keyboardHidden|orientation|screenLayout|screenSize|smallestScreenSize|uiMode">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

---

## 2. App-Level build.gradle (`android/app/build.gradle`)

```groovy
android {
    ndkVersion rootProject.ext.ndkVersion // Recommend NDK 26.1.10909125 or higher

    defaultConfig {
        applicationId "com.edgeai.assistant"
        minSdkVersion 24
        targetSdkVersion 34
        versionCode 1
        versionName "1.0"

        // NDK ABI Filters: ARM64 is mandatory for 64-bit SIMD NEON acceleration in llama.cpp.
        // armeabi-v7a can be included for older 32-bit devices, but arm64-v8a gives 3-4x token/s.
        ndk {
            abiFilters "arm64-v8a"
        }

        // Enable C++ STL and ARM vector optimization flags
        externalNativeBuild {
            cmake {
                cppFlags "-O3 -flto -DGGML_USE_CPU=1"
                arguments "-DANDROID_ARM_NEON=TRUE", "-DANDROID_STL=c++_shared"
            }
        }
    }

    packagingOptions {
        // Prevent duplicate shared library conflict across llama.rn and dependencies
        pickFirst 'lib/arm64-v8a/libllama.so'
        pickFirst 'lib/arm64-v8a/libc++_shared.so'
        pickFirst 'lib/arm64-v8a/libggml.so'
        
        // Exclude unnecessary native architectures to keep APK small
        exclude 'lib/x86/**'
        exclude 'lib/x86_64/**'
    }

    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
        }
    }
}
```

---

## 3. Proguard Rules (`android/app/proguard-rules.pro`)

```pro
# Preserve JNI methods for llama.rn native bindings
-keep class com.rnllama.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Preserve ML Kit Text Recognition native models
-keep class com.google.mlkit.vision.** { *; }

# Preserve SQLite JNI bindings
-keep class io.sqlc.** { *; }
```

---

## 4. GGUF Model Asset Placement
Download quantized `.gguf` files (Q4_K_M) and place them in the assets directory:
```
android/app/src/main/assets/models/qwen2.5-1.5b-instruct-q4_k_m.gguf
android/app/src/main/assets/models/all-minilm-l6-v2-q4_k_m.gguf
```
This enables zero-copy `mmap` loading via `llama.rn`, ensuring weights are mapped directly from internal flash storage without duplicating RAM allocations.
