package com.gop.survey.corporatefarm.data.local


import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gop.survey.corporatefarm.common.Constants
import com.gop.survey.corporatefarm.data.remote.response.NewSurveyNewDao
import com.gop.survey.corporatefarm.data.remote.response.SurveyImageDao
import com.gop.survey.corporatefarm.data.remote.response.SurveyPersonDao
import com.gop.survey.corporatefarm.domain.model.ActiveParcelEntity
import com.gop.survey.corporatefarm.domain.model.AoiBoundaryEntity
import com.gop.survey.corporatefarm.domain.model.BlockEntity
import com.gop.survey.corporatefarm.domain.model.CropEntity
import com.gop.survey.corporatefarm.domain.model.CropTypeEntity
import com.gop.survey.corporatefarm.domain.model.CropVarietyEntity
import com.gop.survey.corporatefarm.domain.model.DivisionEntity
import com.gop.survey.corporatefarm.domain.model.FarmEntity
import com.gop.survey.corporatefarm.domain.model.IrrigationSourceEntity
import com.gop.survey.corporatefarm.domain.model.KachiAbadiEntity
import com.gop.survey.corporatefarm.domain.model.LessorEntity
import com.gop.survey.corporatefarm.domain.model.NewSurveyNewEntity
import com.gop.survey.corporatefarm.domain.model.NotAtHomeSurveyFormEntity
import com.gop.survey.corporatefarm.domain.model.ParcelEntity
import com.gop.survey.corporatefarm.domain.model.PlotEntity
import com.gop.survey.corporatefarm.domain.model.PropertyTypeEntity
import com.gop.survey.corporatefarm.domain.model.SectionEntity
import com.gop.survey.corporatefarm.domain.model.SowingPersonEntity
import com.gop.survey.corporatefarm.domain.model.StatusConverter
import com.gop.survey.corporatefarm.domain.model.SurveyEntity
import com.gop.survey.corporatefarm.domain.model.SurveyFormEntity
import com.gop.survey.corporatefarm.domain.model.SurveyImage
import com.gop.survey.corporatefarm.domain.model.SurveyLessorEntity
import com.gop.survey.corporatefarm.domain.model.SurveyPersonEntity
import com.gop.survey.corporatefarm.domain.model.TaskEntity
import com.gop.survey.corporatefarm.domain.model.TempSurveyFormEntity
import com.gop.survey.corporatefarm.domain.model.TempSurveyLogEntity
import com.gop.survey.corporatefarm.domain.model.ZoneEntity

