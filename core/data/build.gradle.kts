// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library")
    id("deo.android.hilt")
}

android {
    namespace = "com.deox9.musicplayer.core.data"
}

dependencies {
    implementation(projects.core.audio)
    implementation(projects.core.model)
    implementation(projects.core.database)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // LGPLv3, which the GPLv3 licensing decision is what makes usable here.
    implementation(libs.ealvatag)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.androidx)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
