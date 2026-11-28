package `in`.potr.bonsai.ui

import android.app.PendingIntent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.potr.bonsai.BuildConfig
import `in`.potr.bonsai.cloud.UpdateChecker
import `in`.potr.bonsai.data.model.AlertItem
import `in`.potr.bonsai.data.model.CareActionEntity
import `in`.potr.bonsai.data.model.CodeGenerator
import `in`.potr.bonsai.data.model.DefaultSpeciesSuggestions
import `in`.potr.bonsai.data.model.DueDateHelper
import `in`.potr.bonsai.data.model.ScheduledActionEntity
import `in`.potr.bonsai.data.model.SpecimenEntity
import `in`.potr.bonsai.data.model.SpecimenWithDetails
import `in`.potr.bonsai.data.repository.BonsaiRepository
import `in`.potr.bonsai.data.repository.CloudSyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class MainTab { CATALOG, ALERTS, SHOWCASE }
enum class CatalogFilter { ALL, ALERTS_DUE, APPROX_AGE, PUBLISHED }
enum class AlertFilter { ALL, DUE, UPCOMING }
enum class CatalogViewMode { GRID, TABLE }

data class CloudSettingsState(
    val googleEmail: String? = null,
    val driveFolderId: String? = null,
    val driveBackupFileId: String? = null,
    val masterSheetId: String? = null,
    val lastBackupEpochMs: Long = 0L,
    val lastBackupError: String? = null,
    val youtubePrivacy: String = "unlisted",
    val wooStoreUrl: String = "https://potr.in",
    val wooWpUser: String = "",
    val hasWooKeys: Boolean = false,
    val hasWpAppPassword: Boolean = false,
    val isSyncing: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadPercent: Int = 0,
    val authStatusMessage: String? = null,
    val authErrorMessage: String? = null,
    val showOAuthSetupHint: Boolean = false,
    val updateStatusMessage: String = "Installed: v${BuildConfig.VERSION_NAME}",
    val availableUpdateUrl: String? = null,
    val availableUpdateVersion: String? = null,
    val releasePageUrl: String = "https://github.com/iamrajeshrajan/Potr-Bonsai-app/releases/latest"
)

data class MainUiState(
    val specimens: List<SpecimenWithDetails> = emptyList(),
    val filteredSpecimens: List<SpecimenWithDetails> = emptyList(),
    val careActions: List<CareActionEntity> = emptyList(),
    val alerts: List<AlertItem> = emptyList(),
    val activeTab: MainTab = MainTab.CATALOG,
    val showAdminActionsScreen: Boolean = false,
    val searchQuery: String = "",
    val catalogFilter: CatalogFilter = CatalogFilter.ALL,
    val alertFilter: AlertFilter = AlertFilter.ALL,
    val viewMode: CatalogViewMode = CatalogViewMode.GRID,
    val selectedSpecimenId: String? = null,
    val totalCount: Int = 0,
    val dueCount: Int = 0,
    val upcomingCount: Int = 0,
    val publishedCount: Int = 0,
    val pendingVideoUploads: Int = 0,
    val cloud: CloudSettingsState = CloudSettingsState()
) {
    val selectedSpecimen: SpecimenWithDetails?
        get() = selectedSpecimenId?.let { id -> specimens.firstOrNull { it.specimen.id == id } }

    val publishedSpecimens: List<SpecimenWithDetails>
        get() = specimens.filter { it.specimen.published }

    val nextSuggestedCode: String
        get() = CodeGenerator.nextSpecimenCode(specimens.map { it.specimen.code })

    val speciesSuggestions: List<String>
        get() = (specimens.map { it.specimen.species.trim() }.filter { it.isNotBlank() } + DefaultSpeciesSuggestions.common)
            .distinctBy { it.lowercase() }
            .sorted()
}

sealed interface UiEvent {
    data class ShowMessage(val text: String) : UiEvent
    data class LaunchAuthResolution(val pendingIntent: PendingIntent) : UiEvent
    data class InstallApk(val apkUrl: String) : UiEvent
}

