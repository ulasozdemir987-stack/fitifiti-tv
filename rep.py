import re

with open('app/src/main/java/com/fitifiti/tv/ui/AppRoot.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace(
    'shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),',
    'shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),'
)

with open('app/src/main/java/com/fitifiti/tv/ui/AppRoot.kt', 'w', encoding='utf-8') as f:
    f.write(text)
