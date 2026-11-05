
package com.lvnderr.os

class LvnderUserService : ILvnderService.Stub() {

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

        val file = java.io.File("/system/bin/$binary")

        require(file.isFile && file.canExecute()) {
            "Binary tidak ditemukan atau tidak dapat dieksekusi"
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
            output.ifBlank { "Tweak gagal dijalankan: $exitCode" }
        }

        return output
    }

    override fun destroy() {
        System.exit(0)
    }
}
