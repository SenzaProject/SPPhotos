@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.senza.spphotos

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

private val CapBg = Color(0xFF0E0E10)
private val CapPanel = Color(0xFF1C1C1F)
private val CapCyan = Color(0xFF19E3F2)

/** Satu klip di timeline. [inMs]/[outMs] = potongan dari sumber, durasi di timeline = (out-in)/speed. */
data class VClip(
    val id: Long,
    val uri: Uri,
    val srcMs: Long,
    val inMs: Long,
    val outMs: Long,
    val speed: Float = 1f,
    val muted: Boolean = false
) {
    val lenMs: Long get() = ((outMs - inMs) / speed).toLong().coerceAtLeast(100L)
}

data class VAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val filter: Int = 0
)

private val VFILTERS = listOf("Original", "Mono", "Vivid", "Warm", "Cool", "Fade", "Noir")
private val VFILTER_COLORS = listOf(
    listOf(Color(0xFF8EA1B8), Color(0xFF5B6B80)),
    listOf(Color(0xFFBDBDBD), Color(0xFF424242)),
    listOf(Color(0xFFFF5E62), Color(0xFF3B82F6)),
    listOf(Color(0xFFFFB347), Color(0xFFFF6B6B)),
    listOf(Color(0xFF7FD3FF), Color(0xFF4C6EF5)),
    listOf(Color(0xFFD9D2E9), Color(0xFFB6C0CF)),
    listOf(Color(0xFF9E9E9E), Color(0xFF000000))
)

/** Daftar efek video (dipakai sama untuk pratinjau & ekspor agar hasilnya konsisten). */
private fun buildEffects(a: VAdjust, heightPx: Int? = null): List<Effect> {
    val e = ArrayList<Effect>()
    var br = a.brightness
    var ct = a.contrast
    var sat = a.saturation
    var wm = a.warmth
    when (a.filter) {
        2 -> { sat += 0.5f; ct += 0.1f }
        3 -> wm += 0.35f
        4 -> wm -= 0.35f
        5 -> { ct -= 0.15f; br += 0.06f }
        6 -> { sat = -1f; ct += 0.3f }
    }
    if (a.filter == 1) e.add(RgbFilter.createGrayscaleFilter())
    if (br != 0f) e.add(Brightness(br.coerceIn(-1f, 1f)))
    if (ct != 0f) e.add(Contrast(ct.coerceIn(-1f, 1f)))
    if (sat != 0f) e.add(HslAdjustment.Builder().adjustSaturation((sat * 100f).coerceIn(-100f, 100f)).build())
    if (wm != 0f) {
        e.add(
            RgbAdjustment.Builder()
                .setRedScale((1f + 0.25f * wm).coerceIn(0f, 2f))
                .setBlueScale((1f - 0.25f * wm).coerceIn(0f, 2f))
                .build()
        )
    }
    if (heightPx != null) e.add(Presentation.createForHeight(heightPx))
    return e
}

private fun clipMediaItem(c: VClip): MediaItem =
    MediaItem.Builder()
        .setUri(c.uri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(c.inMs)
                .setEndPositionMs(c.outMs)
                .build()
        )
        .build()

private fun buildComposition(clips: List<VClip>, adj: VAdjust, heightPx: Int): Composition {
    val items = clips.map { c ->
        val audio: List<AudioProcessor> =
            if (c.speed != 1f) listOf(SonicAudioProcessor().apply { setSpeed(c.speed); setPitch(1f) }) else emptyList()
        val video = ArrayList<Effect>()
        if (c.speed != 1f) video.add(SpeedChangeEffect(c.speed))
        video.addAll(buildEffects(adj, heightPx))
        EditedMediaItem.Builder(clipMediaItem(c))
            .setRemoveAudio(c.muted)
            .setEffects(Effects(audio, video))
            .build()
    }
    return Composition.Builder(listOf(EditedMediaItemSequence(items))).build()
}

