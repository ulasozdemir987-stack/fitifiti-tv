package com.fitifiti.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fitifiti.tv.data.xtream.Account
import com.fitifiti.tv.data.xtream.AppJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer

/** Xtream hesapları cihazda şifreli saklanır (Android Keystore). Keystore bozuk cihazlarda düz depolamaya düşer. */
class AccountStore(ctx: Context) {
    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(ctx, "accounts_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } catch (e: Exception) {
        ctx.getSharedPreferences("accounts_plain", Context.MODE_PRIVATE)
    }

    private val _accounts = MutableStateFlow(load())
    val accounts: StateFlow<List<Account>> = _accounts
    private val _activeId = MutableStateFlow(prefs.getString("active", null))
    val activeId: StateFlow<String?> = _activeId

    val active: Account? get() = _accounts.value.firstOrNull { it.id == _activeId.value } ?: _accounts.value.firstOrNull()

    private fun load(): List<Account> = try {
        prefs.getString("list", null)?.let { AppJson.decodeFromString(ListSerializer(Account.serializer()), it) } ?: emptyList()
    } catch (e: Exception) { emptyList() }

    private fun save(list: List<Account>) {
        prefs.edit().putString("list", AppJson.encodeToString(ListSerializer(Account.serializer()), list)).apply()
        _accounts.value = list
    }

    fun upsert(a: Account) {
        save(_accounts.value.filterNot { it.id == a.id } + a)
        setActive(a.id)
    }
    fun remove(id: String) {
        save(_accounts.value.filterNot { it.id == id })
        if (_activeId.value == id) setActive(_accounts.value.firstOrNull()?.id)
    }
    fun setActive(id: String?) {
        prefs.edit().putString("active", id).apply()
        _activeId.value = id
    }
}
