# Conversa – Dokumentation

**Version:** 1.2.0 (Build 4)
**Plattform:** Android 13 (API 33) bis aktuelle Android-Versionen (targetSdk 37)
**Stand:** 21.11.2026

Conversa ist ein dezentraler Messenger, der auf Anonymität und Ende-zu-Ende-Verschlüsselung ausgelegt
ist. Er unterstützt Textnachrichten, Bilder, Videos sowie Sprach- und Videoanrufe. Conversa ist ein nicht-kommerzielles Open-Source-Hobbyprojekt (Apache License 2.0). Die Entwickler betreiben
keinen eigenen Server; für die Registrierung sind keine E-Mail-Adresse und keine Telefonnummer nötig.

> **Wichtiger Hinweis:** Vollständige Anonymität und absolute Sicherheit können nicht garantiert werden.
> Externe Relays, Medien- und STUN-Server sehen technisch bedingt Verbindungsdaten wie die IP-Adresse.
> Textnachrichten haben keine Forward Secrecy, und die Software wurde bisher nur gebaut und mit
> Unit-Tests geprüft, aber keinem unabhängigen Sicherheits-Audit unterzogen. Die Nutzung erfolgt auf
> eigenes Risiko. Details: [Abschnitt 17](#17-bekannte-einschränkungen) und [Abschnitt 21](#21-rechtliches).

---

## Inhaltsverzeichnis

1. [Funktionsübersicht](#1-funktionsübersicht)
2. [Bedienungsanleitung](#2-bedienungsanleitung)
3. [Architektur](#3-architektur)
4. [Projektstruktur](#4-projektstruktur)
5. [Identität & Anmeldung](#5-identität--anmeldung)
6. [Zwei-Faktor-Authentifizierung (2FA)](#6-zwei-faktor-authentifizierung-2fa)
7. [Kryptografie](#7-kryptografie)
8. [Netzwerk & Protokoll](#8-netzwerk--protokoll)
9. [Nachrichtenfluss](#9-nachrichtenfluss)
10. [Bilder & Videos](#10-bilder--videos)
11. [Sprach- & Videoanrufe](#11-sprach---videoanrufe)
12. [Datenspeicherung](#12-datenspeicherung)
13. [Sicherheitsmaßnahmen](#13-sicherheitsmaßnahmen)
14. [Einstellungen](#14-einstellungen)
15. [Berechtigungen](#15-berechtigungen)
16. [Build, Tests & Entwicklung](#16-build-tests--entwicklung)
17. [Bekannte Einschränkungen](#17-bekannte-einschränkungen)
18. [Mögliche Erweiterungen](#18-mögliche-erweiterungen)
19. [Abhängigkeiten & Lizenzen](#19-abhängigkeiten--lizenzen)
20. [Versionshistorie](#20-versionshistorie)
21. [Rechtliches](#21-rechtliches)

---

## 1. Funktionsübersicht

| Funktion | Beschreibung |
|---|---|
| Anmeldung | Benutzername + Passwort; der Schlüssel wird lokal berechnet (kein Server) |
| 2FA | TOTP, kompatibel mit Google Authenticator; Abfrage bei Login und App-Start |
| Textchat | Ende-zu-Ende-verschlüsselt (NIP-44), Metadaten verborgen (Gift Wrap) |
| Bilder & Videos | Lokal mit AES-256-GCM verschlüsselt, bis 50 MB pro Datei |
| Sprach- & Videoanrufe | WebRTC Peer-to-Peer, DTLS-SRTP-verschlüsselt |
| Dezentralität | Mehrere unabhängige Nostr-Relays; Medien auf Blossom-Servern (austauschbare Voreinstellungen, von Dritten betrieben) |
| Multi-Device | Gleiche Zugangsdaten = gleiches Konto; Verlauf und Kontakte werden synchronisiert, soweit die Relays sie speichern |
| Datensparsamkeit | Keine persönlichen Daten zur Registrierung; pseudonyme Kennung (öffentlicher Schlüssel); optional Tor (Orbot) und TURN-only-Anrufe zum Verbergen der IP-Adresse |
| Spam-Schutz | Unbekannte Absender erscheinen als „Nachrichtenanfrage“; Anrufe erst nach Annahme |
| Info & Rechtliches | Versionsnummer, Geräte- und Sicherheitsinfos, Nutzungsbedingungen, Datenschutzhinweise, vollständige Open-Source-Lizenztexte |

---

## 2. Bedienungsanleitung

### 2.1 Konto erstellen
1. App öffnen, Schalter **„Neues Konto erstellen“** aktivieren.
2. Benutzername (mind. 3 Zeichen) und Passwort eingeben und wiederholen.
   Passwortregeln: mindestens 12 Zeichen und mindestens 3 von 4 Zeichenklassen
   (Klein-, Großbuchstaben, Ziffern, Sonderzeichen).
3. Nutzungsbedingungen und Datenschutzhinweise lesen und das Kästchen zur Bestätigung anhaken (wird auch bei jeder Anmeldung verlangt).
4. **„Konto erstellen“** tippen. Die Schlüsselberechnung dauert einige Sekunden.

> ⚠ **Das Passwort kann nicht zurückgesetzt werden.** Ohne Passwort ist das Konto unwiederbringlich verloren.

### 2.2 Anmelden (auch auf einem neuen Gerät)
Benutzername und Passwort eingeben, **„Anmelden“** tippen. Kontakte, 2FA-Einstellung und Nachrichtenverlauf
werden aus dem Netzwerk geladen (soweit die Relays sie noch speichern).

### 2.3 Kontakte hinzufügen
1. **Einstellungen → Deine Kontakt-ID**: eigene ID (`npub1…`) kopieren oder QR-Code zeigen.
2. Die andere Person tippt in der Chatliste auf **„+“**, fügt die ID ein und vergibt einen Namen.
   Der Name ist nur lokal sichtbar.

### 2.4 Chatten
- Text eingeben und auf **Senden** tippen.
- **Büroklammer**: Bild oder Video auswählen (Android-Fotoauswahl, keine Speicherberechtigung nötig).
- Statusanzeige: ⏳ wird gesendet · ✓ von mindestens einem Relay bestätigt · ⚠ fehlgeschlagen (antippen = erneut senden).
- Menü (⋮): Kontakt umbenennen oder Kontakt und Chat löschen.

### 2.5 Anrufen
Im Chat auf **Hörer** (Sprachanruf) oder **Kamera** (Videoanruf) tippen. Während des Gesprächs stehen
Mikrofon, Kamera, Kamerawechsel, Lautsprecher und Auflegen zur Verfügung. Ein Anruf ohne Antwort endet nach 45 Sekunden.

### 2.6 2FA einrichten
**Einstellungen → Zwei-Faktor-Authentifizierung → „2FA aktivieren“**, dann QR-Code mit Google Authenticator
scannen (oder „In Authenticator-App öffnen“ bzw. den Schlüssel manuell eingeben) und den 6-stelligen Code bestätigen.

### 2.7 Abmelden
**Einstellungen → „Abmelden & lokale Daten löschen“** löscht alle lokalen Daten und den Keystore-Schlüssel.

---

## 3. Architektur

```
┌────────────────────────── Android-App ──────────────────────────┐
│  UI (Jetpack Compose)                                           │
│   Login · 2FA · Chatliste · Chat · Anruf · Einstellungen        │
│                    │                                            │
│              MainViewModel  (Navigation, Login, 2FA)            │
│                    │                                            │
│               Messenger  (zentrale Logik)                       │
│   ┌───────────┬────────────┬──────────────┬──────────────┐      │
│   │ RelayPool │ ChatStore  │ MediaService │ CallManager  │      │
│   │ (Nostr)   │ (lokal)    │ (Blossom)    │ (WebRTC)     │      │
│   └─────┬─────┴─────┬──────┴──────┬───────┴──────┬───────┘      │
│         │      Vault/Keystore     │              │              │
└─────────┼─────────────────────────┼──────────────┼──────────────┘
          │ WSS (TLS)               │ HTTPS        │ DTLS-SRTP (P2P)
          ▼                         ▼              ▼
   Nostr-Relays (mehrere)    Blossom-Server   Gesprächspartner
   verschlüsselte Events     verschl. Blobs   (via STUN/TURN)
```

**Prinzip:** Alle Inhalte werden auf dem Gerät verschlüsselt, bevor sie es verlassen. Server
(Relays, Medien-Server, STUN/TURN) speichern bzw. leiten nur verschlüsselte Inhalte weiter und sind
austauschbar. Sie werden von unabhängigen Dritten betrieben, nicht von den Entwicklern der App. Technisch
bedingt sehen sie Verbindungs- und Metadaten wie IP-Adresse, Zeitpunkt, Datenmenge und den öffentlichen
Schlüssel des Empfängers.

---

## 4. Projektstruktur

```
app/src/main/java/io/github/eiji_der_sonnige/conversa/
├── ConversaApp.kt            Application (Benachrichtigungskanal)
├── MainActivity.kt           Activity, FLAG_SECURE, Vordergrund-Status
├── MainViewModel.kt          Bildschirme, Login, 2FA, Einstellungen
├── crypto/
│   ├── Hex.kt                Hex, SHA-256, sicherer Zufall
│   ├── Bech32.kt             npub-Kodierung (BIP-173)
│   ├── Secp256k1.kt          BIP-340 Schnorr, ECDH
│   ├── Nip44.kt              NIP-44 v2 Verschlüsselung
│   ├── Identity.kt           Argon2id-Schlüsselableitung, Passwortregeln
│   ├── Totp.kt               RFC 6238 TOTP, Base32
│   └── Vault.kt              Android Keystore (AES-256-GCM)
├── nostr/
│   ├── Event.kt              Nostr-Event, NIP-01-Serialisierung, Signatur
│   ├── RelayPool.kt          WebSocket-Verbindungen, Abos, Abfragen
│   └── GiftWrap.kt           NIP-17/NIP-59 (Rumor → Seal → Gift Wrap)
├── data/
│   ├── Models.kt             ChatMessage, Media, Contact
│   ├── ChatStore.kt          Kontakte & Nachrichten (verschlüsselt)
│   ├── SecureFiles.kt        Verschlüsselte Dateien, sicheres Löschen
│   └── AppSettings.kt        Relays, Medien-Server, TURN, Tor
├── media/MediaService.kt     Verschlüsselung, Upload/Download (Blossom)
├── call/CallManager.kt       WebRTC-Anrufe, Signalisierung
├── core/
│   ├── Messenger.kt          Senden/Empfangen, Kontosynchronisation
│   └── Notifier.kt           Benachrichtigungen ohne Inhalt
└── ui/                       Compose-Bildschirme
    ├── Root.kt, LoginScreen.kt, TotpScreens.kt, ChatListScreen.kt,
    ├── ChatScreen.kt, CallScreen.kt, SettingsScreen.kt, Common.kt

app/src/test/java/io/github/eiji_der_sonnige/conversa/CryptoTest.kt   Krypto-Unit-Tests
app/src/main/res/xml/network_security_config.xml       Nur TLS
app/src/main/res/xml/data_extraction_rules.xml         Keine Backups
```

---

## 5. Identität & Anmeldung

Es gibt keine Registrierung auf einem Server. Die Identität ist ein **secp256k1-Schlüsselpaar**, das
deterministisch aus den Zugangsdaten berechnet wird:

```
salt     = SHA-256("conversa-identity-v1:" + benutzername.trim().lowercase())
privKey  = Argon2id(passwort, salt, m = 64 MB, t = 3, p = 2, Länge = 32 Byte)
pubKey   = x-Koordinate von privKey · G       (32 Byte, x-only, BIP-340)
Kontakt-ID = Bech32("npub", pubKey)
```

- Gleiche Zugangsdaten ergeben auf jedem Gerät dasselbe Konto.
- Ein Konto mit denselben Daten „neu zu erstellen“ ist gleichbedeutend mit Anmelden.
- Der Benutzername wird nirgends veröffentlicht.
- Nach dem Login wird der private Schlüssel mit dem Keystore verschlüsselt lokal gespeichert
  (`session.json`), damit nicht bei jedem Start neu abgeleitet werden muss.

---

## 6. Zwei-Faktor-Authentifizierung (2FA)

- **Verfahren:** TOTP nach RFC 6238 (HMAC-SHA1, 6 Ziffern, 30 s), Geheimnis 160 Bit, Base32.
- **Toleranz:** ±1 Zeitfenster (±30 s); zeitkonstanter Vergleich.
- **Brute-Force-Schutz:** Nach 5 Fehlversuchen Sperre von 30 s, danach exponentiell steigend.
- **Speicherung:** Das Geheimnis liegt verschlüsselt (NIP-44, nur für dich lesbar) im Konto-Event auf den
  Relays und zusätzlich verschlüsselt im lokalen Speicher. Dadurch gilt 2FA auch auf neuen Geräten.
- **Abfrage:** beim Login (vor dem Speichern der Sitzung) und bei jedem App-Kaltstart.
- **Deaktivieren:** nur mit gültigem aktuellem Code.

> **Hinweis:** Da es keinen Server gibt, wird die 2FA in der App selbst durchgesetzt. Sie schützt
> zuverlässig gegen fremden Zugriff auf das Gerät bzw. die App. Wer das Passwort kennt und eine
> modifizierte App verwendet, kann sie umgehen. Der Hauptschutz ist daher ein starkes Passwort.

---

## 7. Kryptografie

| Zweck | Verfahren | Datei |
|---|---|---|
| Schlüsselableitung | Argon2id (64 MB, 3 Iterationen) | `crypto/Identity.kt` |
| Signaturen | Schnorr / BIP-340 auf secp256k1 | `crypto/Secp256k1.kt` |
| Schlüsselaustausch | ECDH secp256k1 (x-Koordinate) | `crypto/Secp256k1.kt` |
| Nachrichten | NIP-44 v2: HKDF-SHA256, ChaCha20, HMAC-SHA256, Padding | `crypto/Nip44.kt` |
| Metadatenschutz | Gift Wrap NIP-59 / Private DMs NIP-17 | `nostr/GiftWrap.kt` |
| Medien | AES-256-GCM, zufälliger Schlüssel & Nonce pro Datei, SHA-256-Prüfung | `media/MediaService.kt` |
| Anrufe | WebRTC DTLS-SRTP | `call/CallManager.kt` |
| Lokale Daten | AES-256-GCM, Schlüssel im Android Keystore (TEE) | `crypto/Vault.kt` |
| 2FA | TOTP RFC 6238 (HMAC-SHA1) | `crypto/Totp.kt` |

### 7.1 NIP-44 v2 im Detail
```
conversation_key = HMAC-SHA256(key = "nip44-v2", ECDH_x(meinPriv, seinPub))
nonce            = 32 zufällige Bytes
(chacha_key, chacha_nonce, hmac_key) = HKDF-Expand(conversation_key, info = nonce, 76 Byte)
padded           = Länge (2 Byte, big-endian) || Klartext || Nullen bis zur Padding-Größe
ciphertext       = ChaCha20(chacha_key, chacha_nonce, padded)
mac              = HMAC-SHA256(hmac_key, nonce || ciphertext)
payload          = Base64(0x02 || nonce || ciphertext || mac)
```
Beim Entschlüsseln wird zuerst der MAC zeitkonstant geprüft; manipulierte Nachrichten werden verworfen.

### 7.2 Gift Wrap (dreistufig)
1. **Rumor** (Kind 14/15/25050): echter Inhalt, `pubkey` = Absender, **unsigniert** (abstreitbar).
2. **Seal** (Kind 13): Rumor mit NIP-44 verschlüsselt, vom Absender signiert, Zeitstempel zufällig bis 2 Tage zurück.
3. **Gift Wrap** (Kind 1059): Seal mit NIP-44 an den Empfänger verschlüsselt, signiert mit einem
   **Einmal-Schlüssel**, Tag `["p", Empfänger]`.

Beim Entpacken wird geprüft: Seal-Signatur gültig, Rumor-ID korrekt und Rumor-Autor = Seal-Signierer
(Schutz vor Identitätsfälschung).

### 7.3 Verifikation
Die Implementierungen werden in `CryptoTest.kt` gegen offizielle Testvektoren geprüft:
BIP-340 (Vektor 0), NIP-44 (Konversationsschlüssel und Verschlüsselung), RFC 6238, Bech32-Roundtrip und
Gift-Wrap-Roundtrip inklusive „Dritte können nicht entschlüsseln“.

---

## 8. Netzwerk & Protokoll

### 8.1 Warum Nostr statt Blockchain?
Auf einer Blockchain wären Daten dauerhaft öffentlich, Transaktionen kosten Gebühren und sind langsam,
und die Kommunikationsbeziehungen wären sichtbar. Nostr ist in der Regel kostenlos nutzbar, dezentral
(viele unabhängige Relays), schwer zu zensieren und unterstützt verschlüsselte private Nachrichten,
bei denen Absender und Inhalt gegenüber den Relays verborgen sind.

### 8.2 Standard-Relays
```
wss://relay.damus.io
wss://nos.lol
wss://relay.primal.net
wss://nostr.mom
wss://relay.snort.social
```
> **Hinweis:** Diese Relays sind lediglich austauschbare Standard-Voreinstellungen. Sie werden von
> unabhängigen Dritten betrieben. Die Entwickler von Conversa betreiben sie nicht und haben keinen Einfluss auf
> ihre Verfügbarkeit, Speicherdauer, Inhalte oder Datenverarbeitung. Die Liste kann in den Einstellungen
> beliebig geändert werden, z. B. auf selbst betriebene Relays.
Die App verbindet sich mit allen gleichzeitig. Bei Verbindungsabbruch wird automatisch neu verbunden
(exponentielles Backoff 4 s bis 60 s). Events werden an alle verbundenen Relays gesendet und gepuffert,
falls gerade keines erreichbar ist.

### 8.3 Verwendete Event-Arten
| Kind | Bedeutung | Sichtbar für Relays |
|---|---|---|
| 1059 | Gift Wrap (Hülle für alles Private) | nur Empfänger-Schlüssel |
| 13 | Seal (innerhalb des Gift Wrap) | nein |
| 14 | Textnachricht (Rumor) | nein |
| 15 | Datei/Bild/Video (Rumor) | nein |
| 25050 | Anruf-Signalisierung (Rumor), Gift Wrap läuft nach 120 s ab | nein |
| 30078 | Konto-Daten (Kontakte, 2FA), NIP-44 an sich selbst | nur Autor; d-Tag ist ein undurchsichtiger Hash |
| 24242 | Blossom-Upload-Autorisierung (Einmal-Schlüssel) | nur Medien-Server |

### 8.4 Abonnement
```json
["REQ", "inbox", {"kinds":[1059], "#p":["<eigener pubkey>"], "since": <letzte Sync − 3 Tage>}]
```
Beim ersten Login ohne `since`, dafür mit `limit: 2000`, um den Verlauf wiederherzustellen.
Jedes empfangene Event wird auf gültige ID und Signatur geprüft; ungültige werden verworfen.

---

## 9. Nachrichtenfluss

**Senden**
1. Rumor erzeugen, lokal mit Status `SENDING` speichern.
2. Zwei Gift Wraps erzeugen: einen für den Empfänger, einen für sich selbst (für Multi-Device-Verlauf).
3. An alle Relays senden.
4. Bestätigt ein Relay (`OK`), wird der Status `SENT` (✓). Nach 20 s ohne Bestätigung: `FAILED`.

**Empfangen**
1. Gift Wrap entpacken und prüfen.
2. Unbekannter Absender: Kontakt als „Nachrichtenanfrage“ (`accepted = false`) anlegen.
3. Nachricht speichern (Duplikate werden über die Rumor-ID erkannt).
4. Benachrichtigung ohne Inhalt und Absender, falls der Chat nicht offen ist.

---

## 10. Bilder & Videos

1. Auswahl über die Android-Fotoauswahl (max. 50 MB).
2. Verschlüsselung mit einem zufälligen AES-256-GCM-Schlüssel und einer 12-Byte-Nonce.
3. Upload des verschlüsselten Blobs per `PUT /upload` an einen Blossom-Server
   (Standard-Voreinstellung: `blossom.primal.net`, `blossom.band`; von Dritten betrieben, in den
   Einstellungen austauschbar). Die Autorisierung wird mit einem **Einmal-Schlüssel** signiert, sodass der
   Server den Upload nicht dem Nostr-Konto zuordnen kann. IP-Adresse, Zeitpunkt und Dateigröße sind für
   den Server dennoch sichtbar.
4. Versand als Rumor Kind 15 mit den Tags `file-type`, `encryption-algorithm`, `decryption-key`,
   `decryption-nonce`, `x` (SHA-256 des Blobs) und `size`.
5. Beim Empfang: Download (Fallback über alle konfigurierten Server per SHA-256), Integritätsprüfung,
   Entschlüsselung. Bilder werden im Speicher entschlüsselt, Videos zur Wiedergabe kurzzeitig in den
   privaten App-Cache (`media_tmp`, wird bei jedem Start gelöscht).

---

## 11. Sprach- & Videoanrufe

- **Transport:** WebRTC, direkt Peer-to-Peer, verschlüsselt mit DTLS-SRTP.
- **NAT-Durchdringung:** STUN-Server von Google (`stun.l.google.com`) und Cloudflare
  (`stun.cloudflare.com`) als Voreinstellung, optional eigener TURN-Server. Diese Betreiber erhalten dabei
  die IP-Adresse; die Entwickler von Conversa betreiben diese Server nicht.
- **IP-Adresse:** Bei direkten P2P-Anrufen sieht der Gesprächspartner die IP-Adresse. Abhilfe: eigener
  TURN-Server mit der Option „Nur über TURN“ (dann sieht der TURN-Betreiber die IP-Adresse).
- **Signalisierung:** JSON-Nachrichten in Gift Wraps (Kind 25050), die nur 60 s lang angenommen werden:

| Typ | Inhalt |
|---|---|
| `offer` | SDP-Angebot, `video: true/false`, `callId` |
| `answer` | SDP-Antwort |
| `candidates` | gebündelte ICE-Kandidaten (alle 600 ms) |
| `hangup` / `reject` / `busy` | Beenden, Ablehnen, besetzt |

Weil die Signalisierung Ende-zu-Ende-verschlüsselt und signiert ist, sind die DTLS-Fingerprints
authentifiziert. Das schützt gegen Man-in-the-Middle-Angriffe durch Relays oder das Netzwerk, solange
die Schlüssel der beiden Teilnehmer nicht kompromittiert sind. Anrufe von nicht angenommenen
Kontakten werden ignoriert.

---

## 12. Datenspeicherung

| Ort | Inhalt | Schutz |
|---|---|---|
| Nostr-Relays | Gift Wraps (Nachrichten), Konto-Event | NIP-44 / Gift Wrap |
| Blossom-Server | Mediendateien | AES-256-GCM |
| `files/secure/session.json` | privater Schlüssel, Benutzername | Keystore AES-256-GCM |
| `files/secure/chats_<id>.json` | Kontakte, Nachrichten, 2FA-Cache, Sync-Zeitpunkt | Keystore AES-256-GCM |
| `files/secure/settings.json` | Netzwerkeinstellungen | Keystore AES-256-GCM |
| `cache/media_enc/` | heruntergeladene Medien (verschlüsselt) | AES-256-GCM |
| `cache/media_tmp/` | entschlüsselte Videos zur Wiedergabe | App-Sandbox, wird beim Start gelöscht |

Dateien werden atomar geschrieben (`AtomicFile`). Beim Abmelden werden alle Verzeichnisse und der
Keystore-Schlüssel gelöscht.

---

## 13. Sicherheitsmaßnahmen

Die folgenden Maßnahmen reduzieren Risiken, schließen sie aber nicht vollständig aus
(siehe [Abschnitt 17](#17-bekannte-einschränkungen)).

- Ende-zu-Ende-Verschlüsselung aller Inhalte; Server sehen nur Chiffretext.
- Reduzierte Metadaten: Absender und Inhalt sind für Relays verborgen, der Zeitpunkt wird verschleiert,
  die Nachrichtenlänge nur grob erkennbar (Padding). Sichtbar bleiben u. a. IP-Adresse,
  Verbindungszeitpunkte und der öffentliche Schlüssel des Empfängers.
- Prüfung jeder Signatur und Event-ID; Schutz vor Identitätsfälschung beim Entpacken.
- Lokale Verschlüsselung mit hardwaregestütztem Keystore-Schlüssel.
- `FLAG_SECURE`: keine Screenshots, Bildschirmaufnahmen oder Vorschau im App-Umschalter.
- Keine Backups (`allowBackup=false`, Datenextraktionsregeln schließen alles aus).
- Nur TLS-Verbindungen, nur System-Zertifikate (`network_security_config.xml`).
- Benachrichtigungen ohne Inhalt und Absender (`VISIBILITY_PRIVATE`).
- 2FA-Geheimnis wird in der Zwischenablage als sensibel markiert.
- Starke Passwortregeln, speicherharte Schlüsselableitung (Argon2id).
- Rate-Limiting bei 2FA-Codes.
- Spam-/Belästigungsschutz durch Nachrichtenanfragen.
- Optional: Tor (Orbot) für alle Verbindungen, TURN-only-Anrufe zum Verbergen der IP-Adresse.
- Keine Werbung, kein Tracking, keine Analyse, keine Google-Dienste.

---

## 14. Einstellungen

| Bereich | Option |
|---|---|
| Deine Kontakt-ID | npub als QR-Code und Text, kopieren |
| 2FA | aktivieren / deaktivieren |
| Netzwerk | Relay-Liste (`wss://`), Medien-Server (`https://`), Tor über Orbot (SOCKS `127.0.0.1:9050`) |
| Anrufe | TURN-URL, -Benutzer, -Passwort; „Nur über TURN“ |
| Info | Version, Build, Gerät, Android-Version, Installations-/Update-Datum, Paketname, Relay-Status, Verschlüsselungsverfahren, Open-Source-Lizenzen |
| Abmelden | löscht alle lokalen Daten |

Nach dem Speichern der Netzwerkeinstellungen wird die Verbindung mit den neuen Werten neu aufgebaut.

---

## 15. Berechtigungen

| Berechtigung | Zweck |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Relays, Medien-Server, Anrufe |
| `CAMERA` | Videoanrufe (wird erst beim Anruf angefragt) |
| `RECORD_AUDIO` | Anrufe (wird erst beim Anruf angefragt) |
| `MODIFY_AUDIO_SETTINGS` | Lautsprecher/Hörer umschalten |
| `POST_NOTIFICATIONS` | Hinweis auf neue Nachrichten |

Für die Medienauswahl ist keine Speicherberechtigung nötig (Android-Fotoauswahl).

---

## 16. Build, Tests & Entwicklung

**Voraussetzungen:** Android Studio (mit JBR/Java 17+), Android SDK 37, Gradle 9.6 (Wrapper), AGP 9.5.2, Kotlin 2.2.

```powershell
# Java aus Android Studio verwenden
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Unit-Tests (Kryptografie)
.\gradlew.bat :app:testDebugUnitTest

# Debug-APK  ->  app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:assembleDebug

# Release-APK (vor Veröffentlichung signieren!)
.\gradlew.bat :app:assembleRelease
```

**Release signieren:**
1. Einmalig einen Signaturschlüssel erzeugen (außerhalb des Projektordners speichern, z. B.
   `C:\Users\<Name>\Keystores\conversa-release.jks`):
   ```powershell
   New-Item -ItemType Directory -Force "$env:USERPROFILE\Keystores"
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
     -keystore "$env:USERPROFILE\Keystores\conversa-release.jks" `
     -alias conversa -keyalg RSA -keysize 4096 -validity 10000
   ```
2. `keystore.properties.example` nach `keystore.properties` kopieren und ausfüllen
   (Pfade mit `/` schreiben). Diese Datei ist in `.gitignore` und wird nie hochgeladen.
3. `.\gradlew.bat :app:assembleRelease` → `app\build\outputs\apk\release\app-release.apk`
4. Schlüsseldatei und Passwörter sicher sichern (z. B. Passwort-Manager + USB-Stick). Ohne diesen
   Schlüssel können keine Updates mehr veröffentlicht werden, die sich über die alte Version
   installieren lassen.

**Paketname:** `io.github.eiji_der_sonnige.conversa` (Bindestriche sind in Paketnamen nicht erlaubt).

**Version erhöhen:** `app/build.gradle.kts` → `versionCode` und `versionName`.
**App-Symbol:** adaptive Icons in `res/mipmap-*/ic_launcher_foreground.png` und
`ic_launcher_monochrome.png`, Hintergrundfarbe in `res/values/ic_launcher_background.xml`.

**Testen mit zwei Geräten:** App auf zwei Geräten/Emulatoren installieren, je ein Konto erstellen,
Kontakt-IDs austauschen, Nachrichten, Medien und Anrufe ausprobieren.

---

## 17. Bekannte Einschränkungen

> **Nutzung auf eigenes Risiko.** Conversa ist auf Anonymität und Ende-zu-Ende-Verschlüsselung
> ausgelegt, gibt aber keine Garantie für vollständige Anonymität oder Sicherheit.

- **Metadaten bei Dritt-Servern:** Relays, Blossom- und STUN-Server erhalten technisch bedingt die
  IP-Adresse (ohne Tor) sowie Zeitpunkte, Datenmengen und den öffentlichen Schlüssel des Empfängers.
- **Passwort-Wiederherstellung** ist prinzipbedingt unmöglich.
- **2FA** wird nur in der App durchgesetzt (siehe Abschnitt 6).
- **Keine Forward Secrecy für Textnachrichten:** NIP-44 nutzt statische Schlüssel. Wird das Passwort
  kompromittiert, sind auf Relays gespeicherte alte Nachrichten lesbar. (Anrufe haben Forward Secrecy.)
- **Empfang nur bei laufender App:** Es gibt keine Push-Benachrichtigungen (bewusst ohne Google-Dienste).
  Eingehende Anrufe kommen nur an, wenn die App geöffnet ist.
- **IP-Adresse bei Anrufen:** Ohne TURN-Server sieht der Gesprächspartner die IP-Adresse.
- **Datenhaltung:** Öffentliche Relays und Medien-Server können alte Daten löschen oder Größenlimits haben.
- **Nur 1:1-Chats**, keine Gruppen.
- **Krypto-Implementierung:** secp256k1 basiert auf Bouncy Castle und ist nicht garantiert
  „constant-time“. Kein unabhängiges Sicherheits-Audit.
- **APK-Größe** ca. 113 MB (WebRTC für alle CPU-Architekturen).
- Die App wurde bisher nur gebaut und per Unit-Tests geprüft, noch nicht umfassend auf Geräten getestet.

---

## 18. Mögliche Erweiterungen

- Hintergrunddienst (Foreground Service) bzw. UnifiedPush für Nachrichten und Anrufe im Hintergrund
- Double-Ratchet-Protokoll (Forward Secrecy für Nachrichten)
- Native `libsecp256k1` statt Bouncy Castle
- QR-Code-Scanner zum Hinzufügen von Kontakten
- Gruppenchats (NIP-17 unterstützt mehrere Empfänger)
- Sprachnachrichten, Dateien beliebigen Typs
- Biometrische App-Sperre
- Sicherheitsnummern-Vergleich zur Kontaktverifizierung
- ABI-Splits / App Bundle zur Verkleinerung der APK
- Eigener Relay- und Blossom-Server für volle Kontrolle

---

## 19. Abhängigkeiten & Lizenzen

| Bibliothek | Version | Lizenz |
|---|---|---|
| Jetpack Compose (BOM) | 2026.02.01 | Apache 2.0 |
| AndroidX Core / Activity / Lifecycle | 1.19.1 / 1.13.0 / 2.11.1 | Apache 2.0 |
| Material Icons Extended | 1.7.8 | Apache 2.0 |
| Kotlin Coroutines | 1.10.2 | Apache 2.0 |
| OkHttp | 4.12.0 | Apache 2.0 |
| Bouncy Castle (bcprov-jdk18on) | 1.80 | MIT |
| stream-webrtc-android | 1.3.8 | BSD-3-Clause / Apache 2.0 |
| ZXing Core | 3.5.3 | Apache 2.0 |

Weitere indirekte Abhängigkeiten (alle Apache 2.0): Okio 3.6.0, Guava ListenableFuture, JSpecify,
JetBrains Annotations.

Die vollständigen Lizenztexte und Copyright-Hinweise liegen in der App unter
`app/src/main/assets/licenses/` (`NOTICES.txt`, `apache-2.0.txt`, `bouncycastle-mit.txt`,
`webrtc-bsd-3-clause.txt`, `webrtc-third-party.txt`) und sind unter **Einstellungen → Info → Open-Source-Lizenzen** einsehbar.
Der NOTICE-Hinweis von OkHttp (Public Suffix List, MPL 2.0) ist enthalten.

Referenzierte Standards: NIP-01, NIP-17, NIP-44, NIP-59, NIP-78 (Nostr), BIP-340, BIP-173,
RFC 6238, RFC 7539, RFC 5869, RFC 9106 (Argon2), Blossom BUD-01/02.

---

## 20. Versionshistorie

| Version | Build | Änderungen |
|---|---|---|
| 1.2.0 | 4 | Ausrichtung als nicht-kommerzielles Open-Source-Hobbyprojekt (Apache 2.0, GitHub Releases): überarbeitete Nutzungsbedingungen (Eigenverantwortung der Nutzer, keine Gewährleistung, Haftung nach §§ 521, 523, 524 BGB), Datenschutzhinweise ohne Verantwortlichen-Angaben, Impressum entfernt, Zustimmung bei jeder Anmeldung, README/LICENSE/NOTICE/SECURITY.md, lokale Release-Signatur |
| 1.1.1 | 3 | Nutzungsbedingungen, Datenschutzerklärung und Impressum in der App; Zustimmung bei Kontoerstellung; vollständige Open-Source-Lizenztexte und Copyright-Hinweise in der App; realistischere Formulierungen zu Anonymität und Sicherheit |
| 1.1.0 | 2 | Neues App-Logo (adaptives Icon inkl. Monochrom), Logo im Login, eigenes Benachrichtigungssymbol, Info-Bereich in den Einstellungen mit Version, Geräte-, Sicherheitsinfos und Open-Source-Lizenzen |
| 1.0 | 1 | Erste Version: Anmeldung per Name/Passwort, 2FA, verschlüsselter Text-, Bild- und Videoversand, Sprach- und Videoanrufe, dezentrale Speicherung über Nostr und Blossom |

---

## 21. Rechtliches

> **Hinweis:** Dieser Abschnitt beschreibt die rechtliche Ausrichtung nach bestem Wissen, ist aber
> **keine Rechtsberatung**. Eine vollständige rechtliche Absicherung kann nicht garantiert werden.
> Bei Unsicherheiten sollte eine Rechtsanwältin oder ein Rechtsanwalt hinzugezogen werden.

### 21.1 Modell: nicht-kommerzielles Open-Source-Hobbyprojekt
Conversa wird als **kostenlose Open-Source-Software unter der Apache License 2.0** über **GitHub Releases**
verbreitet. Die Entwickler
- betreiben **keine Server** (keine Relays, keine Medien-Server, keinen TURN-Server) und damit keinen
  Kommunikations- oder Hosting-Dienst,
- haben **keinen Zugriff** auf Konten, Nachrichten oder Dateien und können Inhalte weder einsehen
  noch sperren,
- erheben über die App **keine personenbezogenen Daten**.

Rechtlich entspricht das der Weitergabe eines **neutralen Software-Werkzeugs**, vergleichbar mit einem
E-Mail-Programm oder Webbrowser. Für Inhalte, die Nutzer damit versenden, sind die Nutzer selbst
verantwortlich.

### 21.2 Haftung (§§ 521, 523, 524 BGB)
Ein pauschaler Haftungsausschluss („Ich hafte für nichts“) ist in Deutschland unwirksam. Die
unentgeltliche Überlassung wird aber rechtlich ähnlich wie eine **Schenkung** behandelt. Daraus folgt
eine gesetzliche Haftungsprivilegierung, auf die sich die Nutzungsbedingungen stützen:
- **§ 521 BGB:** Haftung nur für **Vorsatz und grobe Fahrlässigkeit**.
- **§§ 523, 524 BGB:** Haftung für Rechts- und Sachmängel nur bei **arglistigem Verschweigen**.
- Zusätzlich schließt die **Apache License 2.0** (Abschnitte 7 und 8) Gewährleistung und Haftung im
  gesetzlich zulässigen Umfang aus.
- Unberührt bleiben zwingende Haftungen (z. B. Vorsatz, Verletzung von Leben, Körper, Gesundheit).

### 21.3 Eigenverantwortung der Nutzer
Die Nutzungsbedingungen (`assets/legal/nutzungsbedingungen.txt`) legen fest:
- Conversa ist ein neutrales Werkzeug für **legale** Kommunikation; rechtswidrige Nutzung ist untersagt.
- **Jede Person ist allein verantwortlich** für ihre Inhalte, die Wahl der Server und die Einhaltung der
  Gesetze; die Entwickler machen sich fremde Inhalte nicht zu eigen.
- Freistellung der Entwickler von Ansprüchen Dritter bei rechtswidriger Nutzung (soweit zulässig).
- Keine Gewährleistung, keine Verfügbarkeitszusage, kein Notruf, Nutzung auf eigenes Risiko.

Die Zustimmung wird **bei jeder Anmeldung** per Kontrollkästchen eingeholt, also auch bei der
Anmeldung mit einem bestehenden Konto auf einem neuen Gerät.

### 21.4 Impressum
Eine Impressumspflicht nach § 5 DDG besteht für **geschäftsmäßige, in der Regel gegen Entgelt**
angebotene digitale Dienste. Ein rein privates, nicht-kommerzielles Hobbyprojekt ohne
Gewinnerzielungsabsicht fällt nach überwiegender Auffassung **nicht** darunter. Conversa enthält daher
**kein Impressum**.

Das kann sich ändern, wenn das Projekt geschäftsmäßig wird, z. B. durch Werbung, kostenpflichtige
Funktionen, regelmäßige Einnahmen (auch über Spendenplattformen) oder die Nutzung zur Werbung für eigene
berufliche Leistungen. Dann sollte die Frage neu geprüft werden.

### 21.5 Datenschutz
Da die Entwickler keine personenbezogenen Daten erheben oder verarbeiten, enthält die App
**Datenschutzhinweise** zur Transparenz (`assets/legal/datenschutz.txt`) statt einer klassischen
Datenschutzerklärung mit Verantwortlichen-Angaben. Sie erklären, was lokal gespeichert wird und welche
Daten die App im Auftrag der Nutzer an **Server Dritter** überträgt (Relays, Blossom, STUN, GitHub beim
Download). Kontakt erfolgt über die Issues des GitHub-Repositories, ohne Veröffentlichung persönlicher
Anschriften.

### 21.6 Produkthaftung & Cyber Resilience Act
- Die neue EU-Produkthaftungsrichtlinie (EU) 2024/2853 bezieht Software ein, nimmt aber
  **freie und quelloffene Software, die außerhalb einer Geschäftstätigkeit entwickelt oder
  bereitgestellt wird**, ausdrücklich aus.
- Der Cyber Resilience Act (EU) 2024/2847 gilt für Produkte, die im Rahmen einer **Geschäftstätigkeit**
  auf den Markt gebracht werden; nicht-kommerzielle Open-Source-Software ist davon ausgenommen.
- Beide Ausnahmen setzen voraus, dass das Projekt **nicht kommerziell** bleibt.

### 21.7 Open-Source-Lizenzen
- Eigener Code: **Apache License 2.0** (`LICENSE`, `NOTICE` im Repository).
- Drittkomponenten: vollständige Lizenztexte und Copyright-Hinweise in `app/src/main/assets/licenses/`
  und in der App unter **Einstellungen → Info → Open-Source-Lizenzen**.
- Die in der nativen WebRTC-Bibliothek enthaltenen Komponenten wurden durch Analyse der Bibliothek ermittelt (BoringSSL, Abseil, Opus, libvpx, libaom, dav1d, libyuv, libsrtp, Protocol Buffers lite, libevent, CRC32C, PFFFT, RNNoise, LLVM libc++/libc++abi sowie WebRTC-interne Komponenten). Ihre vollständigen Lizenz- und Patenttexte, übernommen aus den offiziellen Quellen, stehen in `app/src/main/assets/licenses/webrtc-third-party.txt`.

### 21.8 Empfehlungen, damit die Absicherung trägt
1. **Nicht kommerzialisieren:** keine Werbung, keine kostenpflichtigen Funktionen, keine regelmäßigen
   Einnahmen.
2. **Keine eigenen Server betreiben.** Wer selbst Relays, Medien- oder TURN-Server öffentlich anbietet,
   wird Hosting-Anbieter mit eigenen Pflichten (z. B. nach dem Digital Services Act).
3. **Neutral bewerben:** Die App nicht für rechtswidrige Zwecke anpreisen (z. B. „nicht
   rückverfolgbar für …“). Die Beschreibung im README ist bewusst sachlich gehalten.
4. **Sicherheitsmeldungen ernst nehmen** (`SECURITY.md`) und bekannte schwere Lücken nicht verschweigen.
   Arglistiges Verschweigen hebt die Haftungsprivilegierung auf.
5. **Signaturschlüssel geheim halten:** `keystore.properties` und `*.jks` sind in `.gitignore`
   eingetragen und dürfen nie veröffentlicht werden.
6. Release-APKs mit **SHA-256-Prüfsumme** in den Release-Notizen veröffentlichen.

### 21.9 Dateien für das GitHub-Repository
| Datei | Zweck |
|---|---|
| `README.md` | Projektbeschreibung mit deutlichem Haftungs- und Nutzungshinweis |
| `LICENSE` | Apache License 2.0 |
| `NOTICE` | Copyright und Verweis auf Drittlizenzen |
| `SECURITY.md` | Vertrauliches Melden von Sicherheitslücken |
| `DOKUMENTATION.md` | diese Dokumentation |
| `.gitignore` | schließt Signaturschlüssel, `keystore.properties` und `local.properties` aus |

