// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Whether the artwork fallback can produce an image, not merely run without failing.
 *
 * It runs against a fixture bundled with the test rather than against whatever is on
 * the device, and that is the point. Two earlier versions of this read the device's
 * own library: the first could not list directories under scoped storage, the second
 * could not get the audio permission because a library module's androidTest is a
 * separate package and this ROM refuses `pm grant` from the shell. Both times every
 * test skipped, and a skipped test reports as a pass.
 *
 * The fixture is a one-second tone carrying a 64x64 JPEG in an ID3v2.3 APIC frame,
 * copied into the test's own cache directory where no permission is needed to read
 * it. It proves the extraction, which is the part that was in doubt; that the app
 * can open the user's files is settled by the app working at all.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddedArtworkTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var fixture: File

    @Before
    fun copyFixture() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        fixture = File(context.cacheDir, "embedded-art-fixture.mp3")
        assets.open("embedded-art-fixture.mp3").use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
        File(context.cacheDir, "embedded-art").deleteRecursively()
    }

    @Test
    fun aFileWithEmbeddedArtYieldsAnImage() = runBlocking {
        val uri = EmbeddedArtwork(context).artworkUriFor(fixture.absolutePath)

        assertNotNull("extractor returned nothing for a file that has a cover", uri)
        val cached = File(java.net.URI.create(uri!!))
        assertTrue("cached file is empty", cached.length() > 0)

        // The JPEG magic number, so this is the picture rather than arbitrary bytes
        // that happen to have a length.
        val header = cached.readBytes()
        assertEquals(0xFF.toByte(), header[0])
        assertEquals(0xD8.toByte(), header[1])
    }

    /** Parsing a file per scroll would be the whole cost of this feature. */
    @Test
    fun theSecondRequestIsServedFromTheCache() = runBlocking {
        val artwork = EmbeddedArtwork(context)

        val first = artwork.artworkUriFor(fixture.absolutePath)
        val cached = File(java.net.URI.create(first!!))
        val writtenAt = cached.lastModified()
        val second = artwork.artworkUriFor(fixture.absolutePath)

        assertEquals(first, second)
        assertEquals("the file was rewritten", writtenAt, cached.lastModified())
    }

    /**
     * A file with no cover must not be re-parsed on every appearance, and must not
     * report a cover it does not have.
     */
    @Test
    fun aFileWithNoArtYieldsNothingAndIsNotReparsed() = runBlocking {
        val bare = File(context.cacheDir, "no-art.mp3")
        bare.writeBytes(ByteArray(2048))

        val artwork = EmbeddedArtwork(context)
        assertNull(artwork.artworkUriFor(bare.absolutePath))

        // The zero-length marker, which is how "asked already, nothing there" is
        // told apart from "never asked".
        val markers = File(context.cacheDir, "embedded-art").listFiles().orEmpty()
        assertTrue("no marker written", markers.any { it.length() == 0L })
        assertNull(artwork.artworkUriFor(bare.absolutePath))
    }

    @Test
    fun aMissingFileIsNotAnError() = runBlocking {
        assertNull(EmbeddedArtwork(context).artworkUriFor("/nowhere/at/all.mp3"))
        assertNull(EmbeddedArtwork(context).artworkUriFor(null))
        assertNull(EmbeddedArtwork(context).artworkUriFor("  "))
    }
}
