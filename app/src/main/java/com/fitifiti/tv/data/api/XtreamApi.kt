package com.fitifiti.tv.data.api

import com.fitifiti.tv.data.model.AuthResponse
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    fun authenticate(server: String, user: String, pass: String): AuthResponse {
        val url = server.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment("player_api.php")
            ?.addQueryParameter("username", user)
            ?.addQueryParameter("password", pass)
            ?.build() ?: throw IllegalArgumentException("Geçersiz sunucu adresi")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP Hatası: ${response.code}")
            val body = response.body?.string() ?: throw Exception("Boş yanıt")
            return json.decodeFromString<AuthResponse>(body)
        }
    }
}
