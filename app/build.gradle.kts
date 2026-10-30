plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rrrtx.lockin"
    compileSdk = 35

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    defaultConfig {
        applicationId = "com.rrrtx.lockin"
        // Android 6.0 — lowest defensible floor: runtime permissions, doze-aware
        // setExactAndAllowWhileIdle, and JobScheduler all exist at API 23, and
        // every dependency in the graph declares minSdk <= 21.
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("LOCKIN_KEYSTORE") ?: rootProject.layout.projectDirectory.file("release.keystore").asFile.absolutePath
            val storeFileObj = file(storePath)
            if (storeFileObj.exists()) {
                storeFile = storeFileObj
                storePassword = System.getenv("LOCKIN_STORE_PASSWORD")
                keyAlias = System.getenv("LOCKIN_KEY_ALIAS") ?: "lockin"
                keyPassword = System.getenv("LOCKIN_KEY_PASSWORD")
            }
        }
        // One universal APK: JAR signature (v1) is mandatory for Android 6.0/7.0
        // (API 23–25); APK Signature Scheme v2 covers 7.0+ — enable both so the
        // single release file installs across the entire supported range.
        val releaseElement = getByName("release")
        if (releaseElement.storeFile != null) {
            val apkSigning = releaseElement as com.android.build.api.dsl.ApkSigningConfig
            apkSigning.enableV1Signing = true
            apkSigning.enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Lockin Debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only attach signing when a keystore is actually available (env-provided
            // or ./release.keystore); otherwise produce an explicitly unsigned build.
            val storePathEnv = System.getenv("LOCKIN_KEYSTORE")
                ?: rootProject.layout.projectDirectory.file("release.keystore").asFile.absolutePath
            signingConfig = if (file(storePathEnv).exists()) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            resValue("string", "app_name", "Lockin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
