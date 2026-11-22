package `in`.potr.bonsai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.potr.bonsai.BuildConfig
import `in`.potr.bonsai.R
import `in`.potr.bonsai.ui.MainTab
import `in`.potr.bonsai.ui.MainUiState
import `in`.potr.bonsai.ui.theme.SageBorder

@Composable
fun PotrNavigationDrawerSheet(
    state: MainUiState,
    onSelectTab: (MainTab) -> Unit,
    onOpenAdminCareActions: () -> Unit,
    onOpenCloudSettings: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onDownloadAndInstallUpdate: () -> Unit
) {
    val hasUpdate = !state.cloud.availableUpdateUrl.isNullOrBlank()
    val isSignedIn = !state.cloud.googleEmail.isNullOrBlank()

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(308.dp)
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Studio Identity Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.potr_logo),
                    contentDescription = "Pots on the Rocks Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 52.dp, height = 42.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "POTR Bonsai",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.cloud.googleEmail ?: "On-Device Studio Archive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Workspace Section
            Text(
                text = "STUDIO WORKSPACE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )

            NavigationDrawerItem(
                label = { Text("Bonsai Catalog") },
                icon = { Icon(Icons.Default.Park, contentDescription = null) },
                badge = { Text(state.totalCount.toString(), style = MaterialTheme.typography.labelMedium) },
                selected = !state.showAdminActionsScreen && state.activeTab == MainTab.CATALOG,
                onClick = { onSelectTab(MainTab.CATALOG) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("drawer_nav_catalog")
            )

            NavigationDrawerItem(
                label = { Text("Care Alerts") },
                icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                badge = {
                    if (state.dueCount > 0) {
                        StatusPill(
                            text = "${state.dueCount} due",
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                },
                selected = !state.showAdminActionsScreen && state.activeTab == MainTab.ALERTS,
                onClick = { onSelectTab(MainTab.ALERTS) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("drawer_nav_alerts")
            )

            NavigationDrawerItem(
                label = { Text("Public Showcase") },
                icon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                badge = { Text(state.publishedCount.toString(), style = MaterialTheme.typography.labelMedium) },
                selected = !state.showAdminActionsScreen && state.activeTab == MainTab.SHOWCASE,
                onClick = { onSelectTab(MainTab.SHOWCASE) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("drawer_nav_showcase")
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Admin Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "ADMIN",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            NavigationDrawerItem(
                label = { Text("Global Care Actions") },
                icon = { Icon(Icons.Default.EventNote, contentDescription = null) },
                badge = {
                    StatusPill(
                        text = "${state.careActions.size}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                },
                selected = state.showAdminActionsScreen,
                onClick = onOpenAdminCareActions,
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("drawer_admin_care_actions")
            )

            NavigationDrawerItem(
                label = { Text("Cloud, Drive & potr.in") },
                icon = {
                    Icon(
                        imageVector = if (isSignedIn) Icons.Default.CloudDone else Icons.Default.Cloud,
                        contentDescription = null
                    )
                },
                badge = {
                    if (state.pendingVideoUploads > 0) {
                        StatusPill(
                            text = "${state.pendingVideoUploads} vid",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    } else if (isSignedIn) {
                        StatusPill(
                            text = "Synced",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                selected = false,
                onClick = onOpenCloudSettings,
                modifier = Modifier.testTag("drawer_admin_cloud_settings")
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Dedicated In-App Updater Card inside Drawer
            Surface(
                color = if (hasUpdate) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(
                    1.dp,
                    if (hasUpdate) SageBorder else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "App Updates",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        StatusPill(
                            text = "v${BuildConfig.VERSION_NAME}",
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = state.cloud.updateStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasUpdate) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (hasUpdate) {
                        Button(
                            onClick = onDownloadAndInstallUpdate,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("drawer_install_update_button")
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download & Install v${state.cloud.availableUpdateVersion}")
                        }
                        OutlinedButton(
                            onClick = onCheckForUpdate,
                            enabled = !state.cloud.isCheckingUpdate,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Re-check Releases")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onCheckForUpdate,
                            enabled = !state.cloud.isCheckingUpdate,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("drawer_check_update_button")
                        ) {
                            if (state.cloud.isCheckingUpdate) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Checking…")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Check for App Update")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
