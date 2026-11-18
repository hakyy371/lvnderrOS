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

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private val requestCode = 1001

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

        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            MainActivity.remoteService =
                ILvnderService.Stub.asInterface(service)

            Toast.makeText(
                this@MainActivity,
                "LVNDERR Shizuku terhubung",
                Toast.LENGTH_SHORT
            ).show()

            runOnUiThread {
                webView.evaluateJavascript(
                    "updateShizukuStatus(true)",
                    null
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            MainActivity.remoteService = null

            runOnUiThread {
                webView.evaluateJavascript(
                    "updateShizukuStatus(false)",
                    null
                )
            }
        }
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { code, grantResult ->

            if (
                code == requestCode &&
                grantResult == PackageManager.PERMISSION_GRANTED
            ) {
                bindLvnderService()
            } else {
                Toast.makeText(
                    this,
                    "Izin Shizuku ditolak",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        checkShizukuPermission()

        runOnUiThread {
            webView.evaluateJavascript(
                "updateShizukuStatus(${MainActivity.remoteService != null})",
                null
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.webViewClient = WebViewClient()

        webView.addJavascriptInterface(
            LvnderBridge(),
            "lvnder"
        )

        setContentView(webView)

        webView.loadUrl(
            "file:///android_asset/webroot/index.html"
        )

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)

        checkShizukuPermission()
    }

    private fun checkShizukuPermission() {
        if (!Shizuku.pingBinder()) return

        if (
            Shizuku.checkSelfPermission() ==
            PackageManager.PERMISSION_GRANTED
        ) {
            bindLvnderService()
        } else {
            Shizuku.requestPermission(requestCode)
        }
    }

    private fun bindLvnderService() {
        if (!Shizuku.pingBinder()) return

        try {
            Shizuku.bindUserService(
                serviceArgs,
                serviceConnection
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Gagal menghubungkan Shizuku: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    inner class LvnderBridge {

        // =====================================
        // BINARY DAN SCRIPT
        // =====================================

        @JavascriptInterface
        fun exec(binary: String, state: String): String {
            return try {
                val service = MainActivity.remoteService
                    ?: throw IllegalStateException(
                        "Shizuku belum terhubung"
                    )

                val output = service.exec(binary, state)

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

        // =====================================
        // PRELOADER
        // =====================================

        @JavascriptInterface
        fun togglePreloadService(state: String): String {
            return try {
                require(state == "aktif" || state == "nonaktif")

                val intent = Intent(
                    this@MainActivity,
                    PreloadService::class.java
                )

                if (state == "aktif") {
                    if (
                        android.os.Build.VERSION.SDK_INT >=
                        android.os.Build.VERSION_CODES.O
                    ) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                } else {
                    stopService(intent)
                }

                JSONObject().apply {
                    put("success", true)
                    put("output", "Preloader $state")
                }.toString()

            } catch (e: Exception) {
                JSONObject().apply {
                    put("success", false)
                    put("error", e.message ?: "Preloader gagal")
                }.toString()
            }
        }

        // =====================================
        // RENDERTHREAD BOOSTER
        // =====================================

        @JavascriptInterface
        fun toggleRenderThreadService(state: String): String {
            return try {
                require(state == "aktif" || state == "nonaktif") {
                    "Status tidak valid"
                }

                val service = MainActivity.remoteService
                    ?: throw IllegalStateException(
                        "Shizuku belum terhubung"
                    )

                val output = service.exec(
                    "lvndThread.sh",
                    state
                )

                JSONObject().apply {
                    put("success", true)
                    put("output", output)
                }.toString()

            } catch (e: Exception) {
                JSONObject().apply {
                    put("success", false)
                    put("error", e.message ?: "RenderThread gagal")
                }.toString()
            }
        }

        // =====================================
        // SHIZUKU STATUS
        // =====================================

        @JavascriptInterface
        fun isShizukuRunning(): Boolean {
            return Shizuku.pingBinder() &&
                MainActivity.remoteService != null
        }

        // =====================================
        // DEVICE INFORMATION
        // =====================================

        @JavascriptInterface
        fun getDeviceInfo(): String {
            return try {

                val script = """
                    CHIPSET_VAL=${'$'}(getprop ro.soc.model)

                    if [ -z "${'$'}CHIPSET_VAL" ]; then
                        CHIPSET_VAL=${'$'}(getprop ro.board.platform)
                    fi

                    if [ -z "${'$'}CHIPSET_VAL" ]; then
                        CHIPSET_VAL=${'$'}(getprop ro.hardware)
                    fi

                    if [ -z "${'$'}CHIPSET_VAL" ]; then
                        CHIPSET_VAL=${'$'}(getprop ro.product.board)
                    fi

                    if [ -z "${'$'}CHIPSET_VAL" ]; then
                        CHIPSET_VAL=${'$'}(grep -m1 'Hardware' /proc/cpuinfo | cut -d: -f2 | xargs)
                    fi

                    echo "DEVICE   : ${'$'}(getprop ro.product.model)"
                    echo "BRAND    : ${'$'}(getprop ro.product.brand)"
                    echo "ANDROID  : ${'$'}(getprop ro.build.version.release)"
                    echo "SDK      : ${'$'}(getprop ro.build.version.sdk)"
                    echo "KERNEL   : ${'$'}(uname -r)"
                    echo "CHIPSET  : ${'$'}CHIPSET_VAL"
                """.trimIndent()

                val process = Runtime.getRuntime().exec(
                    arrayOf("sh", "-c", script)
                )

                val reader = BufferedReader(
                    InputStreamReader(process.inputStream)
                )

                val output = StringBuilder()
                var line: String?

                while (
                    reader.readLine().also { line = it } != null
                ) {
                    output.append(line).append("\n")
                }

                process.waitFor()

                output.toString().trim()

            } catch (e: Exception) {
                "Gagal membaca data: ${e.message}"
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(
            permissionListener
        )

        Shizuku.removeBinderReceivedListener(
            binderListener
        )

        if (Shizuku.pingBinder()) {
            try {
                Shizuku.unbindUserService(
                    serviceArgs,
                    serviceConnection,
                    false
                )
            } catch (_: Exception) {
            }
        }

        webView.destroy()
        super.onDestroy()
    }
}
package
