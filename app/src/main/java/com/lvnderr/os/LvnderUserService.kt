
package com.lvnderr.os

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

class LvnderUserService(
    private val context: Context
) : ILvnderService.Stub() {

    companion object {
        private const val TAG = "LVNDERR_SERVICE"
        private const val TIMEOUT_SECONDS = 10L
    }

    private val allowed = setOf(
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
        "lvnderthermal",
        "lvnderrpreload",
        "lvndThread.sh",
        "lvndaddgame.sh"
    )

    override fun exec(binary: String, state: String): String {

        require(binary in allowed) {
            "Binary tidak terdaftar: $binary"
        }

        val directory = File("/data/local/tmp/lvnderr")

        check(directory.exists() || directory.mkdirs()) {
            "Gagal membuat direktori binary"
        }

        val file = File(directory, binary)

        try {
            context.assets.open("binaries/$binary").use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            check(file.setExecutable(true, true) || file.canExecute()) {
                "Tidak bisa memberikan izin eksekusi"
            }

            // RENDERTHREAD BOOSTER

            if (binary == "lvndThread.sh") {

                return if (state == "aktif") {

                    ProcessBuilder(
                        "sh",
                        file.absolutePath
                    ).start()

                    "RenderThread Booster Aktif di background."

                } else {

                    "RenderThread Booster Dimatikan. Pastikan mekanisme penghentian script dikonfigurasi."
                }
            }

            // ADD GAME

            if (binary == "lvndaddgame.sh") {

                return executeProcess(
                    listOf("sh", file.absolutePath, state)
                )
            }

            // PRELOAD

            if (binary == "lvnderrpreload") {

                require(
                    state == "aktif" || state == "nonaktif"
                ) {
                    "Status tidak valid"
                }

                if (state == "nonaktif") {
                    return "Preload selesai."
                }

                return executeProcess(
                    listOf(
                        file.absolutePath,
                        "-a", "com.mobile.legends",
                        "-a", "com.dts.freefiremax",
                        "-t", "4",
                        "-v"
                    )
                )
            }

            // TWEAK BIASA

            require(
                state == "aktif" || state == "nonaktif"
            ) {
                "Status tidak valid"
            }

            return executeProcess(
                listOf(file.absolutePath, state)
            )

        } catch (e: Exception) {

            Log.e(TAG, "Gagal menjalankan $binary", e)

            throw IllegalStateException(
                e.message ?: "Eksekusi gagal",
                e
            )
        }
    }

    private fun executeProcess(command: List<String>): String {

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()

        val readerThread = Thread {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    var line: String?

                    while (
                        reader.readLine().also { line = it } != null
                    ) {
                        synchronized(output) {
                            output.append(line).append('\n')
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Output reader error", e)
            }
        }

        readerThread.start()

        val finished = process.waitFor(
            TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )

        if (!finished) {
            process.destroyForcibly()
            readerThread.join(1000)

            throw IllegalStateException(
                "Proses timeout setelah $TIMEOUT_SECONDS detik"
            )
        }

        readerThread.join(1000)

        val result: String

        synchronized(output) {
            result = output.toString().trim()
        }

        val exitCode = process.exitValue()

        check(exitCode == 0) {
            "Binary gagal. Exit code: $exitCode\n$result"
        }

        return result.ifBlank {
            "Berhasil dijalankan"
        }
    }

    override fun destroy() {
        Log.d(TAG, "UserService destroy requested")
        // Biarkan lifecycle Shizuku mengelola penghentian service.
    }
}
