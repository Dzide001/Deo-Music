// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library.compose")
    id("deo.android.hilt")
}

android {
    // Robolectric renders real Material3 components, which look up their own string
    // resources; without this a Switch or a text field throws NotFoundException.
    testOptions.unitTests.isIncludeAndroidResources = true
    namespace = "com.deox9.musicplayer.feature.settings"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.ui)
    implementation(projects.core.designsystem)
    implementation(projects.core.datastore)
    implementation(projects.core.data)

    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.aboutlibraries.compose.m3)
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
