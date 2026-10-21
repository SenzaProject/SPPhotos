package com.senza.spphotos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * SP Pro: watermark dihapus jika pengguna bergabung di GRUP dan CHANNEL Telegram.
 * Keduanya wajib. Keanggotaan diperiksa lewat Bot API (getChatMember); bot harus menjadi admin
 * di grup & channel. Jika salah satu ditinggalkan, Pro dicabut pada pemeriksaan berikutnya.
 */
object Pro {
    const val GROUP = "@senzaportdiscussion"
    const val CHANNEL = "@senzaproject"
    const val GROUP_URL = "https://t.me/senzaportdiscussion"
    const val CHANNEL_URL = "https://t.me/senzaproject"
    private const val RECHECK_MS = 6L * 3600_000L
    private const val GRACE_MS = 72L * 3600_000L

    val configured: Boolean
        get() = BuildConfig.TG_BOT_TOKEN.isNotBlank() && BuildConfig.TG_BOT_USERNAME.isNotBlank()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("sp_pro", Context.MODE_PRIVATE)

    fun userId(ctx: Context): Long = prefs(ctx).getLong("uid", 0L)

    fun revoked(ctx: Context): Boolean = prefs(ctx).getBoolean("revoked", false)

    /** Pro aktif jika terakhir terverifikasi tidak lebih dari masa tenggang (jika offline). */
    fun isPro(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (!p.getBoolean("pro", false)) return false
        return System.currentTimeMillis() - p.getLong("lastOk", 0L) <= GRACE_MS
    }

    fun code(ctx: Context): String {
        val p = prefs(ctx)
        var c = p.getString("code", null)
        if (c == null) {
            c = "sp" + UUID.randomUUID().toString().replace("-", "").take(10)
            p.edit().putString("code", c).apply()
        }
        return c
    }

    private suspend fun api(method: String, query: String = ""): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.telegram.org/bot${BuildConfig.TG_BOT_TOKEN}/$method" + if (query.isEmpty()) "" else "?$query")
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 10000
            val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
            JSONObject(stream.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            null
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** 1 = anggota, 0 = bukan anggota, -1 = tidak bisa diperiksa (jaringan / bot belum admin). */
    private suspend fun isMember(chat: String, uid: Long): Int {
        val r = api("getChatMember", "chat_id=${enc(chat)}&user_id=$uid") ?: return -1
        if (!r.optBoolean("ok")) return -1
        val res = r.optJSONObject("result") ?: return -1
        return when (res.optString("status")) {
            "creator", "administrator", "member" -> 1
            "restricted" -> if (res.optBoolean("is_member", false)) 1 else 0
            "left", "kicked" -> 0
            else -> -1
        }
    }

    /** Cari ID Telegram dari pesan "/start <kode>" yang dikirim pengguna ke bot. >0 ketemu, 0 belum, -1 galat. */
    private suspend fun fetchUserId(ctx: Context): Long {
        val r = api("getUpdates", "offset=-100&limit=100&allowed_updates=${enc("[\"message\"]")}") ?: return -1L
        if (!r.optBoolean("ok")) return -1L
        val arr = r.optJSONArray("result") ?: return 0L
        val want = code(ctx)
        for (i in arr.length() - 1 downTo 0) {
            val m = arr.optJSONObject(i)?.optJSONObject("message") ?: continue
            val text = m.optString("text")
            if (text.startsWith("/start") && text.endsWith(want)) {
                return m.optJSONObject("from")?.optLong("id", 0L) ?: 0L
            }
        }
        return 0L
    }

    class Result(val state: Int, val group: Boolean = false, val channel: Boolean = false) {
        companion object {
            const val CHECKED = 0
            const val NOT_CONFIGURED = 1
            const val NEED_START = 2
            const val NETWORK = 3
        }
    }

    suspend fun verify(ctx: Context): Result {
        if (!configured) return Result(Result.NOT_CONFIGURED)
        var uid = userId(ctx)
        if (uid == 0L) {
            uid = fetchUserId(ctx)
            if (uid < 0L) return Result(Result.NETWORK)
            if (uid == 0L) return Result(Result.NEED_START)
        }
        val g = isMember(GROUP, uid)
        val c = isMember(CHANNEL, uid)
        if (g < 0 || c < 0) return Result(Result.NETWORK)
        val p = prefs(ctx).edit().putLong("uid", uid).putLong("lastCheck", System.currentTimeMillis())
        if (g == 1 && c == 1) {
            p.putBoolean("pro", true).putBoolean("revoked", false).putLong("lastOk", System.currentTimeMillis())
        } else {
            p.putBoolean("pro", false)
        }
        p.apply()
        return Result(Result.CHECKED, g == 1, c == 1)
    }

    /** Dipanggil saat aplikasi dibuka / sebelum ekspor: cabut Pro bila keluar dari grup atau channel. */
    suspend fun revalidate(ctx: Context, force: Boolean = false) {
        val p = prefs(ctx)
        if (!configured || !p.getBoolean("pro", false)) return
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong("lastCheck", 0L) < RECHECK_MS) return
        val uid = p.getLong("uid", 0L)
        if (uid == 0L) return
        val g = isMember(GROUP, uid)
        val c = isMember(CHANNEL, uid)
        val e = p.edit().putLong("lastCheck", now)
        if (g == 1 && c == 1) e.putLong("lastOk", now)
        else if (g == 0 || c == 0) e.putBoolean("pro", false).putBoolean("revoked", true)
        e.apply()
    }

