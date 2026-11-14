package com.gorilla.control.root
object RootStatus {
    fun check(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor() == 0 && out.contains("uid=0")
    } catch (_: Exception) { false }
}
