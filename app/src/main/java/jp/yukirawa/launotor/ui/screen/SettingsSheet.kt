package jp.yukirawa.launotor.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.yukirawa.launotor.BuildConfig
import jp.yukirawa.launotor.domain.model.AppSettings
import jp.yukirawa.launotor.notifications.LiveUpdateSupport

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    canPostPromoted: Boolean,
    hasNotificationPermission: Boolean,
    hasBlePermissions: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestBlePermissions: () -> Unit,
    onUpdateThresholdMinutes: (Int) -> Unit,
    onUpdateHapticsEnabled: (Boolean) -> Unit,
    onUpdateLiveUpdatesEnabled: (Boolean) -> Unit,
    onUpdateP2pSyncEnabled: (Boolean) -> Unit,
    onUpdateBackgroundP2pEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "設定",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )

            // Almost-done threshold setting
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "もうすぐ通知",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = "終わる少し前にお知らせします",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )

                    ButtonGroup(
                        overflowIndicator = { menuState ->
                            FilledTonalIconButton(onClick = { menuState.show() }) {
                                Icon(Icons.Rounded.MoreHoriz, contentDescription = "その他")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = ButtonGroupDefaults.HorizontalArrangement,
                    ) {
                        AppSettings.THRESHOLD_OPTIONS_MINUTES.forEach { option ->
                            val label = if (option == 0) "オフ" else "${option}分前"
                            toggleableItem(
                                checked = settings.almostDoneThresholdMinutes == option,
                                label = label,
                                onCheckedChange = { checked ->
                                    if (checked) onUpdateThresholdMinutes(option)
                                },
                                weight = 1f,
                            )
                        }
                    }
                }
            }

            // Lock screen progress & vibration
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = colorScheme.primary,
                            )
                            Column {
                                Text(
                                    text = "ロック画面の進行表示",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "残り時間を通知やステータスバーに表示",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = settings.liveUpdatesEnabled,
                            onCheckedChange = onUpdateLiveUpdatesEnabled,
                        )
                    }

                    if (LiveUpdateSupport.isAndroid16OrHigher() && !canPostPromoted) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "ステータスバーにも表示するには許可が必要です",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        val intent = LiveUpdateSupport
                                            .createPromotionSettingsIntent(context)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        runCatching { context.startActivity(intent) }
                                    },
                                ) {
                                    Text("設定", maxLines = 1, softWrap = false)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (!hasNotificationPermission) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = colorScheme.errorContainer,
                            contentColor = colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Rounded.Notifications, contentDescription = null)
                                    Text(
                                        text = "通知がオフになっています",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(onClick = onRequestNotificationPermission) {
                                    Text("許可", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Vibration,
                                contentDescription = null,
                                tint = colorScheme.secondary,
                            )
                            Column {
                                Text(
                                    text = "振動フィードバック",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "スライダーやボタン操作で軽く振動します",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = settings.hapticsEnabled,
                            onCheckedChange = onUpdateHapticsEnabled,
                        )
                    }
                }
            }

            // Friendly P2P Auto-Share card
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "みんなで自動共有",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sensors,
                                contentDescription = null,
                                tint = colorScheme.primary,
                            )
                            Column {
                                Text(
                                    text = "近くの端末と自動共有",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "同じWi-Fiや近くの端末と空き状況を自動共有",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = settings.p2pSyncEnabled,
                            onCheckedChange = onUpdateP2pSyncEnabled,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = null,
                                tint = colorScheme.secondary,
                            )
                            Column {
                                Text(
                                    text = "バックグラウンドでも自動共有",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "アプリを閉じていてもすれ違い共有します",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = settings.backgroundP2pEnabled,
                            enabled = settings.p2pSyncEnabled,
                            onCheckedChange = onUpdateBackgroundP2pEnabled,
                        )
                    }

                    if (settings.p2pSyncEnabled && !hasBlePermissions) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = colorScheme.secondaryContainer.copy(alpha = 0.75f),
                            contentColor = colorScheme.onSecondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bluetooth,
                                        contentDescription = null,
                                    )
                                    Text(
                                        text = "すれ違い共有にはBluetoothの許可が必要です",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(onClick = onRequestBlePermissions) {
                                    Text("Bluetooth を許可", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "Launotor v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
