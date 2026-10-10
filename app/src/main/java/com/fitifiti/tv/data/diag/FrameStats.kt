package com.fitifiti.tv.data.diag

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics

/**
 * Kare süresi ölçümü (akıcılık teşhisi): 3 saniyede bir logcat'e "FitiFrames" etiketiyle kare sayısı, takılan kare
 * (> 25 ms) sayısı ve p50/p90/en uzun süre yazar. Kutu ajanı `{"logcat":"FitiFrames"}` ile okunur. Yalnız ekranda
 * hareket varken yazar (boşta kare üretilmez).
 */
object FrameStats {
    private val durations = ArrayList<Long>(512)
    /** bileşen başına toplam (ms): girdi+animasyon, ölçü/yerleşim, çizim kaydı, senkron, GL komutu, GPU */
    private val parts = LongArray(6)
    /** ardışık karelerin vsync aralıkları (ms; 250 ms'den uzun boşluklar = boşta, sayılmaz) */
    private val gaps = ArrayList<Long>(512)
    private var lastVsync = 0L

    fun attach(a: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val t = HandlerThread("frames").apply { start() }
        val h = Handler(t.looper)
        a.window.addOnFrameMetricsAvailableListener({ _, m, _ ->
            val ns = m.getMetric(FrameMetrics.TOTAL_DURATION)
            synchronized(durations) {
                durations.add(ns / 1_000_000)
                val v = m.getMetric(FrameMetrics.VSYNC_TIMESTAMP)
                if (lastVsync != 0L) { val g = (v - lastVsync) / 1_000_000; if (g in 1..250) gaps.add(g) }
                lastVsync = v
                parts[0] += (m.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) + m.getMetric(FrameMetrics.ANIMATION_DURATION)) / 1_000
                parts[1] += m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) / 1_000
                parts[2] += m.getMetric(FrameMetrics.DRAW_DURATION) / 1_000
                parts[3] += m.getMetric(FrameMetrics.SYNC_DURATION) / 1_000
                parts[4] += m.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) / 1_000
                if (Build.VERSION.SDK_INT >= 31) parts[5] += m.getMetric(FrameMetrics.GPU_DURATION) / 1_000
            }
        }, h)
        h.postDelayed(object : Runnable {
            override fun run() {
                var avg = ""
                var gapInfo = ""
                val list = synchronized(durations) {
                    if (gaps.isNotEmpty()) {
                        val g = gaps.sorted()
                        gapInfo = "aralık p50=${g[g.size / 2]} p90=${g[(g.size * 9) / 10]} atlanan=${g.count { it > 20 }}/${g.size}"
                    }
                    gaps.clear()
                    val c = durations.toList()
                    if (c.isNotEmpty()) avg = parts.joinToString("/") { "%.1f".format(it / 1000.0 / c.size) }
                    durations.clear(); parts.fill(0); c
                }
                if (list.size >= 5) {
                    val s = list.sorted()
                    Log.i("FitiFrames", "kare=${s.size} takılan=${s.count { it > 25 }} p50=${s[s.size / 2]}ms p90=${s[(s.size * 9) / 10]}ms max=${s.last()}ms ort[girdi+anim/ölçü/çizim/senk/gl/gpu]=$avg $gapInfo ekran=${Diag.lastScreen}")
                }
                h.postDelayed(this, 3000)
            }
        }, 3000)
    }
}
