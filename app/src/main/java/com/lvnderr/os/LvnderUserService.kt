package com.lvnderr.os

import android.app.Service
import android.content.Intent
import android.os.IBinder

class LvnderUserService : Service() {

    private val binder = object : ILvnderService.Stub() {

        override fun exec(binary: String, state: String): String {
            val allowed = setOf(
                "lvnderapp",
                "lvnderMLBB",
                "lvnderr60HZ",
                "lvnderr90HZ",
                "lvnderr120HZ",
                "lvnderrCPU",
                "lvnderrdevice",
                "lvnderrDriverGame",
                "lvnderrNetwork",
                "lvnderrPERF",
                "lvnderrRender",
                "lvnderrSqlite",
                "lvnderrWindows",
                "lvnderSF",
                "lvnderthermal"
            )

            require(binary in allowed)
            require(state == "aktif" || state == "nonaktif")

            val process = ProcessBuilder(
                "/system/bin/$binary",
                state
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().use {
                it.readText()
            }

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                throw IllegalStateException(output)
            }

            return output
        }

        override fun destroy() {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}
packageackage
