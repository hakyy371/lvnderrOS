
package com.lvnderr.os

import android.content.Context
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.io.BufferedReader
import java.io.InputStreamReader

class LvnderUserService : android.app.Service() {

    override fun onBind(intent: android.content.Intent?) =
        null

    companion object {
        fun execute(binary: String, state: String): String {
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

            require(binary in allowed) {
                "Binary tidak terdaftar"
            }

            require(state == "aktif" || state == "nonaktif") {
                "Status tidak valid"
            }

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
    }
}
