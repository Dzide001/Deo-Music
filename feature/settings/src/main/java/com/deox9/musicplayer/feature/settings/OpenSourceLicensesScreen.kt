// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Attribution for every dependency, generated from the Gradle graph rather than
 * hand-maintained.
 *
 * NOTICE at the repo root covers the direct dependencies by hand; this is the
 * authoritative list Play policy and basic decency both expect, and it stays correct
 * as dependencies change without anyone remembering to update a text file. The one
 * dependency that actually needs calling out — eAlvaTag, LGPLv3 — shows up here like
 * every other library, same as the rest.
 *
 * The AboutLibraries Gradle plugin writes the dependency graph to a raw resource at
 * build time and hands it to [Libs.Builder], which is the plugin's supported entry
 * point in this version rather than a resource-id parameter on the composable.
 *
 * [aboutLibrariesRawResId] must come from the caller rather than being a constant
 * here. The plugin has to run on `:app`, not this module: with
 * `android.nonTransitiveRClass` on, a module's R class only exposes resources it
 * declares itself, and eAlvaTag, Media3, Room and Coil all live in sibling modules
 * this one does not depend on, so a resource generated here would miss them.
 * Verified on device — generating it in this module produced a licenses list missing
 * everything outside this module's own direct dependencies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(onBack: () -> Unit, @RawRes aboutLibrariesRawResId: Int) {
    val context = LocalContext.current
    val libs by produceState<Libs?>(initialValue = null, aboutLibrariesRawResId) {
        value = withContext(Dispatchers.IO) {
            val json = context.resources.openRawResource(aboutLibrariesRawResId)
                .bufferedReader()
                .use { it.readText() }
            Libs.Builder().withJson(json).build()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open source licenses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val loadedLibs = libs
            if (loadedLibs == null) {
                CircularProgressIndicator(modifier = Modifier.Companion.align(Alignment.Center))
            } else {
                LibrariesContainer(
                    libraries = loadedLibs,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
