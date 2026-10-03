import os

# Fix ProfilesScreen.kt
path1 = r"c:\Users\planlama\Documents\antigravity\excited-archimedes\fitifiti-tv\app\src\main\java\com\fitifiti\tv\ui\screens\ProfilesScreen.kt"
with open(path1, "r", encoding="utf-8") as f:
    c1 = f.read()

# Add import for key event
if "import androidx.compose.ui.input.key.onKeyEvent" not in c1:
    c1 = c1.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.input.key.onKeyEvent\nimport android.view.KeyEvent")

click_fix1 = """
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                       (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || 
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                        onClick()
                        true
                    } else false
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .focusable(interactionSource = interactionSource)
"""
old_click1 = """
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .focusable(interactionSource = interactionSource)
"""

c1 = c1.replace(old_click1.strip(), click_fix1.strip())
with open(path1, "w", encoding="utf-8") as f:
    f.write(c1)


# Fix HomeScreen.kt
path2 = r"c:\Users\planlama\Documents\antigravity\excited-archimedes\fitifiti-tv\app\src\main\java\com\fitifiti\tv\ui\screens\HomeScreen.kt"
with open(path2, "r", encoding="utf-8") as f:
    c2 = f.read()

if "import androidx.compose.ui.input.key.onKeyEvent" not in c2:
    c2 = c2.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.input.key.onKeyEvent\nimport android.view.KeyEvent")

click_fix2 = """
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                   (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || 
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    onClicked()
                    true
                } else false
            }
            .focusable()
            .clickable { onClicked() }
"""
old_click2 = """
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClicked() }
"""

c2 = c2.replace(old_click2.strip(), click_fix2.strip())
with open(path2, "w", encoding="utf-8") as f:
    f.write(c2)

print("Fixed TV Click events")
