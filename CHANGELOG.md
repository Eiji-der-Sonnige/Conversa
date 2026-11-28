# Änderungsprotokoll

Alle nennenswerten Änderungen an Conversa. Format angelehnt an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/).

## [1.4.0] – unveröffentlicht (Build 9)
Gruppenchats, Dateien, QR-Scanner, englische Oberfläche und eine viel kleinere APK.
Gruppenchats funktionieren nur, wenn **alle** Mitglieder 1.4.0 nutzen.

### Neu
- **Gruppenchats:** „+“ → „Gruppe erstellen“. Bis 32 Mitglieder und 5 Admins. Admins laden per Nutzer-ID oder
  aus den Kontakten ein, entfernen Mitglieder, ernennen Admins, benennen um und löschen die Gruppe für alle. Nur der
  Ersteller kann Admins absetzen; den Ersteller kann niemand entfernen. Vor dem Beitritt muss ein Hinweis bestätigt
  werden (Löschen nur durch Admins, keine Löschgarantie im dezentralen Netz).
  - Verschlüsselung nach NIP-17: jede Nachricht einzeln für jedes aktuelle Mitglied. Entfernte erhalten ab sofort
    nichts Neues mehr; Relays sehen weder Gruppe noch Mitgliederliste.
  - Jede Änderung ist ein signiertes Event mit Folgenummer; jedes Gerät prüft die Rechte selbst.
- **Dateien beliebigen Typs** senden und empfangen. Empfangene Dateien werden nie automatisch geöffnet, sondern nur
  auf Wunsch (mit Warnhinweis) an einem selbst gewählten Ort gespeichert; Dateinamen werden bereinigt.
- **QR-Scanner** zum Hinzufügen von Kontakten und Einladen in Gruppen (Auswertung nur auf dem Gerät).
- **Chat-Menü in der Chatliste:** lange drücken → „Chat umbenennen“ (nur lokal, wird nie gesendet) oder
  „Chat löschen“ (mit Sicherheitsabfrage, entfernt auch zwischengespeicherte Medien).
- **Blockieren** und **Spam-Schutz:** höchstens 30 offene Nachrichtenanfragen mit je 20 Nachrichten,
  höchstens 10 offene Gruppeneinladungen; blockierte Absender verwaltbar unter Einstellungen.
- **Englische Oberfläche** (folgt der Systemsprache), englische README und Rechtstexte (deutsche Fassung maßgeblich).
- **Besserer Schutz neuer Konten gegen Passwort-Raten:** stärkere Schlüsselableitung (Argon2id 128 MB, 4 Durchläufe,
  etwa 2,7-mal so teuer) und strengere Passwortprüfung (keine Wort+Jahreszahl-Muster, Folgen, Wiederholungen oder den
  Benutzernamen; Passphrasen ab 20 Zeichen erlaubt). **Bestehende Konten bleiben unverändert** und funktionieren wie bisher.

### Verbessert
- **APK pro Prozessortyp:** ca. 22 MB (arm64) statt 96 MB; zusätzlich eine Universal-APK.
- **Schnellere Wiederverbindung:** ausgefallene Relays werden im Vordergrund nach 1, 2, 4, 8 s und dann alle 10 s
  erneut versucht (vorher bis 60 s), beim Öffnen der App sofort.
- Warnung vor Anrufen mit Tor ohne TURN-Server (Anrufe laufen nicht über Tor).
- Kopierte Nachrichten werden als vertraulich markiert.
- Abhängigkeiten aktualisiert: Bouncy Castle 1.87, OkHttp 4.12.1, Stream WebRTC 1.3.10.

### Getestet (zwei Emulatoren, Android 17, Release-Build)
- Gruppe erstellen, Einladung mit Hinweis annehmen, Nachrichten in beide Richtungen, Admin ernennen, Ersteller
  nicht entfernbar, Mitglied entfernen (keine Eingabe mehr), verlassen; Neuanmeldung stellt Gruppen korrekt wieder
  her, verlassene Gruppen bleiben weg
- Chat lokal umbenennen und löschen; englische Oberfläche
- Neuanmeldung mit Bouncy Castle 1.87 ergibt dieselbe Identität; Videoanruf mit WebRTC 1.3.10
- Bestehendes Konto meldet sich unverändert an; neues Konto (V2) anlegen, „Sommer2026!!“ wird abgelehnt,
  Neuanmeldung findet dieselbe Kontakt-ID
