package com.senza.spphotos

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Blur progresif ala iOS (Android 13+): SATU shader menggeser radius blur menurut posisi y —
 * paling kabur di tepi atas/bawah layar, memudar jadi tajam ke arah isi. Diterapkan langsung ke
 * lapisan grid (tanpa menyalin/merekam konten), jadi pasti tampil dan selalu menyala saat scroll.
 * Di Android < 13 modifier ini tidak berbuat apa-apa (dipakai jalur lama di Backdrop.kt).
 */
private const val EDGE_AGSL = """
uniform shader content;
uniform float2 viewSize;
uniform float topH;
uniform float botH;
uniform float maxR;

half4 main(float2 c) {
    float r = 0.0;
    if (topH > 0.0 && c.y < topH) {
        float t = 1.0 - c.y / topH;
        r = maxR * t * t * (3.0 - 2.0 * t);
    }
    float fb = viewSize.y - c.y;
    if (botH > 0.0 && fb < botH) {
        float t = 1.0 - fb / botH;
        r = max(r, maxR * t * t * (3.0 - 2.0 * t));
    }
    if (r < 0.75) {
        return content.eval(c);
    }
    float rot = 6.2831853 * fract(52.9829189 * fract(dot(c, float2(0.06711056, 0.00583715))));
    half4 acc = half4(0.0);
    for (int i = 0; i < 36; i++) {
        float a = float(i) * 2.3999632 + rot;
        float d = sqrt((float(i) + 0.5) / 36.0);
        float2 p = c + float2(cos(a), sin(a)) * (d * r);
        p = clamp(p, float2(0.5, 0.5), viewSize - float2(0.5, 0.5));
        acc += content.eval(p);
    }
    return acc / 36.0;
}
"""

/** true bila blur progresif berbasis shader dipakai (Android 13+). */
val edgeBlurShaderSupported: Boolean get() = Build.VERSION.SDK_INT >= 33

@RequiresApi(33)
private fun edgeEffect(
    shader: Any, w: Float, h: Float, topH: Float, botH: Float, maxR: Float
): androidx.compose.ui.graphics.RenderEffect {
    val s = shader as RuntimeShader
    s.setFloatUniform("viewSize", w, h)
    s.setFloatUniform("topH", topH)
    s.setFloatUniform("botH", botH)
    s.setFloatUniform("maxR", maxR)
    return RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
}

/** Tinggi zona blur bawah (bilah navigasi mengambang + inset sistem). */
@Composable
fun edgeBottomH(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 126.dp

/**
 * Pasang pada kontainer yang MELIPUTI seluruh layar (grid + isinya). [topH]/[botH] = tinggi zona
 * blur dari tepi atas/bawah layar (0.dp = tanpa blur di sisi itu).
 */
@Composable
fun Modifier.edgeBlur(topH: Dp, botH: Dp, maxRadius: Dp = 24.dp): Modifier {
    if (!edgeBlurShaderSupported) return this
    if (LocalBackdrop.current == null) return this          // blur dimatikan / Reduce Motion
    val shader = remember { try { RuntimeShader(EDGE_AGSL) as Any } catch (e: Throwable) { null } }
        ?: return this
    val d = LocalDensity.current
    val topPx = with(d) { topH.toPx() }
    val botPx = with(d) { botH.toPx() }
    val rPx = with(d) { maxRadius.toPx() }
    return this.graphicsLayer {
        renderEffect = if (Build.VERSION.SDK_INT >= 33 && size.width > 0f && size.height > 0f)
            edgeEffect(shader, size.width, size.height, topPx, botPx, rPx)
        else null
    }
}
