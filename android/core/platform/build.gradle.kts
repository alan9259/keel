plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.therecalibrationyears.keel.platform"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.biometric) // BiometricPrompt for the app lock
    api(libs.health.connect) // Health Connect: the permission contract is used by the app
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