private fun fmtT(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

private val thumbCache = android.util.LruCache<String, Bitmap>(160)

@Composable
private fun ClipThumbs(clip: VClip, wPx: Float, height: Dp) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val tilePx = with(density) { height.toPx() }
    val n = (wPx / tilePx).toInt().coerceAtLeast(1) + 1
    val frames by produceState<List<Bitmap?>>(List(n) { null }, clip.uri, clip.inMs, clip.outMs, n) {
        withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, clip.uri)
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 320
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 180
                val k = 240f / max(w, h)
                val dw = max(1, (w * k).toInt())
                val dh = max(1, (h * k).toInt())
                val cur = arrayOfNulls<Bitmap>(n)
                for (i in 0 until n) {
                    val tMs = clip.inMs + (((i + 0.5f) / n) * (clip.outMs - clip.inMs)).toLong()
                    val key = "${clip.uri}#${tMs / 400}"
                    var b = thumbCache.get(key)
                    if (b == null) {
                        b = r.getScaledFrameAtTime(tMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, dw, dh)
                        if (b != null) thumbCache.put(key, b)
                    }
                    cur[i] = b
                    value = cur.toList()
                }
            } catch (_: Throwable) {
            } finally {
                try { r.release() } catch (_: Throwable) {}
            }
        }
    }
    Row(Modifier.fillMaxSize()) {
        val tileDp = with(density) { (wPx / n).toDp() }
        frames.forEach { b ->
            Box(Modifier.width(tileDp).fillMaxHeight().background(Color(0xFF2A2A2E))) {
                if (b != null) Image(
                    b.asImageBitmap(), null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ClipBlock(
    clip: VClip,
    selected: Boolean,
    pxPerMs: Float,
    height: Dp,
    onSelect: () -> Unit,
    onTrimStart: () -> Unit,
    onTrim: (left: Boolean, dxMs: Float) -> Unit
) {
    val density = LocalDensity.current
    val wPx = clip.lenMs * pxPerMs
    Box(
        Modifier.width(with(density) { wPx.toDp() }).height(height)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onSelect)
    ) {
        ClipThumbs(clip, wPx, height)
        if (clip.speed != 1f || clip.muted) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(0.6f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (clip.speed != 1f) Text("${clip.speed}x", color = Color.White, fontSize = 9.sp)
                if (clip.muted) Icon(Icons.Rounded.VolumeOff, null, tint = Color.White, modifier = Modifier.size(11.dp))
            }
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.dp, Color.White, RoundedCornerShape(6.dp)))
            for (left in listOf(true, false)) {
                Box(
                    Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
                        .width(18.dp).fillMaxHeight()
                        .background(Color.White, RoundedCornerShape(if (left) 6.dp else 6.dp))
                        .pointerInput(clip.id, left) {
                            detectHorizontalDragGestures(
                                onDragStart = { onTrimStart() },
                                onHorizontalDrag = { change, dx ->
                                    change.consume()
                                    onTrim(left, dx / pxPerMs)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.width(2.dp).height(16.dp).background(Color(0xFF555555))) }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).alpha(if (enabled) 1f else 0.35f).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun SmallChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) CapCyan else Color(0xFF2C2C30))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (selected) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun PanelSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(86.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = -1f..1f, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = CapCyan)
        )
        Text("${(value * 100).roundToInt()}", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(34.dp))
    }
}

private suspend fun saveVideoToGallery(ctx: Context, file: File): Boolean = withContext(Dispatchers.IO) {
    try {
        val name = "SP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/SP Photos")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: return@withContext false
        ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        cv.clear()
        cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
        ctx.contentResolver.update(uri, cv, null, null)
        file.delete()
        true
    } catch (e: Throwable) {
        false
    }
}

