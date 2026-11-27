package dev.myco.android.ui.m5

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.myco.android.ui.m5.glass.FrameMeter
import dev.myco.android.ui.m5.glass.GLASSLAB_TAG
import dev.myco.android.ui.m5.glass.GlassLabArgs
import dev.myco.android.ui.m5.glass.GlassLabDiag
import dev.myco.android.ui.m5.glass.GlassLabDevice
import dev.myco.android.ui.m5.glass.GlassLabOverview
import dev.myco.android.ui.m5.glass.GlassLabScenes
import dev.myco.android.ui.m5.glass.queryGlassLabDevice

/**
 * Glass Lab —— 液态玻璃 / 真实模糊实验页（m5_glass=true 进入）。
 *
 * 页面结构：
 *   ┌ 固定说明条：当前设备 API / GLES 渲染器 / 是否软件 GPU / 跨窗口模糊 / AGSL 状态
 *   └ 场景区：overview（交互卡片）或某个对照场景（由 glass_scene 指定）
 *
 * 所有实验都是"真 backdrop"：rememberGraphicsLayer 捕获背景内容，再在玻璃区域 drawLayer +
 * RenderEffect 施加模糊，而不是用半透明色近似。
 */
@Composable
fun M5GlassLab() {
    val context = LocalContext.current
    val args = remember { GlassLabArgs.from((context as? Activity)?.intent) }
    val device = remember { queryGlassLabDevice(context) }
    val meter = remember { FrameMeter() }
    val density = LocalDensity.current

    var scroll by remember { mutableStateOf(args.scrollPx) }

    // 软件 GPU 下持续出帧的滚动（性能场景/动画场景需要）
    LaunchedEffect(args.autoScroll) {
        if (!args.autoScroll) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last == 0L) last = t
                val dt = (t - last) / 1_000_000f
                scroll += dt * 0.16f
                if (scroll > 5400f) scroll = 0f
                last = t
            }
        }
    }

    LaunchedEffect(Unit) {
        Log.i(GLASSLAB_TAG, "DEVICE api=${device.api} model=${device.model}")
        Log.i(GLASSLAB_TAG, "DEVICE glRenderer=${device.glRenderer}")
        Log.i(GLASSLAB_TAG, "DEVICE glVendor=${device.glVendor} glVersion=${device.glVersion} glEs=${device.glEsVersion}")
        Log.i(
            GLASSLAB_TAG,
            "DEVICE softwareGpu=${device.isProbablySoftwareGpu} renderEffect=${device.supportsRenderEffect} " +
                "agsl=${device.supportsAgs} crossWindowBlur=${device.crossWindowBlur}",
        )
        Log.i(GLASSLAB_TAG, "SCENE=${args.scene} scroll=${args.scrollPx} blur=${args.blurDp} density=${density.density}")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05050A))
            .statusBarsPadding(),
    ) {
        GlassLabHeader(device = device, args = args, meter = meter)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (args.scene == "overview") {
                GlassLabOverview(scrollPx = scroll, device = device, meter = meter)
            } else {
                GlassLabScenes(args = args, scrollPx = scroll, meter = meter, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun GlassLabHeader(device: GlassLabDevice, args: GlassLabArgs, meter: FrameMeter) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0B14))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row {
            Text(
                "Glass Lab",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "scene=" + args.scene,
                color = Color(0xFF7DF0FF),
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF14202E))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            if (meter.running) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "测帧中",
                    color = Color(0xFFFFD45E),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2A2210))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "设备：API ${device.api} · " + device.model,
            color = Color(0xFFAFC0D8),
            fontSize = 9.sp,
        )
        Text(
            "渲染器：" + device.glRenderer + " · GLES " + device.glEsVersion +
                (if (device.isProbablySoftwareGpu) " · ⚠ 软件 GPU（能渲染≠真机流畅）" else ""),
            color = if (device.isProbablySoftwareGpu) Color(0xFFFFC777) else Color(0xFFAFC0D8),
            fontSize = 9.sp,
        )
        Text(
            "RenderEffect=" + (if (device.supportsRenderEffect) "可用(API31+)" else "不可用") +
                " · AGSL=" + (if (device.supportsAgs) "可用(API33+)" else "不可用") +
                " · 跨窗口模糊=" + (if (device.crossWindowBlur) "系统开启" else "关闭/不支持") +
                " · " + GlassLabDiag.agslStatus,
            color = Color(0xFFAFC0D8),
            fontSize = 9.sp,
        )
    }
}
