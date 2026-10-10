plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    id("androidx.room")
    kotlin("plugin.serialization") version "2.0.20"
    id("kotlin-kapt") // this needs to be on bottom
}

android {
    namespace = "com.isaakhanimann.journal"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.fseixaspt.psyjournal"
        minSdk = 26
        targetSdk = 36
        versionCode = 63
        versionName = "12.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    // the key lives outside the repo; its location and passwords come from ~/.gradle/gradle.properties
    // blank means unsigned, e.g. to check that the build is reproducible
    val releaseStoreFile = providers.gradleProperty("PSYJOURNAL_STORE_FILE").orNull?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("PSYJOURNAL_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("PSYJOURNAL_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("PSYJOURNAL_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
            // F-Droid builds without git history, so the commit must not end up in the APK
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk.debugSymbolLevel = "FULL"
        }
    }
    // the dependency list AGP adds is encrypted for Google; F-Droid rejects it and it isn't needed
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive.navigation.suite.android)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose)

    implementation(libs.androidx.core.splashscreen)

}

kapt {
    correctErrorTypes = true
}