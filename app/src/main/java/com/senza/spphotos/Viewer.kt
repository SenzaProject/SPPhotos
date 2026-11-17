@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.senza.spphotos

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.LayoutInflater
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage

private fun openExternally(ctx: Context, m: Media) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, m.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    } catch (_: Exception) {
    }
}

/**
 * Halaman foto: pinch-zoom, double-tap zoom ke titik ketuk (beranimasi), geser vertikal ke bawah
 * untuk menutup (saat tidak di-zoom), dan state error jika gambar tak bisa didekode.
 */
@Composable
private fun ImagePage(
    m: Media,
    reduce: Boolean,
    onTap: () -> Unit,
    onZoom: (Boolean) -> Unit,
    onDragProgress: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var failed by remember(m.key) { mutableStateOf(false) }
    // Dekode dibatasi 2560 px sisi terpanjang: cukup tajam saat di-zoom tanpa memboroskan memori pada foto raksasa.
    val req = remember(m.key, m.dateMs) {
        coil.request.ImageRequest.Builder(ctx).data(m.uri).size(2560, 2560).crossfade(false).build()
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        val from = scale
                        val o0 = offset
                        val target = if (from > 1f) 1f else 2.5f
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val raw = if (target > 1f) (tap - c) * (1f - target) else Offset.Zero
                        val mx = size.width * (target - 1f) / 2f
                        val my = size.height * (target - 1f) / 2f
                        val o1 = Offset(raw.x.coerceIn(-mx, mx), raw.y.coerceIn(-my, my))
                        scope.launch {
                            animate(0f, 1f, animationSpec = Motion.snappy<Float>(reduce)) { t, _ ->
                                scale = from + (target - from) * t
                                offset = Offset(o0.x + (o1.x - o0.x) * t, o0.y + (o1.y - o0.y) * t)
                            }
                            onZoom(target > 1f)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var acc = Offset.Zero
                    var dragging = false
                    do {
                        val ev = awaitPointerEvent()
                        if (ev.changes.size > 1 || scale > 1f) {
                            val z = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            scale = (scale * z).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                val mx = size.width * (scale - 1f) / 2f
                                val my = size.height * (scale - 1f) / 2f
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-mx, mx),
                                    (offset.y + pan.y).coerceIn(-my, my)
                                )
                            } else offset = Offset.Zero
                            onZoom(scale > 1f)
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else {
                            // Tidak di-zoom, satu jari: geser ke bawah = tutup (iOS). Gerak horizontal dibiarkan untuk pager.
                            val pan = ev.calculatePan()
                            acc += pan
                            if (!dragging && acc.y > viewConfiguration.touchSlop && acc.y > kotlin.math.abs(acc.x) * 1.5f) dragging = true
                            if (dragging) {
                                dragY = (dragY + pan.y).coerceAtLeast(0f)
                                onDragProgress((dragY / size.height).coerceIn(0f, 1f))
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (dragging) {
                        if (dragY > size.height * 0.18f) onDismiss()
                        else { dragY = 0f; onDragProgress(0f) }
                    }
                }
            }
    ) {
        if (failed) {
            StateView(
                Icons.Rounded.Info, "Gambar tidak bisa ditampilkan",
                "Format tidak didukung atau file rusak.",
                Modifier.align(Alignment.Center),
                actionText = "Buka dengan aplikasi lain", onAction = { openExternally(ctx, m) }
            )
        } else {
            val shrink = 1f - (dragY / 1500f).coerceIn(0f, 0.25f)
            AsyncImage(
                model = req, contentDescription = m.name, contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale * shrink, scaleY = scale * shrink,
                    translationX = offset.x, translationY = offset.y + dragY
                )
            )
        }
    }
}

@Composable
private fun VideoPage(m: Media, current: Boolean, onTap: () -> Unit) {
    val ctx = LocalContext.current
    var playing by remember(m.key) { mutableStateOf(false) }
    var error by remember(m.key) { mutableStateOf<String?>(null) }
    LaunchedEffect(current) { if (!current) playing = false }
    Box(Modifier.fillMaxSize()) {
        if (!playing) {
            AsyncImage(
                model = m, contentDescription = m.name, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clickable { onTap() }
            )
            Box(Modifier.align(Alignment.Center)) {
                GlassIconButton(Icons.Rounded.PlayArrow, "Putar video", { error = null; playing = true }, size = 72)
            }
        } else {
            // ExoPlayer + decoder cadangan: jika decoder hardware gagal (mis. 1080p60 / 4K60 / HEVC 10-bit),
            // otomatis mencoba decoder lain (termasuk software) sehingga layar tidak berhenti hitam.
            val exo = remember(m.key) {
                val rf = DefaultRenderersFactory(ctx)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                ExoPlayer.Builder(ctx, rf).build()
            }
            DisposableEffect(exo) {
                val l = object : Player.Listener {
                    override fun onPlayerError(e: PlaybackException) {
                        error = e.errorCodeName
                    }
                }
                exo.addListener(l)
                exo.setMediaItem(MediaItem.fromUri(m.uri))
                exo.prepare()
                exo.playWhenReady = true
                onDispose {
                    exo.removeListener(l)
                    exo.release()
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    (LayoutInflater.from(c).inflate(R.layout.player_view_texture, null) as PlayerView).also { it.player = exo }
                },
                update = { it.player = exo }
            )
            if (error != null) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Video ini tidak bisa diputar oleh pemutar bawaan di perangkat ini (${error}).",
                        color = Color.White, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Glass(Modifier, RoundedCornerShape(50)) {
                        Box(
                            Modifier.clickable {
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, m.mime)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    )
                                } catch (_: Exception) {
                                }
                            }.padding(horizontal = 22.dp, vertical = 12.dp)
                        ) { Text("Buka dengan aplikasi lain", color = Color.White, fontSize = 14.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, style = PhotosType.footnote, modifier = Modifier.weight(0.35f), color = LocalContentColor.current.copy(0.65f))
        Text(v, style = PhotosType.footnote, modifier = Modifier.weight(0.65f))
    }
}

