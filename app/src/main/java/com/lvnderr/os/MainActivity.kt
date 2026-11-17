package com.lvnderr.os

import android.app.Activity
import android.os.Bundle
import android.os.IBinder
import android.content.ComponentName
import android.content.ServiceConnection
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.widget.Toast
import android.content.pm.PackageManager
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var remoteService: ILvnderService? = null
    private val requestCode = 1001

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(this, LvnderUserService::class.java)
        )
            .tag("lvnderr-v1")
            .version(1)
            .daemon(false)
            .processNameSuffix("lvnder")
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remoteService = ILvnderService.Stub.asInterface(service)
            Toast.makeText(this@MainActivity, "LVNDERR Shizuku terhubung", Toast.LENGTH_SHORT).show()
            
            // Beritahu WebView bahwa Shizuku sudah jalan
            runOnUiThread {
                webView.evaluateJavascript("updateShizukuStatus(true)", null)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
            // Beritahu WebView bahwa Shizuku terputus
            runOnUiThread {
                webView.evaluateJavascript("updateShizukuStatus(false)", null)
            }
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, grantResult ->
        if (code == requestCode && grantResult == PackageManager.PERMISSION_GRANTED) {
            bindLvnderService()
        } else {
            Toast.makeText(this, "Izin Shizuku ditolak", Toast.LENGTH_LONG).show()
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        checkShizukuPermission()
        // Jika Shizuku baru dibuka, cek status dan update UI
        runOnUiThread {
            webView.evaluateJavascript("updateShizukuStatus(${remoteService != null})", null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(LvnderBridge(), "lvnder")

        setContentView(webView)
        webView.loadUrl("file:///android_asset/webroot/index.html")

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)

        checkShizukuPermission()
    }

    private fun checkShizukuPermission() {
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bindLvnderService()
        } else {
            Shizuku.requestPermission(requestCode)
        }
    }

    private fun bindLvnderService() {
        if (!Shizuku.pingBinder()) return
        try {
            Shizuku.bindUserService(serviceArgs, serviceConnection)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal menghubungkan Shizuku: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    inner class LvnderBridge {

        @JavascriptInterface
        fun exec(binary: String, state: String): String {
            return try {
                val service = remoteService ?: throw IllegalStateException("Shizuku belum terhubung")
                
                var output = ""
                // === LOGIKA PENERJEMAH UNTUK PRELOADER ===
                if (binary == "lvnderrpreload") {
                    if (state == "aktif") {
                        // Perintah Illuminati: preload MLBB & FF, 4 thread, verbose
                        val args = "-a com.mobile.legends -a com.dts.freefiremax -t 4 -v"
                        output = service.exec(binary, args)
                    } else {
                        output = "Preload dihentikan."
                    }
                } else {
                    // === LOGIKA NORMAL UNTUK TWEAK LAIN ===
                    output = service.exec(binary, state)
                }

                JSONObject().apply {
                    put("success", true)
                    put("output", output)
                }.toString()
            } catch (e: Exception) {
                JSONObject().apply {
                    put("success", false)
                    put("error", e.message ?: "Eksekusi gagal")
                }.toString()
            }
        }

        // Fungsi untuk mengecek apakah Shizuku sedang berjalan & terhubung
        @JavascriptInterface
        fun isShizukuRunning(): Boolean {
            return Shizuku.pingBinder() && remoteService != null
        }

        // Fungsi Device Info (Chipset diperbaiki dengan Fallback Berlapis)
        @JavascriptInterface
        fun getDeviceInfo(): String {
            return try {
                val script = """
                    CHIPSET_VAL=${'$'}(getprop ro.soc.model)
                    if [ -z "${'$'}CHIPSET_VAL" ]; then CHIPSET_VAL=${'$'}(getprop ro.board.platform); fi
                    if [ -z "${'$'}CHIPSET_VAL" ]; then CHIPSET_VAL=${'$'}(getprop ro.hardware); fi
                    if [ -z "${'$'}CHIPSET_VAL" ]; then CHIPSET_VAL=${'$'}(getprop ro.product.board); fi
                    if [ -z "${'$'}CHIPSET_VAL" ]; then CHIPSET_VAL=${'$'}(grep -m1 'Hardware' /proc/cpuinfo | cut -d: -f2 | xargs); fi
                    
                    echo "DEVICE   : ${'$'}(getprop ro.product.model)"
                    echo "BRAND    : ${'$'}(getprop ro.product.brand)"
                    echo "ANDROID  : ${'$'}(getprop ro.build.version.release)"
                    echo "SDK      : ${'$'}(getprop ro.build.version.sdk)"
                    echo "KERNEL   : ${'$'}(uname -r)"
                    echo "CHIPSET  : ${'$'}CHIPSET_VAL"
                """.trimIndent()

                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", script))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                output.toString().trim()
            } catch (e: Exception) {
                "Gagal membaca data: ${e.message}"
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderListener)

        if (Shizuku.pingBinder()) {
            try {
                Shizuku.unbindUserService(serviceArgs, serviceConnection, false)
            } catch (_: Exception) {}
        }

        webView.destroy()
        super.onDestroy()
    }
}
