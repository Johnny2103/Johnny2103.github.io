package io.github.johnny2103.novagalerie.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Draggable scrollbar on the right edge of a grid with a date bubble, so thousands of photos
 * can be skimmed in one gesture. Appears while scrolling and fades out afterwards.
 */
@Composable
fun BoxScope.FastScroller(
    state: LazyGridState,
    itemCount: Int,
    labelFor: (index: Int) -> String?,
    modifier: Modifier = Modifier,
) {
    if (itemCount < 60) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val thumbHeight = 48.dp
    val thumbHeightPx = with(density) { thumbHeight.toPx() }

    val scrollFraction by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) 0f else {
                val visible = info.visibleItemsInfo.size.coerceAtLeast(1)
                (state.firstVisibleItemIndex.toFloat() / (total - visible).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }
    val fraction = if (dragging) dragFraction else scrollFraction

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress, dragging) {
        if (state.isScrollInProgress || dragging) {
            visible = true
        } else {
            delay(1200)
            visible = false
        }
    }

    val currentIndex = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
    val label = if (dragging) labelFor(currentIndex) else null

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(56.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .onSizeChanged { trackHeightPx = (it.height - thumbHeightPx).coerceAtLeast(1f) }
                .pointerInput(itemCount) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = ((offset.y - thumbHeightPx / 2) / trackHeightPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, dragAmount ->
                        change.consume()
                        dragFraction = (dragFraction + dragAmount / trackHeightPx).coerceIn(0f, 1f)
                        val target = (dragFraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                        scope.launch { state.scrollToItem(target) }
                    }
                },
        ) {
            val thumbOffset = (fraction * trackHeightPx).roundToInt()
            if (label != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(x = with(density) { (-44).dp.roundToPx() }, y = thumbOffset) },
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .offset { IntOffset(0, thumbOffset) }
                    .size(width = 8.dp, height = thumbHeight)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
    }
}
