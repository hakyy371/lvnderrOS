package com.lvnderr.os

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class ToastActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mengambil teks dengan key "text" (sama seperti AxManager)
        val message = intent.getStringExtra("text") ?: "lvndThread Active"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish() // Langsung tutup activity setelah memunculkan toast
    }
}
