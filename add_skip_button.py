import os

login_path = r"c:\Users\planlama\Documents\antigravity\excited-archimedes\fitifiti-tv\app\src\main\java\com\fitifiti\tv\ui\screens\LoginScreen.kt"

with open(login_path, "r", encoding="utf-8") as f:
    content = f.read()

# Replace the button area to include a Skip button
old_buttons = """
        Button(onClick = { viewModel.testConnection() }) {
            Text("Bağlantıyı Test Et")
        }
"""

new_buttons = """
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            Button(onClick = { viewModel.testConnection() }) {
                Text("Bağlantıyı Test Et")
            }
            OutlinedButton(onClick = { onLoginSuccess() }) {
                Text("Test Modu (Atla)")
            }
        }
"""

if old_buttons.strip() in content:
    content = content.replace(old_buttons.strip(), new_buttons.strip())
    with open(login_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Added skip button.")
else:
    print("Could not find the button code to replace.")
