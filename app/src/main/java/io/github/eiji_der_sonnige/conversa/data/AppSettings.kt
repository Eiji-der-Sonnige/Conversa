package io.github.eiji_der_sonnige.conversa.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AppSettings(
    val relays: List<String> = DEFAULT_RELAYS,
    val blossomServers: List<String> = DEFAULT_BLOSSOM,
    val turnUrl: String = "",
    val turnUser: String = "",
    val turnPass: String = "",
    val relayOnlyCalls: Boolean = false,
    val useTor: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("relays", JSONArray(relays))
        .put("blossom", JSONArray(blossomServers))
        .put("turnUrl", turnUrl).put("turnUser", turnUser).put("turnPass", turnPass)
        .put("relayOnly", relayOnlyCalls).put("tor", useTor)

    companion object {
        val DEFAULT_RELAYS = listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.primal.net",
            "wss://nostr.mom",
            "wss://relay.snort.social",
        )
        // Nur Server, die verschlüsselte (nicht erkennbare) Dateien annehmen – getestet am 21.11.2026.
        val DEFAULT_BLOSSOM = listOf(
            "https://nostr.download",
            "https://blossom.yakihonne.com",
            "https://files.sovbit.host",
        )
        // Voreinstellung bis v1.2.0 – diese Server lehnen verschlüsselte Dateien ab (HTTP 415).
        private val OLD_DEFAULT_BLOSSOM = listOf("https://blossom.primal.net", "https://blossom.band")
        private const val FILE = "settings.json"

        fun load(ctx: Context): AppSettings {
            val o = SecureFiles.readJson(ctx, FILE) ?: return AppSettings()
            return AppSettings(
                relays = o.optJSONArray("relays")?.strings()?.takeIf { it.isNotEmpty() } ?: DEFAULT_RELAYS,
                blossomServers = o.optJSONArray("blossom")?.strings()
                    ?.takeIf { it.isNotEmpty() && it != OLD_DEFAULT_BLOSSOM } ?: DEFAULT_BLOSSOM,
                turnUrl = o.optString("turnUrl"),
                turnUser = o.optString("turnUser"),
                turnPass = o.optString("turnPass"),
                relayOnlyCalls = o.optBoolean("relayOnly"),
                useTor = o.optBoolean("tor"),
            )
        }

        fun save(ctx: Context, s: AppSettings) = SecureFiles.writeJson(ctx, FILE, s.toJson())
    }
}
