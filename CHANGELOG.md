# Änderungsprotokoll

Alle nennenswerten Änderungen an Conversa. Format angelehnt an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/).

## [1.2.3] – 2026-11-22 (Build 7)
Anrufe repariert. Aktualisieren wird empfohlen – **beide Gesprächspartner** brauchen 1.2.3.

### Korrigiert
- **Sprach- und Videoanrufe funktionierten nicht:** Der Anrufer legte sofort wieder auf, beim
  Angerufenen blieb der Anruf nach dem Annehmen bei „Verbinde…“ hängen. Ursache: Kam das Anrufsignal
  gleichzeitig von mehreren Relays an, wurde es doppelt verarbeitet und die zweite Kopie mit „besetzt“
  beantwortet. Die Duplikatprüfung ist jetzt threadsicher, doppelte Angebote werden ignoriert.
- Verbindungsdaten (ICE-Kandidaten), die vor dem Anrufangebot ankommen, gehen nicht mehr verloren.
- Kommt nach dem Annehmen innerhalb von 30 Sekunden keine Verbindung zustande, wird der Anruf beendet
  statt endlos „Verbinde…“ anzuzeigen.
- Anrufsignale und Zeitstempel nutzen die mit den Relays abgeglichene Uhrzeit; eine falsch gehende
  Handy-Uhr verhindert keine Anrufe mehr (Toleranz ±90 Sekunden).

### Getestet (zwei Emulatoren gleichzeitig, Android 17, Release-Build)
- Sprachanruf A → B und Videoanruf B → A: Verbindung kommt zustande und bleibt stabil (über 40 Sekunden)
- Auflegen durch Anrufer oder Angerufenen beendet den Anruf auf beiden Seiten
- Abbrechen während des Klingelns und Ablehnen funktionieren

## [1.2.2] – 2026-11-22 (Build 6)
Sicherheitsupdate. Aktualisieren wird empfohlen.

### Sicherheit
- **2FA ließ sich überspringen:** Ein Login auf einem neuen Gerät ohne Relay-Verbindung (z. B. im
  Flugmodus) fragte keinen 2FA-Code ab. Ohne Antwort eines Relays wird die Anmeldung jetzt abgebrochen.
- **Datenverlust:** „Konto erstellen“ mit bestehenden Zugangsdaten konnte bei langsamer Verbindung das
  Konto (Kontakte, 2FA) mit einem leeren überschreiben. Behoben (gleiche Ursache wie oben).
- Empfangene Mediendaten werden geprüft (Prüfsumme, Schlüssel, Nonce, HTTPS). Verhindert u. a.
  Pfadmanipulation im Cache.
- Downloads sind auf 50 MB begrenzt (Schutz vor Speicherüberlauf durch manipulierte Links).
- Bilder aus Nachrichtenanfragen unbekannter Absender werden erst nach Tippen geladen (sonst IP-Leck an
  einen vom Absender gewählten Server).
- Mit Tor und eingetragenem TURN-Server laufen Anrufe automatisch nur über TURN.

### Korrigiert
- Sehr lange Nachrichten (z. B. viele nicht-lateinische Zeichen) blieben dauerhaft auf „wird gesendet“
  hängen. Sie werden jetzt mit einer Meldung abgelehnt.

### Dokumentation
- Grenzen der 2FA und das Risiko von Offline-Rateangriffen werden offen beschrieben (README,
  Dokumentation, Nutzungsbedingungen). Datenschutzhinweise ergänzt um das Laden empfangener Medien.

### Getestet (Emulator, Android 17, Release-Build)
- Update über 1.2.1 (gleiche Signatur), Daten und 2FA bleiben erhalten
- Anmeldung ohne Internet (Flugmodus) wird abgebrochen statt 2FA zu überspringen; online Anmeldung
  inkl. 2FA in ca. 4 Sekunden
- „Konto erstellen“ mit bestehenden Zugangsdaten erkennt das Konto, fragt 2FA ab und überschreibt nichts
- Text zwischen zwei Konten, sehr lange Nachrichten (20.000 Zeichen), Bild wird nach Neuanmeldung wieder
  geladen, Wiederherstellung von Kontakten und Verlauf

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
