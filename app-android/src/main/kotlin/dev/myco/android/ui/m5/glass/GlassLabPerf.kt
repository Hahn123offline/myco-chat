package dev.myco.android.ui.m5.glass

import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 帧计时（UI 线程 Choreographer）——用来给报告提供"这台模拟器上到底多慢"的数字。
 *
 * 注意：Choreographer 间隔包含 RenderThread 阻塞导致的掉帧，但不等于真机 GPU 帧时间；
 * 报告里必须标明"软件 GPU 下测得，真机未知"。
 */
class FrameMeter {
    var running by mutableStateOf(false)
        private set
    var frames by mutableStateOf(0)
        private set
    var avgMs by mutableStateOf(0f)
        private set
    var p95Ms by mutableStateOf(0f)
        private set
    var maxMs by mutableStateOf(0f)
        private set
    var jankPct by mutableStateOf(0f)
        private set
    var fps by mutableStateOf(0f)
        private set

    private val deltas = ArrayList<Long>(512)
    private var lastNs = 0L
    private val choreographer: Choreographer = Choreographer.getInstance()

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastNs == 0L) {
                lastNs = frameTimeNanos
            } else {
                deltas.add((frameTimeNanos - lastNs) / 1_000_000L)
                lastNs = frameTimeNanos
                if (deltas.size > 600) deltas.removeAt(0)
                if (deltas.size % 12 == 0) publish()
            }
            choreographer.postFrameCallback(this)
        }
    }

    fun start() {
        if (running) return
        deltas.clear()
        lastNs = 0L
        frames = 0
        avgMs = 0f
        p95Ms = 0f
        maxMs = 0f
        jankPct = 0f
        fps = 0f
        running = true
        choreographer.postFrameCallback(callback)
    }

    fun stop() {
        running = false
        choreographer.removeFrameCallback(callback)
        publish()
        Log.i(
            GLASSLAB_TAG,
            "PERF frames=${deltas.size} avg=${"%.1f".format(avgMs)}ms p95=${"%.1f".format(p95Ms)}ms " +
                "max=${"%.1f".format(maxMs)}ms jank=${"%.1f".format(jankPct)}% fps=${"%.1f".format(fps)}",
        )
    }

    private fun publish() {
        if (deltas.isEmpty()) return
        val sorted = deltas.sorted()
        val sum = deltas.sumOf { it.toLong() }
        frames = deltas.size
        avgMs = sum.toFloat() / deltas.size
        p95Ms = sorted[((sorted.size - 1) * 0.95f).toInt()].toFloat()
        maxMs = sorted.last().toFloat()
        val janky = deltas.count { it > 25 }
        jankPct = janky * 100f / deltas.size
        fps = if (avgMs > 0f) 1000f / avgMs else 0f
        Log.i(
            GLASSLAB_TAG,
            "PERF frames=$frames avg=${"%.1f".format(avgMs)}ms p95=${"%.1f".format(p95Ms)}ms " +
                "max=${"%.1f".format(maxMs)}ms jank=${"%.1f".format(jankPct)}%",
        )
    }

    /** 直接从 Choreographer 时钟量一次"当前 vsync 到下一次 vsync"的间隔，用于页面展示。 */
    fun snapshotNow(): String = "frames=$frames avg=${"%.1f".format(avgMs)}ms p95=${"%.1f".format(p95Ms)}ms " +
        "max=${"%.1f".format(maxMs)}ms jank=${"%.1f".format(jankPct)}% fps=${"%.1f".format(fps)}"

    fun uptimeMs(): Long = SystemClock.uptimeMillis()
}