- QR-Scanner öffnet die Kamera (Auslesen per Unit-Test geprüft)

## [1.3.0] – 2026-11-27 (Build 8)
Neue Funktionen: Sprachnachrichten, Löschen, Lesebestätigungen und Empfang im Hintergrund.
„Für alle löschen“ und Lesebestätigungen funktionieren nur, wenn **beide** Gesprächspartner 1.3.0 nutzen.

### Neu
- **Sprachnachrichten:** Bei leerem Textfeld erscheint ein Mikrofon. Aufnahme bis 5 Minuten, verwerfen oder
  senden; Wiedergabe mit Fortschrittsbalken und Längenanzeige. Verschlüsselt wie Bilder (AES-256-GCM).
- **Nachrichten löschen:** Lange auf eine Nachricht tippen → „Für mich löschen“ (auf allen eigenen Geräten,
  auch nach Neuanmeldung) oder bei eigenen Nachrichten „Für alle löschen“. Niemand kann fremde Nachrichten
  beim anderen löschen. Außerdem „Text kopieren“.
- **Zustellstatus:** ✓ übermittelt, ✓✓ (blau) gelesen. Lesebestätigungen lassen sich abschalten; dann sieht
  man auch die der anderen nicht. An Nachrichtenanfragen werden nie Bestätigungen gesendet.
- **Ungelesen-Zähler** in der Chatliste.
- **Im Hintergrund empfangen (optional, Einstellungen):** Conversa bleibt mit den Relays verbunden, auch
  wenn die App geschlossen ist oder das Handy im Standby liegt, und startet nach einem Neustart des Handys
  oder einem Update wieder. Kein Google-Dienst. Vor dem Einschalten erklärt die App den Akkuverbrauch
  (Schätzung 1–5 % pro Tag). Optional lässt sich die Akku-Optimierung ausschalten.
- **Eingehende Anrufe** klingeln jetzt auch, wenn die App im Hintergrund ist, und öffnen bei ausgeschaltetem
  Bildschirm den Anrufbildschirm (Benachrichtigung mit „Öffnen“ und „Ablehnen“).

### Verbessert
- Verbindungsprüfung gebündelt für alle Relays (spart Akku), im Hintergrund seltener; nach einem
  Netzwechsel (WLAN ↔ Mobilfunk) wird sofort neu verbunden.
- Nach einer Neuverbindung wird nicht mehr der komplette Verlauf erneut angefordert.

### Korrigiert
- Eine als fehlgeschlagen (⚠) markierte Nachricht, die doch noch zugestellt wurde, wechselt jetzt auf ✓.
  „Erneut senden“ erzeugt keine Duplikate mehr.
- Ein erneut synchronisierter Verlauf setzt den Status nicht mehr zurück.
- Anrufsignale oder andere Steuerdaten von Unbekannten legen keine leere Nachrichtenanfrage mehr an.
- Beim Start eines Anrufs mit offener Tastatur verdeckte diese die Anrufknöpfe.
- Eingehende Anrufe blieben unbemerkt, wenn die App im Hintergrund war.

### Getestet (zwei Emulatoren, Android 17)
- Update von 1.2.1 auf 1.3.0 (Release-Build): Sitzung und 2FA-Abfrage bleiben erhalten
- ✓ beim Senden, ✓✓ sobald der Empfänger den Chat öffnet; Ungelesen-Zähler
- Sprachnachricht aufnehmen, senden, beim Empfänger abspielen (stumme Aufnahme, Emulator ohne Mikrofon)
- „Für alle löschen“ entfernt die Nachricht auf beiden Seiten; „Für mich löschen“ nur lokal; beides bleibt
  nach App-Neustart und nach Ab- und Neuanmeldung gelöscht; ✓✓ bleibt nach Neustart erhalten
- Hintergrundempfang: App geschlossen, Bildschirm aus, Doze-Tiefschlaf erzwungen → Nachricht löst
  Benachrichtigung aus; Anruf weckt das Gerät und zeigt den Anrufbildschirm, Gespräch kommt zustande
- Hintergrunddienst startet nach App-Update von selbst wieder

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
