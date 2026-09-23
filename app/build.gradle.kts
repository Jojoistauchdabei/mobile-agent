import java.util.Properties

val versionProps = Properties().apply {
    rootDir.resolve("version.properties").inputStream().use(::load)
}
val versionNameProp = versionProps.getProperty("VERSION_NAME", "0.1.0")
val versionCodeProp = versionProps.getProperty("VERSION_CODE", "1").toInt()

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
    buildTypes {
        release {
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
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    implementation(libs.findLibrary("androidx-core-ktx").get())
    implementation(libs.findLibrary("lifecycle-runtime").get())
    implementation(libs.findLibrary("activity-compose").get())
    implementation(libs.findLibrary("coroutines").get())
    implementation(libs.findLibrary("retrofit").get())
    implementation(libs.findLibrary("retrofit-scalars").get())
    implementation(libs.findLibrary("okhttp").get())
    implementation(libs.findLibrary("onnxruntime-mobile").get())
    implementation(platform(libs.findLibrary("compose-bom").get()))
    implementation(libs.findLibrary("compose-ui").get())
    implementation(libs.findLibrary("compose-material3").get())
    testImplementation(libs.findLibrary("junit").get())
}
