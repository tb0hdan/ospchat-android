plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val projectVersion: String =
    providers
        .fileContents(rootProject.layout.projectDirectory.file("VERSION"))
        .asText
        .get()
        .trim()

android {
    namespace = "com.ospchat.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ospchat.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = projectVersion
        // Exposes the project version as `R.string.app_version_name` so the
        // About screen can render it without enabling BuildConfig.
        resValue("string", "app_version_name", projectVersion)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    }

    packaging {
        resources.excludes +=
            listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties",
            )
    }
}

// Match the Makefile's expected output path:
// app/build/outputs/apk/debug/ospchat-<VERSION>-debug.apk
base {
    archivesName.set("ospchat-$projectVersion")
}

dependencies {
    // Shared Kotlin Multiplatform module: DTOs, wire protocol, Room data
    // layer, identity, peer discovery, attachment + avatar stores,
    // repositories, use cases, MessageClient + MessageServer. Resolved from
    // the `tb0hdan/ospchat-shared` GitHub Packages Maven repo configured in
    // settings.gradle.kts. To bump the version, edit `ospchatShared` in
    // gradle/libs.versions.toml after the corresponding tag is released.
    implementation(libs.ospchat.shared)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.emoji2.bundled)
    implementation(libs.androidx.emoji2.emojipicker)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    runtimeOnly(libs.slf4j.nop)
}
