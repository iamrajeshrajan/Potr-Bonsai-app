package `in`.potr.bonsai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `in`.potr.bonsai.ui.CloudSettingsState
import `in`.potr.bonsai.ui.theme.JetBrainsMonoFamily
import `in`.potr.bonsai.ui.theme.SageBorder
import `in`.potr.bonsai.ui.theme.TerracottaBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSettingsDialog(
    cloud: CloudSettingsState,
    pendingVideoCount: Int,
    appSha1: String,
    onSignInGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onBackupNow: () -> Unit,
    onRestoreFromDrive: () -> Unit,
    onExportSheet: () -> Unit,
    onUploadPendingVideos: () -> Unit,
    onSetYouTubePrivacy: (String) -> Unit,
    onSaveWooConfig: (storeUrl: String, ck: String, cs: String, wpUser: String, wpPass: String) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onCopyText: (label: String, text: String, toastMessage: String) -> Unit,
    onDismiss: () -> Unit
) {
    var storeUrl by remember(cloud.wooStoreUrl) { mutableStateOf(cloud.wooStoreUrl) }
    var consumerKey by remember { mutableStateOf("") }
    var consumerSecret by remember { mutableStateOf("") }
    var wpUser by remember(cloud.wooWpUser) { mutableStateOf(cloud.wooWpUser) }
    var wpAppPass by remember { mutableStateOf("") }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showOAuthGuide by remember { mutableStateOf(cloud.showOAuthSetupHint) }

    LaunchedEffect(cloud.showOAuthSetupHint) {
        if (cloud.showOAuthSetupHint) showOAuthGuide = true
    }

    val formattedLastBackup = remember(cloud.lastBackupEpochMs) {
        if (cloud.lastBackupEpochMs > 0L) {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH).format(Date(cloud.lastBackupEpochMs))
        } else {
            "Not backed up yet"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "POTR BONSAI CLOUD ENGINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Drive Backup, YouTube & potr.in",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close cloud settings")
                    }
                }

                if (cloud.isSyncing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Google Account & Auto-Backup
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, SageBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (!cloud.googleEmail.isNullOrBlank()) {
                                            "Signed in: ${cloud.googleEmail}"
                                        } else {
                                            "Google Account: Not Signed In"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Last Drive backup: $formattedLastBackup",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                if (cloud.googleEmail.isNullOrBlank()) {
                                    Button(
                                        onClick = onSignInGoogle,
                                        enabled = !cloud.isSyncing,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("google_signin_button")
                                    ) {
                                        Text("Sign In")
                                    }
                                } else {
                                    TextButton(onClick = onSignOutGoogle) {
                                        Text("Sign Out")
                                    }
                                }
                            }

                            // Inline feedback banners (visible directly inside the dialog, never hidden behind it!)
                            if (!cloud.authErrorMessage.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, TerracottaBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = cloud.authErrorMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            } else if (!cloud.authStatusMessage.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, SageBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = cloud.authStatusMessage,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            } else if (!cloud.lastBackupError.isNullOrBlank()) {
                                Text(
                                    text = "Last notice: ${cloud.lastBackupError}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            Text(
                                text = "Database edits auto-backup to Google Drive / Bonsai Catalogue / potr_bonsai_backup.json and compressed photos sync to Photos/.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onBackupNow,
                                    enabled = !cloud.isSyncing,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("backup_drive_now_button")
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Backup Now")
                                }
                                OutlinedButton(
                                    onClick = { showRestoreConfirm = true },
                                    enabled = !cloud.isSyncing,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("restore_drive_button")
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Restore")
                                }
                            }

                            OutlinedButton(
                                onClick = onExportSheet,
                                enabled = !cloud.isSyncing,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("export_sheet_button")
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export Master Google Sheet")
                            }

                            // Expandable OAuth Diagnostics & Live Installed APK SHA-1 Copy Helper
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showOAuthGuide = !showOAuthGuide },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VpnKey,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Google Sign-In Setup & App SHA-1",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Icon(
                                            imageVector = if (showOAuthGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Toggle OAuth setup info"
                                        )
                                    }

                                    if (showOAuthGuide) {
                                        Column(
                                            modifier = Modifier.padding(top = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Google requires an Android OAuth 2.0 Client ID in Google Cloud Console matching this exact package and signing SHA-1:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            CopyableCodeRow(
                                                label = "Package Name",
                                                value = "in.potr.bonsai",
                                                onCopy = {
                                                    onCopyText("Package Name", "in.potr.bonsai", "Copied package in.potr.bonsai")
                                                }
                                            )
                                            CopyableCodeRow(
                                                label = "Installed APK SHA-1",
                                                value = appSha1,
                                                onCopy = {
                                                    onCopyText("POTR Bonsai SHA-1", appSha1, "Copied installed APK SHA-1")
                                                }
                                            )
                                            Text(
                                                text = "1. Enable Drive API, Sheets API & YouTube Data API v3.\n" +
                                                    "2. Under Audience, add your Gmail as a Test User.\n" +
                                                    "3. In Clients → POTR Bonsai Android → Advanced settings, check \"Enable custom URI scheme\" and Save.\n" +
                                                    "Sign-In opens Chrome (OAuth 2.0 + PKCE) and stores an encrypted refresh token so auto-backups stay connected.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. YouTube Lifespan Video Uploads
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("YouTube Video Uploads", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = if (pendingVideoCount > 0) {
                                            "$pendingVideoCount video entry(s) queued or waiting retry"
                                        } else {
                                            "All logged videos synced to YouTube"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = onUploadPendingVideos,
                                    enabled = !cloud.isSyncing && pendingVideoCount > 0,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("upload_youtube_queue_button")
                                ) {
                                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Upload")
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Visibility:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilterChip(
                                    selected = cloud.youtubePrivacy == "unlisted",
                                    onClick = { onSetYouTubePrivacy("unlisted") },
                                    label = { Text("Unlisted") }
                                )
                                FilterChip(
                                    selected = cloud.youtubePrivacy == "private",
                                    onClick = { onSetYouTubePrivacy("private") },
                                    label = { Text("Private") }
                                )
                            }
                            Text(
                                text = "Note: Unverified personal Google Cloud OAuth apps default to Private visibility on YouTube until audited; you can toggle visibility anytime in YouTube Studio.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. potr.in WooCommerce API (Encrypted via Android Keystore)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "potr.in WooCommerce API (Encrypted)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            OutlinedTextField(
                                value = storeUrl,
                                onValueChange = { storeUrl = it },
                                label = { Text("Store URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = consumerKey,
                                    onValueChange = { consumerKey = it },
                                    label = {
                                        Text(if (cloud.hasWooKeys) "Consumer Key (Saved ✓)" else "Consumer Key (ck_...)")
                                    },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = consumerSecret,
                                    onValueChange = { consumerSecret = it },
                                    label = {
                                        Text(if (cloud.hasWooKeys) "Secret (Saved ✓)" else "Consumer Secret (cs_...)")
                                    },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = wpUser,
                                    onValueChange = { wpUser = it },
                                    label = { Text("WP User (Media)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = wpAppPass,
                                    onValueChange = { wpAppPass = it },
                                    label = {
                                        Text(if (cloud.hasWpAppPassword) "App Pass (Saved ✓)" else "WP App Password")
                                    },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(
                                    onClick = {
                                        onSaveWooConfig(storeUrl, consumerKey, consumerSecret, wpUser, wpAppPass)
                                        consumerKey = ""
                                        consumerSecret = ""
                                        wpAppPass = ""
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("save_woo_config_button")
                                ) {
                                    Text("Save Credentials")
                                }
                            }
                        }
                    }

                    // 4. App Version & GitHub Releases Self-Update
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("App Updates", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    cloud.updateStatusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            if (!cloud.availableUpdateUrl.isNullOrBlank()) {
                                Button(
                                    onClick = onDownloadUpdate,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Install")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onCheckUpdate,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("check_update_button")
                                ) {
                                    Text("Check")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restore from Google Drive?") },
            text = {
                Text("This replaces your current on-device catalog, care actions, and timelines with the latest potr_bonsai_backup.json from Google Drive and re-downloads specimen photos.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirm = false
                        onRestoreFromDrive()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Restore Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CopyableCodeRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
