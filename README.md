<p align="center">
  <img src="app/src/main/res/drawable-nodpi/conversa_logo.png" width="140" alt="Conversa-Logo">
</p>

<h1 align="center">Conversa</h1>

<p align="center">
Dezentraler Messenger für Android, ausgelegt auf Privatsphäre und Ende-zu-Ende-Verschlüsselung.<br>
Nicht-kommerzielles Open-Source-Hobbyprojekt · Apache License 2.0
</p>

<p align="center">
  <a href="https://github.com/Eiji-der-Sonnige/Conversa/actions/workflows/build.yml"><img src="https://github.com/Eiji-der-Sonnige/Conversa/actions/workflows/build.yml/badge.svg" alt="Build & Tests"></a>
  <a href="https://github.com/Eiji-der-Sonnige/Conversa/releases"><img src="https://img.shields.io/github/v/release/Eiji-der-Sonnige/Conversa" alt="Neuestes Release"></a>
</p>

---

> ### ⚠ Wichtiger Hinweis – bitte vor der Nutzung lesen
> - Conversa ist ein **privates, nicht-kommerzielles Hobbyprojekt**. Es wird **kostenlos und ohne jede
>   Gewährleistung** („as is“) bereitgestellt. Die **Nutzung erfolgt auf eigenes Risiko**.
> - Die Software wurde **keinem unabhängigen Sicherheits-Audit** unterzogen. Vollständige Anonymität
>   oder absolute Sicherheit werden **nicht** zugesichert (siehe [Einschränkungen](#einschränkungen)).
> - Die Entwickler betreiben **keine Server und keinen Kommunikationsdienst**. Sie haben keinen Zugriff
>   auf Konten, Nachrichten oder Dateien und können Inhalte weder einsehen noch löschen.
> - **Jede Person ist für ihre Nutzung und ihre Inhalte selbst verantwortlich.** Die Nutzung ist nur
>   im Rahmen der geltenden Gesetze erlaubt. Rechtswidrige Nutzung ist ausdrücklich untersagt.
> - Conversa **ersetzt keinen Notruf**.
>
> Es gelten die [Nutzungsbedingungen](app/src/main/assets/legal/nutzungsbedingungen.txt) und die
> [Datenschutzhinweise](app/src/main/assets/legal/datenschutz.txt), die auch in der App angezeigt und
> beim Anmelden bestätigt werden.

## Funktionen
- Anmeldung nur mit Benutzername + Passwort – keine E-Mail, keine Telefonnummer
- Zwei-Faktor-Authentifizierung (TOTP, z. B. Google Authenticator)
- Ende-zu-Ende-verschlüsselte Text-, Bild- und Videonachrichten (Nostr NIP-17/44/59)
- Sprach- und Videoanrufe direkt Peer-to-Peer (WebRTC, DTLS-SRTP)
- Dezentral über austauschbare Nostr-Relays und Blossom-Medien-Server
- Optional: Tor (über Orbot), eigener TURN-Server
- Keine Werbung, kein Tracking, keine Analyse, keine Google-Play-Dienste

## Installation
1. Unter **Releases** die Datei `Conversa-<version>.apk` herunterladen.
2. Auf dem Android-Gerät (Android 13 oder neuer) öffnen und die Installation aus dieser Quelle erlauben.
3. Optional: Die SHA-256-Prüfsumme aus den Release-Notizen mit der heruntergeladenen Datei vergleichen.

## Server Dritter
Die voreingestellten Relays (`relay.damus.io`, `nos.lol`, `relay.primal.net`, `nostr.mom`,
`relay.snort.social`), Medien-Server (`nostr.download`, `blossom.yakihonne.com`, `files.sovbit.host`) und STUN-Server
(Google, Cloudflare) sind **austauschbare Voreinstellungen**. Sie werden von unabhängigen Dritten
betrieben, nicht von diesem Projekt. Die Entwickler haben keinen Einfluss auf deren Verfügbarkeit,
Inhalte oder Datenverarbeitung. Technisch bedingt sehen diese Server z. B. die IP-Adresse.

## Einschränkungen
- Kein unabhängiges Sicherheits-Audit; bisher vor allem durch Unit-Tests geprüft.
- Keine Forward Secrecy bei Textnachrichten.
- Bei Anrufen ohne TURN-Server sieht der Gesprächspartner die IP-Adresse.
- Nachrichten kommen nur an, solange die App läuft (keine Push-Dienste).
- Ein verlorenes Passwort kann nicht wiederhergestellt werden.
- Die 2FA ist nur eine Sperre innerhalb der App: Wer Benutzername und Passwort kennt, kommt auch ohne
  2FA an das Konto. Deshalb ein langes, einzigartiges Passwort verwenden.

Ausführliche Beschreibung: [DOKUMENTATION.md](DOKUMENTATION.md)

## Selbst bauen
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```
Zum Signieren `keystore.properties.example` nach `keystore.properties` kopieren und ausfüllen.
`keystore.properties` und Schlüsseldateien (`*.jks`) werden nicht ins Repository übernommen
(siehe `.gitignore`). Details: [DOKUMENTATION.md, Abschnitt 16](DOKUMENTATION.md#16-build-tests--entwicklung).

## Sicherheitslücken melden
Siehe [SECURITY.md](SECURITY.md).

## Lizenz
Copyright 2026 Die Conversa-Autoren

Lizenziert unter der [Apache License, Version 2.0](LICENSE). Die Software wird „AS IS“, ohne
Gewährleistungen oder Bedingungen jeglicher Art bereitgestellt. Komponenten Dritter stehen unter ihren
eigenen Lizenzen, siehe [NOTICE](NOTICE).
