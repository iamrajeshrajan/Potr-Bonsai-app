package `in`.potr.bonsai.data.repository

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import `in`.potr.bonsai.BuildConfig
import `in`.potr.bonsai.cloud.AuthTokenResult
import `in`.potr.bonsai.cloud.GoogleAuthTokenProvider
import `in`.potr.bonsai.cloud.GoogleWorkspaceService
import `in`.potr.bonsai.cloud.WooCommercePublisher
import `in`.potr.bonsai.cloud.YouTubeUploader
import `in`.potr.bonsai.data.local.BonsaiDao
import `in`.potr.bonsai.data.local.SecurePrefs
import `in`.potr.bonsai.data.model.AssetPhotoKeys
import `in`.potr.bonsai.data.model.BackupPayload
import `in`.potr.bonsai.data.model.CareActionEntity
import `in`.potr.bonsai.data.model.DefaultBonsaiActions
import `in`.potr.bonsai.data.model.DueDateHelper
import `in`.potr.bonsai.data.model.ScheduledActionEntity
import `in`.potr.bonsai.data.model.SpecimenEntity
import `in`.potr.bonsai.data.model.SpecimenWithDetails
import `in`.potr.bonsai.data.model.TimelineEntryEntity
import `in`.potr.bonsai.data.model.UploadStatus
import `in`.potr.bonsai.sync.SyncScheduler
import `in`.potr.bonsai.util.PhotoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.UUID

sealed interface CloudSyncOutcome {
    data class Success(val message: String) : CloudSyncOutcome
    data object NotSignedIn : CloudSyncOutcome
    data class NeedsResolution(val pendingIntent: PendingIntent) : CloudSyncOutcome
    data class Error(val message: String, val isConfigError: Boolean = false) : CloudSyncOutcome
}

