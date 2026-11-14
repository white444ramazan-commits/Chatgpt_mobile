package com.gorilla.control.remote

import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object DevicePrefs {
    const val FILE = "gorilla_control_link"
    const val SERVER_URL = "server_url"
    const val DEVICE_TOKEN = "device_token"
    const val PENDING_COMMAND = "pending_command"
    const val CONNECTION_STATE = "connection_state"
}

object DeviceApi {
    data class PairResult(val token: String)

    fun pair(baseUrl: String, pairingCode: String): PairResult {
        require(pairingCode.length >= 16) { "Код сопряжения должен быть не короче 16 символов." }
        val body = JSONObject()
            .put("pairingCode", pairingCode)
            .put("deviceName", Build.MODEL ?: "Android device")
        val response = request(baseUrl, "/device/pair", "POST", body, null)
        val token = response.optString("deviceToken")
        require(token.length >= 32) { "Сервер не вернул действительный токен устройства." }
        return PairResult(token)
    }

    fun heartbeat(baseUrl: String, token: String, rootAvailable: Boolean, width: Int?, height: Int?) {
        val body = JSONObject()
            .put("model", Build.MODEL ?: "Android")
            .put("androidVersion", Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString())
            .put("rootAvailable", rootAvailable)
        if (width != null) body.put("screenWidth", width)
        if (height != null) body.put("screenHeight", height)
        request(baseUrl, "/device/heartbeat", "POST", body, token)
    }

    fun nextCommand(baseUrl: String, token: String): JSONObject? {
        val response = request(baseUrl, "/device/commands/next", "GET", null, token)
        return response.optJSONObject("command")
    }

    fun submitResult(
        baseUrl: String,
        token: String,
        commandId: String,
        status: String,
        result: JSONObject
    ) {
        require(status in setOf("completed", "rejected", "failed"))
        require(commandId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        request(
            baseUrl,
            "/device/commands/$commandId/result",
            "POST",
            JSONObject().put("status", status).put("result", result),
            token
        )
    }

    private fun request(
        baseUrl: String,
        path: String,
        method: String,
        body: JSONObject?,
        token: String?
    ): JSONObject {
        val cleanBase = validateBaseUrl(baseUrl)
        val connection = (URL(cleanBase + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = try { JSONObject(responseText).optString("error", responseText) }
                    catch (_: Exception) { responseText }
                throw IllegalStateException("Сервер вернул HTTP $code: $detail")
            }
            return if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun validateBaseUrl(raw: String): String {
        val value = raw.trim().trimEnd('/')
        val url = URL(value)
        require(url.protocol.equals("https", ignoreCase = true)) {
            "Для удалённого подключения требуется HTTPS."
        }
        require(url.host.isNotBlank() && url.userInfo == null && url.query == null && url.ref == null) {
            "Укажи только базовый HTTPS-адрес Worker без логина, параметров и фрагмента."
        }
        return value
    }
}
