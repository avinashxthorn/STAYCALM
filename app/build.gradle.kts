plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// The app remains usable offline until the owner supplies the Android Firebase config.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")
android {
    namespace = "com.staycalm.focus"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.staycalm.focus"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("STAYCALM_VERSION_CODE")?.toInt() ?: 23
        versionName = "4.2.0"
        testInstrumentationRunner = "com.staycalm.focus.AccountTestRunner"
    }
    val permanentKey = System.getenv("STAYCALM_KEYSTORE_PATH")
    val permanentPassword = System.getenv("STAYCALM_KEYSTORE_PASSWORD")
    if (!permanentKey.isNullOrBlank() && !permanentPassword.isNullOrBlank()) {
        signingConfigs.create("permanent") {
            storeFile = file(permanentKey)
            storeType = "PKCS12"
            storePassword = permanentPassword
            keyAlias = "staycalm"
            keyPassword = permanentPassword
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("permanent")
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
