// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library.compose")
    id("deo.android.hilt")
}

android {
    namespace = "com.deox9.musicplayer.feature.library"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.ui)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.media)

    // The library screens own the audio-permission request UI.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}

/** Screenshot tests verify by default; record with `-Precord`. See feature/player. */
tasks.withType<Test>().configureEach {
    val recording = providers.gradleProperty("record").isPresent
    systemProperty("roborazzi.test.record", recording)
    systemProperty("roborazzi.test.verify", !recording)
}
