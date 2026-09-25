package com.grasscutter.m

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Android implementation of the same gc-opencommand-plugin HTTP contract as the desktop app. */
internal class OpenCommandClient(host: String) {
    private val hostUrl = normalizeHost(host)
    private val api = hostUrl + "/opencommand/api"

    private fun normalizeHost(rawHost: String): String {
        val value = rawHost.trim()
        require(value.isNotBlank()) { "服务器地址不能为空" }
        val candidate = if (value.startsWith("http://") || value.startsWith("https://")) value else "http://$value"
        val parsed = runCatching { URL(candidate) }.getOrElse { error("服务器地址格式无效") }
        require(parsed.protocol == "http" || parsed.protocol == "https") { "仅支持 HTTP 或 HTTPS 服务器地址" }
        require(parsed.host.isNotBlank()) { "服务器地址缺少主机名" }
        return candidate.trimEnd('/')
    }

    suspend fun serverStatus(): String = withContext(Dispatchers.IO) {
        val connection = (URL(hostUrl + "/status/server").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
        }
        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (body.isBlank()) error("服务器状态请求失败（HTTP $responseCode）")
            val json = JSONObject(body)
            if (responseCode !in 200..299) {
                error(json.optString("message", "服务器状态请求失败（HTTP $responseCode）"))
            }
            // gc-opencommand-plugin returns { retcode, status: { version, playerCount, MaxPlayer } }.
            // Keep the top-level fallback for older/custom dispatch implementations.
            val status = json.optJSONObject("status") ?: json
            val version = status.optString("version", json.optString("version", "unknown"))
            val players = status.optInt("playerCount", status.optInt("player_count", json.optInt("playerCount", -1)))
            val maxPlayers = when {
                status.has("MaxPlayer") -> status.optInt("MaxPlayer", -1)
                status.has("maxPlayer") -> status.optInt("maxPlayer", -1)
                status.has("max_player") -> status.optInt("max_player", -1)
                else -> json.optInt("MaxPlayer", -1)
            }
            if (players >= 0 && maxPlayers > 0) "$version ($players/$maxPlayers)" else version
        } finally {
            connection.disconnect()
        }
    }

    suspend fun ping(token: String = ""): String = request("ping", null, token).optString("data", "unknown")

    /** Sends the code and returns the temporary token required by verify. */
    suspend fun sendCode(playerId: Int): String = request("sendCode", playerId).optString("data")

    suspend fun verify(code: Int, token: String): String = request("verify", code, token).optString("data")

    suspend fun invoke(token: String, command: String): String = request("command", command, token).optString("data")

    private suspend fun request(action: String, data: Any?, token: String = ""): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(api).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            // Keep the data key for ping requests too; some plugin versions deserialize
            // the request strictly even when the action has no payload.
            val body = JSONObject().put("token", token).put("action", action)
                .put("data", data ?: JSONObject.NULL).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val responseText = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseText.isBlank()) {
                error("OpenCommand returned an empty response (HTTP ${connection.responseCode})")
            }
            val response = JSONObject(responseText)
            val responseCode = connection.responseCode
            if (response.optInt("retcode", responseCode) != 200) {
                error(response.optString("message", "OpenCommand request failed (HTTP $responseCode)"))
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}

internal class OpenCommandSettings(context: android.content.Context) {
    private val preferences = context.getSharedPreferences("open_command", android.content.Context.MODE_PRIVATE)
    val host: String get() = preferences.getString("host", "http://127.0.0.1:443") ?: "http://127.0.0.1:443"
    val token: String get() = preferences.getString("token", "") ?: ""
    val playerId: String get() = preferences.getString("player_id", "") ?: ""

    fun save(host: String, token: String, playerId: String) {
        preferences.edit().putString("host", host).putString("token", token).putString("player_id", playerId).apply()
    }
}