@Database(
    entities = [
        NewSurveyNewEntity::class,
        SurveyPersonEntity::class,
        SurveyImage::class,
        ParcelEntity::class,
        KachiAbadiEntity::class,
        SurveyEntity::class,
        SurveyFormEntity::class,
        TempSurveyFormEntity::class,
        TempSurveyLogEntity::class,
        NotAtHomeSurveyFormEntity::class,
        ActiveParcelEntity::class,
        TaskEntity::class,
        CropEntity::class,
        CropTypeEntity::class,
        CropVarietyEntity::class,
        SowingPersonEntity::class,
        ZoneEntity::class,
        DivisionEntity::class,
        SectionEntity::class,
        BlockEntity::class,
        FarmEntity::class,
        PlotEntity::class,
        LessorEntity::class,
        SurveyLessorEntity::class,
        IrrigationSourceEntity::class,
        PropertyTypeEntity::class,
        AoiBoundaryEntity::class ],
    version = 34,
    exportSchema = false
)
@TypeConverters(StatusConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun parcelDao(): ParcelDao
    abstract fun activeParcelDao(): ActiveParcelDao
    abstract fun tempSurveyLogDao(): TempSurveyLogDao
    abstract fun kachiAbadiDao(): KachiAbadiDao
    abstract fun surveyDao(): NewSurveyDao
    abstract fun surveyFormDao(): SurveyFormDao
    abstract fun tempSurveyFormDao(): TempSurveyFormDao
    abstract fun notAtHomeSurveyFormDao(): NotAtHomeSurveyFormDao
    abstract fun newSurveyNewDao(): NewSurveyNewDao
    abstract fun personDao(): SurveyPersonDao
    abstract fun imageDao(): SurveyImageDao
    abstract fun taskDao(): TaskDao
    abstract fun cropDao(): CropDao
    abstract fun cropTypeDao(): CropTypeDao
    abstract fun cropVarietyDao(): CropVarietyDao
    abstract fun sowingPersonDao(): SowingPersonDao
    abstract fun zoneDao(): ZoneDao
    abstract fun divisionDao(): DivisionDao
    abstract fun sectionDao(): SectionDao
    abstract fun blockDao(): BlockDao
    abstract fun farmDao(): FarmDao
    abstract fun plotDao(): PlotDao
    abstract fun lessorDao(): LessorDao
    abstract fun surveyLessorDao(): SurveyLessorDao
    abstract fun irrigationSourceDao(): IrrigationSourceDao
    abstract fun propertyTypeDao(): PropertyTypeDao
    abstract fun aoiBoundaryDao(): AoiBoundaryDao
    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                    .addMigrations(migration1to2)
                    .addMigrations(migration2to3)
                    .addMigrations(migration3to4)
                    .addMigrations(migration4to5)
                    .addMigrations(migration5to6)
                    .addMigrations(migration6to7)
                    .addMigrations(migration7to8)
                    .addMigrations(migration8to9)
                    .addMigrations(migration9to10)
                    .addMigrations(migration10to11)
                    .addMigrations(migration11to12)
                    .addMigrations(migration12to13)
                    .addMigrations(migration13to14)
                    .addMigrations(migration14to15)
                    .addMigrations(migration15to16)
                    .addMigrations(migration16to17)
                    .addMigrations(migration17to18)
                    .addMigrations(migration18to19)
                    .addMigrations(migration19to20)
                    .addMigrations(migration20to21)
                    .addMigrations(migration21to22)
                    .addMigrations(migration22to23)
                    .addMigrations(migration23to24)
                    .addMigrations(migration24to25)
                    .addMigrations(migration27to28)
                    .addMigrations(migration28to29)
                    .addMigrations(migration29to30)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

val migration1to2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE NotAtHomeSurveyFormEntity ADD COLUMN qrCode TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE SurveyFormEntity ADD COLUMN qrCode TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE TempSurveyFormEntity ADD COLUMN qrCode TEXT NOT NULL DEFAULT ''")
    }
}

val migration2to3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ParcelEntity ADD COLUMN parcelNo INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ParcelEntity ADD COLUMN subParcelNo TEXT NOT NULL DEFAULT '0'")
        db.execSQL("ALTER TABLE ParcelEntity ADD COLUMN newStatusId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ParcelEntity ADD COLUMN subParcelsStatusList TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE SurveyFormEntity ADD COLUMN parcelNo INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE SurveyFormEntity ADD COLUMN subParcelNo TEXT NOT NULL DEFAULT '0'")
        db.execSQL("ALTER TABLE SurveyFormEntity ADD COLUMN newStatusId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE SurveyFormEntity ADD COLUMN subParcelsStatusList TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE TempSurveyFormEntity ADD COLUMN parcelNo INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE TempSurveyFormEntity ADD COLUMN subParcelNo TEXT NOT NULL DEFAULT '0'")
        db.execSQL("ALTER TABLE TempSurveyFormEntity ADD COLUMN newStatusId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE TempSurveyFormEntity ADD COLUMN subParcelsStatusList TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE NotAtHomeSurveyFormEntity ADD COLUMN parcelNo INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE NotAtHomeSurveyFormEntity ADD COLUMN subParcelNo TEXT NOT NULL DEFAULT '0'")
        db.execSQL("ALTER TABLE NotAtHomeSurveyFormEntity ADD COLUMN newStatusId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE NotAtHomeSurveyFormEntity ADD COLUMN subParcelsStatusList TEXT NOT NULL DEFAULT ''")
    }
}

