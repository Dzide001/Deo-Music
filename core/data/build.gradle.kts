// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library")
    id("deo.android.hilt")
}

android {
    namespace = "com.deox9.musicplayer.core.data"
}

/**
 * eAlvaTag depends on guava 20, which carries its own ListenableFuture; AndroidX
 * pulls com.google.guava:listenablefuture, a standalone copy of that same class.
 * Both land on the instrumentation-test classpath and the duplicate-class check
 * stops the build.
 *
 * Dropping the standalone one is the right way round: it exists only to provide the
 * class for projects that do not already have guava, and this one does. Scoped to
 * the androidTest configurations so the app's own classpath is untouched.
 */
configurations.matching { it.name.startsWith("androidTest") }.configureEach {
    exclude(group = "com.google.guava", module = "listenablefuture")
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

    // The real org.json on the unit-test classpath. android.jar ships stubs that
    // throw "not mocked", so anything parsing JSON is otherwise untestable off-device.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
