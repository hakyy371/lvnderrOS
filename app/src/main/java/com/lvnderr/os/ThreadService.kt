package com.lvnderr.os

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast

class ThreadService : Service() {

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Thread {
            try {
                val service = MainActivity.remoteService
                    ?: throw IllegalStateException("Shizuku belum terhubung")

                val result = service.exec("lvndThread.sh", "aktif")

                android.os.Handler(mainLooper).post {
                    Toast.makeText(
                        this,
                        result,
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                android.os.Handler(mainLooper).post {
                    Toast.makeText(
                        this,
                        "RenderThread gagal: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Thread {
            try {
                MainActivity.remoteService?.exec(
                    "lvndThread.sh",
                    "nonaktif"
                )
            } catch (_: Exception) {
            }
        }.start()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
package
