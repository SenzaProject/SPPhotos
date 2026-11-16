@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.senza.spphotos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Kerangka halaman penuh dengan tombol kembali (untuk sub-halaman Collections). */
@Composable
fun PageScaffold(onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        content()
        GlassIconButton(
            Icons.Rounded.ArrowBack, "Kembali", onBack,
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp), size = 42
        )
    }
}

@Composable
private fun RoundChevron(icon: ImageVector = Icons.Rounded.ChevronRight, rotation: Float = 0f) {
    Box(
        Modifier.size(36.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp).rotate(rotation)) }
}

@Composable
private fun CollectionRow(title: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.onBackground.copy(0.16f)))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = PhotosType.title2, modifier = Modifier.weight(1f))
            RoundChevron()
        }
    }
}

@Composable
private fun UtilityChip(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.7f))
        Spacer(Modifier.size(8.dp))
        Text(label, fontSize = 15.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text, style = PhotosType.subhead, color = PhotosTheme.colors.secondaryLabel,
        modifier = Modifier.padding(horizontal = Space.screenEdge, vertical = Space.sm)
    )
}

/** Baris untuk fitur yang butuh model/layanan tambahan: ditampilkan jujur sebagai "belum tersedia". */
@Composable
private fun UnavailableRow(icon: ImageVector, title: String, reason: String) {
    val c = PhotosTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = c.tertiaryLabel, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(Space.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = PhotosType.title3, color = c.secondaryLabel)
                Text(reason, style = PhotosType.footnote, color = c.tertiaryLabel)
            }
            Text("Belum tersedia", style = PhotosType.caption1, color = c.tertiaryLabel)
        }
    }
}

private class AlbumEntry(val title: String, val items: List<Media>, val custom: Boolean)

