plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.xiankai.ciconnectiondoctor"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.xiankai.ciconnectiondoctor"
        minSdk = 31
        targetSdk = 35
        versionCode = 5
        versionName = "0.1.0"
    }

    val releaseKeystore = System.getenv("RELEASE_KEYSTORE_PATH")?.let(::file)
    fun requireSigningEnv(name: String): String =
        checkNotNull(System.getenv(name)?.takeIf { it.isNotEmpty() }) { "$name must be set when RELEASE_KEYSTORE_PATH is" }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = requireSigningEnv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = requireSigningEnv("RELEASE_KEY_ALIAS")
                keyPassword = requireSigningEnv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:asha"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
