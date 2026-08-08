// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library")
}

android {
    namespace = "com.deox9.musicplayer.core.datastore"
}

dependencies {
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
