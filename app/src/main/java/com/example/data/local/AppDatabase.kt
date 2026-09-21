package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.PoiDao
import com.example.data.local.dao.RaceDao
import com.example.data.local.dao.RoutePointDao
import com.example.data.local.dao.StompedHexDao
import com.example.data.local.dao.WalkSessionDao
import com.example.data.local.entity.CityBoundsEntity
import com.example.data.local.entity.PoiEntity
import com.example.data.local.entity.PoiTileEntity
import com.example.data.local.entity.RaceEntity
import com.example.data.local.entity.RaceParticipantEntity
import com.example.data.local.entity.RoutePointEntity
import com.example.data.local.entity.StompedHexEntity
import com.example.data.local.entity.WalkSessionEntity

@Database(
    entities = [
        StompedHexEntity::class,
        WalkSessionEntity::class,
        RoutePointEntity::class,
        PoiEntity::class,
        PoiTileEntity::class,
        CityBoundsEntity::class,
        RaceEntity::class,
        RaceParticipantEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stompedHexDao(): StompedHexDao
    abstract fun walkSessionDao(): WalkSessionDao
    abstract fun routePointDao(): RoutePointDao
    abstract fun poiDao(): PoiDao
    abstract fun raceDao(): RaceDao

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

        /**
         * Adds the cache of what is on the ground: parks, monuments, metro, bridges, squares, the
         * coastline, and the extent of the towns walked in.
         *
         * Three new tables and nothing existing touched. The cache is the reason the geography
         * achievements can exist at all - Overpass and Nominatim are free shared services, and the
         * app is only allowed to ask them anything because it asks about each ~1 km square once and
         * remembers the answer. `poi_tiles` and the `found` column on `city_bounds` are what
         * remember the *empty* answers, which are the ones that would otherwise be asked forever.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pois (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "tileKey TEXT NOT NULL, " +
                        "kind TEXT NOT NULL, " +
                        "name TEXT, " +
                        "lat REAL NOT NULL, " +
                        "lng REAL NOT NULL, " +
                        "north REAL, south REAL, east REAL, west REAL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pois_tileKey ON pois (tileKey)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS poi_tiles (" +
                        "tileKey TEXT NOT NULL PRIMARY KEY, " +
                        "fetchedAt INTEGER NOT NULL)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS city_bounds (" +
                        "city TEXT NOT NULL PRIMARY KEY, " +
                        "countryCode TEXT, " +
                        "found INTEGER NOT NULL, " +
                        "north REAL, south REAL, east REAL, west REAL, " +
                        "centerLat REAL, centerLng REAL, " +
                        "fetchedAt INTEGER NOT NULL)"
                )
            }
        }

        /**
         * Adds the races the player has set up or been invited to, and who is in them.
         *
         * Two new tables, nothing existing touched. A real migration rather than the destructive
         * fallback for the usual reason - the walked ground lives in the table next to these - and
         * because a race is a commitment to other people: a player who upgrades mid-race must not
         * come back to find the race, its code and its deadline gone.
         *
         * The unique index on `races.code` is what makes the code the identity: a second race
         * carrying a code somebody has already shared would be a second race behind one link.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS races (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "code TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "mode TEXT NOT NULL, " +
                        "maxParticipants INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "startsAt INTEGER NOT NULL, " +
                        "endsAt INTEGER NOT NULL, " +
                        "hostId TEXT NOT NULL, " +
                        "hostName TEXT NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_races_code ON races (code)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS race_participants (" +
                        "raceId TEXT NOT NULL, " +
                        "playerId TEXT NOT NULL, " +
                        "nickname TEXT NOT NULL, " +
                        "countryCode TEXT, " +
                        "joinedAt INTEGER NOT NULL, " +
                        "score REAL NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(raceId, playerId))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_race_participants_raceId " +
                        "ON race_participants (raceId)"
                )
            }
        }

        /**
         * Adds the co-op format and the shared goal that goes with it.
         *
         * Two columns on `races`, and a separate migration rather than an edit to [MIGRATION_8_9]
         * even though the two were written days apart: a build with version 9 on it has been
         * installed, and rewriting a migration that has already run on a device leaves that device
         * with a schema Room will refuse to open.
         *
         * `format` is added NOT NULL with a default, which is exactly right for the rows already
         * there - every race that existed before this column was a versus race. `targetScore` stays
         * nullable, because those races have no goal and never will; see `RaceEntity`.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE races ADD COLUMN format TEXT NOT NULL DEFAULT 'VERSUS'")
                db.execSQL("ALTER TABLE races ADD COLUMN targetScore REAL")
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
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
