"""Erzeugt Sprecherstimme + Hintergrundmusik für das Conversa-Erklärvideo.

Aufruf:  python build_audio.py de|en
- spricht die Texte mit einer Microsoft-Neural-Stimme ein (Python-Paket edge-tts, braucht Internet)
- passt die Szenenlängen in conversa_erklaervideo.html an die Länge der Sprache an
- erzeugt eine eigene, lizenzfreie Hintergrundmusik (synthetisiert, keine fremden Aufnahmen)
- mischt beides (Musik wird unter der Stimme leiser) zu conversa_ton_<lang>.m4a
"""
import json, math, os, re, struct, subprocess, sys, wave
from array import array

HERE = os.path.dirname(os.path.abspath(__file__))
FFMPEG = os.environ.get('FFMPEG', 'ffmpeg')
BASE_DURS = [5, 6.5, 9.5, 7.5, 6, 6, 5.5]   # Mindestlänge je Szene (Animation)
LEAD = [0.9, 0.6, 0.6, 0.6, 0.6, 0.6, 0.7]   # Pause vor dem Sprechen
TAIL = 0.8                                    # Pause nach dem Sprechen

VOICES = {'de': 'en-US-AvaMultilingualNeural', 'en': 'en-US-AvaNeural'}  # Ava Multilingual spricht auch Deutsch
SCRIPT = {
    'de': [
        'Das ist Conversa: der dezentrale Messenger für deine Privatsphäre. Exklusiv für Android.',
        'Zum Anmelden brauchst du weder E-Mail-Adresse noch Telefonnummer. Benutzername und Passwort genügen. '
        'Auf Wunsch schützt ein zweiter Faktor dein Konto.',
        'Jede Nachricht wird schon auf deinem Handy verschlüsselt. Das Relay unterwegs sieht nur unlesbaren Zeichensalat. '
        'Erst das Gerät des Empfängers kann sie wieder lesen.',
        'Einen zentralen Server gibt es nicht. Nachrichten laufen über mehrere unabhängige Relays. '
        'Fällt eines aus, übernehmen einfach die anderen.',
        'Sprach- und Videoanrufe gehen direkt von Gerät zu Gerät, verschlüsselt mit WebRTC.',
        'Dazu kommen Zwei-Faktor-Schutz, Tor-Unterstützung, keine Werbung und kein Tracking. '
        'Und der gesamte Quellcode ist offen.',
        'Conversa gibt es nur für Android, ab Version dreizehn. Eine iPhone-Version gibt es nicht. '
        'Die App ist kostenlos auf GitHub. Es ist ein Hobbyprojekt, die Nutzung erfolgt auf eigenes Risiko.',
    ],
    'en': [
        'This is Conversa, the decentralized messenger for your privacy. Available exclusively for Android.',
        "To sign in, you don't need an email address or a phone number. A username and password are all it takes. "
        'If you like, a second factor protects your account.',
        "Every message is encrypted right on your phone. Along the way, the relay only sees unreadable gibberish. "
        "Only the recipient's device can read it again.",
        'There is no central server. Messages travel through several independent relays. '
        'If one goes down, the others simply take over.',
        'Voice and video calls go directly from device to device, encrypted with WebRTC.',
        'On top of that: two-factor protection, Tor support, no ads and no tracking. And all of the source code is open.',
        "Conversa is available only for Android, version 13 and up. There is no iPhone version. "
        "You can get it for free on GitHub. It's a hobby project, so use it at your own risk.",
    ],
}


def audio_duration(path):
    out = subprocess.run([FFMPEG.replace('ffmpeg', 'ffprobe'), '-v', 'error', '-show_entries', 'format=duration',
                          '-of', 'csv=p=0', path], check=True, capture_output=True, text=True).stdout
    return float(out.strip())


def speak(lang, outdir):
    os.makedirs(outdir, exist_ok=True)
    clips = []
    for i, t in enumerate(SCRIPT[lang]):
        out = os.path.join(outdir, f'sprecher_{i + 1}.mp3')
        subprocess.run([sys.executable, '-m', 'edge_tts', '--voice', VOICES[lang], '--rate=-4%',
                        '--text', t, '--write-media', out], check=True, capture_output=True)
        clips.append(out)
    return clips


def update_timings(lang, durs):
    html = os.path.join(HERE, 'conversa_erklaervideo.html')
    s = open(html, encoding='utf-8').read()
    m = re.search(r'const TIMINGS = (\{.*?\});', s)
    timings = json.loads(m.group(1))
    timings[lang] = durs
    s = s[:m.start(1)] + json.dumps(timings) + s[m.end(1):]
    open(html, 'w', encoding='utf-8', newline='\n').write(s)


# ---------- Musik (ruhiger Ambient-Track: Am – F – C – G) ----------
SR = 44100


def note(n):  # MIDI -> Hz
    return 440.0 * 2 ** ((n - 69) / 12)


