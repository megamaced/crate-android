package com.megamaced.crate.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.megamaced.crate.data.db.CrateDatabase.Companion.MIGRATION_1_2
import com.megamaced.crate.data.db.CrateDatabase.Companion.MIGRATION_2_3
import com.megamaced.crate.data.db.CrateDatabase.Companion.MIGRATION_3_4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration coverage for [CrateDatabase]. Each test creates the schema at
 * version N, runs the migration to N+1, and lets Room validate the result
 * against the JSON schema exported under `app/schemas/`. We also poke a
 * couple of columns introduced by each migration to catch the easy
 * "ALTER TABLE typo" bugs that schema validation alone would miss.
 *
 * Phase 16 backfill: closes the deferred items from Phase A20 / A21.
 */
@RunWith(AndroidJUnit4::class)
class CrateDatabaseMigrationTest {
    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CrateDatabase::class.java,
    )

    @Test
    fun migrate1To2_addsPlaylistColumns() {
        helper.createDatabase(testDb, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO playlists (id, name, createdAt, updatedAt)
                VALUES (1, 'Mix', '2026-01-01', '2026-01-01')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(testDb, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT itemCount, coverId FROM playlists WHERE id = 1").use { c ->
                assertEquals(true, c.moveToFirst())
                // itemCount defaults to 0 for pre-existing rows
                assertEquals(0, c.getInt(0))
                // coverId is nullable and should be null
                assertEquals(true, c.isNull(1))
            }
        }
    }

    @Test
    fun migrate2To3_addsPurchasePriceColumns() {
        helper.createDatabase(testDb, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO media_items (id, title, artist, format, status, category)
                VALUES (1, 'OK Computer', 'Radiohead', 'LP', 'owned', 'music')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(testDb, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT purchasePrice, purchasePriceCurrency FROM media_items WHERE id = 1").use { c ->
                assertEquals(true, c.moveToFirst())
                // Pre-existing rows resolve to null on upgrade.
                assertEquals(true, c.isNull(0))
                assertEquals(true, c.isNull(1))
            }
        }
    }

    @Test
    fun migrate3To4_addsPhotoFlagColumns() {
        helper.createDatabase(testDb, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO media_items (id, title, artist, format, status, category)
                VALUES (1, 'Kid A', 'Radiohead', 'LP', 'owned', 'music')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(testDb, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT hasPhoto1, hasPhoto2 FROM media_items WHERE id = 1").use { c ->
                assertEquals(true, c.moveToFirst())
                // INTEGER NOT NULL DEFAULT 0 — existing rows report "no photos".
                assertEquals(0, c.getInt(0))
                assertEquals(0, c.getInt(1))
            }
        }
    }

    @Test
    fun migrateAll1To4_chainedMigrations() {
        helper.createDatabase(testDb, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO media_items (id, title, artist, format, status, category)
                VALUES (1, 'Tracks', 'Bruce Springsteen', 'CD', 'owned', 'music')
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            testDb,
            4,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        )
        migrated.use { db ->
            val cursor = db.query(
                "SELECT purchasePrice, purchasePriceCurrency, hasPhoto1, hasPhoto2 FROM media_items WHERE id = 1",
            )
            cursor.use { c ->
                assertEquals(true, c.moveToFirst())
                assertEquals(true, c.isNull(0))
                assertEquals(true, c.isNull(1))
                assertEquals(0, c.getInt(2))
                assertEquals(0, c.getInt(3))
            }
        }
    }
}
