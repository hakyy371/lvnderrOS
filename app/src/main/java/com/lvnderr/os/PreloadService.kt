package com.lvnderr.os

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class PreloadService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = true
    private val CHANNEL_ID = "lvnderr_preload_channel"

    private val runnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            try {
                // Panggil preload setiap 5 detik
                MainActivity.remoteService?.exec(
                    "lvnderrpreload", 
                    "-a com.mobile.legends -a com.dts.freefiremax -t 2"
                )
            } catch (_: Exception) {}
            
            // Ulangi setiap 5000 ms (5 detik)
            handler.postDelayed(this, 5000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("lvnderrOS Preloader")
                .setContentText("Menjaga cache game tetap hangat di RAM...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("lvnderrOS Preloader")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()
        }

        startForeground(1, notification)
        isRunning = true
        handler.post(runnable)
        
        return START_STICKY // Agar service tidak mudah dibunuh Android
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "lvnderrOS Preload Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
