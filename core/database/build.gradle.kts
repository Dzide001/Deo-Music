// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library")
    id("deo.android.hilt")
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.deox9.musicplayer.core.database"

    // MigrationTestHelper loads the exported schemas from assets, so the committed
    // schema directory has to be on the test asset path.
    sourceSets {
        getByName("test") { assets.srcDirs("$projectDir/schemas") }
        getByName("androidTest") { assets.srcDirs("$projectDir/schemas") }
    }
}

room {
    // Schemas are committed so migrations can be written against a known baseline
    // and verified in tests, rather than reconstructed from memory later.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(projects.core.model)

    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
