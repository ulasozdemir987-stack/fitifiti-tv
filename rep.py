import re
with open('app/src/main/java/com/fitifiti/tv/data/catalog/UserData.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace(
    'fun clearSearches() = scope.launch { if (pid >= 0) db.recent().clearSearches(pid) }',
    'fun clearSearches() = scope.launch { if (pid >= 0) db.recent().clearSearches(pid) }\n    fun upsertChannelConfig(c: ChannelConfigEntity) = scope.launch { db.channelConfigs().upsert(c) }\n    fun upsertChannelConfigs(list: List<ChannelConfigEntity>) = scope.launch { db.channelConfigs().upsertAll(list) }'
)

with open('app/src/main/java/com/fitifiti/tv/data/catalog/UserData.kt', 'w', encoding='utf-8') as f:
    f.write(text)

with open('app/src/main/java/com/fitifiti/tv/ui/screens/ChannelEditScreen.kt', 'r', encoding='utf-8') as f:
    text2 = f.read()

text2 = text2.replace(
    'import androidx.compose.ui.unit.sp',
    'import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.text.font.FontWeight'
)

with open('app/src/main/java/com/fitifiti/tv/ui/screens/ChannelEditScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text2)
