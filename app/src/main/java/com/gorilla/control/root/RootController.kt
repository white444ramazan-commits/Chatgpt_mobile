package com.gorilla.control.root

import java.util.concurrent.TimeUnit

data class DeviceResolution(val width: Int, val height: Int) {
    override fun toString(): String = "${width} × ${height}"
}

/**
 * Local, user-initiated Root controls. This class intentionally exposes only
 * a small command allowlist instead of an arbitrary shell command interface.
 */
object RootController {
    private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun hasRoot(): Boolean = try {
        val result = runText("id")
        result.contains("uid=0")
    } catch (_: Exception) {
        false
    }

    fun getResolution(): DeviceResolution {
        val output = runText("wm size")
        val matches = Regex("(\\d+)\\s*x\\s*(\\d+)").findAll(output).toList()
        val match = matches.lastOrNull()
            ?: throw IllegalStateException("Не удалось определить разрешение экрана: $output")
        return DeviceResolution(
            width = match.groupValues[1].toInt(),
            height = match.groupValues[2].toInt()
        )
    }

    fun tap(x: Int, y: Int) {
        val size = getResolution()
        require(x in 0 until size.width && y in 0 until size.height) {
            "Координаты должны быть внутри экрана $size"
        }
        runText("input tap $x $y")
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 350) {
        val size = getResolution()
        require(x1 in 0 until size.width && x2 in 0 until size.width &&
            y1 in 0 until size.height && y2 in 0 until size.height) {
            "Все координаты должны быть внутри экрана $size"
        }
        require(durationMs in 80..2000) { "Длительность свайпа: от 80 до 2000 мс" }
        runText("input swipe $x1 $y1 $x2 $y2 $durationMs")
    }

    fun launchApp(packageName: String) {
        require(packagePattern.matches(packageName)) { "Некорректное имя пакета" }
        runText("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    fun captureScreen(): ByteArray {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p"))
        val bytes = process.inputStream.use { it.readBytes() }
        val error = process.errorStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(12, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("Снимок экрана превысил лимит времени")
        }
        if (process.exitValue() != 0 || bytes.size < 8) {
            throw IllegalStateException(error.ifBlank { "Не удалось получить снимок экрана через Root" })
        }
        val pngSignature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        require(bytes.take(8).toByteArray().contentEquals(pngSignature)) {
            "Команда screencap вернула данные в неожиданном формате"
        }
        return bytes
    }

    private fun runText(command: String): String {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val error = process.errorStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(12, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("Root-команда превысила лимит времени")
        }
        if (process.exitValue() != 0) {
            throw IllegalStateException(error.ifBlank { "Root-команда завершилась с кодом ${process.exitValue()}" })
        }
        return output
    }
}
