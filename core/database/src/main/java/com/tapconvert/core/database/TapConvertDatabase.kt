package com.tapconvert.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tapconvert.core.database.converter.Converters
import com.tapconvert.core.database.dao.ConversionDao
import com.tapconvert.core.database.entity.ConversionRecordEntity

@Database(
    entities = [ConversionRecordEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TapConvertDatabase : RoomDatabase() {

    abstract fun conversionDao(): ConversionDao

    companion object {
        private const val DATABASE_NAME = "tapconvert.db"

        @Volatile
        private var instance: TapConvertDatabase? = null

        fun getInstance(context: Context): TapConvertDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): TapConvertDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                TapConvertDatabase::class.java,
                DATABASE_NAME
            ).fallbackToDestructiveMigration().build()
        }
    }
}