@Composable
fun ViewerScreen(
    vm: GalleryVM,
    items: List<Media>,
    start: Int,
    onClose: () -> Unit,
    onEdit: (Media) -> Unit,
    onDelete: (Media) -> Unit,
    onShare: (Media) -> Unit
) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, items.lastIndex)) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val cur = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    val fav = cur.key in vm.favs
    LaunchedEffect(pager.currentPage) { zoomed = false; dragProgress = 0f }

    // Info terbuka: tombol kembali menutup panel info dulu, bukan seluruh viewer.
    BackHandler(enabled = info) { info = false }

    // Imersif: bilah sistem ikut disembunyikan saat kontrol disembunyikan; ikonnya terang di atas hitam.
    val view = LocalView.current
    val controller = remember(view) { view.context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) } }
    DisposableEffect(controller) {
        val prevStatus = controller?.isAppearanceLightStatusBars ?: false
        val prevNav = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            controller?.isAppearanceLightStatusBars = prevStatus
            controller?.isAppearanceLightNavigationBars = prevNav
        }
    }
    LaunchedEffect(chrome, controller) {
        if (chrome) controller?.show(WindowInsetsCompat.Type.systemBars())
        else {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    val showChrome = chrome && dragProgress < 0.02f
    val fade = tween<Float>(if (vm.reduceEffective) 0 else Motion.FADE_MS)

    CompositionLocalProvider(
        LocalPalette provides PhotosDark,
        LocalGlass provides GlassStyle(vm.glass, vm.reduceEffective, true, vm.blurOn, vm.refractOn, LocalGlass.current.lite),
        LocalContentColor provides Color.White
    ) {
        // Latar memudar saat foto digeser turun, memperlihatkan layar di baliknya (seperti iOS).
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (1f - dragProgress * 1.4f).coerceIn(0f, 1f)))) {
            HorizontalPager(
                state = pager, modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !zoomed, key = { items[it].key }
            ) { page ->
                val m = items[page]
                if (m.isVideo) VideoPage(m, page == pager.currentPage) { chrome = !chrome }
                else ImagePage(
                    m, vm.reduceEffective,
                    onTap = { chrome = !chrome },
                    onZoom = { if (page == pager.currentPage) zoomed = it },
                    onDragProgress = { if (page == pager.currentPage) dragProgress = it },
                    onDismiss = onClose
                )
            }

            AnimatedVisibility(showChrome, Modifier.align(Alignment.TopCenter), enter = fadeIn(fade), exit = fadeOut(fade)) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassIconButton(Icons.Rounded.ArrowBack, "Kembali", onClose, size = 42)
                    Spacer(Modifier.weight(1f))
                    Glass(Modifier, RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatDate(cur.dateMs, "d MMMM yyyy"), style = PhotosType.footnote.copy(fontWeight = FontWeight.SemiBold))
                            Text(formatDate(cur.dateMs, "HH:mm"), style = PhotosType.caption2)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    GlassIconButton(Icons.Rounded.Info, "Info", { info = !info }, size = 42)
                }
            }

            AnimatedVisibility(info && dragProgress < 0.02f, Modifier.align(Alignment.BottomCenter), enter = fadeIn(fade), exit = fadeOut(fade)) {
                Glass(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    RoundedCornerShape(28.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Info", style = PhotosType.headline)
                        Spacer(Modifier.height(8.dp))
                        InfoRow("Nama", cur.name)
                        InfoRow("Tanggal", formatDate(cur.dateMs, "EEEE, d MMMM yyyy · HH:mm"))
                        if (cur.width > 0) {
                            val mp = cur.width.toLong() * cur.height / 1_000_000.0
                            InfoRow("Dimensi", "${cur.width} × ${cur.height}  (${"%.1f".format(mp)} MP)")
                        }
                        InfoRow("Ukuran", formatBytes(cur.size))
                        InfoRow("Jenis", cur.mime)
                        if (cur.isVideo) InfoRow("Durasi", formatDuration(cur.durationMs))
                        InfoRow("Album", cur.bucket)
                        InfoRow("Posisi", "${pager.currentPage + 1} dari ${items.size}")
                    }
                }
            }

            AnimatedVisibility(showChrome, Modifier.align(Alignment.BottomCenter), enter = fadeIn(fade), exit = fadeOut(fade)) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Glass(Modifier.fillMaxWidth().height(64.dp), RoundedCornerShape(32.dp)) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            BarIcon(Icons.Rounded.Share, "Bagikan") { onShare(cur) }
                            BarIcon(
                                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                if (fav) "Hapus dari favorit" else "Tambah ke favorit",
                                tint = if (fav) FavoritePink else Color.White
                            ) { vm.toggleFav(cur) }
                            BarIcon(Icons.Rounded.Tune, "Edit") { onEdit(cur) }
                            BarIcon(Icons.Rounded.Delete, "Hapus", tint = Destructive) { onDelete(cur) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String,
    tint: Color = Color.White, onClick: () -> Unit
) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(26.dp)) }
}