class BonsaiRepository(
    private val context: Context,
    private val dao: BonsaiDao,
    val prefs: SecurePrefs,
    private val workspaceService: GoogleWorkspaceService = GoogleWorkspaceService(),
    private val youtubeUploader: YouTubeUploader = YouTubeUploader(),
    private val wooPublisher: WooCommercePublisher = WooCommercePublisher()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    val specimensFlow: Flow<List<SpecimenWithDetails>> = dao.observeSpecimensWithDetails()
    val careActionsFlow: Flow<List<CareActionEntity>> = dao.observeCareActions()
    val pendingVideoCountFlow: Flow<Int> = dao.observePendingVideoCount()

    fun observeSpecimen(id: String): Flow<SpecimenWithDetails?> = dao.observeSpecimenById(id)

    suspend fun compressPhotoFromUri(sourceUri: Uri, specimenCode: String): PhotoCompressor.CompressedPhoto =
        withContext(Dispatchers.IO) {
            PhotoCompressor.compressUriToJpeg(
                context = context,
                sourceUri = sourceUri,
                specimenCode = specimenCode
            )
        }

    suspend fun stageVideoFromUri(sourceUri: Uri, prefix: String): File =
        withContext(Dispatchers.IO) {
            PhotoCompressor.stageVideoFromUri(context, sourceUri, prefix)
        }

    suspend fun addSpecimen(
        code: String,
        name: String,
        species: String,
        subspecies: String,
        age: Int,
        ageType: String,
        ageNote: String,
        pot: String,
        potMeta: String,
        localImagePath: String?,
        purchasePrice: Double,
        newPotPrice: Double,
        effortCost: Double,
        sellingPrice: Double,
        initialActionTitle: String?,
        initialActionDays: Int?
    ): String {
        val id = "bns-${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()
        val monthYear = DueDateHelper.currentMonthYear()
        val resolvedImage = localImagePath?.takeIf { it.isNotBlank() }
            ?: AssetPhotoKeys.defaultForSpecies(species)

        val specimen = SpecimenEntity(
            id = id,
            code = code.trim(),
            name = name.trim(),
            species = species.trim(),
            subspecies = subspecies.trim(),
            age = age.coerceIn(1, 500),
            ageType = ageType,
            ageNote = ageNote.trim().ifBlank {
                if (ageType == "accurate") "Recorded at cataloging" else "Estimated age at cataloging"
            },
            localImagePath = resolvedImage,
            pot = pot.trim().ifBlank { "Handcrafted Terracotta Oval" },
            potMeta = potMeta.trim().ifBlank { "Cataloged $monthYear" },
            repotted = monthYear,
            published = false,
            purchasePrice = purchasePrice.coerceAtLeast(0.0),
            newPotPrice = newPotPrice.coerceAtLeast(0.0),
            effortCost = effortCost.coerceAtLeast(0.0),
            sellingPrice = sellingPrice.coerceAtLeast(0.0),
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        dao.upsertSpecimen(specimen)

        val cleanActionTitle = initialActionTitle?.trim().orEmpty()
        if (cleanActionTitle.isNotBlank() && initialActionDays != null && initialActionDays >= 0) {
            val safeDays = initialActionDays.coerceIn(0, 3650)
            val dueDay = LocalDate.now().plusDays(safeDays.toLong()).toEpochDay()
            dao.upsertAction(
                ScheduledActionEntity(
                    id = "act-${UUID.randomUUID().toString().take(8)}",
                    specimenId = id,
                    title = cleanActionTitle,
                    dueDateEpochDay = dueDay,
                    ruleDescription = if (safeDays == 0) "Due today from catalog" else "Scheduled +$safeDays days from catalog",
                    intervalDays = safeDays.coerceAtLeast(1)
                )
            )
        }

        triggerAutoBackupIfSignedIn()
        return id
    }

    suspend fun updateSpecimen(
        existing: SpecimenEntity,
        code: String,
        name: String,
        species: String,
        subspecies: String,
        age: Int,
        ageType: String,
        ageNote: String,
        pot: String,
        potMeta: String,
        repotted: String,
        localImagePath: String?,
        purchasePrice: Double,
        newPotPrice: Double,
        effortCost: Double,
        sellingPrice: Double
    ) {
        val photoChanged = !localImagePath.isNullOrBlank() && localImagePath != existing.localImagePath
        val updated = existing.copy(
            code = code.trim(),
            name = name.trim(),
            species = species.trim(),
            subspecies = subspecies.trim(),
            age = age.coerceIn(1, 500),
            ageType = ageType,
            ageNote = ageNote.trim(),
            pot = pot.trim(),
            potMeta = potMeta.trim(),
            repotted = repotted.trim(),
            localImagePath = localImagePath?.takeIf { it.isNotBlank() } ?: existing.localImagePath,
            drivePhotoFileId = if (photoChanged) null else existing.drivePhotoFileId,
            drivePhotoUrl = if (photoChanged) null else existing.drivePhotoUrl,
            purchasePrice = purchasePrice.coerceAtLeast(0.0),
            newPotPrice = newPotPrice.coerceAtLeast(0.0),
            effortCost = effortCost.coerceAtLeast(0.0),
            sellingPrice = sellingPrice.coerceAtLeast(0.0),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        dao.updateSpecimen(updated)
        triggerAutoBackupIfSignedIn()
    }

    suspend fun updateSpecimenPhoto(specimen: SpecimenEntity, newLocalPath: String) {
        dao.updateSpecimen(
            specimen.copy(
                localImagePath = newLocalPath,
                drivePhotoFileId = null,
                drivePhotoUrl = null,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
        triggerAutoBackupIfSignedIn()
    }

    suspend fun updateSpecimenPricing(
        specimen: SpecimenEntity,
        purchasePrice: Double,
        newPotPrice: Double,
        effortCost: Double,
        sellingPrice: Double
    ) {
        dao.updateSpecimen(
            specimen.copy(
                purchasePrice = purchasePrice.coerceAtLeast(0.0),
                newPotPrice = newPotPrice.coerceAtLeast(0.0),
                effortCost = effortCost.coerceAtLeast(0.0),
                sellingPrice = sellingPrice.coerceAtLeast(0.0),
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
        triggerAutoBackupIfSignedIn()
    }

    suspend fun cycleTip(specimen: SpecimenEntity) {
        dao.updateSpecimen(specimen.copy(tipIndex = specimen.tipIndex + 1))
    }

    suspend fun deleteSpecimen(id: String) {
        dao.deleteSpecimenById(id)
        triggerAutoBackupIfSignedIn()
    }

    suspend fun scheduleActionForSpecimen(
        specimenId: String,
        title: String,
        days: Int,
        note: String = ""
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        val safeDays = days.coerceIn(0, 3650)
        val dueDay = LocalDate.now().plusDays(safeDays.toLong()).toEpochDay()
        val desc = note.trim().ifBlank {
            if (safeDays == 0) "Scheduled for today" else "Scheduled +$safeDays days from today"
        }
        dao.upsertAction(
            ScheduledActionEntity(
                id = "act-${UUID.randomUUID().toString().take(8)}",
                specimenId = specimenId,
                title = cleanTitle,
                dueDateEpochDay = dueDay,
                ruleDescription = desc,
                intervalDays = safeDays.coerceAtLeast(1)
            )
        )
        triggerAutoBackupIfSignedIn()
    }

    suspend fun deleteScheduledAction(actionId: String) {
        dao.deleteAction(actionId)
        triggerAutoBackupIfSignedIn()
    }

    suspend fun completeActionAndLogTimeline(
        specimenId: String,
        action: ScheduledActionEntity,
        notes: String,
        beforeVideoPath: String?,
        afterVideoPath: String?,
        followUpTitle: String?,
        followUpDays: Int?
    ) {
        dao.deleteAction(action.id)

        val entry = TimelineEntryEntity(
            id = "tl-${UUID.randomUUID().toString().take(8)}",
            specimenId = specimenId,
            dateIso = DueDateHelper.todayIso(),
            taskTitle = action.title,
            notes = notes.trim().ifBlank { "Task completed and verified on bench." },
            beforeLocalVideoPath = beforeVideoPath,
            afterLocalVideoPath = afterVideoPath,
            beforeUploadStatus = if (!beforeVideoPath.isNullOrBlank()) UploadStatus.PENDING else UploadStatus.NONE,
            afterUploadStatus = if (!afterVideoPath.isNullOrBlank()) UploadStatus.PENDING else UploadStatus.NONE,
            createdAtEpochMs = System.currentTimeMillis()
        )
        dao.upsertTimelineEntry(entry)

        val cleanFollowUp = followUpTitle?.trim().orEmpty()
        if (cleanFollowUp.isNotBlank() && followUpDays != null && followUpDays >= 0) {
            val safeDays = followUpDays.coerceIn(0, 3650)
            val nextDue = LocalDate.now().plusDays(safeDays.toLong()).toEpochDay()
            dao.upsertAction(
                ScheduledActionEntity(
                    id = "act-${UUID.randomUUID().toString().take(8)}",
                    specimenId = specimenId,
                    title = cleanFollowUp,
                    dueDateEpochDay = nextDue,
                    ruleDescription = if (safeDays == 0) "Follow-up due today" else "Follow-up +$safeDays days post-task",
                    intervalDays = safeDays.coerceAtLeast(1)
                )
            )
        }

        if (!beforeVideoPath.isNullOrBlank() || !afterVideoPath.isNullOrBlank()) {
            SyncScheduler.scheduleYouTubeUploads(context)
        }
        triggerAutoBackupIfSignedIn()
    }

    // Global Care Actions Library CRUD (Admin)
    suspend fun upsertCareAction(
        existingId: String?,
        title: String,
        category: String,
        defaultDays: Int,
        description: String
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        val action = CareActionEntity(
            id = existingId ?: "ca-${UUID.randomUUID().toString().take(8)}",
            title = cleanTitle,
            category = category.trim().ifBlank { "Custom" },
            defaultDays = defaultDays.coerceIn(1, 3650),
            description = description.trim()
        )
        dao.upsertCareAction(action)
        triggerAutoBackupIfSignedIn()
    }

    suspend fun deleteCareAction(actionId: String) {
        dao.deleteCareAction(actionId)
        triggerAutoBackupIfSignedIn()
    }

    suspend fun restoreMissingDefaultCareActions(): Int {
        val existingIds = dao.getAllCareActions().map { it.id }.toSet()
        val missing = DefaultBonsaiActions.all.filter { it.id !in existingIds }
        if (missing.isNotEmpty()) {
            dao.insertCareActionsIfMissing(missing)
            triggerAutoBackupIfSignedIn()
        }
        return missing.size
    }

    /**
     * Toggles local publish flag and synchronizes with potr.in WooCommerce if API keys are configured.
     */
    suspend fun togglePublishAndSyncWoo(item: SpecimenWithDetails): Result<String> = withContext(Dispatchers.IO) {
        val nextPublished = !item.specimen.published
        var updatedSpecimen = item.specimen.copy(
            published = nextPublished,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        dao.updateSpecimen(updatedSpecimen)
        triggerAutoBackupIfSignedIn()

        if (!prefs.hasWooKeys) {
            val msg = if (nextPublished) {
                "${updatedSpecimen.code} published to Showcase (add potr.in keys in Cloud settings to sync storefront)"
            } else {
                "${updatedSpecimen.code} moved to Private"
            }
            return@withContext Result.success(msg)
        }

        val config = WooCommercePublisher.WooConfig(
            storeUrl = prefs.wooStoreUrl,
            consumerKey = prefs.wooConsumerKey,
            consumerSecret = prefs.wooConsumerSecret,
            wpUser = prefs.wooWpUser,
            wpAppPassword = prefs.wooWpAppPassword
        )

        runCatching {
            if (nextPublished) {
                val res = wooPublisher.publishOrUpdateSpecimen(config, item.copy(specimen = updatedSpecimen))
                updatedSpecimen = updatedSpecimen.copy(
                    wooProductId = res.productId,
                    wooPermalink = res.permalink,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.updateSpecimen(updatedSpecimen)
                triggerAutoBackupIfSignedIn()
                "Live on potr.in: ${res.permalink}"
            } else {
                val productId = updatedSpecimen.wooProductId
                if (productId != null) {
                    wooPublisher.unpublishSpecimen(config, productId)
                }
                "${updatedSpecimen.code} set to Draft on potr.in"
            }
        }
    }

    fun triggerAutoBackupIfSignedIn() {
        if (!prefs.googleEmail.isNullOrBlank()) {
            SyncScheduler.scheduleDriveAutoBackup(context)
        }
    }

    /**
     * Ensures Drive folders exist, uploads any unsynced local compressed photos to Drive/Photos,
     * and writes/patches `potr_bonsai_backup.json` in `Bonsai Catalogue/`.
     */
    suspend fun performDriveBackupAndPhotoSync(allowInteractive: Boolean = false): CloudSyncOutcome = withContext(Dispatchers.IO) {
        when (val auth = GoogleAuthTokenProvider.fetchAccessToken(context, prefs.googleEmail, allowInteractive)) {
            is AuthTokenResult.NotSignedIn -> return@withContext CloudSyncOutcome.NotSignedIn
            is AuthTokenResult.NeedsResolution -> {
                if (!allowInteractive) {
                    prefs.lastBackupError = "Google authorization expired — open Cloud settings and tap Sign In."
                }
                return@withContext CloudSyncOutcome.NeedsResolution(auth.pendingIntent)
            }
            is AuthTokenResult.Error -> {
                prefs.lastBackupError = auth.message
                return@withContext CloudSyncOutcome.Error(auth.message, auth.isConfigError)
            }
            is AuthTokenResult.Success -> {
                try {
                    prefs.googleEmail = auth.email
                    val rootFolderId = prefs.driveFolderId?.takeIf { it.isNotBlank() }
                        ?: workspaceService.ensureDriveFolder(auth.token, GoogleWorkspaceService.DEFAULT_ROOT_FOLDER)
                            .also { prefs.driveFolderId = it }

                    val photosFolderId = prefs.drivePhotosFolderId?.takeIf { it.isNotBlank() }
                        ?: workspaceService.ensureDriveFolder(
                            auth.token,
                            GoogleWorkspaceService.PHOTOS_SUBFOLDER,
                            parentFolderId = rootFolderId
                        ).also { prefs.drivePhotosFolderId = it }

                    // 1. Upload any local compressed photos that aren't on Drive yet
                    var uploadedPhotos = 0
                    val unsyncedPhotos = dao.getSpecimensNeedingDrivePhotoUpload()
                    for (specimen in unsyncedPhotos) {
                        val file = File(specimen.localImagePath)
                        if (file.exists() && file.length() > 0L) {
                            val result = workspaceService.uploadCompressedJpegToDrive(
                                accessToken = auth.token,
                                folderId = photosFolderId,
                                fileName = "${specimen.code}_${file.name}",
                                jpegBytes = file.readBytes()
                            )
                            dao.updateSpecimen(
                                specimen.copy(
                                    drivePhotoFileId = result.fileId,
                                    drivePhotoUrl = result.webViewLink
                                )
                            )
                            uploadedPhotos++
                        }
                    }

                    // 2. Serialize entire Room DB to versioned JSON snapshot and PATCH in place on Drive
                    val payload = BackupPayload(
                        schemaVersion = 2,
                        appVersion = BuildConfig.VERSION_NAME,
                        exportedAtEpochMs = System.currentTimeMillis(),
                        specimens = dao.getAllSpecimens(),
                        careActions = dao.getAllCareActions(),
                        scheduledActions = dao.getAllScheduledActions(),
                        timelineEntries = dao.getAllTimelineEntries()
                    )
                    val jsonText = json.encodeToString(payload)
                    val backupFileId = workspaceService.uploadOrUpdateBackupJson(
                        accessToken = auth.token,
                        folderId = rootFolderId,
                        existingFileId = prefs.driveBackupFileId,
                        backupJson = jsonText
                    )
                    prefs.driveBackupFileId = backupFileId
                    prefs.lastBackupEpochMs = payload.exportedAtEpochMs
                    prefs.lastBackupError = null

                    val photoNote = if (uploadedPhotos > 0) " & $uploadedPhotos photo(s)" else ""
                    CloudSyncOutcome.Success("Backed up ${payload.specimens.size} specimens$photoNote to Google Drive ✓")
                } catch (e: Exception) {
                    val msg = e.message ?: "Drive backup failed"
                    prefs.lastBackupError = msg
                    CloudSyncOutcome.Error(msg)
                }
            }
        }
    }

    /**
     * Downloads `potr_bonsai_backup.json` from Google Drive, atomically restores all Room tables,
     * and re-downloads specimen photos from Drive if missing on this device.
     */
    suspend fun restoreFromGoogleDrive(): CloudSyncOutcome = withContext(Dispatchers.IO) {
        when (val auth = GoogleAuthTokenProvider.fetchAccessToken(context, prefs.googleEmail, allowInteractiveResolution = true)) {
            is AuthTokenResult.NotSignedIn -> return@withContext CloudSyncOutcome.NotSignedIn
            is AuthTokenResult.NeedsResolution -> return@withContext CloudSyncOutcome.NeedsResolution(auth.pendingIntent)
            is AuthTokenResult.Error -> return@withContext CloudSyncOutcome.Error(auth.message, auth.isConfigError)
            is AuthTokenResult.Success -> {
                try {
                    val rootFolderId = prefs.driveFolderId?.takeIf { it.isNotBlank() }
                        ?: workspaceService.ensureDriveFolder(auth.token, GoogleWorkspaceService.DEFAULT_ROOT_FOLDER)
                            .also { prefs.driveFolderId = it }

                    val downloaded = workspaceService.downloadBackupJson(auth.token, rootFolderId)
                        ?: return@withContext CloudSyncOutcome.Error("No backup file (potr_bonsai_backup.json) found in Google Drive / Bonsai Catalogue")

                    val (fileId, rawJson) = downloaded
                    val payload = json.decodeFromString<BackupPayload>(rawJson)

                    // Re-download any Drive-backed photos that don't exist locally on this device
                    val compressedDir = File(context.filesDir, "compressed").apply { mkdirs() }
                    var restoredPhotos = 0
                    val restoredSpecimens = payload.specimens.map { s ->
                        val localExists = !s.localImagePath.startsWith("asset:") &&
                            s.localImagePath.isNotBlank() &&
                            File(s.localImagePath).exists()
                        if (!localExists && !s.drivePhotoFileId.isNullOrBlank()) {
                            val targetFile = File(compressedDir, "${s.code}_${s.drivePhotoFileId}.jpg")
                            try {
                                workspaceService.downloadDriveFile(auth.token, s.drivePhotoFileId, targetFile)
                                restoredPhotos++
                                s.copy(localImagePath = targetFile.absolutePath)
                            } catch (_: Exception) {
                                s.copy(localImagePath = AssetPhotoKeys.defaultForSpecies(s.species))
                            }
                        } else if (!localExists && !s.localImagePath.startsWith("asset:")) {
                            s.copy(localImagePath = AssetPhotoKeys.defaultForSpecies(s.species))
                        } else {
                            s
                        }
                    }

                    // If restoring an older v1 backup that had no global careActions list, seed defaults
                    val actionsToRestore = payload.careActions.ifEmpty { DefaultBonsaiActions.all }

                    dao.replaceAllFromBackup(
                        specimens = restoredSpecimens,
                        careActions = actionsToRestore,
                        actions = payload.scheduledActions,
                        timelineEntries = payload.timelineEntries
                    )
                    prefs.driveBackupFileId = fileId
                    prefs.lastBackupEpochMs = payload.exportedAtEpochMs

                    CloudSyncOutcome.Success(
                        "Restored ${restoredSpecimens.size} specimens ($restoredPhotos photos downloaded) from Drive ✓"
                    )
                } catch (e: Exception) {
                    CloudSyncOutcome.Error("Restore failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Streams any queued/failed Before & After timeline videos to YouTube via resumable upload,
     * saves the returned `youtu.be` links into Room, cleans up local temp files, and refreshes Drive backup.
     */
    suspend fun processPendingYouTubeUploads(allowInteractive: Boolean = false): CloudSyncOutcome = withContext(Dispatchers.IO) {
        val pending = dao.getPendingVideoEntries()
        if (pending.isEmpty()) {
            return@withContext CloudSyncOutcome.Success("No pending videos to upload")
        }

        when (
            val auth = GoogleAuthTokenProvider.fetchAccessToken(
                context = context,
                existingEmail = prefs.googleEmail,
                allowInteractiveResolution = allowInteractive,
                scopes = GoogleAuthTokenProvider.YOUTUBE_SCOPES
            )
        ) {
            is AuthTokenResult.NotSignedIn -> return@withContext CloudSyncOutcome.NotSignedIn
            is AuthTokenResult.NeedsResolution -> return@withContext CloudSyncOutcome.NeedsResolution(auth.pendingIntent)
            is AuthTokenResult.Error -> return@withContext CloudSyncOutcome.Error(auth.message, auth.isConfigError)
            is AuthTokenResult.Success -> {
                var uploadedCount = 0
                var failedCount = 0
                var lastErr: String? = null
                val privacy = prefs.youtubePrivacyStatus

                for (entry in pending) {
                    val specimen = dao.getSpecimenById(entry.specimenId)
                    val code = specimen?.code ?: "BNS"
                    val treeName = specimen?.name ?: "Bonsai Specimen"
                    val species = specimen?.species.orEmpty()
                    var current = entry

                    // Upload Before video if queued
                    if ((current.beforeUploadStatus == UploadStatus.PENDING || current.beforeUploadStatus == UploadStatus.FAILED) &&
                        !current.beforeLocalVideoPath.isNullOrBlank()
                    ) {
                        val file = File(current.beforeLocalVideoPath!!)
                        if (file.exists()) {
                            current = current.copy(beforeUploadStatus = UploadStatus.UPLOADING, uploadError = null)
                            dao.updateTimelineEntry(current)
                            try {
                                val res = youtubeUploader.uploadVideoResumable(
                                    accessToken = auth.token,
                                    videoFile = file,
                                    title = "[$code] $treeName — ${current.taskTitle} (Before)",
                                    description = "Specimen: $treeName ($code)\nSpecies: $species\nDate: ${current.dateIso}\nNotes: ${current.notes}\n\nLogged via POTR Bonsai Studio.",
                                    privacyStatus = privacy
                                )
                                file.delete()
                                current = current.copy(
                                    beforeLocalVideoPath = null,
                                    beforeYoutubeVideoId = res.videoId,
                                    beforeYoutubeUrl = res.watchUrl,
                                    beforeUploadStatus = UploadStatus.UPLOADED,
                                    uploadError = null
                                )
                                dao.updateTimelineEntry(current)
                                uploadedCount++
                            } catch (e: Exception) {
                                failedCount++
                                lastErr = e.message
                                current = current.copy(
                                    beforeUploadStatus = UploadStatus.FAILED,
                                    uploadError = e.message
                                )
                                dao.updateTimelineEntry(current)
                            }
                        } else {
                            current = current.copy(beforeUploadStatus = UploadStatus.NONE, beforeLocalVideoPath = null)
                            dao.updateTimelineEntry(current)
                        }
                    }

                    // Upload After video if queued
                    if ((current.afterUploadStatus == UploadStatus.PENDING || current.afterUploadStatus == UploadStatus.FAILED) &&
                        !current.afterLocalVideoPath.isNullOrBlank()
                    ) {
                        val file = File(current.afterLocalVideoPath!!)
                        if (file.exists()) {
                            current = current.copy(afterUploadStatus = UploadStatus.UPLOADING, uploadError = null)
                            dao.updateTimelineEntry(current)
                            try {
                                val res = youtubeUploader.uploadVideoResumable(
                                    accessToken = auth.token,
                                    videoFile = file,
                                    title = "[$code] $treeName — ${current.taskTitle} (After)",
                                    description = "Specimen: $treeName ($code)\nSpecies: $species\nDate: ${current.dateIso}\nNotes: ${current.notes}\n\nLogged via POTR Bonsai Studio.",
                                    privacyStatus = privacy
                                )
                                file.delete()
                                current = current.copy(
                                    afterLocalVideoPath = null,
                                    afterYoutubeVideoId = res.videoId,
                                    afterYoutubeUrl = res.watchUrl,
                                    afterUploadStatus = UploadStatus.UPLOADED,
                                    uploadError = null
                                )
                                dao.updateTimelineEntry(current)
                                uploadedCount++
                            } catch (e: Exception) {
                                failedCount++
                                lastErr = e.message
                                current = current.copy(
                                    afterUploadStatus = UploadStatus.FAILED,
                                    uploadError = e.message
                                )
                                dao.updateTimelineEntry(current)
                            }
                        } else {
                            current = current.copy(afterUploadStatus = UploadStatus.NONE, afterLocalVideoPath = null)
                            dao.updateTimelineEntry(current)
                        }
                    }
                }

                if (uploadedCount > 0) {
                    triggerAutoBackupIfSignedIn()
                }

                if (failedCount > 0) {
                    CloudSyncOutcome.Error("Uploaded $uploadedCount video(s); $failedCount failed: ${lastErr.orEmpty()}")
                } else {
                    CloudSyncOutcome.Success("Uploaded $uploadedCount video(s) to YouTube ✓")
                }
            }
        }
    }

    /**
     * Exports the current catalog to the Master Google Sheet.
     */
    suspend fun exportToGoogleSheet(): CloudSyncOutcome = withContext(Dispatchers.IO) {
        when (
            val auth = GoogleAuthTokenProvider.fetchAccessToken(
                context = context,
                existingEmail = prefs.googleEmail,
                allowInteractiveResolution = true,
                scopes = GoogleAuthTokenProvider.SHEETS_SCOPES
            )
        ) {
            is AuthTokenResult.NotSignedIn -> return@withContext CloudSyncOutcome.NotSignedIn
            is AuthTokenResult.NeedsResolution -> return@withContext CloudSyncOutcome.NeedsResolution(auth.pendingIntent)
            is AuthTokenResult.Error -> return@withContext CloudSyncOutcome.Error(auth.message, auth.isConfigError)
            is AuthTokenResult.Success -> {
                try {
                    val specimens = specimensFlow.first()
                    val sheetId = workspaceService.syncSpecimensToSheet(
                        accessToken = auth.token,
                        existingSheetId = prefs.masterSheetId,
                        specimens = specimens
                    )
                    prefs.masterSheetId = sheetId
                    CloudSyncOutcome.Success("Exported ${specimens.size} specimens to Google Sheet ✓")
                } catch (e: Exception) {
                    CloudSyncOutcome.Error("Google Sheet export failed: ${e.message}")
                }
            }
        }
    }
}
