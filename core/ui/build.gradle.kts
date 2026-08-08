// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("deo.android.library.compose")
}

android {
    namespace = "com.deox9.musicplayer.core.ui"
}

dependencies {
    testImplementation(libs.junit)
}
