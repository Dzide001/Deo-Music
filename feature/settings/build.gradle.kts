// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library.compose")
    id("deo.android.hilt")
}

android {
    namespace = "com.deox9.musicplayer.feature.settings"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.ui)
    implementation(projects.core.datastore)

    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.kotlinx.coroutines.android)
}
