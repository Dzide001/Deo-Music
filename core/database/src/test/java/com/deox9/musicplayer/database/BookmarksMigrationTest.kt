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
 * The v3 to v4 migration, which adds bookmarks.
 *
 * Worth testing rather than eyeballing: this SQL runs against a real library on
 * someone's phone, and Room compares the table it finds afterwards against the one
 * the entity describes — a difference as small as a missing ON DELETE clause fails
 * the app at startup, on the upgrade, for everyone.
 */
@RunWith(RobolectricTestRunner::class)
class BookmarksMigrationTest {

    private fun openV3(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB)

        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Only the table the new foreign key points at.
                db.execSQL(
                    """
                    CREATE TABLE tracks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("INSERT INTO tracks (id, title) VALUES (1, 'Grade 1')")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
        }

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB).callback(callback).build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `the migration creates a usable bookmarks table`() {
        val db = openV3()
        DatabaseModule.MIGRATION_3_4.migrate(db)

        db.execSQL(
            "INSERT INTO bookmarks (trackId, positionMs, label, createdAtMs) " +
                "VALUES (1, 134000, 'The solo', 42)",
        )

        db.query("SELECT trackId, positionMs, label FROM bookmarks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(134_000L, cursor.getLong(1))
            assertEquals("The solo", cursor.getString(2))
        }
        db.close()
    }

    /** The index the entity declares; without it Room rejects the schema. */
    @Test
    fun `the migration creates the index on trackId`() {
        val db = openV3()
        DatabaseModule.MIGRATION_3_4.migrate(db)

        db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='bookmarks'")
            .use { cursor ->
                val names = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertTrue(names.toString(), "index_bookmarks_trackId" in names)
            }
        db.close()
    }

    /**
     * Deleting a track takes its bookmarks with it. Without the cascade they would
     * outlive the track and point at nothing.
     */
    @Test
    fun `bookmarks go when their track goes`() {
        val db = openV3()
        DatabaseModule.MIGRATION_3_4.migrate(db)
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("INSERT INTO bookmarks (trackId, positionMs, label, createdAtMs) VALUES (1, 1, '', 0)")

        db.execSQL("DELETE FROM tracks WHERE id = 1")

        db.query("SELECT COUNT(*) FROM bookmarks").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    /** Running it twice must not fail; migrations get retried after a crash. */
    @Test
    fun `the migration is safe to run twice`() {
        val db = openV3()
        DatabaseModule.MIGRATION_3_4.migrate(db)
        DatabaseModule.MIGRATION_3_4.migrate(db)
        db.close()
    }

    private companion object {
        const val TEST_DB = "bookmarks-migration-test.db"
    }
}
