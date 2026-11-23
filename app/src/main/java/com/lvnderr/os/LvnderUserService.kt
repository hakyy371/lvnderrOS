package com.lvnderr.os

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

class LvnderUserService(
    private val context: Context
) : ILvnderService.Stub() {

    private val allowed = setOf(
        "lvnderapp", "lvnderMLBB", "lvnderr60HZ", "lvnderr90HZ", "lvnderr120HZ",
        "lvnderrCPU", "lvnderrdevice", "lvnderrDriverGame", "lvnderrNetwork",
        "lvnderrPERF", "lvnderrRender", "lvnderrSqlite", "lvnderrWindows",
        "lvnderSF", "lvnderthermal", "lvnderrpreload",
        "lvndThread.sh", "lvndaddgame.sh"
    )

    override fun exec(binary: String, state: String): String {
        require(binary in allowed) { "Binary tidak terdaftar: $binary" }

        val directory = File("/data/local/tmp/lvnderr")
        check(directory.exists() || directory.mkdirs()) { "Gagal membuat direktori binary" }
        val file = File(directory, binary)

        context.assets.open("binaries/$binary").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        check(file.setExecutable(true, true) || file.canExecute()) { "Tidak bisa memberikan izin eksekusi" }

        // === LOGIKA UNTUK SCRIPT SHELL (RENDERTHREAD BOOSTER) ===
        if (binary == "lvndThread.sh") {
            return if (state == "aktif") {
                ProcessBuilder("sh", file.absolutePath).start()
                "RenderThread Booster Aktif di background."
            } else {
                try {
                    Runtime.getRuntime().exec("pkill -f lvndThread.sh").waitFor()
                } catch (_: Exception) {}
                "RenderThread Booster Dimatikan."
            }
        }

        // === LOGIKA UNTUK MENAMBAH GAME ===
        if (binary == "lvndaddgame.sh") {
            val process = ProcessBuilder("sh", file.absolutePath, state).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            return output.ifBlank { "Game berhasil ditambahkan" }
        }

        // === LOGIKA UNTUK ILLUMINATI PRELOADER ===
        if (binary == "lvnderrpreload") {
            require(state == "aktif" || state == "nonaktif") { "Status tidak valid" }
            if (state == "aktif") {
                val process = ProcessBuilder(
                    file.absolutePath, "-a", "com.mobile.legends", "-a", "com.dts.freefiremax", "-t", "4", "-v"
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                return output.ifBlank { "Preload berhasil" }
            }
            return "Preload selesai."
        }

        // === LOGIKA UNTUK TWEAK BIASA (60HZ, PERF, SF, dll) ===
        require(state == "aktif" || state == "nonaktif") { "Status tidak valid" }
        
        val process = ProcessBuilder(file.absolutePath, state).redirectErrorStream(true).start()
        
        // === KUNCI PERBAIKAN: WAITFOR DENGAN TIMEOUT 5 DETIK ===
        val isFinished = process.waitFor(5, TimeUnit.SECONDS)
        
        if (!isFinished) {
            process.destroyForcibly() // Matikan paksa jika hang
            throw Exception("Timeout! Tweak memakan waktu terlalu lama atau tidak didukung HP ini.")
        }

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.exitValue()
        
        check(exitCode == 0) { "Binary gagal. Exit code: $exitCode\n$output" }
        
        return output.ifBlank { "Berhasil dijalankan" }
    }

    override fun destroy() {
        System.exit(0)
    }
}
