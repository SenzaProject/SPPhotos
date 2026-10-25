package com.senza.spphotos

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max
import kotlin.math.min

/**
 * Pra-muat thumbnail di depan arah scroll supaya tile sudah terisi sebelum masuk layar
 * (mengurangi kotak abu-abu / hitam saat scroll cepat). Berjalan di thread terpisah berprioritas rendah.
 */
@Composable
fun ThumbPrefetch(
    state: LazyGridState,
    items: List<Media>,
    indexOf: (Any) -> Int,
    ahead: Int = 48,
    behind: Int = 16
) {
    val ctx = LocalContext.current
    LaunchedEffect(state, items) {
        if (items.isEmpty()) return@LaunchedEffect
        val done = HashSet<Long>()
        val loader = ctx.imageLoader
        snapshotFlow {
            var first = Int.MAX_VALUE
            var last = -1
            for (v in state.layoutInfo.visibleItemsInfo) {
                val i = indexOf(v.key)
                if (i >= 0) { first = min(first, i); last = max(last, i) }
            }
            if (last < 0) -1 to -1 else first to last
        }.distinctUntilChanged().collectLatest { (a, b) ->
            if (b < 0) return@collectLatest
            delay(80) // tunggu scroll agak tenang
            val from = max(0, a - behind)
            val to = min(items.lastIndex, b + ahead)
            for (i in (b + 1)..to) enqueue(ctx, loader, items[i], done)
            for (i in (a - 1) downTo from) enqueue(ctx, loader, items[i], done)
        }
    }
}

private fun enqueue(
    ctx: android.content.Context,
    loader: coil.ImageLoader,
    m: Media,
    done: HashSet<Long>
) {
    if (!done.add(m.key)) return
    loader.enqueue(
        ImageRequest.Builder(ctx)
            .data(m)
            .size(TILE_THUMB_PX)
            .setParameter(PARAM_PREFETCH, true, memoryCacheKey = null)
            .build()
    )
}
