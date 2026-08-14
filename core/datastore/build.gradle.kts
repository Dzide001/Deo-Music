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
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
