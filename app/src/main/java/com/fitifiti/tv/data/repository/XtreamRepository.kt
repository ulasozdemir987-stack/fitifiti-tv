package com.fitifiti.tv.data.repository

import com.fitifiti.tv.data.api.XtreamApi
import com.fitifiti.tv.data.model.AuthResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamRepository @Inject constructor(
    private val api: XtreamApi
) {
    suspend fun authenticate(server: String, username: String, password: String): AuthResponse {
        return withContext(Dispatchers.IO) {
            api.authenticate(server, username, password)
        }
    }
}
