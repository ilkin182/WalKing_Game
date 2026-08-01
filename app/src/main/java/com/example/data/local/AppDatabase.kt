package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.StompedHexDao
import com.example.data.local.entity.StompedHexEntity

@Database(
    entities = [StompedHexEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stompedHexDao(): StompedHexDao

    companion object {
        /**
         * Adds the fog's exploration level to the existing cells.
         *
         * A real migration rather than the destructive fallback: every row in this table is ground
         * the player physically walked over, and the epic's own acceptance criterion is that walked
         * areas survive a restart. Existing cells default to 1.0 - they were all recorded by walking
         * into them, which is exactly what a fully cleared cell means.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE stomped_hexes ADD COLUMN explorationLevel REAL NOT NULL DEFAULT 1.0"
                )
            }
        }

        /**
         * Added the district-membership cache, which version 5 drops again ([MIGRATION_4_5]).
         *
         * Kept even though the feature is gone: an install still on version 3 has to walk through
         * every step to reach the current schema, and dropping this one would strand it.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS district_cells (" +
                        "cellId TEXT NOT NULL, districtId TEXT, PRIMARY KEY(cellId))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_district_cells_districtId " +
                        "ON district_cells (districtId)"
                )
            }
        }

        /**
         * Drops the district-membership cache along with the districts feature.
         *
         * A migration rather than the destructive fallback, for the same reason as [MIGRATION_2_3]:
         * removing a feature must not cost the player the ground they walked, which lives in the
         * table next to this one.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_district_cells_districtId")
                db.execSQL("DROP TABLE IF EXISTS district_cells")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stomped_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
