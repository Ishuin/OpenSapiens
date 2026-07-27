package org.opensapien.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Transcript>>

    @Query("SELECT * FROM transcripts WHERE id = :id")
    suspend fun byId(id: Long): Transcript?

    @Query(
        "SELECT * FROM transcripts WHERE title LIKE '%' || :q || '%' " +
            "OR preview LIKE '%' || :q || '%' ORDER BY createdAt DESC"
    )
    fun search(q: String): Flow<List<Transcript>>

    @Query("SELECT * FROM transcripts WHERE syncState = 'PENDING' OR syncState = 'FAILED'")
    suspend fun pendingSync(): List<Transcript>

    @Insert
    suspend fun insert(t: Transcript): Long

    @Update
    suspend fun update(t: Transcript)

    @Query("DELETE FROM transcripts WHERE id = :id")
    suspend fun delete(id: Long)
}
