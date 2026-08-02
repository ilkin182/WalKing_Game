package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.RoutePointDao
import com.example.data.local.dao.StompedHexDao
import com.example.data.local.dao.WalkSessionDao
import com.example.data.local.entity.RoutePointEntity
import com.example.data.local.entity.StompedHexEntity
import com.example.data.local.entity.WalkSessionEntity

@Database(
    entities = [StompedHexEntity::class, WalkSessionEntity::class, RoutePointEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stompedHexDao(): StompedHexDao
    abstract fun walkSessionDao(): WalkSessionDao
    abstract fun routePointDao(): RoutePointDao

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

        /**
         * Records what the world was like when each cell was claimed, and keeps walks apart.
         *
         * Every added column is nullable with no default: a cell claimed before this migration has
         * no weather to remember and never will, and "unknown" has to stay distinguishable from
         * zero degrees. The place and the elevation of those old cells *can* be filled in later -
         * see the elevation enrichment pass - which is the other reason not to default them.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "temperatureC REAL",
                    "weatherCode INTEGER",
                    "windKmh REAL",
                    "sunriseMinute INTEGER",
                    "sunsetMinute INTEGER",
                    "city TEXT",
                    "countryCode TEXT",
                    "elevationM REAL"
                ).forEach { column ->
                    db.execSQL("ALTER TABLE stomped_hexes ADD COLUMN $column")
                }

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS walk_sessions (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "startedAt INTEGER NOT NULL, " +
                        "endedAt INTEGER NOT NULL, " +
                        "distanceMeters REAL NOT NULL)"
                )
            }
        }

        /**
         * Records the line each walk traces, for the route-shape achievements.
         *
         * Only a new table - nothing existing changes, and nothing is backfilled: the shape of walks
         * taken before this migration was never written down and cannot be recovered from the cells,
         * which are a set with no order to them. Those achievements start counting from here.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS route_points (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "sessionId INTEGER NOT NULL, " +
                        "lat REAL NOT NULL, " +
                        "lng REAL NOT NULL, " +
                        "at INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_route_points_sessionId " +
                        "ON route_points (sessionId)"
                )
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
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