@Composable
fun VideoEditorScreen(vm: GalleryVM, start: Media, onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val firstLen = start.durationMs.coerceAtLeast(500L)
    val clips = remember { mutableStateListOf(VClip(1L, start.uri, firstLen, 0L, firstLen)) }
    var nextId by remember { mutableLongStateOf(2L) }
    var selId by remember { mutableStateOf<Long?>(null) }
    var adj by remember { mutableStateOf(VAdjust()) }
    var panel by remember { mutableIntStateOf(0) } // 0 utama, 1 speed, 2 filter, 3 adjust
    var posMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var exportHeight by remember { mutableIntStateOf(720) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    val history = remember { mutableStateListOf<List<VClip>>() }
    val future = remember { mutableStateListOf<List<VClip>>() }

    val scroll = rememberScrollState()
    val pxPerMs = with(density) { 56.dp.toPx() } / 1000f
    var lastSet by remember { mutableIntStateOf(0) }
    val total = clips.sumOf { it.lenMs }

    val exo = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { exo.release() } }

    fun mapPos(t: Long): Pair<Int, Long> {
        var acc = 0L
        for ((i, c) in clips.withIndex()) {
            if (t < acc + c.lenMs || i == clips.lastIndex) {
                val off = ((t - acc).coerceIn(0L, c.lenMs) * c.speed).toLong()
                return i to off
            }
            acc += c.lenMs
        }
        return 0 to 0L
    }

    fun seekTimeline(t: Long) {
        if (clips.isEmpty()) return
        val (i, off) = mapPos(t)
        exo.seekTo(i, off)
    }

    fun snapshot() {
        history.add(clips.toList())
        future.clear()
        if (history.size > 60) history.removeAt(0)
    }

    fun undo() {
        if (history.isEmpty()) return
        future.add(clips.toList())
        val s = history.removeAt(history.lastIndex)
        clips.clear(); clips.addAll(s)
    }

    fun redo() {
        if (future.isEmpty()) return
        history.add(clips.toList())
        val s = future.removeAt(future.lastIndex)
        clips.clear(); clips.addAll(s)
    }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    fun split() {
        var acc = 0L
        for ((i, c) in clips.withIndex()) {
            if (posMs > acc + 150 && posMs < acc + c.lenMs - 150) {
                snapshot()
                val cut = c.inMs + ((posMs - acc) * c.speed).toLong()
                clips[i] = c.copy(outMs = cut)
                val b = c.copy(id = nextId++, inMs = cut)
                clips.add(i + 1, b)
                selId = b.id
                return
            }
            acc += c.lenMs
        }
        toast("Letakkan playhead di dalam klip untuk memotong")
    }

    fun selIndex(): Int = clips.indexOfFirst { it.id == selId }

    fun deleteSel() {
        val i = selIndex()
        if (i < 0) { toast("Pilih klip dulu"); return }
        snapshot(); clips.removeAt(i); selId = null
    }

    fun duplicateSel() {
        val i = selIndex()
        if (i < 0) { toast("Pilih klip dulu"); return }
        snapshot(); val c = clips[i]; clips.add(i + 1, c.copy(id = nextId++))
    }

    fun toggleMute() {
        val i = selIndex()
        if (i < 0) { toast("Pilih klip dulu"); return }
        snapshot(); clips[i] = clips[i].copy(muted = !clips[i].muted)
    }

    fun setSpeed(v: Float) {
        val i = selIndex()
        if (i < 0) return
        snapshot(); clips[i] = clips[i].copy(speed = v)
    }

    fun trim(id: Long, left: Boolean, dxTimelineMs: Float) {
        val i = clips.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = clips[i]
        val d = (dxTimelineMs * c.speed).toLong()
        val minLen = (200 * c.speed).toLong()
        clips[i] = if (left) c.copy(inMs = (c.inMs + d).coerceIn(0L, c.outMs - minLen))
        else c.copy(outMs = (c.outMs + d).coerceIn(c.inMs + minLen, c.srcMs))
    }

    // Susun ulang playlist pemutar saat klip berubah
    LaunchedEffect(clips.toList()) {
        if (clips.isEmpty()) {
            exo.clearMediaItems()
        } else {
            exo.setMediaItems(clips.map { clipMediaItem(it) }, false)
            exo.prepare()
            seekTimeline(posMs.coerceAtMost(total))
        }
    }
    LaunchedEffect(adj) { exo.setVideoEffects(buildEffects(adj)) }

    // Putar: playhead mengikuti pemutar
    LaunchedEffect(playing) {
        if (playing) exo.play() else exo.pause()
        while (playing && clips.isNotEmpty()) {
            val idx = exo.currentMediaItemIndex.coerceIn(0, clips.lastIndex)
            val c = clips[idx]
            if (exo.playbackParameters.speed != c.speed) exo.setPlaybackSpeed(c.speed)
            val vol = if (c.muted) 0f else 1f
            if (exo.volume != vol) exo.volume = vol
            var acc = 0L
            for (k in 0 until idx) acc += clips[k].lenMs
            posMs = (acc + (exo.currentPosition / c.speed).toLong()).coerceAtMost(total)
            lastSet = (posMs * pxPerMs).roundToInt()
            scroll.scrollTo(lastSet)
            if (exo.playbackState == Player.STATE_ENDED) playing = false
            delay(16)
        }
    }

    // Geser timeline oleh pengguna → seek
    LaunchedEffect(Unit) {
        snapshotFlow { scroll.value }.collect { v ->
            if (v != lastSet) {
                if (playing) playing = false
                posMs = (v / pxPerMs).toLong().coerceIn(0L, total)
                seekTimeline(posMs)
            }
        }
    }

    val sel = selIndex()

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(Modifier.fillMaxSize().background(CapBg).statusBarsPadding().navigationBarsPadding()) {
            // Bilah atas
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Close, "Tutup", Modifier.size(28.dp).clickable(onClick = onClose))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2C2C30))
                        .clickable { showExport = true }.padding(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("${exportHeight}P", fontSize = 13.sp) }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(CapCyan)
                        .clickable { if (clips.isEmpty()) toast("Timeline kosong") else showExport = true }
                        .padding(horizontal = 18.dp, vertical = 7.dp)
                ) { Text("Ekspor", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }

            // Pratinjau
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        PlayerView(c).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = exo
                        }
                    }
                )
                if (clips.isEmpty()) Text("Timeline kosong — tekan +", color = Color.White.copy(0.6f))
            }

            // Waktu + kontrol
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${fmtT(posMs)} / ${fmtT(total)}", fontSize = 12.sp, color = Color.White.copy(0.85f), modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable {
                        if (clips.isNotEmpty()) {
                            if (!playing && posMs >= total - 50) { posMs = 0; seekTimeline(0); lastSet = 0; scope.launch { scroll.scrollTo(0) } }
                            playing = !playing
                        }
                    },
                    contentAlignment = Alignment.Center
                ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar", Modifier.size(30.dp)) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    Icon(Icons.Rounded.Undo, "Undo", Modifier.size(24.dp).alpha(if (history.isEmpty()) 0.35f else 1f).clickable { undo() })
                    Spacer(Modifier.width(18.dp))
                    Icon(Icons.Rounded.Redo, "Redo", Modifier.size(24.dp).alpha(if (future.isEmpty()) 0.35f else 1f).clickable { redo() })
                }
            }

            // Timeline
            BoxWithConstraints(Modifier.fillMaxWidth().height(112.dp).background(CapBg)) {
                val half = maxWidth / 2
                Row(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                    Spacer(Modifier.width(half))
                    Column {
                        // penggaris waktu
                        Row(Modifier.height(18.dp)) {
                            val secDp = with(density) { (pxPerMs * 1000f).toDp() }
                            for (s in 0..(total / 1000)) {
                                Box(Modifier.width(secDp)) {
                                    Text(
                                        if (s % 2L == 0L) fmtT(s * 1000) else "·",
                                        color = Color.White.copy(0.45f), fontSize = 9.sp, maxLines = 1, softWrap = false
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            clips.forEach { c ->
                                ClipBlock(
                                    c, c.id == selId, pxPerMs, 58.dp,
                                    onSelect = { selId = if (selId == c.id) null else c.id },
                                    onTrimStart = { snapshot() },
                                    onTrim = { left, dx -> trim(c.id, left, dx) }
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                                    .clickable { showPicker = true },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.Add, "Tambah klip", tint = Color.Black, modifier = Modifier.size(28.dp)) }
                        }
                    }
                    Spacer(Modifier.width(half))
                }
                // playhead tengah
                Box(
                    Modifier.align(Alignment.Center).width(2.dp).fillMaxHeight().background(Color.White)
                )
            }

            // Bilah alat / panel
            Box(Modifier.fillMaxWidth().background(CapPanel)) {
                when (panel) {
                    1 -> Column(Modifier.padding(14.dp)) {
                        PanelHeader("Kecepatan") { panel = 0 }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)) { _, v ->
                                SmallChip("${v}x", sel >= 0 && clips[sel].speed == v) { setSpeed(v) }
                            }
                        }
                    }
                    2 -> Column(Modifier.padding(14.dp)) {
                        PanelHeader("Filter") { panel = 0 }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(VFILTERS) { i, n ->
                                Column(
                                    Modifier.clickable { adj = adj.copy(filter = i) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier.size(62.dp).clip(RoundedCornerShape(10.dp))
                                            .background(Brush.linearGradient(VFILTER_COLORS[i]))
                                            .border(if (adj.filter == i) 2.dp else 0.dp, if (adj.filter == i) CapCyan else Color.Transparent, RoundedCornerShape(10.dp))
                                    )
                                    Text(n, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                    3 -> Column(Modifier.padding(14.dp)) {
                        PanelHeader("Adjust") { panel = 0 }
                        PanelSlider("Kecerahan", adj.brightness) { adj = adj.copy(brightness = it) }
                        PanelSlider("Kontras", adj.contrast) { adj = adj.copy(contrast = it) }
                        PanelSlider("Saturasi", adj.saturation) { adj = adj.copy(saturation = it) }
                        PanelSlider("Kehangatan", adj.warmth) { adj = adj.copy(warmth = it) }
                    }
                    else -> Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
                        ToolButton(Icons.Rounded.ContentCut, "Pisah") { split() }
                        ToolButton(Icons.Rounded.Speed, "Kecepatan") { if (sel < 0) toast("Pilih klip dulu") else panel = 1 }
                        ToolButton(if (sel >= 0 && clips[sel].muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "Volume") { toggleMute() }
                        ToolButton(Icons.Rounded.AutoAwesome, "Filter") { panel = 2 }
                        ToolButton(Icons.Rounded.Tune, "Adjust") { panel = 3 }
                        ToolButton(Icons.Rounded.ContentCopy, "Duplikat") { duplicateSel() }
                        ToolButton(Icons.Rounded.Delete, "Hapus") { deleteSel() }
                        ToolButton(Icons.Rounded.TextFields, "Teks", enabled = false) { toast("Teks: segera hadir") }
                        ToolButton(Icons.Rounded.MusicNote, "Audio", enabled = false) { toast("Audio: segera hadir") }
                    }
                }
            }
        }
    }

    if (showPicker) {
        val videos = remember(vm.media) { vm.media.filter { it.isVideo } }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Tambah video") },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(videos, key = { it.key }) { m ->
                        PhotoTile(m, false, false, false, onClick = {
                            snapshot()
                            val d = m.durationMs.coerceAtLeast(500L)
                            clips.add(VClip(nextId++, m.uri, d, 0L, d))
                            showPicker = false
                        }, onLong = {})
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Batal") } }
        )
    }

    if (showExport) {
        AlertDialog(
            onDismissRequest = { if (!exporting) showExport = false },
            title = { Text(if (exporting) "Mengekspor…" else "Ekspor video") },
            text = {
                Column {
                    if (!exporting) {
                        Text("Resolusi", fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(480, 720, 1080).forEach { h -> SmallChip("${h}P", exportHeight == h) { exportHeight = h } }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Durasi ${fmtT(total)} · disimpan ke Movies/SP Photos", fontSize = 12.sp)
                    } else {
                        LinearProgressIndicator(progress = { exportProgress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${(exportProgress * 100).roundToInt()}%")
                    }
                }
            },
            confirmButton = {
                if (!exporting) TextButton(onClick = {
                    exporting = true
                    exportProgress = 0f
                    playing = false
                    scope.launch {
                        val out = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.mp4")
                        val composition = buildComposition(clips.toList(), adj, exportHeight)
                        var transformer: Transformer? = null
                        val error = suspendCancellableCoroutine<Throwable?> { cont ->
                            val t = Transformer.Builder(ctx)
                                .addListener(object : Transformer.Listener {
                                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                        if (cont.isActive) cont.resume(null)
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException
                                    ) {
                                        if (cont.isActive) cont.resume(exportException)
                                    }
                                })
                                .build()
                            transformer = t
                            cont.invokeOnCancellation { t.cancel() }
                            t.start(composition, out.absolutePath)
                            scope.launch {
                                val holder = ProgressHolder()
                                while (cont.isActive) {
                                    val st = t.getProgress(holder)
                                    if (st == Transformer.PROGRESS_STATE_AVAILABLE) exportProgress = holder.progress / 100f
                                    delay(200)
                                }
                            }
                        }
                        if (error == null && saveVideoToGallery(ctx, out)) {
                            toast("Video disimpan di Movies/SP Photos")
                            exporting = false; showExport = false
                            onSaved()
                        } else {
                            out.delete()
                            toast("Ekspor gagal: ${error?.message ?: "tidak bisa menyimpan"}")
                            exporting = false
                        }
                    }
                }) { Text("Ekspor") }
            },
            dismissButton = { if (!exporting) TextButton(onClick = { showExport = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun PanelHeader(title: String, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Check, "Selesai", tint = CapCyan, modifier = Modifier.size(26.dp).clickable(onClick = onDone))
    }
}
