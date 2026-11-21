package io.github.eiji_der_sonnige.conversa.data

import android.content.Context
import android.util.AtomicFile
import io.github.eiji_der_sonnige.conversa.crypto.Vault
import org.json.JSONObject
import java.io.File

/** Alle lokalen Dateien werden mit dem Keystore-Schlüssel verschlüsselt geschrieben. */
object SecureFiles {
    private fun dir(ctx: Context) = File(ctx.filesDir, "secure").apply { mkdirs() }

    @Synchronized
    fun write(ctx: Context, name: String, data: ByteArray) {
        val f = AtomicFile(File(dir(ctx), name))
        val out = f.startWrite()
        try {
            out.write(Vault.encrypt(data))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
            throw e
        }
    }

    @Synchronized
    fun read(ctx: Context, name: String): ByteArray? {
        val file = File(dir(ctx), name)
        if (!file.exists()) return null
        return try {
            Vault.decrypt(AtomicFile(file).readFully())
        } catch (e: Exception) {
            null
        }
    }

    fun writeJson(ctx: Context, name: String, o: JSONObject) = write(ctx, name, o.toString().toByteArray())

    fun readJson(ctx: Context, name: String): JSONObject? =
        read(ctx, name)?.let { try { JSONObject(String(it)) } catch (e: Exception) { null } }

    fun delete(ctx: Context, name: String) {
        AtomicFile(File(dir(ctx), name)).delete()
    }

    /** Löscht sämtliche lokalen Daten (Abmelden). */
    fun wipeAll(ctx: Context) {
        dir(ctx).deleteRecursively()
        ctx.cacheDir.deleteRecursively()
        Vault.deleteKey()
    }
}
