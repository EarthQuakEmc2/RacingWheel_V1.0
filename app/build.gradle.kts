plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Upload-key material is read from the environment or from gradle.properties.
 * Neither the keystore nor its passwords belong in git. When they are absent,
 * release builds fall back to the public development keystore in this module
 * so a GitHub APK can still be signed. That development key is not a Play
 * upload key. See play-store/PUBLISHING.md.
 */
fun secret(name: String): String? {
    val fromEnv = System.getenv(name)?.takeIf { it.isNotBlank() }
    if (fromEnv != null) return fromEnv
    return (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
}

val genericNames = secret("RWP_GENERIC_NAMES") == "true" ||
    (findProperty("racingwheel.genericNames") as String?) == "true"

val telegramUrl = run {
    val text = rootProject.file("community.json").readText()
    val match = Regex(""""telegram"\s*:\s*"([^"]+)"""").find(text)
        ?: error("community.json is missing the telegram URL")
    match.groupValues[1]
}

android {
    namespace = "com.racingwheelpro"
    compileSdk = 35

    defaultConfig {
        applicationId = "site.myracingwheel.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 25
        versionName = "1.0.01"
        buildConfigField("String", "BUILD_DATE", "\"2026-11-25\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "GENERIC_NAMES", genericNames.toString())
        buildConfigField("String", "PREMIUM_PRODUCT_ID", "\"racing_wheel_premium\"")
        buildConfigField("String", "TELEGRAM_URL", "\"$telegramUrl\"")
    }

    flavorDimensions += "tier"
    productFlavors {
        create("free") {
            dimension = "tier"
            isDefault = true
        }
        create("premium") {
            dimension = "tier"
        }
    }

    signingConfigs {
        create("release") {
            val upload = secret("RWP_UPLOAD_STORE_FILE")
            if (upload != null) {
                storeFile = file(upload)
                storePassword = secret("RWP_UPLOAD_STORE_PASSWORD")
                keyAlias = secret("RWP_UPLOAD_KEY_ALIAS")
                keyPassword = secret("RWP_UPLOAD_KEY_PASSWORD")
            } else {
                storeFile = file("release.keystore")
                storePassword = "racingwheelpro"
                keyAlias = "racingwheelpro"
                keyPassword = "racingwheelpro"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
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
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    testImplementation("junit:junit:4.13.2")
    // android.jar stubs org.json and throws. The real library lets unit tests parse templates.
    testImplementation("org.json:json:20240303")
}
