@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.johnny2103.novagalerie.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.johnny2103.novagalerie.BuildConfig
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.FaceSensitivity
import io.github.johnny2103.novagalerie.data.GroupBy
import io.github.johnny2103.novagalerie.data.ThemeMode
import io.github.johnny2103.novagalerie.ui.GalleryViewModel
import io.github.johnny2103.novagalerie.ui.canUseAppLock
import io.github.johnny2103.novagalerie.util.MediaActions

private const val SOURCE_URL = "https://github.com/Johnny2103/Johnny2103.github.io"

@Composable
fun SettingsScreen(viewModel: GalleryViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmRebuild by remember { mutableStateOf(false) }
    val lockAvailable = remember { canUseAppLock(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // --- Appearance ---
            SectionTitle(stringResource(R.string.settings_appearance))
            SubTitle(stringResource(R.string.settings_theme))
            ThemeMode.entries.forEach { mode ->
                RadioRow(
                    label = stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        },
                    ),
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_desc),
                    checked = settings.dynamicColor,
                    onChange = { viewModel.setDynamicColor(it) },
                )
            }

            Divider()

            // --- Grid ---
            SectionTitle(stringResource(R.string.settings_grid))
            Text(
                stringResource(R.string.settings_columns, settings.gridColumns),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Slider(
                value = settings.gridColumns.toFloat(),
                onValueChange = { viewModel.setGridColumns(it.toInt()) },
                valueRange = 2f..6f,
                steps = 3,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                stringResource(R.string.settings_columns_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SubTitle(stringResource(R.string.settings_group_by))
            GroupBy.entries.forEach { g ->
                RadioRow(
                    label = stringResource(
                        when (g) {
                            GroupBy.DAY -> R.string.group_day
                            GroupBy.MONTH -> R.string.group_month
                            GroupBy.YEAR -> R.string.group_year
                            GroupBy.NONE -> R.string.group_none
                        },
                    ),
                    selected = settings.groupBy == g,
                    onClick = { viewModel.setGroupBy(g) },
                )
            }
            SwitchRow(
                title = stringResource(R.string.settings_show_videos),
                subtitle = null,
                checked = settings.showVideosInTimeline,
                onChange = { viewModel.setShowVideos(it) },
            )

            Divider()

            // --- Analysis ---
            SectionTitle(stringResource(R.string.settings_analysis))
            SwitchRow(
                title = stringResource(R.string.settings_index_enabled),
                subtitle = stringResource(R.string.settings_index_desc),
                checked = settings.indexingEnabled,
                onChange = { viewModel.setIndexingEnabled(it) },
            )
            SubTitle(stringResource(R.string.settings_sensitivity))
            FaceSensitivity.entries.forEach { s ->
                RadioRow(
                    label = stringResource(
                        when (s) {
                            FaceSensitivity.STRICT -> R.string.sensitivity_strict
                            FaceSensitivity.NORMAL -> R.string.sensitivity_normal
                            FaceSensitivity.LOOSE -> R.string.sensitivity_loose
                        },
                    ),
                    selected = settings.faceSensitivity == s,
                    onClick = { viewModel.setFaceSensitivity(s) },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_recluster)) },
                supportingContent = { Text(stringResource(R.string.settings_recluster_desc)) },
                modifier = Modifier.clickable { viewModel.recluster() },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_rebuild_index)) },
                supportingContent = { Text(stringResource(R.string.settings_rebuild_index_desc)) },
                modifier = Modifier.clickable { confirmRebuild = true },
            )

            Divider()

            // --- Viewer ---
            SectionTitle(stringResource(R.string.settings_viewer))
            Text(
                stringResource(R.string.settings_slideshow_interval, settings.slideshowSeconds),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Slider(
                value = settings.slideshowSeconds.toFloat(),
                onValueChange = { viewModel.setSlideshowSeconds(it.toInt()) },
                valueRange = 2f..15f,
                steps = 12,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (MediaActions.isTrashSupported()) {
                SwitchRow(
                    title = stringResource(R.string.settings_use_trash),
                    subtitle = stringResource(R.string.settings_use_trash_desc),
                    checked = settings.useTrash,
                    onChange = { viewModel.setUseTrash(it) },
                )
            }

            Divider()

            // --- Security ---
            SectionTitle(stringResource(R.string.settings_security))
            SwitchRow(
                title = stringResource(R.string.settings_app_lock),
                subtitle = stringResource(if (lockAvailable) R.string.settings_app_lock_desc else R.string.settings_app_lock_unavailable),
                checked = settings.appLock && lockAvailable,
                enabled = lockAvailable,
                onChange = { viewModel.setAppLock(it) },
            )

            Divider()

            // --- About ---
            SectionTitle(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_source)) },
                supportingContent = { Text(SOURCE_URL) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                modifier = Modifier.clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) }
                },
            )
            Text(
                stringResource(R.string.settings_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (confirmRebuild) {
        AlertDialog(
            onDismissRequest = { confirmRebuild = false },
            title = { Text(stringResource(R.string.settings_rebuild_index)) },
            text = { Text(stringResource(R.string.settings_rebuild_index_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRebuild = false
                    viewModel.rebuildIndex()
                }) { Text(stringResource(R.string.settings_rebuild_index)) }
            },
            dismissButton = { TextButton(onClick = { confirmRebuild = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SubTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
    )
}
