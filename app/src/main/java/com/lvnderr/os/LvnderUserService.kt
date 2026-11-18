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
        "lvnderthermal",
        "lvnderrpreload",
        "lvndThread.sh"
    )

    private val directory =
        File("/data/local/tmp/lvnderr")

    private fun runShell(command: String): String {
        val process = ProcessBuilder(
            "/system/bin/sh",
            "-c",
            command
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use {
            it.readText()
        }

        val exitCode = process.waitFor()

        check(exitCode == 0) {
            "Shell command gagal: $output"
        }

        return output.trim()
    }

    override fun exec(binary: String, state: String): String {

        require(binary in allowed) {
            "Binary tidak terdaftar: $binary"
        }

        require(state == "aktif" || state == "nonaktif") {
            "Status tidak valid"
        }

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

        // === LVNDTHREAD SERVICE ===

        if (binary == "lvndThread.sh") {

            val pidFile = File(directory, "lvndThread.pid")
            val scriptPath = file.absolutePath
            val pidPath = pidFile.absolutePath

            return if (state == "aktif") {

                runShell(
                    """
                    if [ -f '$pidPath' ] &&
                       kill -0 "$(cat '$pidPath')" 2>/dev/null; then
                        echo "lvndThread sudah berjalan"
                        exit 0
                    fi

                    /system/bin/sh '$scriptPath' >/dev/null 2>&1 &

                    echo ${'$'}! > '$pidPath'
                    echo "lvndThread berhasil diaktifkan"
                    """.trimIndent()
                )

            } else {

                runShell(
                    """
                    if [ -f '$pidPath' ]; then
                        pid=${'$'}(cat '$pidPath')

                        case "${'$'}pid" in
                            ''|*[!0-9]*)
                                echo "PID tidak valid"
                                ;;
                            *)
                                kill "${'$'}pid" 2>/dev/null || true
                                ;;
                        esac

                        rm -f '$pidPath'
                    fi

                    echo "lvndThread berhasil dinonaktifkan"
                    """.trimIndent()
                )
            }
        }

        // === ILLUMINATI PRELOADER ===

        if (binary == "lvnderrpreload") {

            if (state == "nonaktif") {
                return "Preload selesai (status: nonaktif)."
            }

            val actualArgs = arrayOf(
                file.absolutePath,
                "-a", "com.mobile.legends",
                "-a", "com.dts.freefiremax",
                "-t", "4",
                "-v"
            )

            val process = ProcessBuilder(*actualArgs)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use {
                it.readText()
            }

            val exitCode = process.waitFor()

            check(exitCode == 0) {
                "Preloader gagal. Exit code: $exitCode\n$output"
            }

            return output.ifBlank {
                "Preloader berhasil dijalankan"
            }
        }

        // === TWEAK BIASA ===

        val process = ProcessBuilder(
            file.absolutePath,
            state
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use {
            it.readText()
        }

        val exitCode = process.waitFor()

        check(exitCode == 0) {
            "Binary gagal. Exit code: $exitCode\n$output"
        }

        return output.ifBlank {
            "Berhasil dijalankan"
        }
    }

    override fun destroy() {
        System.exit(0)
    }
}
