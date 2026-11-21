# Änderungsprotokoll

Alle nennenswerten Änderungen an Conversa. Format angelehnt an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/).

## [Unveröffentlicht]
### Korrigiert
- Copyright-Vermerk von Bouncy Castle wortgetreu nach der offiziellen Lizenz von Version 1.80 („2000-2023“)
- Automatischer Build (GitHub Actions) repariert

## [1.2.0] – 2026-11-21 (Build 4)
Erstes öffentliches Release auf GitHub.

### Hinzugefügt
- Nutzungsbedingungen und Datenschutzhinweise in der App; Zustimmung bei jeder Anmeldung
- Vollständige Open-Source-Lizenztexte in der App, inklusive aller in WebRTC enthaltenen Komponenten
- README, LICENSE (Apache 2.0), NOTICE, SECURITY.md, Issue-Vorlagen, automatischer Build (GitHub Actions)

### Geändert
- Paketname: `io.github.eiji_der_sonnige.conversa`
- Ausrichtung als nicht-kommerzielles Open-Source-Hobbyprojekt; Impressum entfernt
- Realistischere Formulierungen zu Anonymität und Sicherheit

## [1.1.0] – Build 2
- Neues App-Logo (adaptives Icon inkl. Monochrom-Variante), eigenes Benachrichtigungssymbol
- Info-Bereich in den Einstellungen (Version, Gerät, Sicherheitsinfos, Lizenzen)

## [1.0] – Build 1
- Anmeldung mit Benutzername und Passwort (Argon2id), Zwei-Faktor-Authentifizierung (TOTP)
- Ende-zu-Ende-verschlüsselte Text-, Bild- und Videonachrichten über Nostr (NIP-17/44/59) und Blossom
- Sprach- und Videoanrufe über WebRTC (Peer-to-Peer)
