package com.senza.spphotos

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Sistem desain Photos (Tahap 2).
 * Satu sumber untuk warna, tipografi, spacing, radius, dan motion.
 * Layar baru/yang diredesain WAJIB memakai token di sini, bukan angka/warna lepas.
 */

// ───────────────────────── Warna ─────────────────────────

/** Warna semantik. Dua instance: [PhotosDark] dan [PhotosLight]. */
@Immutable
class PhotosPalette(
    val dark: Boolean,
    val background: Color,
    val surface: Color,          // kartu / grup
    val surfaceRaised: Color,    // sheet, dialog, kontrol di atas surface
    val label: Color,            // teks utama
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fillSubtle: Color,       // placeholder thumbnail, chip tak aktif
    val glassSolid: Color,       // pengganti kaca saat Reduce Motion / perangkat ringan
    val accent: Color,
    val destructive: Color,
    val favorite: Color
)

/** Biru aksen Photos. Tetap satu nilai di terang & gelap agar konsisten dengan komponen lama. */
val Accent = Color(0xFF0A84FF)
val Destructive = Color(0xFFFF453A)
val FavoritePink = Color(0xFFFF375F)

val PhotosDark = PhotosPalette(
    dark = true,
    background = Color.Black,
    surface = Color(0xFF1C1C1E),
    surfaceRaised = Color(0xFF2C2C2E),
    label = Color.White,
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x54545458),
    fillSubtle = Color(0x33888888),
    glassSolid = Color(0xFF23232C),
    accent = Accent,
    destructive = Destructive,
    favorite = FavoritePink
)

val PhotosLight = PhotosPalette(
    dark = false,
    background = Color(0xFFF2F2F7),
    surface = Color.White,
    surfaceRaised = Color.White,
    label = Color(0xFF111118),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fillSubtle = Color(0x33888888),
    glassSolid = Color.White,
    accent = Accent,
    destructive = Destructive,
    favorite = FavoritePink
)

val LocalPalette = staticCompositionLocalOf { PhotosDark }

object PhotosTheme {
    val colors: PhotosPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

// ───────────────────────── Spacing & radius ─────────────────────────

object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val screenEdge = 16.dp
    /** Target sentuh minimum (aksesibilitas). */
    val touch = 48.dp
}

object Radius {
    val tile = 4.dp
    val card = 16.dp
    val cardLarge = 24.dp
    val sheet = 32.dp
    val pill = 50   // persen, untuk RoundedCornerShape(percent)
}

// ───────────────────────── Motion ─────────────────────────

object Motion {
    /** Pegas lembut untuk perpindahan layar/elemen besar. Reduce Motion → tween pendek tanpa pantulan. */
    fun <T> soft(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) tween(120)
        else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    /** Pegas cepat untuk umpan balik sentuh, pilihan, dan toggle. */
    fun <T> snappy(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) tween(90)
        else spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)

    /** Pegas membal untuk lensa kaca / jelly. Reduce Motion → tween. */
    fun <T> bouncy(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) tween(90)
        else spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)

    const val FADE_MS = 180
}

// ───────────────────────── Tipografi ─────────────────────────

/**
 * Skala tipe mengikuti pola iOS (Large Title … Caption). Font Apple (SF Pro) proprietari,
 * jadi dipakai font sans sistem (Roboto/Google Sans) dengan bobot & tracking yang mendekati.
 */
object PhotosType {
    private val ff = FontFamily.SansSerif

