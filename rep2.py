import re

with open('app/src/main/java/com/fitifiti/tv/ui/screens/Detail.kt', 'r', encoding='utf-8') as f:
    text = f.read()

old = '''HeroBackdrop(art, video = trailer?.let { t -> { TrailerVideo(t, atTop) } })'''
new = '''HeroBackdrop(art, modifier = Modifier.graphicsLayer { translationY = if (list.firstVisibleItemIndex == 0) -list.firstVisibleItemScrollOffset.toFloat() else -size.height }, video = trailer?.let { t -> { TrailerVideo(t, atTop) } })'''

text = text.replace(old, new)
# also add import if missing
if 'import androidx.compose.ui.graphics.graphicsLayer' not in text:
    text = text.replace('import androidx.compose.ui.Modifier', 'import androidx.compose.ui.Modifier\nimport androidx.compose.ui.graphics.graphicsLayer')

with open('app/src/main/java/com/fitifiti/tv/ui/screens/Detail.kt', 'w', encoding='utf-8') as f:
    f.write(text)
