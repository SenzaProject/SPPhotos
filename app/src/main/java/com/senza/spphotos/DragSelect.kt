package com.senza.spphotos

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pilih banyak item dengan menggeser jari di atas grid (seperti galeri bawaan HP).
 *
 * Cara pakai:
 *  - Tekan lama sebuah foto lalu geser → semua foto yang dilewati terpilih (juga bisa langsung
 *    dari mode Select).
 *  - Saat mode Select aktif, geser jari MENDATAR di atas foto → memilih; geser menegak → scroll biasa.
 *  - Mulai dari foto yang sudah terpilih → mode hapus pilihan (item yang dilewati dilepas).
 *  - Jari di dekat tepi atas/bawah → grid menggulir otomatis sambil terus memilih.
 *
 * @param indexOf  ubah item grid (lewat key-nya) jadi indeks di [items]; -1 jika bukan foto (header, dll).
 */
@Composable
fun Modifier.dragSelect(
    state: LazyGridState,
    items: List<Media>,
    selecting: Boolean,
    selected: Set<Long>,
    indexOf: (key: Any) -> Int,
    onEnterSelect: () -> Unit,
    onSelected: (Set<Long>) -> Unit
): Modifier {
    val itemsNow = rememberUpdatedState(items)
    val selectingNow = rememberUpdatedState(selecting)
    val selectedNow = rememberUpdatedState(selected)
    val indexOfNow = rememberUpdatedState(indexOf)
    val enterNow = rememberUpdatedState(onEnterSelect)
    val setNow = rememberUpdatedState(onSelected)
    val scope = rememberCoroutineScope()
    val vc = LocalViewConfiguration.current

    return this.pointerInput(state) {
        val slop = vc.touchSlop
        val longPressMs = vc.longPressTimeoutMillis
        val edge = 96f * density          // zona auto-scroll di tepi atas/bawah (px)
        val maxSpeed = 26f * density      // kecepatan auto-scroll maksimum (px / frame)

        fun hit(pos: Offset): LazyGridItemInfo? {
            val vis = state.layoutInfo.visibleItemsInfo
            val x = pos.x.toInt()
            val y = pos.y.toInt()
            // longgar 2 px supaya celah antar-tile tidak "kosong"
            return vis.firstOrNull {
                x >= it.offset.x - 2 && x <= it.offset.x + it.size.width + 2 &&
                    y >= it.offset.y - 2 && y <= it.offset.y + it.size.height + 2 &&
                    indexOfNow.value(it.key) >= 0
            }
        }

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startHit = hit(down.position)
            val startIdx = if (startHit != null) indexOfNow.value(startHit.key) else -1
            val wasSelecting = selectingNow.value
            val base = selectedNow.value

            var dragging = false
            var anchor = -1
            var addMode = true
            var lastPos = down.position
            var lastIdx = -1
            var autoJob: Job? = null
            var speed = 0f
            var dx = 0f
            var dy = 0f
            var decided = false      // sudah diputuskan: bukan drag-select (biarkan scroll/klik)
            var longPressChecked = false

            fun applyRange(cur: Int) {
                if (anchor < 0 || cur < 0) return
                val list = itemsNow.value
                if (list.isEmpty()) return
                val lo = max(0, min(anchor, cur))
                val hi = min(list.lastIndex, max(anchor, cur))
                val keys = HashSet<Long>(hi - lo + 1)
                for (i in lo..hi) keys.add(list[i].key)
                setNow.value(if (addMode) base + keys else base - keys)
            }

            fun updateAt(pos: Offset) {
                val h = hit(pos) ?: return
                val idx = indexOfNow.value(h.key)
                if (idx < 0 || idx == lastIdx) return
                lastIdx = idx
                applyRange(idx)
            }

            fun begin() {
                if (dragging || startIdx < 0) return
                val list = itemsNow.value
                if (startIdx > list.lastIndex) return
                dragging = true
                anchor = startIdx
                addMode = list[startIdx].key !in base
                if (!wasSelecting) enterNow.value()
                lastIdx = -1
                updateAt(lastPos)
                autoJob = scope.launch {
                    while (isActive) {
                        if (speed != 0f) {
                            state.scrollBy(speed)
                            updateAt(lastPos)
                        }
                        delay(16)
                    }
                }
            }

            try {
                while (true) {
                    val ev = if (!dragging && !decided && !longPressChecked) {
                        val remaining = longPressMs - (SystemClock.uptimeMillis() - down.uptimeMillis)
                        withTimeoutOrNull(max(1L, remaining)) { awaitPointerEvent(PointerEventPass.Initial) }
                    } else awaitPointerEvent(PointerEventPass.Initial)

                    if (ev == null) {
                        // jari ditahan lama tanpa bergerak → tekan lama; drag-select siap dimulai
                        longPressChecked = true
                        begin()
                        continue
                    }
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (ev.changes.size > 1 && !dragging) break   // dua jari (pinch) → bukan urusan kita
                    if (!ch.pressed) {
                        if (dragging) ch.consume()
                        break
                    }
                    lastPos = ch.position
                    val d = ch.position - ch.previousPosition
                    if (!dragging) {
                        dx += d.x; dy += d.y
                        if (!decided) {
                            val moved = max(abs(dx), abs(dy))
                            if (moved > slop) {
                                // mode Select + geser mendatar → drag-select; selain itu serahkan ke scroll
                                if (wasSelecting && abs(dx) > abs(dy) * 1.2f) {
                                    begin()
                                } else if (!longPressChecked) {
                                    decided = true
                                }
                            }
                        }
                        if (dragging) ch.consume()
                    } else {
                        ch.consume()
                        updateAt(ch.position)
                        val h = size.height.toFloat()
                        speed = when {
                            ch.position.y < edge -> -maxSpeed * ((edge - ch.position.y) / edge).coerceIn(0f, 1f)
                            ch.position.y > h - edge -> maxSpeed * ((ch.position.y - (h - edge)) / edge).coerceIn(0f, 1f)
                            else -> 0f
                        }
                    }
                }
            } finally {
                autoJob?.cancel()
            }
        }
    }
}
