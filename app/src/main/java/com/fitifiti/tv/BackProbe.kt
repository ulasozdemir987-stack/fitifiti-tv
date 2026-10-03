package com.fitifiti.tv

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.KeyEvent
import com.fitifiti.tv.data.diag.Diag

/**
 * Geri tuşu ölçümü: "3-4 kez basmam gerekiyor" şikâyeti için. Tuşa basıldığı an → geri işlendiği an → sonraki kare.
 * İşlenmeyen basış ya da 700 ms'den uzun gecikme olursa olay halkasıyla birlikte rapor gönderilir (oturumda en fazla 3).
 */
object BackProbe {
    private var downAt = 0L
    private var handledAt = 0L
    private var reports = 0
    private val main = Handler(Looper.getMainLooper())

    fun key(e: KeyEvent) {
        when {
            e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 -> { downAt = SystemClock.uptimeMillis(); Diag.log("geri ↓") }
            e.action == KeyEvent.ACTION_UP -> {
                val d = downAt
                Diag.log("geri ↑ (${SystemClock.uptimeMillis() - d} ms basılı)")
                main.postDelayed({ if (handledAt < d) report("geri tuşu işlenmedi") }, 600)
            }
        }
    }

    fun handled() {
        val d = downAt
        handledAt = SystemClock.uptimeMillis()
        Diag.log("geri işlendi (+${handledAt - d} ms)")
        Choreographer.getInstance().postFrameCallback {
            val dt = SystemClock.uptimeMillis() - d
            Diag.log("geri sonrası ilk kare +$dt ms")
            if (d > 0 && dt > 700) report("geri tuşu yavaş: $dt ms")
        }
    }

    private fun report(what: String) {
        Diag.log(what)
        if (reports++ >= 3) return
        Diag.send("freeze", "$what\n${Diag.memory()}\nSon olaylar:\n${Diag.snapshot()}")
    }
}
