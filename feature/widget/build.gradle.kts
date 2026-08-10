// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library.compose")
}

android {
    namespace = "com.deox9.musicplayer.feature.widget"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.datastore)
    // For PlaybackService, which the widget's controller binds to.
    implementation(projects.core.media)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
