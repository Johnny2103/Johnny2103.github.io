package io.github.johnny2103.novagalerie.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.johnny2103.novagalerie.data.MediaItem
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Full-screen image with pinch-to-zoom, pan and double-tap zoom.
 * While not zoomed in, horizontal drags are left to the surrounding pager.
 */
@Composable
fun ZoomableImage(
    item: MediaItem,
    resetKey: Any,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(target: Offset, forScale: Float): Offset {
        val maxX = (containerSize.width * (forScale - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (containerSize.height * (forScale - 1f) / 2f).coerceAtLeast(0f)
        return Offset(target.x.coerceIn(-maxX, maxX), target.y.coerceIn(-maxY, maxY))
    }

    LaunchedEffect(resetKey) {
        scale.snapTo(1f)
        offset.snapTo(Offset.Zero)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        scope.launch {
                            if (scale.value > 1.05f) {
                                launch { scale.animateTo(1f) }
                                launch { offset.animateTo(Offset.Zero) }
                            } else {
                                val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                val target = clampOffset((center - tap) * (DOUBLE_TAP_SCALE - 1f), DOUBLE_TAP_SCALE)
                                launch { scale.animateTo(DOUBLE_TAP_SCALE) }
                                launch { offset.animateTo(target) }
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectZoomGestures(
                    consumePan = { scale.value > 1.02f },
                ) { centroid, pan, zoom ->
                    val oldScale = scale.value
                    val newScale = (oldScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                    // Keep the point under the fingers fixed while zooming.
                    val focal = centroid - center
                    val newOffset = (offset.value - focal) * (newScale / oldScale) + focal + pan
                    scope.launch {
                        scale.snapTo(newScale)
                        offset.snapTo(clampOffset(newOffset, newScale))
                    }
                }
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.value.x
                    translationY = offset.value.y
                },
        )
    }
}

/**
 * Like detectTransformGestures, but only consumes single-finger pans when [consumePan] says so,
 * so a pager underneath can still swipe between pages while the image is not zoomed.
 */
private suspend fun PointerInputScope.detectZoomGestures(
    consumePan: () -> Boolean,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val multiTouch = event.changes.count { it.pressed } > 1

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()
                    if (zoomMotion > touchSlop || panMotion > touchSlop) pastTouchSlop = true
                }
                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(centroid, panChange, zoomChange)
                    }
                    if (multiTouch || consumePan()) {
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}
