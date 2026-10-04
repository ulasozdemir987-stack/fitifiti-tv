import re

with open('app/src/main/java/com/fitifiti/tv/ui/screens/HeroRows.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace(
    'var lastSwitch = 0L',
    'var lastSwitch = SystemClock.uptimeMillis()'
)

with open('app/src/main/java/com/fitifiti/tv/ui/screens/HeroRows.kt', 'w', encoding='utf-8') as f:
    f.write(text)
