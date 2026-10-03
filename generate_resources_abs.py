import os
from pathlib import Path

def write_file(path, content):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f:
        f.write(content.strip() + '\n')

base_dir = r"c:\Users\planlama\Documents\antigravity\excited-archimedes\fitifiti-tv\app\src\main\res"

# Banner (required for Android TV)
write_file(f"{base_dir}/drawable/app_banner.xml", """
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="320dp"
    android:height="180dp"
    android:viewportWidth="320"
    android:viewportHeight="180">
    <path
        android:fillColor="#8B5CF6"
        android:pathData="M0,0h320v180h-320z"/>
</vector>
""")

# Launcher Icon
write_file(f"{base_dir}/mipmap-anydpi-v26/ic_launcher.xml", """
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#8B5CF6"
        android:pathData="M0,0h108v108h-108z"/>
</vector>
""")

# Backup rules
write_file(f"{base_dir}/xml/backup_rules.xml", """
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <include domain="sharedpref" path="."/>
</full-backup-content>
""")

# Data extraction rules
write_file(f"{base_dir}/xml/data_extraction_rules.xml", """
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <include domain="sharedpref" path="."/>
    </cloud-backup>
    <device-transfer>
        <include domain="sharedpref" path="."/>
    </device-transfer>
</data-extraction-rules>
""")

print("Missing resources created.")
