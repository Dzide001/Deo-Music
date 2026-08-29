// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    // Applied by id without a version: AGP is already on the build classpath via the
    // convention plugins, and asking for a version again makes Gradle refuse rather
    // than assume they match.
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.deox9.musicplayer.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // 28 is the floor for Baseline Profiles; the app's own minSdk is lower and
        // simply gets no profile on older devices, which is the documented behaviour.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The app has foss and full flavours and this module has none, so Gradle
        // cannot tell which variant to test against. The profile is generated from
        // foss: the two share every screen a startup profile touches, and foss is
        // the build without the WebView, so nothing here depends on that flavour.
        missingDimensionStrategy("distribution", "foss")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    /**
     * A managed emulator, because generation cannot run on the test phone.
     *
     * Capturing a profile needs a rooted or userdebug build; the device here is a
     * user build, so Gradle brings up an AOSP emulator instead. AOSP rather than
     * Google APIs because only the AOSP images are rooted.
     */
    testOptions.managedDevices.localDevices.create("profileGenerator") {
        device = "Pixel 6"
        apiLevel = 34
        systemImageSource = "aosp"
    }
}

baselineProfile {
    managedDevices += "profileGenerator"
    // Off, so generation does not silently depend on a phone being plugged in.
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.core)
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.junit)
}
