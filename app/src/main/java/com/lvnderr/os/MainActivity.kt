
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
        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            remoteService = ILvnderService.Stub.asInterface(service)
            Toast.makeText(
                this@MainActivity,
                "LVNDERR Shizuku terhubung",
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
        }
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener {
            code, grantResult ->

            if (code == requestCode &&
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

    private val binderListener =
        Shizuku.OnBinderReceivedListener {
            checkShizukuPermission()
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

        Shizuku.addRequestPermissionResultListener(
            permissionListener
        )

        Shizuku.addBinderReceivedListenerSticky(
            binderListener
        )

        checkShizukuPermission()
    }

    private fun checkShizukuPermission() {
        if (!Shizuku.pingBinder()) return

        if (Shizuku.checkSelfPermission() ==
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

        @JavascriptInterface
        fun exec(binary: String, state: String): String {
            return try {
                val service = remoteService
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