@Composable
fun CollectionsScreen(
    vm: GalleryVM,
    onAlbums: () -> Unit,
    onMemories: () -> Unit,
    onRecentDays: () -> Unit,
    onMediaTypes: () -> Unit,
    onOpenGrid: (String, (Media) -> Boolean) -> Unit,
    onTrash: () -> Unit,
    onSettings: () -> Unit,
    onOpenAlbum: (String, (Media) -> Boolean, String?) -> Unit = { t, f, _ -> onOpenGrid(t, f) },
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    var utilOpen by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    val today = remember { startOfDay(System.currentTimeMillis()) }
    val recentDays = remember(vm.media) {
        vm.media.groupBy { startOfDay(it.dateMs) }.entries.sortedByDescending { it.key }.take(8)
    }
    val memory = remember(vm.media) { buildMemories(vm.media).firstOrNull() }
    val albumCards = remember(vm.media, vm.albums) {
        val custom = vm.albums.keys.map { n ->
            val keys = vm.albums[n] ?: emptySet()
            AlbumEntry(n, vm.media.filter { it.key in keys }, true)
        }
        val device = vm.media.groupBy { it.bucket }.entries
            .sortedByDescending { it.value.size }.take(10).map { AlbumEntry(it.key, it.value, false) }
        custom + device
    }
    LazyColumn(Modifier.fillMaxSize().edgeBlur(0.dp, edgeBottomH()).backdropSource(), state = listState, contentPadding = PaddingValues(bottom = 150.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Collections", style = PhotosType.largeTitle, modifier = Modifier.weight(1f))
                Box {
                    GlassIconButton(Icons.Rounded.MoreHoriz, "Menu", { menu = true }, size = 48, blur = false)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
                        DropdownMenuItem(text = { Text("Recently Deleted") }, onClick = { menu = false; onTrash() })
                    }
                }
                GlassIconButton(Icons.Rounded.Settings, "Settings", onSettings, size = 48, blur = false)
            }
        }
        item { SectionHeader("Recent Days", actionText = "See All", onAction = onRecentDays) }
        item {
            if (recentDays.isEmpty()) EmptyHint(if (vm.loaded) "Belum ada foto atau video." else "Memuat…")
            else LazyRow(
                contentPadding = PaddingValues(horizontal = Space.screenEdge),
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                items(recentDays, key = { it.key }) { (day, list) ->
                    val title = when {
                        day >= today -> "Today"
                        day >= today - DAY_MS + 3_600_000L -> "Yesterday"
                        else -> formatDate(day, "EEE, d MMM")
                    }
                    CoverCard(
                        list.first(), title, "${list.size} item",
                        onClick = { val ids = list.map { it.key }.toSet(); onOpenGrid(title) { it.key in ids } },
                        modifier = Modifier.width(150.dp)
                    )
                }
            }
        }
        item { SectionHeader("Memories", actionText = "See All", onAction = onMemories) }
        item {
            val mem = memory
            if (mem == null) EmptyHint("Memories muncul saat satu bulan punya minimal 5 foto/video.")
            else Box(
                Modifier.fillMaxWidth().padding(horizontal = Space.screenEdge).height(200.dp)
                    .clip(RoundedCornerShape(Radius.cardLarge)).background(PhotosTheme.colors.fillSubtle)
                    .clickable(role = Role.Button, onClick = onMemories)
            ) {
                Thumb(mem.items[mem.items.size / 2], Modifier.fillMaxSize(), fixedPx = 640)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.55f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(Space.lg)) {
                    Text(mem.title, style = PhotosType.title2, color = Color.White)
                    Text(mem.subtitle, style = PhotosType.footnote, color = Color.White.copy(0.85f))
                }
            }
        }
        item { SectionHeader("Albums", actionText = "See All", onAction = onAlbums) }
        item {
            if (albumCards.isEmpty()) EmptyHint(if (vm.loaded) "Belum ada album." else "Memuat…")
            else LazyRow(
                contentPadding = PaddingValues(horizontal = Space.screenEdge),
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                items(albumCards, key = { (if (it.custom) "u" else "b") + it.title }) { e ->
                    CoverCard(
                        e.items.firstOrNull(), e.title, "${e.items.size}",
                        onClick = {
                            if (e.custom) onOpenAlbum(e.title, { m -> m.key in (vm.albums[e.title] ?: emptySet()) }, e.title)
                            else onOpenAlbum(e.title, { m -> m.bucket == e.title }, null)
                        },
                        modifier = Modifier.width(140.dp)
                    )
                }
            }
        }
        item { Spacer(Modifier.height(Space.lg)) }
        item { UnavailableRow(Icons.Rounded.Face, "People & Pets", "Butuh pengenalan wajah di perangkat.") }
        item { UnavailableRow(Icons.Rounded.Place, "Places", "Butuh data lokasi (GPS) pada foto.") }
        item { CollectionRow("Media Types", onMediaTypes) }
        item {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.onBackground.copy(0.16f)))
                Row(
                    Modifier.fillMaxWidth().clickable { utilOpen = !utilOpen }
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Utilities", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(0.5f), modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    RoundChevron(Icons.Rounded.KeyboardArrowDown, if (utilOpen) 0f else -90f)
                }
            }
        }
        if (utilOpen) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UtilityChip(Icons.Rounded.FavoriteBorder, "Favorites", Modifier.weight(1f)) {
                            onOpenGrid("Favorites") { it.key in vm.favs }
                        }
                        UtilityChip(Icons.Rounded.Videocam, "Videos", Modifier.weight(1f)) {
                            onOpenGrid("Videos") { it.isVideo }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UtilityChip(Icons.Rounded.Smartphone, "Screenshots", Modifier.weight(1f)) {
                            onOpenGrid("Screenshots") { it.isScreenshot }
                        }
                        UtilityChip(Icons.Rounded.Delete, "Recently Deleted", Modifier.weight(1f), onTrash)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentDaysScreen(
    vm: GalleryVM,
    onOpen: (String, (Media) -> Boolean) -> Unit,
    onPlay: (List<Media>) -> Unit
) {
    val today = remember { startOfDay(System.currentTimeMillis()) }
    val days = remember(vm.media) {
        vm.media.groupBy { startOfDay(it.dateMs) }.entries.sortedByDescending { it.key }.take(30)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 76.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Recent Days", style = PhotosType.largeTitle,
                modifier = Modifier.statusBarsPadding().padding(start = 6.dp, bottom = 4.dp)
            )
        }
        items(days, key = { it.key }) { (day, list) ->
            val title = when {
                day >= today -> "Today"
                day >= today - DAY_MS + 3_600_000L -> "Yesterday"
                else -> formatDate(day, "EEEE, d MMMM")
            }
            Box(
                Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(28.dp))
                    .background(PhotosTheme.colors.fillSubtle)
                    .clickable { val ids = list.map { it.key }.toSet(); onOpen(title) { it.key in ids } }
            ) {
                Thumb(list.first(), Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f)))
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${list.size} item", color = Color.White.copy(0.85f), fontSize = 13.sp)
                }
                GlassIconButton(
                    Icons.Rounded.PlayArrow, "Putar slideshow", { onPlay(list) },
                    Modifier.align(Alignment.BottomEnd).padding(14.dp), size = 46
                )
            }
        }
    }
}

@Composable
fun MediaTypesScreen(vm: GalleryVM, onOpen: (String, (Media) -> Boolean) -> Unit) {
    val types: List<Pair<String, (Media) -> Boolean>> = listOf(
        "Photos" to { m: Media -> !m.isVideo },
        "Videos" to { m: Media -> m.isVideo },
        "Screenshots" to { m: Media -> m.isScreenshot }
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 76.dp, bottom = 40.dp)) {
        item {
            Text(
                "Media Types", style = PhotosType.largeTitle,
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, bottom = 12.dp)
            )
        }
        items(types, key = { it.first }) { (name, pred) ->
            val count = vm.media.count(pred)
            Column(Modifier.fillMaxWidth().clickable { onOpen(name, pred) }) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.onBackground.copy(0.16f)))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("$count", color = MaterialTheme.colorScheme.onBackground.copy(0.6f), modifier = Modifier.padding(end = 12.dp))
                    RoundChevron()
                }
            }
        }
    }
}
