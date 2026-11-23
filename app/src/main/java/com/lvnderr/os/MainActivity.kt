
package com.lvnderr.os

import android.app.Activity
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Canvas
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private val requestCode = 1001

    companion object {
        var remoteService: ILvnderService? = null
    }

    private var isBinding = false

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(this, LvnderUserService::class.java)
        )
            .tag("lvnderr-v1")
            .version(1)
            .daemon(false)
            .processNameSuffix("lvnder")
    }

    // =================================
    // UPDATE SHIZUKU STATUS
    // =================================

    private fun updateStatus(connected: Boolean) {
        if (!::webView.isInitialized) return

        runOnUiThread {
            webView.evaluateJavascript(
                "if(typeof updateShizukuStatus === 'function') updateShizukuStatus($connected);",
                null
            )
        }
    }

    // =================================
    // SERVICE CONNECTION
    // =================================

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            if (service == null) {
                remoteService = null
                isBinding = false
                updateStatus(false)
                return
            }

            remoteService = ILvnderService.Stub.asInterface(service)
            isBinding = false

            Log.d("LVNDERR", "UserService connected")
            updateStatus(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
            isBinding = false

            Log.e("LVNDERR", "UserService disconnected")
            updateStatus(false)
        }
    }

    // =================================
    // SHIZUKU PERMISSION
    // =================================

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { code, grantResult ->

            if (code != requestCode) {
                return@OnRequestPermissionResultListener
            }

            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindLvnderService()
            } else {
                updateStatus(false)

                Toast.makeText(
                    this,
                    "Izin Shizuku ditolak",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val binderListener =
        Shizuku.OnBinderReceivedListener {
            Log.d("LVNDERR", "Shizuku binder received")
            checkShizukuPermission()
        }

    // =================================
    // ON CREATE
    // =================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)

                updateStatus(
                    Shizuku.pingBinder() &&
                    remoteService != null
                )
            }
        }

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

    // =================================
    // CHECK SHIZUKU
    // =================================

    private fun checkShizukuPermission() {

        if (!Shizuku.pingBinder()) {
            Log.w("LVNDERR", "Shizuku binder unavailable")
            updateStatus(false)
            return
        }

        try {
            if (
                Shizuku.checkSelfPermission() ==
                PackageManager.PERMISSION_GRANTED
            ) {
                bindLvnderService()
            } else {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Exception) {
            Log.e("LVNDERR", "Permission error", e)
            updateStatus(false)
        }
    }

    // =================================
    // BIND USER SERVICE
    // =================================

    private fun bindLvnderService() {

        if (!Shizuku.pingBinder()) {
            updateStatus(false)
            return
        }

        if (isBinding || remoteService != null) return

        try {
            isBinding = true

            Shizuku.bindUserService(
                serviceArgs,
                serviceConnection
            )

            Log.d("LVNDERR", "Binding UserService")

        } catch (e: Exception) {
            isBinding = false
            updateStatus(false)

            Log.e("LVNDERR", "Bind failed", e)

            Toast.makeText(
                this,
                "Gagal menghubungkan Shizuku: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =================================
    // JAVASCRIPT BRIDGE
    // =================================

    inner class LvnderBridge {

        // =================================
        // EXEC ASYNC
        // =================================

        @JavascriptInterface
        fun execAsync(
            binary: String,
            state: String,
            callbackId: String
        ) {
            Thread {

                val result = try {

                    val service =
                        remoteService ?: throw IllegalStateException(
                            "Shizuku belum terhubung"
                        )

                    val output = if (binary == "lvnderrpreload") {

                        if (state == "aktif") {
                            service.exec(
                                binary,
                                "-a com.mobile.legends -a com.dts.freefiremax -t 4 -v"
                            )
                        } else {
                            "Preload dihentikan."
                        }

                    } else {
                        service.exec(binary, state)
                    }

                    JSONObject().apply {
                        put("success", true)
                        put("output", output)
                    }.toString()

                } catch (e: Exception) {

                    Log.e("LVNDERR", "Exec error", e)

                    JSONObject().apply {
                        put("success", false)
                        put("error", e.message ?: "Eksekusi gagal")
                    }.toString()
                }

                runOnUiThread {

                    val safeCallback = JSONObject.quote(callbackId)
                    val safeResult = JSONObject.quote(result)

                    webView.evaluateJavascript(
                        "if(typeof window.lvnderCallback === 'function') window.lvnderCallback($safeCallback, $safeResult);",
                        null
                    )
                }

            }.start()
        }

        // =================================
        // PRELOAD SERVICE
        // =================================

        @JavascriptInterface
        fun togglePreloadService(state: String) {

            try {
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

            } catch (e: Exception) {
                Log.e("LVNDERR", "Preload error", e)
            }
        }

        // =================================
        // RENDER THREAD
        // =================================

        @JavascriptInterface
        fun toggleRenderThread(state: String): String {

            return try {

                val service =
                    remoteService ?: throw IllegalStateException(
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

                Log.e("LVNDERR", "RenderThread error", e)

                JSONObject().apply {
                    put("success", false)
                    put("error", e.message ?: "Eksekusi gagal")
                }.toString()
            }
        }

        // =================================
        // ADD GAME
        // =================================

        @JavascriptInterface
        fun addGameToBooster(packageName: String): String {

            return try {

                val service =
                    remoteService ?: throw IllegalStateException(
                        "Shizuku belum terhubung"
                    )

                val output = service.exec(
                    "lvndaddgame.sh",
                    packageName
                )

                JSONObject().apply {
                    put("success", true)
                    put("output", output)
                }.toString()

            } catch (e: Exception) {

                Log.e("LVNDERR", "Add game error", e)

                JSONObject().apply {
                    put("success", false)
                    put("error", e.message ?: "Gagal menambah game")
                }.toString()
            }
        }

        // =================================
        // AUTO ICON BASE64
        // =================================

        @JavascriptInterface
        fun getAppIconBase64(packageName: String): String {

            return try {

                val drawable =
                    packageManager.getApplicationIcon(packageName)

                val size = 192

                val bitmap = Bitmap.createBitmap(
                    size,
                    size,
                    Bitmap.Config.ARGB_8888
                )

                val canvas = Canvas(bitmap)

                drawable.setBounds(
                    0,
                    0,
                    size,
                    size
                )

                drawable.draw(canvas)

                val stream = ByteArrayOutputStream()

                bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    stream
                )

                val encoded = Base64.encodeToString(
                    stream.toByteArray(),
                    Base64.NO_WRAP
                )

                bitmap.recycle()

                "data:image/png;base64,$encoded"

            } catch (e: Exception) {

                Log.e(
                    "LVNDERR_ICON",
                    "Gagal mengambil icon: $packageName",
                    e
                )

                ""
            }
        }

        // =================================
        // AUTO NAME
        // =================================

        @JavascriptInterface
        fun getAppName(packageName: String): String {

            return try {

                val appInfo = packageManager.getApplicationInfo(
                    packageName,
                    0
                )

                packageManager.getApplicationLabel(
                    appInfo
                ).toString()

            } catch (e: Exception) {
                packageName
            }
        }

        // =================================
        // SHIZUKU STATUS
        // =================================

        @JavascriptInterface
        fun isShizukuRunning(): Boolean {

            return try {
                Shizuku.pingBinder() &&
                remoteService != null
            } catch (e: Exception) {
                false
            }
        }

        // =================================
        // DEVICE INFO
        // =================================

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

                Log.e("LVNDERR", "Device info error", e)

                "Gagal membaca data: ${e.message}"
            }
        }
    }

    // =================================
    // ON DESTROY
    // =================================

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
            } catch (e: Exception) {
                Log.e("LVNDERR", "Unbind error", e)
            }
        }

        remoteService = null
        isBinding = false

        if (::webView.isInitialized) {
            webView.destroy()
        }

        super.onDestroy()
    }
}
