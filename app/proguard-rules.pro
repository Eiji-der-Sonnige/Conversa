# Nur ungenutzten Bibliothekscode entfernen (APK-Größe). Bewusst KEINE Optimierung und keine
# Umbenennung: Die teilweise Optimierung verursachte in v1.2.0 einen Absturz beim Start (IllegalAccessError),
# und lesbare Stacktraces erleichtern die Fehlersuche. Eigener Code bleibt vollständig erhalten.
-dontoptimize
-dontobfuscate
-keep class io.github.eiji_der_sonnige.conversa.** { *; }
# WebRTC ruft Java-Klassen per JNI aus nativem Code auf.
-keep class org.webrtc.** { *; }
-keep class livekit.org.webrtc.** { *; }
# Bouncy Castle (Argon2, HKDF, ChaCha20) – Klassen werden teils per Reflexion geladen.
-keep class org.bouncycastle.crypto.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
