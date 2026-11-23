package com.lvnderr.os

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ToastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Mengambil pesan dari script lvndThread.sh
        val message = intent.getStringExtra("message") ?: "lvndThread Active"
        
        // Menampilkan Toast di layar
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
