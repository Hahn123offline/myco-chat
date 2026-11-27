package dev.myco.android.ui.m5.glass

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.view.WindowManager

/**
 * 设备 / 渲染器事实。报告里"能渲染"与"真机性能未知"必须分开写，
 * 所以这里把软件 GPU（swiftshader）也当成一级事实暴露到页面上。
 */
data class GlassLabDevice(
    val api: Int,
    val model: String,
    val avdName: String,
    val hardwareEgl: String,
    val fingerprint: String,
    val glRenderer: String,
    val glVendor: String,
    val glVersion: String,
    val glEsVersion: String,
    val crossWindowBlur: Boolean,
    val isProbablySoftwareGpu: Boolean,
) {
    val gpuMode: String get() = if (isProbablySoftwareGpu) "swiftshader(软件)" else "host(硬件)"

    val supportsRenderEffect: Boolean get() = api >= 31
    val supportsAgs: Boolean get() = api >= 33

    /** 顶部说明条用的一行摘要 */
    fun oneLine(): String =
        "API $api · " + glRenderer + " · GLES " + glEsVersion +
            (if (isProbablySoftwareGpu) " · 软件 GPU" else "") +
            " · 跨窗口模糊=" + (if (crossWindowBlur) "开" else "关/不支持")
}

/** 用一次 EGL pbuffer 上下文查 GL_RENDERER —— 这是判断"软件渲染"最硬的证据。 */
fun queryGlassLabDevice(context: Context): GlassLabDevice {
    var renderer = "查询失败"
    var vendor = "查询失败"
    var version = "查询失败"
    try {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        if (display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, ver, 0, ver, 1)) {
            val cfgAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            if (EGL14.eglChooseConfig(display, cfgAttribs, 0, configs, 0, 1, num, 0) && num[0] > 0) {
                val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                val eglCtx = EGL14.eglCreateContext(
                    display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0,
                )
                val surfAttribs = intArrayOf(
                    EGL14.EGL_WIDTH, 2, EGL14.EGL_HEIGHT, 2, EGL14.EGL_NONE,
                )
                val surf = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttribs, 0)
                if (eglCtx != EGL14.EGL_NO_CONTEXT && surf != EGL14.EGL_NO_SURFACE &&
                    EGL14.eglMakeCurrent(display, surf, surf, eglCtx)
                ) {
                    renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "null"
                    vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "null"
                    version = GLES20.glGetString(GLES20.GL_VERSION) ?: "null"
                    EGL14.eglMakeCurrent(
                        display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
                    )
                }
                if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surf)
                if (eglCtx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglCtx)
            }
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
    } catch (t: Throwable) {
        renderer = "异常：" + (t.message ?: t::class.simpleName)
    }

    val glEs = try {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo.glEsVersion
    } catch (t: Throwable) {
        "?"
    }

    val crossBlur = if (Build.VERSION.SDK_INT >= 31) {
        try {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).isCrossWindowBlurEnabled
        } catch (t: Throwable) {
            false
        }
    } else {
        false
    }

    val isSw = renderer.contains("swiftshader", ignoreCase = true) ||
        renderer.contains("software", ignoreCase = true) ||
        renderer.contains("llvmpipe", ignoreCase = true)

    return GlassLabDevice(
        api = Build.VERSION.SDK_INT,
        model = Build.MODEL + " / " + Build.DEVICE,
        avdName = readProp("ro.boot.qemu.avd_name").ifBlank { "?" },
        hardwareEgl = readProp("ro.hardware.egl").ifBlank { "?" },
        fingerprint = Build.FINGERPRINT,
        glRenderer = renderer,
        glVendor = vendor,
        glVersion = version,
        glEsVersion = glEs,
        crossWindowBlur = crossBlur,
        isProbablySoftwareGpu = isSw,
    )
}

fun readProp(name: String): String = try {
    ProcessBuilder("getprop", name).redirectErrorStream(true).start().inputStream
        .bufferedReader().readText().trim()
} catch (t: Throwable) {
    ""
}

internal const val GLASSLAB_TAG = "GLASSLAB"
