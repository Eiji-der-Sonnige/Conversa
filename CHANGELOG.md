# Änderungsprotokoll

Alle nennenswerten Änderungen an Conversa. Format angelehnt an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/).

## [1.2.1] – 2026-11-21 (Build 5)
### Korrigiert
- **Absturz beim Start** der Release-Version (v1.2.0) behoben: Die teilweise R8-Optimierung machte
  Kotlin-Klassen unzugänglich (`IllegalAccessError`). Die Optimierung ist für Release-Builds abgeschaltet.
- **Bilder/Videos senden** schlug fehl: Die bisherigen Medien-Server lehnen verschlüsselte Dateien ab
  (HTTP 415). Neue Voreinstellung: `nostr.download`, `blossom.yakihonne.com`, `files.sovbit.host`;
  alte Voreinstellungen werden automatisch ersetzt. Fehlermeldungen nennen nun alle Server.
- **2FA-Code abgelehnt** bei falsch gehender Geräteuhr: Conversa gleicht die Uhr jetzt mit der Zeit der
  Relays ab und zeigt die erkannte Abweichung an.
- Copyright-Vermerk von Bouncy Castle wortgetreu nach der offiziellen Lizenz von Version 1.80 („2000-2023“)
- Automatischer Build (GitHub Actions) repariert

### Geändert
- Screenshot-Schutz nur noch in Release-Builds (Debug-Builds für automatisierte Tests)

### Getestet (Emulator, Android 17)
- Konto erstellen/anmelden, 2FA einrichten und abfragen (auch mit 5 Min. falscher Uhr), Text und Bild
  zwischen zwei Konten über Relays/Blossom, Nachrichtenanfrage annehmen, Wiederherstellung von Kontakten,
  2FA und Verlauf nach Neuinstallation, Videoanruf-Bildschirm mit Kamera

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
