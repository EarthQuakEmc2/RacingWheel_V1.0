package com.racingwheelpro.catalog

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64

/**
 * Checks an Ed25519 licence from the worker. The signed bytes are the
 * canonical payload. Changing a package id, template id, or file hash
 * breaks the signature, so a pack cannot be unlocked by editing the file.
 */
object LicenceVerifier {
    const val ISSUER = "racingwheel-pro"

    data class Licence(
        val subject: String,
        val packages: Set<String>,
        val templates: Set<String>,
        val files: Map<String, String>,
    )

    fun verify(envelope: JSONObject, publicKeyB64: String): Licence? {
        return try {
            val payload = b64url(envelope.getString("payload"))
            val signature = b64url(envelope.getString("signature"))
            val keyBytes = Base64.getDecoder().decode(publicKeyB64.trim())
            if (keyBytes.size != 32 || signature.size != 64) return null
            val curve = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
            val key = EdDSAPublicKey(EdDSAPublicKeySpec(keyBytes, curve))
            val engine = EdDSAEngine(MessageDigest.getInstance("SHA-512"))
            engine.initVerify(key)
            engine.update(payload)
            if (!engine.verify(signature)) return null
            val json = JSONObject(payload.toString(Charsets.UTF_8))
            if (json.optInt("v") != 1) return null
            if (json.optString("alg") != "Ed25519") return null
            if (json.optString("iss") != ISSUER) return null
            val files = LinkedHashMap<String, String>()
            val listed = json.optJSONArray("files") ?: return null
            for (index in 0 until listed.length()) {
                val file = listed.getJSONObject(index)
                val name = file.getString("name")
                if (!name.matches(Regex("^[A-Za-z0-9_.-]{1,80}$"))) return null
                files[name] = file.getString("sha256").lowercase()
            }
            Licence(
                subject = json.optString("sub"),
                packages = stringSet(json, "packages"),
                templates = stringSet(json, "templates"),
                files = files,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun stringSet(json: JSONObject, name: String): Set<String> {
        val array = json.optJSONArray(name) ?: return emptySet()
        val out = LinkedHashSet<String>()
        for (index in 0 until array.length()) out.add(array.getString(index))
        return out
    }

    private fun b64url(value: String): ByteArray {
        var text = value.replace('-', '+').replace('_', '/')
        while (text.length % 4 != 0) text += "="
        return Base64.getDecoder().decode(text)
    }
}
