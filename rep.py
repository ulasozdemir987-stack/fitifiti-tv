import re

with open('app/src/main/java/com/fitifiti/tv/ui/screens/LiveScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# I will just write the function correctly
text = re.sub(
    r'@Composable\s+private fun LiveStrip\(.*?\}.*?\}',
    '''@Composable
private fun LiveStrip(title: String, channels: List<LiveChannel>, nav: com.fitifiti.tv.ui.Actions) {
    Column(Modifier.padding(top = 32.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(channels, key = { it.channel.id }) { c ->
                ChannelCard(c.channel, onClick = { nav.nav.push(Route.LivePlayer(c.channel.id)) }, modifier = Modifier.width(220.dp))
            }
        }
    }
}''',
    text,
    flags=re.DOTALL
)

with open('app/src/main/java/com/fitifiti/tv/ui/screens/LiveScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)
