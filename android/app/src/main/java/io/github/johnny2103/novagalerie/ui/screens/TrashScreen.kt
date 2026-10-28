package io.github.johnny2103.novagalerie.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.GroupBy
import io.github.johnny2103.novagalerie.data.MediaSource
import io.github.johnny2103.novagalerie.ui.GalleryViewModel

/** Items in the system trash (Android 11+): restore or delete permanently. */
@Composable
fun TrashScreen(viewModel: GalleryViewModel, onBack: () -> Unit, onOpenViewer: (MediaSource, Int) -> Unit) {
    LaunchedEffect(Unit) { viewModel.loadTrash() }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.trash_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
        }
        return
    }
    CollectionScreen(
        viewModel = viewModel,
        source = MediaSource.Trash,
        title = stringResource(R.string.explore_trash),
        subtitle = stringResource(R.string.trash_info),
        onBack = onBack,
        onOpenViewer = onOpenViewer,
        emptyText = stringResource(R.string.trash_empty),
        trashMode = true,
        groupBy = GroupBy.NONE,
    )
}
