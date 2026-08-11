// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library.compose")
    id("deo.android.hilt")
}

android {
    namespace = "com.deox9.musicplayer.core.media"
}

dependencies {
    implementation(projects.core.audio)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)

    api(libs.media3.exoplayer)
    api(libs.media3.session)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
