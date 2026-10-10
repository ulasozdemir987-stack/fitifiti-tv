package com.fitifiti.tv.domain

import com.fitifiti.tv.App
import com.fitifiti.tv.data.local.ChannelConfigEntity
import com.fitifiti.tv.data.xtream.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LiveChannel(
    val channel: Channel,
    val num: Int,
    val name: String,
    val isHidden: Boolean,
    val lists: List<String>
)

object LiveManager {
    /**
     * Kanal listesi bir kez hesaplanır ve paylaşılır. Eskiden her `getChannels()` çağrısı yeni bir Flow kuruyordu;
     * ekranlar bunu her çizimde çağırdığı için 15 bin kanal (ad temizleme regex'iyle) her yeniden çizimde baştan
     * hesaplanıyor, toplama da yeniden başlıyordu → Canlı TV gezinirken takılma.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class Flows(app: App, scope: CoroutineScope) {
        val all: StateFlow<List<LiveChannel>> = combine(app.catalog.catalog, app.user.channelConfigs) { cat, configs ->
            val confMap = configs.associateBy { it.channelId }
            cat.channels.map { ch ->
                val conf = confMap[ch.id]
                val lists = if (conf?.customList != null) listOf("Tüm kanallar", conf.customList) else listOf("Tüm kanallar")
                LiveChannel(
                    channel = ch,
                    num = conf?.sortOrder ?: ch.num,
                    name = conf?.customName?.takeIf { it.isNotBlank() } ?: cleanChannelName(ch.name),
                    isHidden = conf?.isHidden ?: false,
                    lists = lists
                )
            }.sortedBy { it.num }
        }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())
        val visible: StateFlow<List<LiveChannel>> = all.map { l -> l.filter { !it.isHidden } }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())
        val lists: StateFlow<List<String>> = visible.map { list ->
            val names = list.flatMapTo(LinkedHashSet()) { it.lists }.toMutableList()
            if (names.remove("Tüm kanallar")) names.add(0, "Tüm kanallar")
            names
        }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, listOf("Tüm kanallar"))
    }
    @Volatile private var flows: Pair<App, Flows>? = null
    /** Uygulama örneğine bağlı (testlerde her önizleme yeni App kurar) */
    private fun f(): Flows {
        val app = App.instance
        flows?.let { (a, fl) -> if (a === app) return fl }
        return synchronized(this) { flows?.takeIf { it.first === app }?.second ?: Flows(app, scope).also { flows = app to it } }
    }

    fun getChannels(): StateFlow<List<LiveChannel>> = f().all
    fun getVisibleChannels(): StateFlow<List<LiveChannel>> = f().visible
    fun getVisibleLists(): StateFlow<List<String>> = f().lists

    suspend fun applyTurkishSort() {
        val trList = listOf("TRT 1", "KANAL D", "SHOW", "ATV", "STAR", "NOW", "TV8", "KANAL 7", "BEYAZ", "TV100", "HALK", "SÖZCÜ")
        val cat = App.instance.catalog.catalog.value
        val configs = App.instance.user.channelConfigs.value.associateBy { it.channelId }.toMutableMap()
        
        var nextNum = 1
        
        // 1. Assign TR channels first
        for (trName in trList) {
            val matches = cat.channels.filter { cleanChannelName(it.name).uppercase().contains(trName) && configs[it.id]?.isHidden != true }
            for (ch in matches) {
                val existing = configs[ch.id]
                configs[ch.id] = ChannelConfigEntity(
                    profileId = App.instance.user.profileId.value,
                    channelId = ch.id,
                    isHidden = existing?.isHidden ?: false,
                    customName = existing?.customName,
                    sortOrder = nextNum++,
                    customList = existing?.customList
                )
            }
        }
        
        // 2. Assign the rest
        val rest = cat.channels.filter { !configs.containsKey(it.id) || configs[it.id]?.sortOrder == 0 || configs[it.id]?.sortOrder == it.num }
        for (ch in rest.sortedBy { it.num }) {
            val existing = configs[ch.id]
            configs[ch.id] = ChannelConfigEntity(
                profileId = App.instance.user.profileId.value,
                channelId = ch.id,
                isHidden = existing?.isHidden ?: false,
                customName = existing?.customName,
                sortOrder = nextNum++,
                customList = existing?.customList
            )
        }
        
        App.instance.user.upsertChannelConfigs(configs.values.toList())
    }
    
    suspend fun moveChannel(channelId: Int, toNum: Int) {
        val cat = App.instance.catalog.catalog.value
        val configs = App.instance.user.channelConfigs.value.associateBy { it.channelId }.toMutableMap()
        val all = cat.channels.map { ch ->
            val c = configs[ch.id]
            ch.id to (c?.sortOrder ?: ch.num)
        }.sortedBy { it.second }.toMutableList()
        
        val currentIndex = all.indexOfFirst { it.first == channelId }
        if (currentIndex < 0) return
        
        val item = all.removeAt(currentIndex)
        val targetIndex = (toNum - 1).coerceIn(0, all.size)
        all.add(targetIndex, item)
        
        val newConfigs = all.mapIndexed { idx, pair ->
            val id = pair.first
            val c = configs[id]
            ChannelConfigEntity(
                profileId = App.instance.user.profileId.value,
                channelId = id,
                isHidden = c?.isHidden ?: false,
                customName = c?.customName,
                sortOrder = idx + 1,
                customList = c?.customList
            )
        }
        App.instance.user.upsertChannelConfigs(newConfigs)
    }
}
