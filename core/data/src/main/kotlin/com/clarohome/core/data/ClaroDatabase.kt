package com.clarohome.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "foundation_records")
data class FoundationRecord(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface FoundationDao {
    @Query("SELECT * FROM foundation_records ORDER BY `key`")
    fun observeAll(): Flow<List<FoundationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: FoundationRecord)
}

@Database(
    entities = [FoundationRecord::class],
    version = 1,
    exportSchema = true,
)
abstract class ClaroDatabase : RoomDatabase() {
    abstract fun foundationDao(): FoundationDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ClaroDatabase =
        Room.databaseBuilder(context, ClaroDatabase::class.java, "claro.db").build()
}
