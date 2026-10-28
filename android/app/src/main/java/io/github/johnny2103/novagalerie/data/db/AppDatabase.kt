package io.github.johnny2103.novagalerie.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MediaMetaEntity::class, FaceEntity::class, PersonEntity::class, TagEntity::class, MediaTagEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaMetaDao(): MediaMetaDao
    abstract fun faceDao(): FaceDao
    abstract fun personDao(): PersonDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "nova.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
