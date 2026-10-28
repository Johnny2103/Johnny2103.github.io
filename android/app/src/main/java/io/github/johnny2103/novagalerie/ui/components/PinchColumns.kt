package io.github.johnny2103.novagalerie.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Two-finger pinch on a grid changes the column count: zoom in → fewer, bigger tiles;
 * zoom out → more columns. Single-finger scrolling is left untouched.
 */
fun Modifier.pinchToChangeColumns(onChange: (delta: Int) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var accumulated = 1f
            var fired = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    accumulated *= event.calculateZoom()
                    event.changes.forEach { it.consume() }
                    if (!fired) {
                        if (accumulated > 1.35f) {
                            fired = true
                            onChange(-1)
                        } else if (accumulated < 0.72f) {
                            fired = true
                            onChange(+1)
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
