<p align="center">
  <img src="app/src/main/res/drawable-nodpi/conversa_logo.png" width="140" alt="Conversa logo">
</p>

<h1 align="center">Conversa</h1>

<p align="center">
Decentralized messenger for Android, designed for privacy and end-to-end encryption.<br>
Non-commercial open-source hobby project · Apache License 2.0
</p>

<p align="center">
  <a href="https://github.com/Eiji-der-Sonnige/Conversa/actions/workflows/build.yml"><img src="https://github.com/Eiji-der-Sonnige/Conversa/actions/workflows/build.yml/badge.svg" alt="Build & Tests"></a>
  <a href="https://github.com/Eiji-der-Sonnige/Conversa/releases"><img src="https://img.shields.io/github/v/release/Eiji-der-Sonnige/Conversa" alt="Latest release"></a>
</p>

<p align="center"><b>English</b> · <a href="README.de.md">Deutsch</a></p>

---

> ### ⚠ Disclaimer – please read before use
> - Conversa is a **private, non-commercial hobby project**. It is provided **free of charge and
>   "AS IS", without warranty of any kind**, express or implied. **You use it at your own risk.**
> - The software has **not been independently audited**. Complete anonymity or absolute security is
>   **not** guaranteed (see [Limitations](#limitations)).
> - The developers operate **no servers and no communication service**. They have no access to accounts,
>   messages or files and can neither view nor delete content.
> - **Every user is solely responsible for their own use and content.** Use is only permitted in
>   accordance with applicable law. Unlawful use is expressly prohibited. The developers are not liable
>   for the behavior of users.
> - Conversa **does not replace emergency calls**.
>
> The [Terms of Use](app/src/main/assets/legal/nutzungsbedingungen_en.txt) and the
> [Privacy Notice](app/src/main/assets/legal/datenschutz_en.txt) apply. They are also shown in the app
> and accepted at login. The English texts are convenience translations; in case of doubt the
> [German versions](app/src/main/assets/legal/nutzungsbedingungen.txt) are binding. To the extent
> permitted by law, liability is limited as described in the Apache License 2.0 (sections 7 and 8)
> and in the Terms of Use.

## Features
- Sign in with username + password only – no email, no phone number
- Two-factor authentication (TOTP, e.g. Google Authenticator)
- End-to-end encrypted text, image, video and voice messages and files of any type (Nostr NIP-17/44/59)
- Group chats (up to 32 members, up to 5 admins) with cryptographically signed admin rights
- Add contacts by scanning their QR code
- Delete messages for yourself or for everyone
- Delivery status: ✓ delivered, ✓✓ read (read receipts can be turned off)
- Spam protection for message requests, block senders
- Long-press a chat to rename it (locally only) or delete it
- Optional background receiving without Google services (uses some battery, see settings)
- Voice and video calls directly peer-to-peer (WebRTC, DTLS-SRTP)
- Decentralized via replaceable Nostr relays and Blossom media servers
- Optional: Tor (via Orbot), your own TURN server
- No ads, no tracking, no analytics, no Google Play services
- App language follows the system language (German, English)

## Installation
1. Download the APK for your device from **Releases**:
   - `Conversa-<version>-arm64-v8a.apk` – almost all phones from the last ~8 years (**recommended**)
   - `Conversa-<version>-armeabi-v7a.apk` – very old 32-bit phones
   - `Conversa-<version>-x86_64.apk` – Android emulator on a PC, some Chromebooks
   - `Conversa-<version>-universal.apk` – contains all of the above (larger), if you are unsure
2. Open it on your Android device (Android 13 or newer) and allow installation from this source.
3. Optional: compare the SHA-256 checksum from the release notes with the downloaded file.

## Third-party servers
The preset relays (`relay.damus.io`, `nos.lol`, `relay.primal.net`, `nostr.mom`, `relay.snort.social`),
media servers (`nostr.download`, `blossom.yakihonne.com`, `files.sovbit.host`) and STUN servers
(Google, Cloudflare) are **replaceable defaults**. They are operated by independent third parties, not by
this project. The developers have no influence on their availability, content or data processing. For
technical reasons, these servers can see e.g. your IP address.

## Limitations
- No independent security audit; tested with unit tests and on emulators and devices.
- No forward secrecy for text messages.
- In calls without a TURN server, the other person can see your IP address.
- Without the optional background receiving, messages only arrive while the app is running (no push
  services). With it, the permanent connection uses some battery (roughly 1–5 % per day, estimated).
- "Delete for everyone" only removes the message for the other person if they use Conversa 1.3 or newer,
  and cannot prevent them from having seen or saved it before.
- Groups: new members trust the inviting admin's member list when joining; deleting a group cannot
  guarantee removal from every device and relay. All members need Conversa 1.4 or newer.
- A lost password cannot be recovered.
- 2FA is only a lock inside the app: anyone who knows username and password can access the account
  without 2FA. Use a long, unique password.

Detailed documentation (German): [DOKUMENTATION.md](DOKUMENTATION.md)

## Building
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```
To sign, copy `keystore.properties.example` to `keystore.properties` and fill it in.
`keystore.properties` and key files (`*.jks`) are never committed (see `.gitignore`).

## Reporting security issues
See [SECURITY.md](SECURITY.md).

## License
Copyright 2026 The Conversa authors

Licensed under the [Apache License, Version 2.0](LICENSE). The software is provided "AS IS", WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. In no event shall the authors be liable
for any claim, damages or other liability arising from the use of the software (see sections 7 and 8 of
the license). Third-party components are subject to their own licenses, see [NOTICE](NOTICE).