    val largeTitle = TextStyle(fontFamily = ff, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    val title1 = TextStyle(fontFamily = ff, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
    val title2 = TextStyle(fontFamily = ff, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
    val title3 = TextStyle(fontFamily = ff, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
    val headline = TextStyle(fontFamily = ff, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    val body = TextStyle(fontFamily = ff, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.4).sp)
    val callout = TextStyle(fontFamily = ff, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.3).sp)
    val subhead = TextStyle(fontFamily = ff, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp)
    val footnote = TextStyle(fontFamily = ff, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.1).sp)
    val caption1 = TextStyle(fontFamily = ff, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val caption2 = TextStyle(fontFamily = ff, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp)
}

/** Menyambungkan skala tipe Photos ke Material3 agar komponen bawaan ikut konsisten. */
fun photosTypography(): Typography = Typography(
    displayLarge = PhotosType.largeTitle,
    headlineLarge = PhotosType.title1,
    headlineMedium = PhotosType.title2,
    headlineSmall = PhotosType.title3,
    titleLarge = PhotosType.title2,
    titleMedium = PhotosType.headline,
    titleSmall = PhotosType.subhead.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = PhotosType.body,
    bodyMedium = PhotosType.callout,
    bodySmall = PhotosType.footnote,
    labelLarge = PhotosType.subhead.copy(fontWeight = FontWeight.Medium),
    labelMedium = PhotosType.caption1,
    labelSmall = PhotosType.caption2
)

// ───────────────────────── Aksesibilitas ─────────────────────────

/** Menjamin area sentuh ≥ 48dp tanpa mengubah tampilan visual. */
fun Modifier.minTouchTarget(): Modifier = this.defaultMinSize(Space.touch, Space.touch)

// ───────────────────────── Komponen reusable ─────────────────────────

/** Judul seksi ("Albums", "Recent Days") dengan aksi opsional di kanan ("See All"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val c = PhotosTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.screenEdge, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = PhotosType.title3, color = c.label)
        if (actionText != null && onAction != null) {
            Text(
                actionText, style = PhotosType.subhead, color = c.accent,
                modifier = Modifier.clip(RoundedCornerShape(Radius.pill))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = Space.sm, vertical = Space.xs)
            )
        }
    }
}

/** Tombol pil. [prominent] = isi aksen (aksi utama); selain itu isi halus. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    destructive: Boolean = false
) {
    val c = PhotosTheme.colors
    val bg = when {
        destructive -> c.destructive
        prominent -> c.accent
        else -> c.fillSubtle
    }
    val fg = if (prominent || destructive) Color.White else c.label
    Box(
        modifier.minTouchTarget()
            .clip(RoundedCornerShape(Radius.pill))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xl, vertical = Space.md),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = PhotosType.subhead.copy(fontWeight = FontWeight.SemiBold), color = fg)
    }
}

/** Grup bergaya iOS (daftar Settings, dll.): latar surface, sudut membulat. */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .padding(horizontal = Space.screenEdge)
            .clip(RoundedCornerShape(Radius.card))
            .background(PhotosTheme.colors.surface),
        content = content
    )
}

/** Keadaan kosong / error / izin: ikon, judul, penjelasan, dan satu aksi opsional. */
@Composable
fun StateView(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val c = PhotosTheme.colors
    Column(
        modifier.fillMaxWidth().padding(Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = c.tertiaryLabel)
        Spacer(Modifier.height(Space.md))
        Text(title, style = PhotosType.headline, color = c.label, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(Space.xs))
            Text(message, style = PhotosType.subhead, color = c.secondaryLabel, textAlign = TextAlign.Center)
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(Space.lg))
            PillButton(actionText, onAction, prominent = true)
        }
    }
}

/** Kartu sampul (album, hari, dsb.): gambar bersudut membulat + judul + keterangan di bawahnya. */
@Composable
fun CoverCard(
    cover: Media?,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspect: Float = 1f
) {
    val c = PhotosTheme.colors
    Column(modifier.clip(RoundedCornerShape(Radius.card)).clickable(role = Role.Button, onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(aspect)
                .clip(RoundedCornerShape(Radius.card)).background(c.fillSubtle),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) Thumb(cover, Modifier.fillMaxSize(), fixedPx = 480)
            else Icon(Icons.Rounded.Collections, null, tint = c.tertiaryLabel, modifier = Modifier.size(36.dp))
        }
        Text(
            title, style = PhotosType.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Space.xs)
        )
        if (subtitle != null) Text(subtitle, style = PhotosType.caption1, color = c.secondaryLabel, maxLines = 1)
    }
}
