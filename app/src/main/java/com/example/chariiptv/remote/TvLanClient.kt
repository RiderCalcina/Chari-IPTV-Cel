package com.example.chariiptv.remote

import android.util.Log
import com.example.chariiptv.domain.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class TvStatus(
    val isPlaying: Boolean = false,
    val channelName: String? = null,
    val channelLogo: String? = null,
    val channelCategory: String? = null
)

@Singleton
class TvLanClient @Inject constructor() {

    companion object {
        private const val TAG = "TvLanClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5000, TimeUnit.MILLISECONDS)
        .readTimeout(8000, TimeUnit.MILLISECONDS)
        .writeTimeout(8000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun getTvInfo(tv: DiscoveredTv): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${tv.baseUrl}/api/info")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "{}"
                    Result.success(JSONObject(bodyStr))
                } else {
                    Result.failure(Exception("HTTP error ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando /api/info de ${tv.displayName}", e)
            Result.failure(e)
        }
    }

    suspend fun getTvStatus(tv: DiscoveredTv): Result<TvStatus> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${tv.baseUrl}/api/status")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val status = TvStatus(
                        isPlaying = json.optBoolean("isPlaying", false),
                        channelName = if (json.has("channelName")) json.optString("channelName") else null,
                        channelLogo = if (json.has("channelLogo")) json.optString("channelLogo") else null,
                        channelCategory = if (json.has("channelCategory")) json.optString("channelCategory") else null
                    )
                    Result.success(status)
                } else {
                    Result.failure(Exception("HTTP error ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun playChannelOnTv(tv: DiscoveredTv, channel: Channel): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", "PLAY_CHANNEL")
                put("channel", JSONObject().apply {
                    put("name", channel.name)
                    put("logo", channel.logo)
                    put("url", channel.url)
                    put("category", channel.category)
                    put("country", channel.country)
                    put("referer", channel.referer)
                    put("userAgent", channel.userAgent)
                })
            }

            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("${tv.baseUrl}/api/command")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Canal ${channel.name} enviado exitosamente a ${tv.displayName}")
                    Result.success(true)
                } else {
                    Result.failure(Exception("HTTP error ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando canal a ${tv.displayName}", e)
            Result.failure(e)
        }
    }

    suspend fun sendRemoteKey(tv: DiscoveredTv, key: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", "KEY_EVENT")
                put("key", key)
            }

            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("${tv.baseUrl}/api/remote")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("HTTP error ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando tecla $key a ${tv.displayName}", e)
            Result.failure(e)
        }
    }

    suspend fun stopTvPlayback(tv: DiscoveredTv): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", "STOP")
            }

            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("${tv.baseUrl}/api/command")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendSearchQuery(tv: DiscoveredTv, query: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", "INPUT_TEXT")
                put("text", query)
            }

            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("${tv.baseUrl}/api/command")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncFavorites(tv: DiscoveredTv, favoriteUrls: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            favoriteUrls.forEach { jsonArray.put(it) }

            val payload = JSONObject().apply {
                put("action", "SYNC_FAVORITES")
                put("favorites", jsonArray)
            }

            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("${tv.baseUrl}/api/command")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respJson = JSONObject(response.body?.string() ?: "{}")
                    val synced = respJson.optInt("synced", favoriteUrls.size)
                    Result.success(synced)
                } else {
                    Result.failure(Exception("HTTP error ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando favoritos a ${tv.displayName}", e)
            Result.failure(e)
        }
    }
}
