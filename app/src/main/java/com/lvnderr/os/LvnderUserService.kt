
package com.lvnderr.os

import android.content.Context
import java.io.File

class LvnderUserService(
    private val context: Context
) : ILvnderService.Stub() {

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
        "lvnderthermal"
    )

    override fun exec(binary: String, state: String): String {
        require(binary in allowed) {
            "Binary tidak terdaftar: $binary"
        }

        require(state == "aktif" || state == "nonaktif") {
            "Status tidak valid"
        }

        val directory = File("/data/local/tmp/lvnderr")

        check(directory.exists() || directory.mkdirs()) {
            "Gagal membuat direktori binary"
        }

        val file = File(directory, binary)

        context.assets.open("binaries/$binary").use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        check(file.setExecutable(true, true) || file.canExecute()) {
            "Tidak bisa memberikan izin eksekusi"
        }

        val process = ProcessBuilder(
            file.absolutePath,
            state
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().use {
            it.readText()
        }

        val exitCode = process.waitFor()

        check(exitCode == 0) {
            "Binary gagal. Exit code: $exitCode\n$output"
        }

        return output.ifBlank { "Berhasil dijalankan" }
    }

    override fun destroy() {
        System.exit(0)
    }
}
