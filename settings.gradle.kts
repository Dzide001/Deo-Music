// SPDX-License-Identifier: GPL-3.0-or-later
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "MusicPlayer"

include(":app")

// Shared, UI-free layers.
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:media")
include(":core:ui")

// Feature modules.
include(":feature:player")
include(":feature:library")
include(":feature:settings")
