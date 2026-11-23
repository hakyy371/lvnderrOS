package com.lvnderr.os

import android.app.Activity
import android.content.Intent
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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private val requestCode = 1001

    // === COMPANION OBJECT (Agar remoteService bisa diakses PreloadService) ===
    companion object {
        var remoteService: ILvnderService? = null
    }

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
            MainActivity.remoteService = ILvnderService.Stub.asInterface(service)
            Toast.makeText(this@MainActivity, "LVNDERR Shizuku terhubung", Toast.LENGTH_SHORT).show()
            runOnUiThread {
                webView.evaluateJavascript("updateShizukuStatus(true)", null)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            MainActivity.remoteService = null
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
        runOnUiThread {
            webView.evaluateJavascript("updateShizukuStatus(${MainActivity.remoteService != null})", null)
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

        // === FUNGSI ASYNC UNTUK MENCEGAH APLIKASI STUCK (FREEZE) ===
        @JavascriptInterface
        fun execAsync(binary: String, state: String, callbackId: String) {
            Thread {
                var resultString = ""
                try {
                    val service = MainActivity.remoteService ?: throw IllegalStateException("Shizuku belum terhubung")
                    
                    var output = ""
                    if (binary == "lvnderrpreload") {
                        if (state == "aktif") {
                            val args = "-a com.mobile.legends -a com.dts.freefiremax -t 4 -v"
                            output = service.exec(binary, args)
                        } else {
                            output = "Preload dihentikan."
                        }
                    } else {
                        output = service.exec(binary, state)
                    }

                    resultString = JSONObject().apply {
                        put("success", true)
                        put("output", output)
                    }.toString()
                } catch (e: Exception) {
                    resultString = JSONObject().apply {
                        put("success", false)
                        put("error", e.message ?: "Eksekusi gagal")
                    }.toString()
                }

                val finalResult = resultString
                runOnUiThread {
                    webView.evaluateJavascript("window.lvnderCallback('$callbackId', ${JSONObject.quote(finalResult)})", null)
                }
            }.start()
        }

        // === FUNGSI UNTUK MENYALAKAN FOREGROUND SERVICE (PRELOADER) ===
        @JavascriptInterface
        fun togglePreloadService(state: String) {
            val intent = Intent(this@MainActivity, PreloadService::class.java)
            if (state == "aktif") {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                stopService(intent)
            }
        }

        // === FUNGSI UNTUK RENDERTHREAD BOOSTER ===
        @JavascriptInterface
        fun toggleRenderThread(state: String): String {
            return try {
                val service = MainActivity.remoteService ?: throw IllegalStateException("Shizuku belum terhubung")
                val output = service.exec("lvndThread.sh", state)
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

        // === FUNGSI UNTUK MENAMBAH GAME KE LIST ===
        @JavascriptInterface
        fun addGameToBooster(packageName: String): String {
            return try {
                val service = MainActivity.remoteService ?: throw IllegalStateException("Shizuku belum terhubung")
                val output = service.exec("lvndaddgame.sh", packageName)
                JSONObject().apply {
                    put("success", true)
                    put("output", output)
                }.toString()
            } catch (e: Exception) {
                JSONObject().apply {
                    put("success", false)
                    put("error", e.message ?: "Gagal menambah game")
                }.toString()
            }
        }

        // === FUNGSI BARU: AUTO-ICON BASE64 ===
        @JavascriptInterface
        fun getAppIconBase64(packageName: String): String {
            return try {
                val pm = packageManager
                val drawable = pm.getApplicationIcon(packageName)
                
                val bitmap = if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val bmp = Bitmap.createBitmap(
                        drawable.intrinsicWidth, 
                        drawable.intrinsicHeight, 
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
                
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val byteArray = stream.toByteArray()
                "data:image/png;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
            } catch (e: Exception) {
                "" // Jika gagal, kembalikan string kosong
            }
        }

        // === FUNGSI BARU: AUTO-NAME ===
        @JavascriptInterface
        fun getAppName(packageName: String): String {
            return try {
                val pm = packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName // Jika gagal, tampilkan nama paketnya
            }
        }

        // Fungsi untuk mengecek apakah Shizuku sedang berjalan & terhubung
        @JavascriptInterface
        fun isShizukuRunning(): Boolean {
            return Shizuku.pingBinder() && MainActivity.remoteService != null
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
