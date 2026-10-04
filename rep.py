import re
with open('app/src/main/java/com/fitifiti/tv/ui/Actions.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace(
    'fun playChannel(id: Int, list: List<Int>) { app.user.touchChannel(id); nav.push(Route.LivePlayer(id, list)) }',
    'fun playChannel(id: Int, list: List<Int>) { app.user.touchChannel(id); nav.push(Route.LivePlayer(id)) }'
)

with open('app/src/main/java/com/fitifiti/tv/ui/Actions.kt', 'w', encoding='utf-8') as f:
    f.write(text)
