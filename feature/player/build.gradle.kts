// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library.compose")
}

android {
    namespace = "com.deox9.musicplayer.feature.player"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.ui)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.media)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
}
