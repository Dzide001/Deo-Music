// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.deox9.musicplayer.database.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the migration object against a database built in the old shape.
 *
 * Room's MigrationTestHelper is designed for instrumented tests and does not resolve
 * a library module's schema assets under Robolectric, and CI here runs JVM tests
 * only. Driving the Migration directly still verifies the thing that matters: that
 * the SQL applies cleanly and existing rows survive it.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private fun openV1Database(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB)

        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // The v1 shape of the tables the migration touches.
                db.execSQL(
                    """
                    CREATE TABLE artists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        sortName TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE albums (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        sortTitle TEXT NOT NULL,
                        albumArtistId INTEGER,
                        year INTEGER,
                        artworkUri TEXT,
                        discCount INTEGER NOT NULL DEFAULT 1,
                        isCompilation INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(callback)
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `migrating 1 to 2 adds the artwork id column and keeps existing rows`() {
        val db = openV1Database()
        db.execSQL("INSERT INTO artists (name, sortName) VALUES ('Stonebwoy', 'stonebwoy')")
        db.execSQL(
            """
            INSERT INTO albums (title, sortTitle, albumArtistId, discCount, isCompilation)
            VALUES ('Grade 1', 'grade 1', 1, 1, 0)
            """.trimIndent(),
        )

        DatabaseModule.MIGRATION_1_2.migrate(db)

        db.query("SELECT title, mediaStoreAlbumId FROM albums").use { cursor ->
            assertTrue("the album row should survive the migration", cursor.moveToFirst())
            assertEquals("Grade 1", cursor.getString(0))
            // Existing rows have no album id until the next scan backfills them.
            assertTrue("expected a null artwork id before the next scan", cursor.isNull(1))
        }
        db.close()
    }

    @Test
    fun `migrating an empty database succeeds`() {
        val db = openV1Database()

        DatabaseModule.MIGRATION_1_2.migrate(db)

        db.query("SELECT COUNT(*) FROM albums").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
