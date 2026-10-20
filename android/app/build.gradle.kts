import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sitpro.notevoice"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sitpro.notevoice"
        // Single device: Samsung Galaxy Z Fold 8 (Android 17 / API 37).
        minSdk = 37
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.0"
    }

    // Own keystore notevoice.jks (SPEC). Passwords from android/keystore.properties or the environment; neither is
    // committed. Without them assembleRelease fails instead of signing with the debug key.
    val keystore = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
    }
    fun secret(key: String, env: String): String? = keystore.getProperty(key) ?: System.getenv(env)
    signingConfigs {
        create("release") {
            secret("storeFile", "NOTEVOICE_KEYSTORE")?.let { storeFile = rootProject.file(it) }
            storePassword = secret("storePassword", "NOTEVOICE_KEYSTORE_PASSWORD")
            keyAlias = secret("keyAlias", "NOTEVOICE_KEY_ALIAS") ?: "notevoice"
            keyPassword = secret("keyPassword", "NOTEVOICE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // One phone, no store: no R8, which would need keep rules for Retrofit and kotlinx.serialization.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    // MigrationTest (Robolectric) reads the exported Room schemas from the app's assets: debug build only.
    sourceSets {
        getByName("debug").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric on the JDK 25 bundled with Android Studio needs these.
                it.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                )
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime)
    implementation(libs.security.crypto)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
}