    fun open(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}

@Composable
fun ProDialog(vm: GalleryVM, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var g by remember { mutableStateOf<Boolean?>(null) }
    var c by remember { mutableStateOf<Boolean?>(null) }
    var hasUid by remember { mutableStateOf(Pro.userId(ctx) != 0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SP Pro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (vm.pro) "✓ SP Pro aktif — watermark SP dihapus dari hasil ekspor."
                    else if (Pro.revoked(ctx)) "Pro dicabut karena kamu tidak lagi bergabung di grup/channel."
                    else "Belum aktif — hasil ekspor diberi watermark SP.",
                    fontSize = 14.sp
                )
                Text(
                    "Syarat: bergabung di KEDUA tempat ini. Jika salah satu belum atau kamu keluar dari salah satunya, Pro tidak aktif / dicabut saat pemeriksaan berikutnya.",
                    fontSize = 12.sp
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { Pro.open(ctx, Pro.GROUP_URL) }) { Text("Buka Grup") }
                    Text(when (g) { true -> "✓ bergabung"; false -> "✗ belum"; null -> "" }, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { Pro.open(ctx, Pro.CHANNEL_URL) }) { Text("Buka Channel") }
                    Text(when (c) { true -> "✓ bergabung"; false -> "✗ belum"; null -> "" }, fontSize = 12.sp)
                }
                if (!Pro.configured) {
                    Text("Verifikasi belum dikonfigurasi pengembang (bot Telegram belum dipasang di build ini).", fontSize = 12.sp)
                } else if (!hasUid) {
                    TextButton(onClick = {
                        Pro.open(ctx, "https://t.me/${BuildConfig.TG_BOT_USERNAME}?start=${Pro.code(ctx)}")
                    }) { Text("Buka bot & tekan Start") }
                }
                if (busy) CircularProgressIndicator(Modifier.height(24.dp))
                msg?.let { Text(it, fontSize = 13.sp) }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Privasi: aplikasi menghubungi Telegram hanya untuk memeriksa keanggotaan. ID Telegram-mu disimpan di perangkat ini.",
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    msg = null
                    val r = Pro.verify(ctx)
                    vm.syncPro()
                    hasUid = Pro.userId(ctx) != 0L
                    when (r.state) {
                        Pro.Result.NOT_CONFIGURED -> msg = "Bot verifikasi belum dikonfigurasi."
                        Pro.Result.NEED_START -> msg = "Belum menemukan Start. Gabung grup & channel, tekan \"Buka bot & tekan Start\", lalu coba lagi."
                        Pro.Result.NETWORK -> msg = "Tidak bisa memeriksa (jaringan bermasalah atau bot belum menjadi admin). Coba lagi."
                        else -> {
                            g = r.group; c = r.channel
                            msg = if (r.group && r.channel) "Berhasil! SP Pro aktif." else "Belum lengkap: gabung ke grup dan channel dulu, lalu periksa lagi."
                        }
                    }
                    busy = false
                }
            }) { Text(if (vm.pro) "Periksa ulang" else "Verifikasi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}
