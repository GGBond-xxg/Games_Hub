plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.bond.md3elauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bond.md3elauncher"
        minSdk = 23
        targetSdk = 36
        versionCode = 87
        versionName = "0.1.87"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // Libretro cores need their original symbols; stripping can break some cores.
            keepDebugSymbols += setOf("*/*/*_libretro_android.so")
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation("com.github.Swordfish90:LibretroDroid:0.13.2")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
