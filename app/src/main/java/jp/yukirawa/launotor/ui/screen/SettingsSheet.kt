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
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    onRequestNotificationPermission: () -> Unit,
    onUpdateThresholdMinutes: (Int) -> Unit,
    onUpdateHapticsEnabled: (Boolean) -> Unit,
    onUpdateLiveUpdatesEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                text = "監視・通知設定",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = colorScheme.onSurface,
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
                        text = "まもなく空き通知のタイミング",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "指定した残り時間以下になると事前通知を送信します（開始時間がしきい値以下の場合はスキップ）。",
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
                            val label = if (option == 0) "OFF" else "${option}分前"
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

            // Notification & Live Updates settings
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
                                    text = "Live Updates (リアルタイム常駐表示)",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = if (LiveUpdateSupport.isAndroid16OrHigher()) {
                                        "Android 16 ステータスバーチップ＆進行バー表示"
                                    } else {
                                        "常駐プログレス通知で残り時間を監視"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = settings.liveUpdatesEnabled,
                            onCheckedChange = onUpdateLiveUpdatesEnabled,
                        )
                    }

                    if (LiveUpdateSupport.isAndroid16OrHigher()) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (canPostPromoted) {
                                colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = if (canPostPromoted) {
                                            "Android 16 Live Updates 許可済み"
                                        } else {
                                            "Android 16 Live Updates が未許可"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = if (canPostPromoted) {
                                            "ステータスバーのチップ表示が有効です。"
                                        } else {
                                            "システム設定でプロモート通知を許可するとチップ表示されます。"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        val intent = LiveUpdateSupport
                                            .createPromotionSettingsIntent(context)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        runCatching { context.startActivity(intent) }
                                    },
                                ) {
                                    Text("設定")
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
                                        text = "通知権限が無効です。アラートを受け取るには許可してください。",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(onClick = onRequestNotificationPermission) {
                                    Text("許可")
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
                                    text = "触覚フィードバック (Haptics)",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "タイマー操作時に心地よい振動フィードバックを返します",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = settings.hapticsEnabled,
                            onCheckedChange = onUpdateHapticsEnabled,
                        )
                    }
                }
            }

            // About footer
            Text(
                text = "洗濯乾燥機時間監視 (Launotor) v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
