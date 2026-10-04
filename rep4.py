import re
with open('app/build.gradle.kts', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('versionCode = 40', 'versionCode = 41').replace('versionName = "2.9.8"', 'versionName = "2.9.9"')

with open('app/build.gradle.kts', 'w', encoding='utf-8') as f:
    f.write(text)
