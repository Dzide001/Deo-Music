// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library.compose")
    id("deo.android.hilt")
}

android {
    namespace = "com.deox9.musicplayer.feature.player"
}

dependencies {
    implementation(projects.core.audio)
    implementation(projects.core.model)
    implementation(projects.core.ui)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.media)

    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}

/**
 * Screenshot tests verify by default and record only when asked.
 *
 * The flags have to reach the *test* JVM: passing -Droborazzi.test.record to Gradle
 * sets it on the daemon, where the tests cannot see it, and Roborazzi then quietly
 * does nothing — the run goes green having written and compared no images at all.
 *
 * Record new or intentionally-changed images with:
 *   ./gradlew :feature:player:test -Precord
 */
tasks.withType<Test>().configureEach {
    val recording = providers.gradleProperty("record").isPresent
    systemProperty("roborazzi.test.record", recording)
    systemProperty("roborazzi.test.verify", !recording)
}