val migration3to4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE SurveyFormEntity ADD COLUMN isRevisit INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE TempSurveyFormEntity ADD COLUMN isRevisit INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE NotAtHomeSurveyFormEntity ADD COLUMN isRevisit INTEGER NOT NULL DEFAULT 0")

    }
}

val migration4to5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE active_parcels ADD COLUMN isActivate INTEGER NOT NULL DEFAULT 1")
    }
}

// ✅ NEW MIGRATION: Add tasks table
val migration5to6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                taskId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                assignDate TEXT NOT NULL DEFAULT '',
                issueType TEXT NOT NULL DEFAULT '',
                details TEXT NOT NULL DEFAULT '',
                picData TEXT NOT NULL DEFAULT '',
                parcelId INTEGER NOT NULL DEFAULT 0,
                parcelNo TEXT NOT NULL DEFAULT '',
                mauzaId INTEGER NOT NULL DEFAULT 0,
                assignedByUserId INTEGER NOT NULL DEFAULT 0,
                assignedToUserId INTEGER NOT NULL DEFAULT 0,
                createdOn INTEGER NOT NULL DEFAULT 0,
                isSynced INTEGER NOT NULL DEFAULT 0
            )
        """
        )
    }
}

val migration6to7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN khewatInfo TEXT NOT NULL DEFAULT ''")
    }
}

val migration7to8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN unitId INTEGER")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN groupId INTEGER")
    }
}

val migration8to9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE tasks ADD COLUMN daysToComplete INTEGER NOT NULL DEFAULT 0")
    }
}

val migration9to10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add any migration that was supposed to be in version 10
        // If version 10 doesn't have changes, you can leave this empty
    }
}

val migration10to11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE survey_images ADD COLUMN latitude REAL")
        database.execSQL("ALTER TABLE survey_images ADD COLUMN longitude REAL")
        database.execSQL("ALTER TABLE survey_images ADD COLUMN timestamp INTEGER")
        database.execSQL("ALTER TABLE survey_images ADD COLUMN locationAddress TEXT")
    }
}

val migration11to12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE survey_images ADD COLUMN bearing REAL")
    }
}

val migration12to13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE survey_persons ADD COLUMN address TEXT NOT NULL DEFAULT ''")
    }
}
val migration13to14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE new_surveys ADD COLUMN address TEXT NOT NULL DEFAULT ''")
    }
}

val migration14to15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sowing_persons (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                surveyId INTEGER NOT NULL,
                name TEXT NOT NULL,
                cnic TEXT NOT NULL,
                growerCode TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent()
        )

        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sowing_persons_surveyId ON sowing_persons(surveyId)"
        )
    }
}

val migration15to16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE new_surveys ADD COLUMN sowingStatus TEXT NOT NULL DEFAULT 'No'")
        database.execSQL("ALTER TABLE new_surveys ADD COLUMN sowingDate TEXT")
    }
}

val migration16to17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN zone TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN division TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN section TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN farm TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN block TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN plot TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN ownershipStatus TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN lessorName TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN plotBifurcation TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN plotSizeAcres REAL")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN calculatedArea TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN year TEXT")
    }
}

// STEP 3: ADD THIS MIGRATION (this is version 2 → 3)
private val migration17to18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create zones table
        database.execSQL(
            """
                    CREATE TABLE IF NOT EXISTS zones (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        zoneId INTEGER NOT NULL,
                        zoneName TEXT NOT NULL,
                        description TEXT,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        lastSynced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent()
        )

        // Create divisions table with foreign key
        database.execSQL(
            """
                    CREATE TABLE IF NOT EXISTS divisions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        divisionId INTEGER NOT NULL,
                        zoneLocalId INTEGER NOT NULL,
                        divisionName TEXT NOT NULL,
                        description TEXT,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        lastSynced INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(zoneLocalId) REFERENCES zones(id) ON DELETE CASCADE
                    )
                """.trimIndent()
        )

        // Create sections table with foreign key
        database.execSQL(
            """
                    CREATE TABLE IF NOT EXISTS sections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sectionId INTEGER NOT NULL,
                        divisionLocalId INTEGER NOT NULL,
                        sectionName TEXT NOT NULL,
                        description TEXT,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        lastSynced INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(divisionLocalId) REFERENCES divisions(id) ON DELETE CASCADE
                    )
                """.trimIndent()
        )

        // Create blocks table with foreign key
        database.execSQL(
            """
                    CREATE TABLE IF NOT EXISTS blocks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        blockId INTEGER NOT NULL,
                        sectionLocalId INTEGER NOT NULL,
                        blockName TEXT NOT NULL,
                        description TEXT,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        lastSynced INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(sectionLocalId) REFERENCES sections(id) ON DELETE CASCADE
                    )
                """.trimIndent()
        )

        // Create indices for foreign keys
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_divisions_zoneLocalId ON divisions(zoneLocalId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_sections_divisionLocalId ON sections(divisionLocalId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_blocks_sectionLocalId ON blocks(sectionLocalId)")
    }
}

