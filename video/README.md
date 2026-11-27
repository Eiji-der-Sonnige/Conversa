# Erklärvideos

Quellen der Conversa-Erklärvideos (Deutsch/Englisch, 16:9 und als 9:16-Short). Die fertigen Videos
liegen nicht im Repository, sondern als Download im jeweiligen GitHub-Release.

| Datei | Inhalt |
|---|---|
| `conversa_erklaervideo.html` | Szenen, Texte und Animation (16:9, mit `?format=short` im Hochformat) |
| `build_audio.py` | Sprechertexte und Musik → Tonspur (`python build_audio.py de` bzw. `en`) |
| `render.mjs` | Rendert die Szenen zur Tonspur als MP4 (`node render.mjs --lang de [--short]`) |

Erzeugte Dateien (`*.mp4`, `*.m4a`, `ton_*/`) sind in `.gitignore` ausgenommen.
