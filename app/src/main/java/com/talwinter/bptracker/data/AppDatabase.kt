package com.talwinter.bptracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun armToString(v: Arm): String = v.name
    @TypeConverter fun stringToArm(v: String): Arm = Arm.valueOf(v)

    @TypeConverter fun positionToString(v: BodyPosition): String = v.name
    @TypeConverter fun stringToPosition(v: String): BodyPosition = BodyPosition.valueOf(v)

    @TypeConverter fun occasionToString(v: Occasion): String = v.name
    @TypeConverter fun stringToOccasion(v: String): Occasion = Occasion.valueOf(v)

    @TypeConverter fun medsToString(v: MedicationState): String = v.name
    @TypeConverter fun stringToMeds(v: String): MedicationState = MedicationState.valueOf(v)

    @TypeConverter fun sourceToString(v: ReadingSource): String = v.name
    @TypeConverter fun stringToSource(v: String): ReadingSource = ReadingSource.valueOf(v)
}

@Database(entities = [Reading::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "bp-tracker.db"
            )
                // No destructive migration. This is health data the user has hand-entered
                // over months; a schema change must never silently wipe it. A missing
                // migration should fail loudly in development instead.
                .build()
                .also { instance = it }
        }
    }
}