val migration18to19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. Create farms table first
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS farms (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                farmId INTEGER NOT NULL,
                sectionLocalId INTEGER NOT NULL,
                farmName TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                lastSynced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(sectionLocalId) REFERENCES sections(id) ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_farms_sectionLocalId ON farms(sectionLocalId)")

        // 2. Recreate blocks with farmLocalId BEFORE creating plots
        database.execSQL("DROP TABLE IF EXISTS blocks")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS blocks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                blockId INTEGER NOT NULL,
                farmLocalId INTEGER NOT NULL,
                blockName TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                lastSynced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(farmLocalId) REFERENCES farms(id) ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_blocks_farmLocalId ON blocks(farmLocalId)")

        // 3. Create plots AFTER blocks is recreated
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS plots (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                plotId INTEGER NOT NULL,
                blockLocalId INTEGER NOT NULL,
                plotName TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                lastSynced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(blockLocalId) REFERENCES blocks(id) ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_plots_blockLocalId ON plots(blockLocalId)")
    }
}
val migration19to20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE new_surveys ADD COLUMN farm TEXT DEFAULT NULL")
    }
}

val migration20to21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Drop and recreate all cascade tables with new schema
        database.execSQL("DROP TABLE IF EXISTS plots")
        database.execSQL("DROP TABLE IF EXISTS blocks")
        database.execSQL("DROP TABLE IF EXISTS farms")
        database.execSQL("DROP TABLE IF EXISTS sections")
        database.execSQL("DROP TABLE IF EXISTS divisions")
        database.execSQL("DROP TABLE IF EXISTS zones")

        database.execSQL("CREATE TABLE IF NOT EXISTS zones (zoneId INTEGER PRIMARY KEY NOT NULL, zoneName TEXT NOT NULL, isActive INTEGER NOT NULL DEFAULT 1, lastSynced INTEGER NOT NULL DEFAULT 0)")
        database.execSQL("CREATE TABLE IF NOT EXISTS divisions (divisionId INTEGER PRIMARY KEY NOT NULL, zoneId INTEGER NOT NULL, divisionName TEXT NOT NULL, isActive INTEGER NOT NULL DEFAULT 1, lastSynced INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(zoneId) REFERENCES zones(zoneId) ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_divisions_zoneId ON divisions(zoneId)")
        database.execSQL("CREATE TABLE IF NOT EXISTS sections (sectionId INTEGER PRIMARY KEY NOT NULL, divisionId INTEGER NOT NULL, sectionName TEXT NOT NULL, isActive INTEGER NOT NULL DEFAULT 1, lastSynced INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(divisionId) REFERENCES divisions(divisionId) ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_sections_divisionId ON sections(divisionId)")
        database.execSQL("CREATE TABLE IF NOT EXISTS farms (farmId INTEGER PRIMARY KEY NOT NULL, sectionId INTEGER NOT NULL, farmName TEXT NOT NULL, isActive INTEGER NOT NULL DEFAULT 1, lastSynced INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(sectionId) REFERENCES sections(sectionId) ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_farms_sectionId ON farms(sectionId)")
        database.execSQL("CREATE TABLE IF NOT EXISTS blocks (blockId INTEGER PRIMARY KEY NOT NULL, farmId INTEGER NOT NULL, blockName TEXT NOT NULL, isActive INTEGER NOT NULL DEFAULT 1, lastSynced INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(farmId) REFERENCES farms(farmId) ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_blocks_farmId ON blocks(farmId)")
        database.execSQL("CREATE TABLE IF NOT EXISTS plots (plotId INTEGER PRIMARY KEY NOT NULL, blockId INTEGER NOT NULL, plotName TEXT NOT NULL, isActive INTEGER NOT NULL DEFAULT 1, lastSynced INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(blockId) REFERENCES blocks(blockId) ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_plots_blockId ON plots(blockId)")
    }
}

