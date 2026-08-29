// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library")
    id("deo.android.hilt")
}

android {
    namespace = "com.deox9.musicplayer.core.datastore"
}

dependencies {
    // api, not implementation: AppSettings exposes CrossfadeSettings and EqBand in
    // its public shape, so anything reading settings needs those types too.
    api(projects.core.audio)
    // For BackupSettingsBridge, which lives in the leaf module both ends can see.
    implementation(projects.core.model)
    // LibraryTab lives with the other UI enums the settings screen already uses.
    implementation(projects.core.ui)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // Robolectric, because SavedQueuesRepository writes real DataStore files and
    // encodes with org.json — android.jar's stub throws "not mocked" off-device.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
