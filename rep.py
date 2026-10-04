import re
with open('app/src/main/java/com/fitifiti/tv/ui/player/LivePlayerScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('rememberEpg(channel.id, 6)', 'rememberEpg(channel)')
text = text.replace('rememberEpg(c.id)', 'rememberEpg(c)')

with open('app/src/main/java/com/fitifiti/tv/ui/player/LivePlayerScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)