val migration21to22 = object : Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {

        // 1. Create new table
        database.execSQL("""
            CREATE TABLE active_parcels_new (
                pkid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                id INTEGER NOT NULL,
                parcelNo INTEGER NOT NULL,
                subParcelNo TEXT NOT NULL,
                mauzaId INTEGER NOT NULL,
                mauzaName TEXT NOT NULL,
                khewatInfo TEXT NOT NULL,
                areaAssigned TEXT NOT NULL,
                geomWKT TEXT NOT NULL,
                centroid TEXT NOT NULL,
                distance INTEGER NOT NULL,
                parcelType TEXT NOT NULL,
                parcelAreaKMF TEXT,
                parcelAreaAbadiDeh TEXT,
                surveyStatusCode INTEGER NOT NULL,
                surveyId TEXT,
                isActivate INTEGER NOT NULL DEFAULT 1,
                unitId INTEGER,
                groupId INTEGER,
                zone TEXT,
                division TEXT,
                section TEXT,
                farm TEXT,
                block TEXT,
                plot TEXT,
                ownershipStatus TEXT,
                lessorName TEXT,
                plotBifurcation TEXT,
                plotSizeAcres REAL,
                calculatedArea TEXT,
                year TEXT
            )
        """)

        // 2. Copy data
        database.execSQL("""
            INSERT INTO active_parcels_new
            SELECT
                pkid, id, parcelNo, subParcelNo, mauzaId, mauzaName,
                khewatInfo, areaAssigned, geomWKT, centroid, distance,
                parcelType, parcelAreaKMF, parcelAreaAbadiDeh,
                surveyStatusCode,
                surveyId,
                isActivate,
                unitId, groupId, zone, division, section, farm,
                block, plot, ownershipStatus, lessorName,
                plotBifurcation, plotSizeAcres, calculatedArea, year
            FROM active_parcels
        """)

        // 3. Drop old table
        database.execSQL("DROP TABLE active_parcels")

        // 4. Rename
        database.execSQL("ALTER TABLE active_parcels_new RENAME TO active_parcels")
    }
}
// ============================================
// DATABASE MIGRATION: Version 22 → 23
// ============================================
// This migration fixes the primary key strategy for cascade tables
// Changes: Remove autoGenerate = true, use server-provided IDs as PKs

