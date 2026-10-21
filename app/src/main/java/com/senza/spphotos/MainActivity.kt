@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.senza.spphotos

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.DataSource
import coil.decode.VideoFrameDecoder
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.memory.MemoryCache
import coil.request.Options
import coil.size.pxOrElse
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kunci cache memori untuk thumbnail Media. */
class MediaThumbKeyer : Keyer<Media> {
    override fun key(data: Media, options: Options): String = "thumb:${data.key}:${data.dateMs}:${data.size}"
}

/**
 * Thumbnail cepat lewat MediaStore.loadThumbnail (di-cache sistem) — jauh lebih ringan
 * daripada men-decode foto/video penuh, sehingga scroll jadi mulus dan hemat memori.
 */
class MediaThumbFetcher(
    private val ctx: Context,
    private val media: Media,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val w = options.size.width.pxOrElse { 320 }.coerceIn(96, 1024)
        val h = options.size.height.pxOrElse { 320 }.coerceIn(96, 1024)
        val bmp = withContext(Dispatchers.IO) {
            ctx.contentResolver.loadThumbnail(media.uri, android.util.Size(w, h), null)
        }
        return DrawableResult(BitmapDrawable(ctx.resources, bmp), true, DataSource.DISK)
    }

    class Factory(private val ctx: Context) : Fetcher.Factory<Media> {
        override fun create(data: Media, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbFetcher(ctx, data, options)
    }
}

class SPPhotosApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Simpan jejak crash ke file supaya bisa dilihat/disalin saat aplikasi dibuka lagi.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                java.io.File(filesDir, "last_crash.txt").writeText(
                    "SP Photos 1.3 · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Thread: ${t.name}\n\n" + sw.toString()
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(MediaThumbKeyer())
                add(MediaThumbFetcher.Factory(this@SPPhotosApp))
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.28).build() }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
}

@Composable
private fun CrashDialog() {
    val ctx = LocalContext.current
    val file = remember { java.io.File(ctx.filesDir, "last_crash.txt") }
    var text by remember { mutableStateOf(if (file.exists()) runCatching { file.readText() }.getOrNull() else null) }
    val t = text ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { file.delete(); text = null },
        title = { Text("Aplikasi sempat berhenti") },
        text = {
            Column {
                Text("Salin laporan ini dan kirim ke pengembang agar bug bisa diperbaiki.", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(t.take(1200), fontSize = 10.sp, lineHeight = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", t))
                android.widget.Toast.makeText(ctx, "Laporan disalin", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Salin") }
        },
        dismissButton = { TextButton(onClick = { file.delete(); text = null }) { Text("Tutup") } }
    )
}

class MainActivity : ComponentActivity() {
    private val vm: GalleryVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SPTheme(vm) {
                PermissionGate { SPPhotosRoot(vm) }
                CrashDialog()
            }
        }
    }
}

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val perms = remember {
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    fun has() = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var granted by remember { mutableStateOf(has()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = has()
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms) }

    if (granted) {
        content()
    } else {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(72.dp), tint = Accent)
            Spacer(Modifier.height(16.dp))
            Text("SP Photos", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Izinkan akses ke foto dan video agar galeri dapat ditampilkan. Semua data tetap di perangkat.",
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { launcher.launch(perms) }) { Text("Izinkan akses") }
            TextButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                )
            }) { Text("Buka pengaturan aplikasi") }
        }
    }
}
