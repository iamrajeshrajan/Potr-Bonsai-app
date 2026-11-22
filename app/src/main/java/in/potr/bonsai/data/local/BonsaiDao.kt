package `in`.potr.bonsai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import `in`.potr.bonsai.data.model.CareActionEntity
import `in`.potr.bonsai.data.model.ScheduledActionEntity
import `in`.potr.bonsai.data.model.SpecimenEntity
import `in`.potr.bonsai.data.model.SpecimenWithDetails
import `in`.potr.bonsai.data.model.TimelineEntryEntity
import `in`.potr.bonsai.data.model.UploadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BonsaiDao {

    @Transaction
    @Query("SELECT * FROM specimens ORDER BY createdAtEpochMs DESC")
    fun observeSpecimensWithDetails(): Flow<List<SpecimenWithDetails>>

    @Transaction
    @Query("SELECT * FROM specimens WHERE id = :id")
    fun observeSpecimenById(id: String): Flow<SpecimenWithDetails?>

    @Query("SELECT * FROM care_actions ORDER BY category ASC, title ASC")
    fun observeCareActions(): Flow<List<CareActionEntity>>

    @Query("SELECT * FROM specimens ORDER BY createdAtEpochMs DESC")
    suspend fun getAllSpecimens(): List<SpecimenEntity>

    @Query("SELECT * FROM specimens WHERE id = :id")
    suspend fun getSpecimenById(id: String): SpecimenEntity?

    @Query("SELECT * FROM care_actions ORDER BY category ASC, title ASC")
    suspend fun getAllCareActions(): List<CareActionEntity>

    @Query("SELECT COUNT(*) FROM care_actions")
    suspend fun countCareActions(): Int

    @Query("SELECT * FROM scheduled_actions")
    suspend fun getAllScheduledActions(): List<ScheduledActionEntity>

    @Query("SELECT * FROM timeline_entries ORDER BY createdAtEpochMs DESC")
    suspend fun getAllTimelineEntries(): List<TimelineEntryEntity>

    @Query("SELECT COUNT(*) FROM specimens")
    suspend fun countSpecimens(): Int

    @Query(
        """
        SELECT * FROM timeline_entries
        WHERE (beforeUploadStatus = :pending OR beforeUploadStatus = :failed)
           OR (afterUploadStatus = :pending OR afterUploadStatus = :failed)
        ORDER BY createdAtEpochMs ASC
        """
    )
    suspend fun getPendingVideoEntries(
        pending: String = UploadStatus.PENDING,
        failed: String = UploadStatus.FAILED
    ): List<TimelineEntryEntity>

    @Query(
        """
        SELECT COUNT(*) FROM timeline_entries
        WHERE beforeUploadStatus IN ('PENDING', 'UPLOADING', 'FAILED')
           OR afterUploadStatus IN ('PENDING', 'UPLOADING', 'FAILED')
        """
    )
    fun observePendingVideoCount(): Flow<Int>

    @Query("SELECT * FROM specimens WHERE drivePhotoFileId IS NULL AND localImagePath NOT LIKE 'asset:%' AND localImagePath != ''")
    suspend fun getSpecimensNeedingDrivePhotoUpload(): List<SpecimenEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpecimen(specimen: SpecimenEntity)

    @Update
    suspend fun updateSpecimen(specimen: SpecimenEntity)

    @Query("DELETE FROM specimens WHERE id = :id")
    suspend fun deleteSpecimenById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCareAction(action: CareActionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCareActionsIfMissing(items: List<CareActionEntity>)

    @Query("DELETE FROM care_actions WHERE id = :actionId")
    suspend fun deleteCareAction(actionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAction(action: ScheduledActionEntity)

    @Query("DELETE FROM scheduled_actions WHERE id = :actionId")
    suspend fun deleteAction(actionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimelineEntry(entry: TimelineEntryEntity)

    @Update
    suspend fun updateTimelineEntry(entry: TimelineEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSpecimens(items: List<SpecimenEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCareActions(items: List<CareActionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllActions(items: List<ScheduledActionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTimelineEntries(items: List<TimelineEntryEntity>)

    @Query("DELETE FROM timeline_entries")
    suspend fun clearTimelineEntries()

    @Query("DELETE FROM scheduled_actions")
    suspend fun clearScheduledActions()

    @Query("DELETE FROM care_actions")
    suspend fun clearCareActions()

    @Query("DELETE FROM specimens")
    suspend fun clearSpecimens()

    @Transaction
    suspend fun replaceAllFromBackup(
        specimens: List<SpecimenEntity>,
        careActions: List<CareActionEntity>,
        actions: List<ScheduledActionEntity>,
        timelineEntries: List<TimelineEntryEntity>
    ) {
        clearTimelineEntries()
        clearScheduledActions()
        clearCareActions()
        clearSpecimens()
        insertAllCareActions(careActions)
        insertAllSpecimens(specimens)
        insertAllActions(actions)
        insertAllTimelineEntries(timelineEntries)
    }
}