private data class FilterNavState(
    val activeTab: MainTab = MainTab.CATALOG,
    val showAdminActionsScreen: Boolean = false,
    val searchQuery: String = "",
    val catalogFilter: CatalogFilter = CatalogFilter.ALL,
    val alertFilter: AlertFilter = AlertFilter.ALL,
    val viewMode: CatalogViewMode = CatalogViewMode.GRID,
    val selectedSpecimenId: String? = null
)

class MainViewModel(
    private val repository: BonsaiRepository,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val navState = MutableStateFlow(FilterNavState())
    private val cloudState = MutableStateFlow(readCloudStateFromPrefs())

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val uiState: StateFlow<MainUiState> = combine(
        repository.specimensFlow,
        repository.careActionsFlow,
        repository.pendingVideoCountFlow,
        navState,
        cloudState
    ) { specimens, careActions, pendingVideos, nav, cloud ->
        val today = LocalDate.now().toEpochDay()
        var due = 0
        var upcoming = 0
        var published = 0
        val allAlerts = mutableListOf<AlertItem>()

        specimens.forEach { item ->
            if (item.specimen.published) published++
            item.actions.forEach { act ->
                val days = DueDateHelper.daysUntilDue(act.dueDateEpochDay, today)
                if (days <= 0) due++ else upcoming++
                allAlerts.add(AlertItem(item.specimen, act, days))
            }
        }

        val query = nav.searchQuery.trim().lowercase()
        val filtered = specimens.filter { item ->
            val s = item.specimen
            val matchesQuery = query.isEmpty() ||
                s.name.lowercase().contains(query) ||
                s.code.lowercase().contains(query) ||
                s.species.lowercase().contains(query) ||
                s.subspecies.lowercase().contains(query) ||
                s.pot.lowercase().contains(query)
            if (!matchesQuery) return@filter false
            when (nav.catalogFilter) {
                CatalogFilter.ALL -> true
                CatalogFilter.ALERTS_DUE -> item.dueCount(today) > 0
                CatalogFilter.APPROX_AGE -> s.ageType == "approximate"
                CatalogFilter.PUBLISHED -> s.published
            }
        }

        val filteredAlerts = allAlerts
            .filter {
                when (nav.alertFilter) {
                    AlertFilter.ALL -> true
                    AlertFilter.DUE -> it.daysUntilDue <= 0
                    AlertFilter.UPCOMING -> it.daysUntilDue > 0
                }
            }
            .sortedBy { it.daysUntilDue }

        MainUiState(
            specimens = specimens,
            filteredSpecimens = filtered,
            careActions = careActions,
            alerts = filteredAlerts,
            activeTab = nav.activeTab,
            showAdminActionsScreen = nav.showAdminActionsScreen,
            searchQuery = nav.searchQuery,
            catalogFilter = nav.catalogFilter,
            alertFilter = nav.alertFilter,
            viewMode = nav.viewMode,
            selectedSpecimenId = nav.selectedSpecimenId,
            totalCount = specimens.size,
            dueCount = due,
            upcomingCount = upcoming,
            publishedCount = published,
            pendingVideoUploads = pendingVideos,
            cloud = cloud
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(cloud = readCloudStateFromPrefs())
    )

    private fun readCloudStateFromPrefs(): CloudSettingsState {
        val p = repository.prefs
        return CloudSettingsState(
            googleEmail = p.googleEmail,
            driveFolderId = p.driveFolderId,
            driveBackupFileId = p.driveBackupFileId,
            masterSheetId = p.masterSheetId,
            lastBackupEpochMs = p.lastBackupEpochMs,
            lastBackupError = p.lastBackupError,
            youtubePrivacy = p.youtubePrivacyStatus,
            wooStoreUrl = p.wooStoreUrl,
            wooWpUser = p.wooWpUser,
            hasWooKeys = p.hasWooKeys,
            hasWpAppPassword = p.hasWpAppPassword
        )
    }

    fun refreshCloudState() {
        val current = cloudState.value
        val fresh = readCloudStateFromPrefs()
        cloudState.value = fresh.copy(
            isSyncing = current.isSyncing,
            isCheckingUpdate = current.isCheckingUpdate,
            isDownloadingUpdate = current.isDownloadingUpdate,
            updateDownloadPercent = current.updateDownloadPercent,
            authStatusMessage = current.authStatusMessage,
            authErrorMessage = current.authErrorMessage,
            showOAuthSetupHint = current.showOAuthSetupHint,
            updateStatusMessage = current.updateStatusMessage,
            availableUpdateUrl = current.availableUpdateUrl,
            availableUpdateVersion = current.availableUpdateVersion,
            releasePageUrl = current.releasePageUrl
        )
    }

    fun selectTab(tab: MainTab) {
        navState.update { it.copy(activeTab = tab, showAdminActionsScreen = false) }
    }

    fun openAdminCareActions() {
        navState.update { it.copy(showAdminActionsScreen = true, selectedSpecimenId = null) }
    }

    fun closeAdminCareActions() {
        navState.update { it.copy(showAdminActionsScreen = false) }
    }

    fun jumpToCatalogAll() {
        navState.update {
            it.copy(
                activeTab = MainTab.CATALOG,
                catalogFilter = CatalogFilter.ALL,
                showAdminActionsScreen = false
            )
        }
    }

    fun jumpToAlerts(filter: AlertFilter) {
        navState.update {
            it.copy(
                activeTab = MainTab.ALERTS,
                alertFilter = filter,
                showAdminActionsScreen = false
            )
        }
    }

    fun updateSearchQuery(q: String) {
        navState.update { it.copy(searchQuery = q) }
    }

    fun selectCatalogFilter(filter: CatalogFilter) {
        navState.update { it.copy(catalogFilter = filter) }
    }

    fun selectAlertFilter(filter: AlertFilter) {
        navState.update { it.copy(alertFilter = filter) }
    }

    fun setViewMode(mode: CatalogViewMode) {
        navState.update { it.copy(viewMode = mode) }
    }

    fun openSpecimenDetail(id: String) {
        navState.update { it.copy(selectedSpecimenId = id) }
    }

    fun closeSpecimenDetail() {
        navState.update { it.copy(selectedSpecimenId = null) }
    }

    fun addSpecimen(
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
    ) {
        viewModelScope.launch {
            repository.addSpecimen(
                code = code,
                name = name,
                species = species,
                subspecies = subspecies,
                age = age,
                ageType = ageType,
                ageNote = ageNote,
                pot = pot,
                potMeta = potMeta,
                localImagePath = localImagePath,
                purchasePrice = purchasePrice,
                newPotPrice = newPotPrice,
                effortCost = effortCost,
                sellingPrice = sellingPrice,
                initialActionTitle = initialActionTitle,
                initialActionDays = initialActionDays
            )
            emitMessage("Cataloged $name ($code)")
        }
    }

    fun updateSpecimen(
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
        viewModelScope.launch {
            repository.updateSpecimen(
                existing = existing,
                code = code,
                name = name,
                species = species,
                subspecies = subspecies,
                age = age,
                ageType = ageType,
                ageNote = ageNote,
                pot = pot,
                potMeta = potMeta,
                repotted = repotted,
                localImagePath = localImagePath,
                purchasePrice = purchasePrice,
                newPotPrice = newPotPrice,
                effortCost = effortCost,
                sellingPrice = sellingPrice
            )
            emitMessage("Updated $name ($code)")
        }
    }

    fun replaceSpecimenPhotoById(specimenId: String, uri: Uri, tempFileToDelete: java.io.File? = null) {
        viewModelScope.launch {
            try {
                val specimen = uiState.value.specimens.firstOrNull { it.specimen.id == specimenId }?.specimen
                    ?: return@launch
                val compressed = repository.compressPhotoFromUri(uri, specimen.code)
                repository.updateSpecimenPhoto(specimen, compressed.file.absolutePath)
                val kb = (compressed.sizeBytes / 1024).coerceAtLeast(1)
                emitMessage("Photo compressed (${compressed.width}×${compressed.height}, ${kb} KB) & queued for Drive")
            } catch (e: Exception) {
                emitMessage("Photo compression failed: ${e.message}")
            } finally {
                tempFileToDelete?.delete()
            }
        }
    }

    suspend fun compressPickedPhoto(uri: Uri, code: String, tempFileToDelete: java.io.File? = null): Result<String> =
        try {
            runCatching {
                val compressed = repository.compressPhotoFromUri(uri, code)
                val kb = (compressed.sizeBytes / 1024).coerceAtLeast(1)
                emitMessage("Compressed to ${compressed.width}×${compressed.height} (${kb} KB)")
                compressed.file.absolutePath
            }
        } finally {
            tempFileToDelete?.delete()
        }

    suspend fun stagePickedVideo(uri: Uri, prefix: String): Result<String> = runCatching {
        repository.stageVideoFromUri(uri, prefix).absolutePath
    }

    fun updatePricing(
        specimen: SpecimenEntity,
        purchasePrice: Double,
        newPotPrice: Double,
        effortCost: Double,
        sellingPrice: Double
    ) {
        viewModelScope.launch {
            repository.updateSpecimenPricing(specimen, purchasePrice, newPotPrice, effortCost, sellingPrice)
            emitMessage("Saved valuation for ${specimen.code}")
        }
    }

    fun cycleTip(specimen: SpecimenEntity) {
        viewModelScope.launch {
            repository.cycleTip(specimen)
        }
    }

    fun deleteSpecimen(specimen: SpecimenEntity) {
        viewModelScope.launch {
            repository.deleteSpecimen(specimen.id)
            closeSpecimenDetail()
            emitMessage("Deleted ${specimen.name} (${specimen.code})")
        }
    }

    fun scheduleActionForSpecimen(specimenId: String, title: String, days: Int, note: String = "") {
        viewModelScope.launch {
            repository.scheduleActionForSpecimen(specimenId, title, days, note)
            val label = if (days == 0) "today" else "in +${days}d"
            emitMessage("Scheduled \"$title\" $label")
        }
    }

    fun deleteScheduledAction(actionId: String) {
        viewModelScope.launch {
            repository.deleteScheduledAction(actionId)
            emitMessage("Scheduled reminder removed")
        }
    }

    fun completeActionAndLog(
        specimenId: String,
        specimenCode: String,
        action: ScheduledActionEntity,
        notes: String,
        beforeVideoPath: String?,
        afterVideoPath: String?,
        followUpTitle: String?,
        followUpDays: Int?
    ) {
        viewModelScope.launch {
            repository.completeActionAndLogTimeline(
                specimenId = specimenId,
                action = action,
                notes = notes,
                beforeVideoPath = beforeVideoPath,
                afterVideoPath = afterVideoPath,
                followUpTitle = followUpTitle,
                followUpDays = followUpDays
            )
            val videoNote = if (!beforeVideoPath.isNullOrBlank() || !afterVideoPath.isNullOrBlank()) {
                " — YouTube upload queued"
            } else {
                ""
            }
            emitMessage("${action.title} logged to $specimenCode$videoNote")
        }
    }

    fun togglePublish(item: SpecimenWithDetails) {
        viewModelScope.launch {
            cloudState.update { it.copy(isSyncing = true) }
            val result = repository.togglePublishAndSyncWoo(item)
            cloudState.update { it.copy(isSyncing = false) }
            result.fold(
                onSuccess = { emitMessage(it) },
                onFailure = { emitMessage("potr.in sync error: ${it.message}") }
            )
        }
    }

    // Global Care Actions Admin Management
    fun saveCareAction(
        existingId: String?,
        title: String,
        category: String,
        defaultDays: Int,
        description: String
    ) {
        viewModelScope.launch {
            repository.upsertCareAction(existingId, title, category, defaultDays, description)
            emitMessage(if (existingId == null) "Added \"$title\" to global actions" else "Updated \"$title\"")
        }
    }

    fun deleteCareAction(action: CareActionEntity) {
        viewModelScope.launch {
            repository.deleteCareAction(action.id)
            emitMessage("Removed \"${action.title}\" from library")
        }
    }

    fun restoreDefaultCareActions() {
        viewModelScope.launch {
            val restored = repository.restoreMissingDefaultCareActions()
            if (restored > 0) {
                emitMessage("Restored $restored default bonsai action(s)")
            } else {
                emitMessage("All default bonsai actions are already present")
            }
        }
    }

    fun onGoogleSignInStarted() {
        cloudState.update {
            it.copy(
                isSyncing = true,
                authStatusMessage = "Connecting to Google…",
                authErrorMessage = null
            )
        }
    }

    fun onGoogleSignedIn(email: String) {
        repository.prefs.googleEmail = email
        repository.prefs.lastBackupError = null
        cloudState.update {
            it.copy(
                googleEmail = email,
                authStatusMessage = "Signed in as $email — syncing Drive backup…",
                authErrorMessage = null,
                showOAuthSetupHint = false
            )
        }
        refreshCloudState()
        emitMessage("Signed in as $email — syncing Drive backup…")
        backupNowToDrive()
    }

    fun onGoogleAuthFailed(message: String, isConfigError: Boolean = false) {
        cloudState.update {
            it.copy(
                isSyncing = false,
                authStatusMessage = null,
                authErrorMessage = message,
                showOAuthSetupHint = isConfigError || it.showOAuthSetupHint
            )
        }
        emitMessage(message)
    }

    fun onGoogleBrowserSignInDismissed() {
        cloudState.update {
            it.copy(
                isSyncing = false,
                authStatusMessage = "Sign-In closed before completing — tap Sign In to try again.",
                authErrorMessage = null
            )
        }
    }

    fun onGoogleSignedOut() {
        repository.prefs.clearGoogleSession()
        cloudState.update {
            it.copy(
                isSyncing = false,
                authStatusMessage = "Signed out of Google account",
                authErrorMessage = null,
                showOAuthSetupHint = false
            )
        }
        refreshCloudState()
        emitMessage("Signed out of Google account")
    }

    fun backupNowToDrive() {
        viewModelScope.launch {
            cloudState.update { it.copy(isSyncing = true, authErrorMessage = null) }
            val outcome = repository.performDriveBackupAndPhotoSync(allowInteractive = true)
            cloudState.update { it.copy(isSyncing = false) }
            refreshCloudState()
            handleCloudOutcome(outcome)
        }
    }

    fun restoreFromDrive() {
        viewModelScope.launch {
            cloudState.update { it.copy(isSyncing = true, authErrorMessage = null) }
            val outcome = repository.restoreFromGoogleDrive()
            cloudState.update { it.copy(isSyncing = false) }
            refreshCloudState()
            handleCloudOutcome(outcome)
        }
    }

    fun uploadPendingVideosToYouTube() {
        viewModelScope.launch {
            cloudState.update { it.copy(isSyncing = true, authErrorMessage = null) }
            val outcome = repository.processPendingYouTubeUploads(allowInteractive = true)
            cloudState.update { it.copy(isSyncing = false) }
            refreshCloudState()
            handleCloudOutcome(outcome)
        }
    }

    fun exportToGoogleSheet() {
        viewModelScope.launch {
            cloudState.update { it.copy(isSyncing = true, authErrorMessage = null) }
            val outcome = repository.exportToGoogleSheet()
            cloudState.update { it.copy(isSyncing = false) }
            refreshCloudState()
            handleCloudOutcome(outcome)
        }
    }

    fun setYouTubePrivacy(privacy: String) {
        repository.prefs.youtubePrivacyStatus = privacy
        refreshCloudState()
        emitMessage("YouTube default visibility set to ${privacy.replaceFirstChar { it.uppercase() }}")
    }

    fun saveWooCommerceConfig(
        storeUrl: String,
        consumerKey: String,
        consumerSecret: String,
        wpUser: String,
        wpAppPass: String
    ) {
        val p = repository.prefs
        p.wooStoreUrl = storeUrl.trim().ifBlank { "https://potr.in" }
        p.wooWpUser = wpUser.trim()
        if (consumerKey.isNotBlank()) p.wooConsumerKey = consumerKey.trim()
        if (consumerSecret.isNotBlank()) p.wooConsumerSecret = consumerSecret.trim()
        if (wpAppPass.isNotBlank()) p.wooWpAppPassword = wpAppPass.trim()
        refreshCloudState()
        emitMessage("Saved potr.in WooCommerce credentials (encrypted)")
    }

    fun checkForAppUpdate() {
        val currentVer = BuildConfig.VERSION_NAME
        cloudState.update {
            it.copy(
                isCheckingUpdate = true,
                updateStatusMessage = "Checking GitHub Releases…"
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                updateChecker.checkLatestRelease(currentVer)
            }
            result.fold(
                onSuccess = { info ->
                    when {
                        info.updateAvailable && !info.downloadUrl.isNullOrBlank() -> {
                            cloudState.update {
                                it.copy(
                                    isCheckingUpdate = false,
                                    updateStatusMessage = "v${info.latestVersion} available (installed: v${info.currentVersion})",
                                    availableUpdateUrl = info.downloadUrl,
                                    availableUpdateVersion = info.latestVersion,
                                    releasePageUrl = info.releasePageUrl
                                )
                            }
                        }
                        info.updateAvailable -> {
                            cloudState.update {
                                it.copy(
                                    isCheckingUpdate = false,
                                    updateStatusMessage = "v${info.latestVersion} found, but APK asset is not attached yet — retry shortly or open Releases",
                                    availableUpdateUrl = null,
                                    availableUpdateVersion = info.latestVersion,
                                    releasePageUrl = info.releasePageUrl
                                )
                            }
                        }
                        else -> {
                            cloudState.update {
                                it.copy(
                                    isCheckingUpdate = false,
                                    updateStatusMessage = "Up to date (v${info.currentVersion})",
                                    availableUpdateUrl = null,
                                    availableUpdateVersion = null,
                                    releasePageUrl = info.releasePageUrl
                                )
                            }
                        }
                    }
                },
                onFailure = { e ->
                    cloudState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateStatusMessage = "Update check failed: ${e.message}",
                            releasePageUrl = updateChecker.releasesPageUrl()
                        )
                    }
                }
            )
        }
    }

    fun setUpdateDownloadProgress(downloading: Boolean, percent: Int, statusMessage: String? = null) {
        cloudState.update {
            it.copy(
                isDownloadingUpdate = downloading,
                updateDownloadPercent = percent.coerceIn(0, 100),
                updateStatusMessage = statusMessage ?: it.updateStatusMessage
            )
        }
    }

    fun requestDownloadUpdate() {
        if (cloudState.value.isDownloadingUpdate) return
        val url = cloudState.value.availableUpdateUrl ?: return
        viewModelScope.launch {
            _events.emit(UiEvent.InstallApk(url))
        }
    }

    private fun handleCloudOutcome(outcome: CloudSyncOutcome) {
        when (outcome) {
            is CloudSyncOutcome.Success -> {
                cloudState.update {
                    it.copy(
                        authStatusMessage = outcome.message,
                        authErrorMessage = null,
                        showOAuthSetupHint = false
                    )
                }
                emitMessage(outcome.message)
            }
            is CloudSyncOutcome.NotSignedIn -> {
                val msg = "Tap Sign In above to connect your Google Drive & YouTube account."
                cloudState.update { it.copy(authErrorMessage = msg, authStatusMessage = null) }
                emitMessage(msg)
            }
            is CloudSyncOutcome.NeedsResolution -> viewModelScope.launch {
                _events.emit(UiEvent.LaunchAuthResolution(outcome.pendingIntent))
            }
            is CloudSyncOutcome.Error -> {
                cloudState.update {
                    it.copy(
                        authStatusMessage = null,
                        authErrorMessage = outcome.message,
                        showOAuthSetupHint = outcome.isConfigError || it.showOAuthSetupHint
                    )
                }
                emitMessage(outcome.message)
            }
        }
    }

    fun emitMessage(text: String) {
        viewModelScope.launch {
            _events.emit(UiEvent.ShowMessage(text))
        }
    }

    class Factory(
        private val repository: BonsaiRepository,
        private val updateChecker: UpdateChecker
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, updateChecker) as T
        }
    }
}
