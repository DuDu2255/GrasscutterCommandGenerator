package io.github.jie65535.grasscutter.commandgenerator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Android implementation of the same gc-opencommand-plugin HTTP contract as the desktop app. */
internal class OpenCommandClient(host: String) {
    private val hostUrl = host.trimEnd('/')
    private val api = hostUrl + "/opencommand/api"

    suspend fun serverStatus(): String = withContext(Dispatchers.IO) {
        val connection = (URL(hostUrl + "/status/server").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
        }
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val version = json.optString("version", "unknown")
            val players = json.optInt("playerCount", json.optInt("player_count", -1))
            val maxPlayers = json.optInt("maxPlayer", json.optInt("max_player", -1))
            if (players >= 0 && maxPlayers > 0) "$version ($players/$maxPlayers)" else version
        } finally {
            connection.disconnect()
        }
    }

    suspend fun ping(token: String = ""): String = request("ping", null, token).optString("data", "unknown")

    suspend fun sendCode(playerId: Int) {
        request("sendCode", playerId)
    }

    suspend fun verify(code: Int): String = request("verify", code).optString("data")

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
            val body = JSONObject().put("token", token).put("action", action).put("data", data).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val responseText = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = JSONObject(responseText)
            if (response.optInt("retcode", connection.responseCode) != 200) {
                error(response.optString("message", "OpenCommand request failed"))
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
