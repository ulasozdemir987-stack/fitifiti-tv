package com.fitifiti.tv.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

@Serializable
data class AuthResponse(
    @SerialName("user_info") val userInfo: UserInfo? = null
)

@Serializable
data class UserInfo(
    @SerialName("username") val username: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("exp_date") val expDate: String? = null,
    @SerialName("active_cons") val activeConnections: String? = null,
    @SerialName("max_connections") val maxConnections: String? = null
)
