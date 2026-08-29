// SPDX-License-Identifier: GPL-3.0-or-later
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Shared Android library configuration.
 *
 * Keeps compileSdk, minSdk and the Java level in one place. AGP 9 provides Kotlin
 * support built in, so no Kotlin plugin is applied here.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            compileSdk = ProjectConfig.COMPILE_SDK

            defaultConfig {
                minSdk = ProjectConfig.MIN_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                consumerProguardFiles("consumer-rules.pro")
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            lint {
                abortOnError = true
                checkDependencies = true
            }
        }
    }
}

object ProjectConfig {
    /**
     * Compiling against 37 is forced by current AndroidX; the app's targetSdk stays
     * at 36, which is what Google Play requires for uploads from 31 August 2026.
     */
    const val COMPILE_SDK = 37
    const val MIN_SDK = 26
    const val TARGET_SDK = 36
}
