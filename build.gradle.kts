// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    // Existing structural debt, almost all of it inside the oversized
    // MainActivity.kt. New violations still fail the build. Entries should be
    // deleted as the UI is split into feature modules — the goal is no baseline.
    baseline = rootProject.file("config/detekt/baseline.xml")
    source.setFrom(
        files(
            "app/src/main/java",
            "app/src/foss/java",
            "app/src/full/java",
            "core/model/src/main/java",
            "core/audio/src/main/java",
            "core/data/src/main/java",
            "core/database/src/main/java",
            "core/designsystem/src/main/java",
            "core/datastore/src/main/java",
            "core/media/src/main/java",
            "core/ui/src/main/java",
            "feature/player/src/main/java",
            "feature/library/src/main/java",
            "feature/settings/src/main/java",
            "feature/widget/src/main/java",
        ),
    )
    parallel = true
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}
