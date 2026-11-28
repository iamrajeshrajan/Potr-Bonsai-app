package `in`.potr.bonsai

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import `in`.potr.bonsai.cloud.AuthTokenResult
import `in`.potr.bonsai.cloud.GoogleAuthTokenProvider
import `in`.potr.bonsai.data.model.ScheduledActionEntity
import `in`.potr.bonsai.data.model.SpecimenEntity
import `in`.potr.bonsai.ui.AlertFilter
import `in`.potr.bonsai.ui.MainTab
import `in`.potr.bonsai.ui.MainViewModel
import `in`.potr.bonsai.ui.UiEvent
import `in`.potr.bonsai.ui.components.PotrNavigationDrawerSheet
import `in`.potr.bonsai.ui.screens.ActionLogDialog
import `in`.potr.bonsai.ui.screens.AddEditSpecimenDialog
import `in`.potr.bonsai.ui.screens.AdminCareActionsScreen
import `in`.potr.bonsai.ui.screens.AlertsScreen
import `in`.potr.bonsai.ui.screens.CatalogScreen
import `in`.potr.bonsai.ui.screens.CloudSettingsDialog
import `in`.potr.bonsai.ui.screens.ShowcaseScreen
import `in`.potr.bonsai.ui.screens.SpecimenDetailScreen
import `in`.potr.bonsai.ui.theme.PotrBonsaiTheme
import `in`.potr.bonsai.util.PhotoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private val appInstance: PotrBonsaiApp
        get() = application as PotrBonsaiApp

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(appInstance.repository, appInstance.updateChecker)
    }

    // Active media pick targets (persisted across Activity recreation when Camera app opens)
    private var pendingPhotoTargetCode: String = "BNS-NEW"
    private var pendingPhotoReplaceSpecimenId: String? = null
    private var pendingCameraTempPath: String? = null
    private var stagedAddEditPhotoPath = mutableStateOf<String?>(null)

    private var pendingVideoSlot: String = "before" // "before" | "after"
    private var stagedBeforeVideoPath = mutableStateOf<String?>(null)
    private var stagedAfterVideoPath = mutableStateOf<String?>(null)
    private var pendingInstallApkUrl: String? = null
    private var awaitingBrowserOAuth: Boolean = false

    private val unknownAppSourcesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pendingUrl = pendingInstallApkUrl
        pendingInstallApkUrl = null
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()
        if (allowed && !pendingUrl.isNullOrBlank()) {
            downloadAndInstallUpdate(pendingUrl)
        } else if (!allowed) {
            viewModel.emitMessage("Enable 'Allow from this source' for POTR Bonsai to install updates")
        }
    }

    // Zero-permission Android Photo Picker for Images
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handlePickedPhotoUri(uri, tempFileToDelete = null)
        }
    }

    // Native Camera capture via FileProvider
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val tempPath = pendingCameraTempPath
        pendingCameraTempPath = null
        val tempFile = tempPath?.let { File(it) }
        if (success && tempFile != null && tempFile.exists() && tempFile.length() > 0L) {
            val uri = PhotoCompressor.uriForFile(this, tempFile)
            handlePickedPhotoUri(uri, tempFileToDelete = tempFile)
        } else {
            tempFile?.delete()
            if (success) {
                viewModel.emitMessage("Camera photo was empty — please try again")
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraInternal()
        } else {
            viewModel.emitMessage("Camera permission denied — you can still pick from Gallery")
        }
    }

    // Zero-permission Android Photo Picker for Before/After Videos
    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val slot = pendingVideoSlot
            lifecycleScope.launch {
                val res = viewModel.stagePickedVideo(uri, "${pendingPhotoTargetCode}_$slot")
                res.fold(
                    onSuccess = { path ->
                        if (slot == "before") {
                            stagedBeforeVideoPath.value = path
                        } else {
                            stagedAfterVideoPath.value = path
                        }
                        viewModel.emitMessage("${slot.replaceFirstChar { it.uppercase() }} video staged for YouTube upload ✓")
                    },
                    onFailure = { e ->
                        viewModel.emitMessage("Could not load video: ${e.message}")
                    }
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            pendingPhotoTargetCode = savedInstanceState.getString(STATE_PHOTO_CODE, "BNS-NEW")
            pendingPhotoReplaceSpecimenId = savedInstanceState.getString(STATE_REPLACE_ID)
            pendingCameraTempPath = savedInstanceState.getString(STATE_CAMERA_TEMP_PATH)
            stagedAddEditPhotoPath.value = savedInstanceState.getString(STATE_STAGED_PHOTO_PATH)
            pendingInstallApkUrl = savedInstanceState.getString(STATE_PENDING_INSTALL_URL)
            awaitingBrowserOAuth = savedInstanceState.getBoolean(STATE_AWAITING_OAUTH, false)
        }
        enableEdgeToEdge()

        setContent {
            PotrBonsaiTheme {
                PotrBonsaiRoot()
            }
        }

        handleOAuthIntentIfPresent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntentIfPresent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (awaitingBrowserOAuth) {
            awaitingBrowserOAuth = false
            viewModel.onGoogleBrowserSignInDismissed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHOTO_CODE, pendingPhotoTargetCode)
        outState.putString(STATE_REPLACE_ID, pendingPhotoReplaceSpecimenId)
        outState.putString(STATE_CAMERA_TEMP_PATH, pendingCameraTempPath)
        outState.putString(STATE_STAGED_PHOTO_PATH, stagedAddEditPhotoPath.value)
        outState.putString(STATE_PENDING_INSTALL_URL, pendingInstallApkUrl)
        outState.putBoolean(STATE_AWAITING_OAUTH, awaitingBrowserOAuth)
    }

    @Composable
    private fun PotrBonsaiRoot() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        var showCloudDialog by rememberSaveable { mutableStateOf(false) }
        var showAddEditDialog by rememberSaveable { mutableStateOf(false) }
        var editingSpecimenId by rememberSaveable { mutableStateOf<String?>(null) }
        var activeActionModal by remember { mutableStateOf<Pair<SpecimenEntity, ScheduledActionEntity>?>(null) }

        val editingSpecimen = remember(editingSpecimenId, state.specimens) {
            editingSpecimenId?.let { id -> state.specimens.firstOrNull { it.specimen.id == id }?.specimen }
        }

        val stagedPhoto by stagedAddEditPhotoPath
        val beforeVideo by stagedBeforeVideoPath
        val afterVideo by stagedAfterVideoPath

        BackHandler(enabled = drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }

        LaunchedEffect(Unit) {
            viewModel.checkForAppUpdate()
        }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
                    is UiEvent.LaunchAuthResolution -> startGoogleSignIn()
                    is UiEvent.InstallApk -> downloadAndInstallUpdate(event.apkUrl)
                }
            }
        }

        val selectedDetail = state.selectedSpecimen
        val allowDrawerGestures = selectedDetail == null && !state.showAdminActionsScreen
        val hasAppUpdate = !state.cloud.availableUpdateUrl.isNullOrBlank()

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = allowDrawerGestures,
            drawerContent = {
                PotrNavigationDrawerSheet(
                    state = state,
                    onSelectTab = { tab ->
                        scope.launch { drawerState.close() }
                        viewModel.selectTab(tab)
                    },
                    onOpenAdminCareActions = {
                        scope.launch { drawerState.close() }
                        viewModel.openAdminCareActions()
                    },
                    onOpenCloudSettings = {
                        scope.launch { drawerState.close() }
                        viewModel.refreshCloudState()
                        showCloudDialog = true
                    },
                    onCheckForUpdate = { viewModel.checkForAppUpdate() },
                    onDownloadAndInstallUpdate = {
                        scope.launch { drawerState.close() }
                        viewModel.requestDownloadUpdate()
                    },
                    onOpenReleasesPage = {
                        scope.launch { drawerState.close() }
                        openExternalUrl(state.cloud.releasePageUrl)
                    }
                )
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    selectedDetail != null -> {
                        SpecimenDetailScreen(
                            item = selectedDetail,
                            careActions = state.careActions,
                            onBack = { viewModel.closeSpecimenDetail() },
                            onEditSpecimen = {
                                editingSpecimenId = selectedDetail.specimen.id
                                stagedAddEditPhotoPath.value = null
                                showAddEditDialog = true
                            },
                            onTogglePublish = { viewModel.togglePublish(selectedDetail) },
                            onDeleteSpecimen = { viewModel.deleteSpecimen(selectedDetail.specimen) },
                            onCapturePhoto = {
                                pendingPhotoReplaceSpecimenId = selectedDetail.specimen.id
                                pendingPhotoTargetCode = selectedDetail.specimen.code
                                requestCameraCapture()
                            },
                            onPickPhoto = {
                                pendingPhotoReplaceSpecimenId = selectedDetail.specimen.id
                                pendingPhotoTargetCode = selectedDetail.specimen.code
                                imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onSavePricing = { p, pot, eff, sell ->
                                viewModel.updatePricing(selectedDetail.specimen, p, pot, eff, sell)
                            },
                            onCycleTip = { viewModel.cycleTip(selectedDetail.specimen) },
                            onOpenUrl = { url -> openExternalUrl(url) },
                            onCopyTag = { code -> copyToClipboard("POTR Tag", code, "Bench Tag $code copied") },
                            onScheduleAction = { title, days, note ->
                                viewModel.scheduleActionForSpecimen(selectedDetail.specimen.id, title, days, note)
                            },
                            onDeleteScheduledAction = { actionId ->
                                viewModel.deleteScheduledAction(actionId)
                            },
                            onActionTask = { action ->
                                stagedBeforeVideoPath.value = null
                                stagedAfterVideoPath.value = null
                                pendingPhotoTargetCode = selectedDetail.specimen.code
                                activeActionModal = selectedDetail.specimen to action
                            },
                            onRetryYouTubeUploads = { viewModel.uploadPendingVideosToYouTube() },
                            onPlayLocalVideo = { path -> playLocalVideo(path) }
                        )
                    }

                    state.showAdminActionsScreen -> {
                        AdminCareActionsScreen(
                            careActions = state.careActions,
                            onBack = { viewModel.closeAdminCareActions() },
                            onSaveAction = { id, title, category, days, desc ->
                                viewModel.saveCareAction(id, title, category, days, desc)
                            },
                            onDeleteAction = { action -> viewModel.deleteCareAction(action) },
                            onRestoreDefaults = { viewModel.restoreDefaultCareActions() }
                        )
                    }

                    else -> {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                PotrTopHeader(
                                    isSignedIn = !state.cloud.googleEmail.isNullOrBlank(),
                                    pendingVideos = state.pendingVideoUploads,
                                    hasAppUpdate = hasAppUpdate,
                                    onOpenDrawer = {
                                        viewModel.refreshCloudState()
                                        scope.launch { drawerState.open() }
                                    },
                                    onBrandClick = { viewModel.jumpToCatalogAll() },
                                    onOpenCloud = {
                                        viewModel.refreshCloudState()
                                        showCloudDialog = true
                                    }
                                )
                            },
                            floatingActionButton = {
                                if (state.activeTab == MainTab.CATALOG || state.activeTab == MainTab.SHOWCASE) {
                                    ExtendedFloatingActionButton(
                                        onClick = {
                                            editingSpecimenId = null
                                            stagedAddEditPhotoPath.value = null
                                            showAddEditDialog = true
                                        },
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                        text = { Text("Catalog Bonsai") },
                                        modifier = Modifier.testTag("fab_add_bonsai")
                                    )
                                }
                            },
                            bottomBar = {
                                PotrBottomNavBar(
                                    activeTab = state.activeTab,
                                    dueCount = state.dueCount,
                                    onSelectTab = { viewModel.selectTab(it) }
                                )
                            }
                        ) { innerPadding ->
                            val contentModifier = Modifier.padding(innerPadding)
                            when (state.activeTab) {
                                MainTab.CATALOG -> CatalogScreen(
                                    state = state,
                                    onSearchChange = { viewModel.updateSearchQuery(it) },
                                    onSelectFilter = { viewModel.selectCatalogFilter(it) },
                                    onSetViewMode = { viewModel.setViewMode(it) },
                                    onOpenSpecimen = { viewModel.openSpecimenDetail(it) },
                                    onKpiTotalClick = { viewModel.jumpToCatalogAll() },
                                    onKpiDueClick = { viewModel.jumpToAlerts(AlertFilter.DUE) },
                                    onKpiUpcomingClick = { viewModel.jumpToAlerts(AlertFilter.UPCOMING) },
                                    onKpiPublishedClick = { viewModel.selectTab(MainTab.SHOWCASE) },
                                    modifier = contentModifier
                                )
                                MainTab.ALERTS -> AlertsScreen(
                                    state = state,
                                    onSelectFilter = { viewModel.selectAlertFilter(it) },
                                    onOpenSpecimen = { viewModel.openSpecimenDetail(it) },
                                    onActionTask = { alert ->
                                        stagedBeforeVideoPath.value = null
                                        stagedAfterVideoPath.value = null
                                        pendingPhotoTargetCode = alert.specimen.code
                                        activeActionModal = alert.specimen to alert.action
                                    },
                                    modifier = contentModifier
                                )
                                MainTab.SHOWCASE -> ShowcaseScreen(
                                    state = state,
                                    onOpenSpecimen = { viewModel.openSpecimenDetail(it) },
                                    onCopyPublicLink = {
                                        copyToClipboard(
                                            "POTR Bonsai Showcase",
                                            "${state.cloud.wooStoreUrl.trimEnd('/')}/product-category/bonsai/",
                                            "Public showcase link copied"
                                        )
                                    },
                                    modifier = contentModifier
                                )
                            }
                        }
                    }
                }

                if (showAddEditDialog) {
                    AddEditSpecimenDialog(
                        editingSpecimen = editingSpecimen,
                        suggestedCode = state.nextSuggestedCode,
                        speciesSuggestions = state.speciesSuggestions,
                        careActions = state.careActions,
                        stagedPhotoPath = stagedPhoto,
                        onRequestCameraPhoto = { code ->
                            pendingPhotoReplaceSpecimenId = null
                            pendingPhotoTargetCode = code
                            requestCameraCapture()
                        },
                        onRequestGalleryPhoto = { code ->
                            pendingPhotoReplaceSpecimenId = null
                            pendingPhotoTargetCode = code
                            imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onClearPhoto = { stagedAddEditPhotoPath.value = null },
                        onDismiss = {
                            showAddEditDialog = false
                            editingSpecimenId = null
                            stagedAddEditPhotoPath.value = null
                        },
                        onSaveNew = { code, name, species, sub, age, ageType, ageNote, pot, potMeta, photo, p, potP, eff, sell, initTitle, initDays ->
                            viewModel.addSpecimen(
                                code, name, species, sub, age, ageType, ageNote, pot, potMeta, photo, p, potP, eff, sell, initTitle, initDays
                            )
                            showAddEditDialog = false
                            stagedAddEditPhotoPath.value = null
                        },
                        onSaveEdit = { existing, code, name, species, sub, age, ageType, ageNote, pot, potMeta, repotted, photo, p, potP, eff, sell ->
                            viewModel.updateSpecimen(
                                existing, code, name, species, sub, age, ageType, ageNote, pot, potMeta, repotted, photo, p, potP, eff, sell
                            )
                            showAddEditDialog = false
                            editingSpecimenId = null
                            stagedAddEditPhotoPath.value = null
                        }
                    )
                }

                activeActionModal?.let { (specimen, action) ->
                    ActionLogDialog(
                        specimen = specimen,
                        action = action,
                        careActions = state.careActions,
                        beforeVideoPath = beforeVideo,
                        afterVideoPath = afterVideo,
                        onPickBeforeVideo = {
                            pendingVideoSlot = "before"
                            videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        onPickAfterVideo = {
                            pendingVideoSlot = "after"
                            videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        onDismiss = {
                            activeActionModal = null
                            stagedBeforeVideoPath.value = null
                            stagedAfterVideoPath.value = null
                        },
                        onSubmit = { notes, followUpTitle, followUpDays ->
                            viewModel.completeActionAndLog(
                                specimenId = specimen.id,
                                specimenCode = specimen.code,
                                action = action,
                                notes = notes,
                                beforeVideoPath = beforeVideo,
                                afterVideoPath = afterVideo,
                                followUpTitle = followUpTitle,
                                followUpDays = followUpDays
                            )
                            activeActionModal = null
                            stagedBeforeVideoPath.value = null
                            stagedAfterVideoPath.value = null
                        }
                    )
                }

                if (showCloudDialog) {
                    val installedSha1 = remember { GoogleAuthTokenProvider.getInstalledAppSha1(this@MainActivity) }
                    CloudSettingsDialog(
                        cloud = state.cloud,
                        pendingVideoCount = state.pendingVideoUploads,
                        appSha1 = installedSha1,
                        onSignInGoogle = { startGoogleSignIn() },
                        onSignOutGoogle = { signOutGoogle() },
                        onBackupNow = { viewModel.backupNowToDrive() },
                        onRestoreFromDrive = { viewModel.restoreFromDrive() },
                        onExportSheet = { viewModel.exportToGoogleSheet() },
                        onUploadPendingVideos = { viewModel.uploadPendingVideosToYouTube() },
                        onSetYouTubePrivacy = { viewModel.setYouTubePrivacy(it) },
                        onSaveWooConfig = { url, ck, cs, user, pass ->
                            viewModel.saveWooCommerceConfig(url, ck, cs, user, pass)
                        },
                        onCheckUpdate = { viewModel.checkForAppUpdate() },
                        onDownloadUpdate = { viewModel.requestDownloadUpdate() },
                        onCopyText = { label, text, msg -> copyToClipboard(label, text, msg) },
                        onDismiss = { showCloudDialog = false }
                    )
                }

                if (state.cloud.isDownloadingUpdate) {
                    val pct = state.cloud.updateDownloadPercent
                    AlertDialog(
                        onDismissRequest = {},
                        title = {
                            Text(
                                text = "Downloading Update",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = state.cloud.availableUpdateVersion?.let { "Downloading POTR Bonsai v$it…" }
                                        ?: "Downloading latest POTR Bonsai APK…",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (pct in 1..99) {
                                    LinearProgressIndicator(
                                        progress = { pct / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Text(
                                    text = if (pct > 0) "$pct% complete" else "Connecting to GitHub Releases…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { openExternalUrl(state.cloud.releasePageUrl) }
                            ) {
                                Text("Open in Browser")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun requestCameraCapture() {
        val hasCameraPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasCameraPerm) {
            launchCameraInternal()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraInternal() {
        try {
            val (file, uri) = PhotoCompressor.createTempCameraUri(this)
            pendingCameraTempPath = file.absolutePath
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            viewModel.emitMessage("Camera unavailable: ${e.message}")
        }
    }

    private fun handlePickedPhotoUri(uri: Uri, tempFileToDelete: File?) {
        val replaceId = pendingPhotoReplaceSpecimenId
        if (!replaceId.isNullOrBlank()) {
            pendingPhotoReplaceSpecimenId = null
            viewModel.replaceSpecimenPhotoById(replaceId, uri, tempFileToDelete)
        } else {
            val code = pendingPhotoTargetCode
            lifecycleScope.launch {
                val res = viewModel.compressPickedPhoto(uri, code, tempFileToDelete)
                res.fold(
                    onSuccess = { path -> stagedAddEditPhotoPath.value = path },
                    onFailure = { e -> viewModel.emitMessage("Photo compression error: ${e.message}") }
                )
            }
        }
    }

    private fun startGoogleSignIn() {
        try {
            viewModel.onGoogleSignInStarted()
            val browserIntent = GoogleAuthTokenProvider.buildBrowserSignInIntent(appInstance.repository.prefs)
            awaitingBrowserOAuth = true
            startActivity(browserIntent)
        } catch (e: Exception) {
            awaitingBrowserOAuth = false
            viewModel.onGoogleAuthFailed(
                "Could not open browser for Google Sign-In: ${e.message}",
                isConfigError = false
            )
        }
    }

    private fun handleOAuthIntentIfPresent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!GoogleAuthTokenProvider.isOAuthRedirectUri(uri)) return
        awaitingBrowserOAuth = false
        intent.data = null
        viewModel.onGoogleSignInStarted()
        lifecycleScope.launch {
            when (val result = GoogleAuthTokenProvider.handleOAuthRedirect(uri, appInstance.repository.prefs)) {
                is AuthTokenResult.Success -> viewModel.onGoogleSignedIn(result.email)
                is AuthTokenResult.NotSignedIn -> viewModel.onGoogleBrowserSignInDismissed()
                is AuthTokenResult.Error -> viewModel.onGoogleAuthFailed(result.message, result.isConfigError)
            }
        }
    }

    private fun signOutGoogle() {
        lifecycleScope.launch {
            GoogleAuthTokenProvider.revokeTokens(appInstance.repository.prefs)
            viewModel.onGoogleSignedOut()
        }
    }

    private fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            viewModel.emitMessage("No app available to open link")
        }
    }

    private fun playLocalVideo(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                viewModel.emitMessage("Local video file no longer exists")
                return
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            viewModel.emitMessage("No video player app found")
        }
    }

    private fun copyToClipboard(label: String, text: String, toastMessage: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        viewModel.emitMessage(toastMessage)
    }

    private fun downloadAndInstallUpdate(apkUrl: String) {
        if (viewModel.uiState.value.cloud.isDownloadingUpdate) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingInstallApkUrl = apkUrl
            viewModel.emitMessage("Enable 'Allow from this source' — download will start automatically when you return")
            unknownAppSourcesLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
            return
        }
        lifecycleScope.launch {
            viewModel.setUpdateDownloadProgress(true, 0, "Downloading APK update…")
            try {
                val destFile = File(cacheDir, "downloads/potr-bonsai-update.apk")
                withContext(Dispatchers.IO) {
                    appInstance.updateChecker.downloadApk(apkUrl, destFile) { pct ->
                        runOnUiThread {
                            viewModel.setUpdateDownloadProgress(true, pct, "Downloading update… $pct%")
                        }
                    }.getOrThrow()
                }
                viewModel.setUpdateDownloadProgress(false, 100, "Opening Android installer…")
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", destFile)
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(installIntent)
            } catch (e: Exception) {
                val msg = "Update download failed: ${e.message ?: "unknown error"}"
                viewModel.setUpdateDownloadProgress(false, 0, msg)
                viewModel.emitMessage(msg)
            }
        }
    }

    companion object {
        private const val STATE_PHOTO_CODE = "pending_photo_code"
        private const val STATE_REPLACE_ID = "pending_photo_replace_id"
        private const val STATE_CAMERA_TEMP_PATH = "pending_camera_temp_path"
        private const val STATE_STAGED_PHOTO_PATH = "staged_add_edit_photo_path"
        private const val STATE_PENDING_INSTALL_URL = "pending_install_apk_url"
        private const val STATE_AWAITING_OAUTH = "awaiting_browser_oauth"
    }
}

@Composable
private fun PotrTopHeader(
    isSignedIn: Boolean,
    pendingVideos: Int,
    hasAppUpdate: Boolean,
    onOpenDrawer: () -> Unit,
    onBrandClick: () -> Unit,
    onOpenCloud: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("drawer_menu_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (hasAppUpdate) {
                                Badge(containerColor = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open menu, Admin & App Updates",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onBrandClick)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.potr_logo),
                        contentDescription = "Pots on the Rocks Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(width = 44.dp, height = 34.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Bonsai Catalogue",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Pots on the Rocks • Studio",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Surface(
                    onClick = onOpenCloud,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("cloud_settings_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isSignedIn) Icons.Default.CloudDone else Icons.Default.Cloud,
                            contentDescription = "Cloud and potr.in settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = if (pendingVideos > 0) "Cloud ($pendingVideos)" else "Cloud",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun PotrBottomNavBar(
    activeTab: MainTab,
    dueCount: Int,
    onSelectTab: (MainTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets.navigationBars
    ) {
        val navColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        NavigationBarItem(
            selected = activeTab == MainTab.CATALOG,
            onClick = { onSelectTab(MainTab.CATALOG) },
            icon = {
                Icon(
                    imageVector = if (activeTab == MainTab.CATALOG) Icons.Filled.Park else Icons.Outlined.Park,
                    contentDescription = "Catalog"
                )
            },
            label = { Text("Catalog") },
            colors = navColors,
            modifier = Modifier.testTag("nav_catalog")
        )

        NavigationBarItem(
            selected = activeTab == MainTab.ALERTS,
            onClick = { onSelectTab(MainTab.ALERTS) },
            icon = {
                BadgedBox(
                    badge = {
                        if (dueCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ) {
                                Text(dueCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (activeTab == MainTab.ALERTS) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                        contentDescription = "Alerts"
                    )
                }
            },
            label = { Text("Alerts") },
            colors = navColors,
            modifier = Modifier.testTag("nav_alerts")
        )

        NavigationBarItem(
            selected = activeTab == MainTab.SHOWCASE,
            onClick = { onSelectTab(MainTab.SHOWCASE) },
            icon = {
                Icon(
                    imageVector = if (activeTab == MainTab.SHOWCASE) Icons.Filled.Storefront else Icons.Outlined.Storefront,
                    contentDescription = "Showcase"
                )
            },
            label = { Text("Showcase") },
            colors = navColors,
            modifier = Modifier.testTag("nav_showcase")
        )
    }
}
