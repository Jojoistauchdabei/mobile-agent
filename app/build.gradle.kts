import java.util.Properties

val versionProps = Properties().apply {
    rootDir.resolve("version.properties").inputStream().use(::load)
}
val versionNameProp = versionProps.getProperty("VERSION_NAME", "0.1.0")
val versionCodeProp = versionProps.getProperty("VERSION_CODE", "1").toInt()
val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseSigningAvailable = !releaseKeystorePath.isNullOrBlank() &&
    !System.getenv("ANDROID_KEYSTORE_PASSWORD").isNullOrBlank() &&
    !System.getenv("ANDROID_KEY_ALIAS").isNullOrBlank() &&
    !System.getenv("ANDROID_KEY_PASSWORD").isNullOrBlank()
val releaseSigningEnvironmentProvided = listOf(
    releaseKeystorePath,
    System.getenv("ANDROID_KEYSTORE_PASSWORD"),
    System.getenv("ANDROID_KEY_ALIAS"),
    System.getenv("ANDROID_KEY_PASSWORD"),
).any { !it.isNullOrBlank() }
if (releaseSigningEnvironmentProvided && !releaseSigningAvailable) {
    error("Release signing requires ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mobileagent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mobileagent"
        minSdk = 29
        targetSdk = 34
        versionCode = versionCodeProp
        versionName = versionNameProp
    }
    if (releaseSigningAvailable) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.activity.compose)
    implementation(libs.coroutines)
    implementation(libs.retrofit)
    implementation(libs.retrofit.scalars)
    implementation(libs.okhttp)
    implementation(libs.onnxruntime.mobile)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
