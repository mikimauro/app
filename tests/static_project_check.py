from pathlib import Path
import re
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parent.parent
java=list((root/'app/src/main/java').rglob('*.java'))
assert len(java)>=6, 'Java files missing'
xml=list((root/'app/src/main/res').rglob('*.xml'))+[root/'app/src/main/AndroidManifest.xml']
for f in xml:
    ET.parse(f)
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert 'android.intent.action.SEND_MULTIPLE' in manifest
assert 'android.intent.action.SEND' in manifest
assert 'android.permission.INTERNET' not in manifest
assert 'android:allowBackup="false"' in manifest
ui=(root/'app/src/main/java/it/ritrova/app/MainActivity.java').read_text()
for value in ['Salva', 'Cerca', 'Archivio', 'ACTION_SEND', 'ACTION_SEND_MULTIPLE', 'PROCESS_TEXT', 'shareBackup']:
    assert value in ui, value
crypto=(root/'app/src/main/java/it/ritrova/app/PortableCrypto.java').read_text()
assert 'AES/GCM/NoPadding' in crypto
assert 'PBKDF2WithHmacSHA256' in crypto
assert not (root/'app/build/outputs/apk/debug/app-debug.apk').exists(), 'Do not falsely treat nonexistent APK as built'
print(f'OK: {len(java)} sorgenti Java, {len(xml)} XML validi; intent, backup e schermate presenti; nessun permesso Internet.')
