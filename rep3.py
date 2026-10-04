import re
with open('app/build.gradle.kts', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('versionCode = 38', 'versionCode = 39').replace('versionName = "2.9.6"', 'versionName = "2.9.7"')

with open('app/build.gradle.kts', 'w', encoding='utf-8') as f:
    f.write(text)
