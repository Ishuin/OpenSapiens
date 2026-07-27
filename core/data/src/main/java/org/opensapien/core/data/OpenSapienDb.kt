package org.opensapien.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun sourceToString(s: Transcript.Source) = s.name
    @TypeConverter fun stringToSource(s: String) = Transcript.Source.valueOf(s)
    @TypeConverter fun syncToString(s: Transcript.SyncState) = s.name
    @TypeConverter fun stringToSync(s: String) = Transcript.SyncState.valueOf(s)
}

@Database(entities = [Transcript::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class OpenSapienDb : RoomDatabase() {
    abstract fun transcripts(): TranscriptDao

    companion object {
        @Volatile private var instance: OpenSapienDb? = null

        fun get(context: Context): OpenSapienDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OpenSapienDb::class.java,
                    "open_sapien.db",
                ).build().also { instance = it }
            }
    }
}
