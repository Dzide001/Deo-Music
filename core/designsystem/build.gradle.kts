// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library.compose")
}

android {
    namespace = "com.deox9.musicplayer.core.designsystem"
}

dependencies {
    // Seed extraction from album artwork.
    implementation(libs.androidx.palette)
    // Seed -> full Material 3 tonal scheme. Compose only exposes the
    // wallpaper-based dynamic API, not scheme-from-seed.
    api(libs.material.kolor)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