val migration22to23 = object : Migration(22, 23) {
    override fun migrate(database: SupportSQLiteDatabase) {

        // ========== STEP 1: Drop tables in dependency order (reverse of creation) ==========
        database.execSQL("DROP TABLE IF EXISTS plots")
        database.execSQL("DROP TABLE IF EXISTS blocks")
        database.execSQL("DROP TABLE IF EXISTS farms")
        database.execSQL("DROP TABLE IF EXISTS sections")
        database.execSQL("DROP TABLE IF EXISTS divisions")
        // Keep zones (already correct)

        // ========== STEP 2: Recreate divisions with correct PK strategy ==========
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS divisions (
                divisionId INTEGER PRIMARY KEY NOT NULL,
                zoneId INTEGER NOT NULL,
                divisionName TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                lastSynced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(zoneId) REFERENCES zones(zoneId) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_divisions_zoneId ON divisions(zoneId)")
        Log.d("Migration", "✅ Recreated divisions table with correct PK strategy")

        // ========== STEP 3: Recreate sections with correct PK strategy ==========
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS sections (
                sectionId INTEGER PRIMARY KEY NOT NULL,
                divisionId INTEGER NOT NULL,
                sectionName TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                lastSynced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(divisionId) REFERENCES divisions(divisionId) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_sections_divisionId ON sections(divisionId)")
        Log.d("Migration", "✅ Recreated sections table with correct PK strategy")

        // ========== STEP 4: Recreate farms with correct PK strategy ==========
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS farms (
                farmId INTEGER PRIMARY KEY NOT NULL,
                sectionId INTEGER NOT NULL,
                farmName TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                lastSynced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(sectionId) REFERENCES sections(sectionId) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_farms_sectionId ON farms(sectionId)")
        Log.d("Migration", "✅ Recreated farms table with correct PK strategy")

        // ========== STEP 5: Recreate blocks (now correctly references farmId, not sectionId) ==========
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS blocks (
                blockId INTEGER PRIMARY KEY NOT NULL,
                farmId INTEGER NOT NULL,
                blockName TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                lastSynced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(farmId) REFERENCES farms(farmId) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_blocks_farmId ON blocks(farmId)")
        Log.d("Migration", "✅ Recreated blocks table (now references farms, not sections)")

        // ========== STEP 6: Recreate plots ==========
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS plots (
                plotId INTEGER PRIMARY KEY NOT NULL,
                blockId INTEGER NOT NULL,
                plotName TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                lastSynced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(blockId) REFERENCES blocks(blockId) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_plots_blockId ON plots(blockId)")
        Log.d("Migration", "✅ Recreated plots table with correct PK strategy")

        Log.d("Migration", "✅✅✅ Migration 22→23 completed successfully!")
    }
}

val migration23to24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Drop and recreate with new schema (parcelNo is now TEXT)
        db.execSQL("DROP TABLE IF EXISTS active_parcels")
        db.execSQL("""
            CREATE TABLE active_parcels (
                pkid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                id INTEGER NOT NULL,
                parcelNo TEXT NOT NULL DEFAULT '',
                subParcelNo TEXT NOT NULL DEFAULT '',
                mauzaId INTEGER NOT NULL DEFAULT 0,
                mauzaName TEXT NOT NULL DEFAULT '',
                khewatInfo TEXT NOT NULL DEFAULT '',
                areaAssigned TEXT NOT NULL DEFAULT '',
                geomWKT TEXT NOT NULL DEFAULT '',
                centroid TEXT NOT NULL DEFAULT '',
                distance INTEGER NOT NULL DEFAULT 0,
                parcelType TEXT NOT NULL DEFAULT '',
                parcelAreaKMF TEXT,
                parcelAreaAbadiDeh TEXT,
                surveyStatusCode INTEGER NOT NULL DEFAULT 1,
                surveyId TEXT,
                isActivate INTEGER NOT NULL DEFAULT 1,
                unitId INTEGER DEFAULT 0,
                groupId INTEGER DEFAULT 0,
                plotId TEXT,
                ownerName TEXT,
                cnic TEXT,
                mobileNo TEXT,
                cropName TEXT,
                cropArea REAL,
                tehsil TEXT,
                district TEXT,
                zone TEXT,
                division TEXT,
                section TEXT,
                farm TEXT,
                block TEXT,
                plot TEXT,
                area REAL,
                ownershipStatus TEXT,
                lessorName TEXT,
                plotBifurcation TEXT,
                plotSizeAcres REAL,
                calculatedArea REAL,
                year TEXT
            )
        """)
    }
}

val migration24to25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE active_parcels ADD COLUMN multiMergeParcelNos TEXT"
        )
    }
}
val migration27to28 = object : Migration(27, 28) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE new_surveys ADD COLUMN irrigationSource TEXT DEFAULT NULL")
    }
}
val migration28to29 = object : Migration(28, 29) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE new_surveys ADD COLUMN irrigationSourceQuantity TEXT DEFAULT NULL"
        )
    }
}

val migration29to30 = object : Migration(29, 30) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS property_types (
                value TEXT NOT NULL PRIMARY KEY,
                sortOrder INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}