def make_music(path, dur):
    bpm = 92.0
    beat = 60 / bpm
    bar = beat * 4
    chords = [[57, 60, 64, 67], [53, 57, 60, 64], [48, 55, 60, 64], [55, 59, 62, 67]]  # Am7, Fmaj7, C(add9), G
    roots = [45, 41, 48, 43]
    chord_len = bar * 2
    n = int(dur * SR)
    L = array('f', [0.0]) * n
    R = array('f', [0.0]) * n
    tau = 2 * math.pi

    # Flächenklang (Pad): leicht verstimmte Sinustöne mit weichem Ein-/Ausblenden
    n_chords = int(dur / chord_len) + 2
    for ci in range(n_chords):
        c = chords[ci % 4]
        start = ci * chord_len - 0.6
        end = start + chord_len + 1.2
        s0, s1 = max(0, int(start * SR)), min(n, int(end * SR))
        freqs = []
        for k, m in enumerate(c):
            f = note(m)
            freqs += [(f * 1.0015, 0.050, -0.3 + 0.2 * k), (f * 0.9985, 0.050, 0.3 - 0.2 * k), (f * 2, 0.010, 0.0)]
        bass = note(roots[ci % 4])
        freqs += [(bass, 0.09, 0.0), (bass * 2, 0.025, 0.0)]
        for i in range(s0, s1):
            t = i / SR
            u = (t - start) / (end - start)
            env = max(0.0, math.sin(math.pi * u)) ** 1.5
            acc_l = acc_r = 0.0
            for f, a, pan in freqs:
                v = a * math.sin(tau * f * t)
                acc_l += v * (1 - pan) * 0.5
                acc_r += v * (1 + pan) * 0.5
            L[i] += acc_l * env
            R[i] += acc_r * env

    # Arpeggio: sanfte, gezupfte Achtelnoten
    step = beat / 2
    pattern = [0, 2, 1, 3, 2, 1, 3, 2]
    k = 0
    t0 = bar * 1  # Arpeggio setzt nach einem Takt ein
    while t0 < dur - 1.0:
        ci = int(t0 / chord_len)
        c = chords[ci % 4]
        f = note(c[pattern[k % 8]] + 12)
        pan = -0.5 if k % 2 == 0 else 0.5
        amp = 0.045 * (0.75 + 0.25 * math.sin(k * 0.7))
        s0, s1 = int(t0 * SR), min(n, int((t0 + 1.2) * SR))
        for i in range(s0, s1):
            tt = (i - s0) / SR
            e = math.exp(-tt * 5.0) * min(1, tt * 200)
            v = amp * e * (math.sin(tau * f * tt) + 0.25 * math.sin(tau * 2 * f * tt))
            L[i] += v * (1 - pan) * 0.5
            R[i] += v * (1 + pan) * 0.5
        t0 += step
        k += 1

    # Ein- und Ausblenden, normieren
    fade_in, fade_out = 2.0 * SR, 3.0 * SR
    peak = 1e-9
    for i in range(n):
        g = min(1.0, i / fade_in, (n - i) / fade_out)
        L[i] *= g
        R[i] *= g
        peak = max(peak, abs(L[i]), abs(R[i]))
    scale = 0.8 / peak
    with wave.open(path, 'wb') as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for i in range(n):
            frames += struct.pack('<hh', int(L[i] * scale * 32767), int(R[i] * scale * 32767))
        w.writeframes(bytes(frames))


def main():
    lang = sys.argv[1] if len(sys.argv) > 1 else 'de'
    outdir = os.path.join(HERE, f'ton_{lang}')
    clips = speak(lang, outdir)
    lens = [audio_duration(c) for c in clips]
    durs = [round(max(b, LEAD[i] + lens[i] + TAIL), 2) for i, b in enumerate(BASE_DURS)]
    durs[-1] = round(durs[-1] + 0.8, 2)  # Abspann etwas länger stehen lassen
    total = sum(durs)
    update_timings(lang, durs)
    print(f'{lang}: Sprachlängen {[round(x, 1) for x in lens]} -> Szenen {durs} = {total:.1f} s')

    music = os.path.join(outdir, 'musik.wav')
    make_music(music, total)

    starts = [sum(durs[:i]) for i in range(len(durs))]
    inputs, filters, labels = ['-i', music], [], []
    for i, c in enumerate(clips):
        inputs += ['-i', c]
        ms = int((starts[i] + LEAD[i]) * 1000)
        filters.append(f'[{i + 1}:a]aresample=44100,aformat=channel_layouts=stereo,adelay={ms}|{ms},apad[v{i}]')
        labels.append(f'[v{i}]')
    filters.append(f'{"".join(labels)}amix=inputs={len(clips)}:normalize=0:duration=longest,atrim=0:{total},'
                   f'highpass=f=80,acompressor=threshold=0.1:ratio=3,volume=1.6[voice]')
    filters.append('[voice]asplit=2[vo][sc]')
    filters.append(f'[0:a]volume=0.32,aecho=0.8:0.6:120|240:0.25|0.15,atrim=0:{total}[mu]')
    filters.append('[mu][sc]sidechaincompress=threshold=0.03:ratio=5:attack=40:release=600[duck]')
    filters.append('[duck][vo]amix=inputs=2:normalize=0,loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000[out]')
    out = os.path.join(HERE, f'conversa_ton_{lang}.m4a')
    subprocess.run([FFMPEG, '-y', '-loglevel', 'error', *inputs, '-filter_complex', ';'.join(filters),
                    '-map', '[out]', '-t', str(total), '-c:a', 'aac', '-b:a', '192k', out], check=True)
    print('Ton fertig:', out)


if __name__ == '__main__':
    main()
