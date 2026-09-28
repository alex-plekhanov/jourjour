import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.jourjour"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "app.jourjour"
        minSdk = 26
        targetSdk = 36
        // Both are raised in a commit of their own, which is then tagged and built from:
        // a release carries the version the tag names and nothing else changes with it.
        // The code must be a positive integer — Gradle refuses 0 — and has to grow by at
        // least one for every build a store accepts; the name is free text and is what the
        // About screen shows.
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // A different application id, so a debug build and a release one can sit side
            // by side instead of refusing to replace each other. They are separate apps to
            // Android, with separate data: nothing entered in one is visible in the other.
            //
            // What tells them apart in the launcher is under src/debug/res — a name and a
            // colour, which Gradle lays over src/main/res for this build type alone.
            applicationIdSuffix = ".debug"
            // Reaches BuildConfig.VERSION_NAME, and from there the About screen.
            versionNameSuffix = "-debug"
        }
        release {
            // R8 drops what nothing reaches and optimizes what is left, which takes the
            // APK down to a fraction of its size. Room, Hilt and kotlinx.serialization
            // each ship the rules their own generated code needs. Renaming is turned off in
            // proguard-rules.pro, where the reasons are.
            isMinifyEnabled = true
            // Resource shrinking stays off. Preloaded dictionary names are resolved by
            // string rather than by R.string (see core/text/LocalizedName.kt); they survive
            // today only because DefaultData also names each of them, and a seeded row
            // added without that would vanish from a release with nothing to show for it.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG is needed: a debug build recreates the database when the
        // schema changes.
        buildConfig = true
    }
}

// An APK named after what is in it — jourjour-<version>.apk, and jourjour-<version>-debug.apk
// where the build type adds it — so that a file taken out of the build folder still says which
// version it is once it is away from the tag it was built from.
//
// Naming an output is not part of the public variant API; VariantOutputImpl is the Gradle
// plugin's own class. The cast is therefore a safe one: should a later plugin stop offering
// it, the artifact goes back to being called app-release.apk instead of the build failing.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            (output as? VariantOutputImpl)?.outputFileName?.set(
                "jourjour-${output.versionName.get()}.apk"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
ksp {
    // The schema of every version is written down under app/schemas, so that a migration
    // written after release has the version it starts from to be tested against.
    arg("room.schemaLocation", "$projectDir/schemas")
}
