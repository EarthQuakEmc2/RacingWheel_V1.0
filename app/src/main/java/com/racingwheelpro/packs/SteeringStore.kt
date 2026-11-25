package com.racingwheelpro.packs

import android.content.Context
import com.racingwheelpro.BuildConfig
import com.racingwheelpro.catalog.LicenceCachePolicy
import com.racingwheelpro.catalog.LicenceVerifier
import com.racingwheelpro.catalog.WheelCatalog
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Downloads template packs after the server signs a licence, then keeps that
 * licence on disk. A later refresh that fails does not delete it.
 */
class SteeringStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("rwp", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "steering-packs")
    private val files = File(root, "files")
    private val licenceFile = File(root, "licence.json")
    private val packTemplates = LinkedHashSet<String>()

    var detail: String = "Sign in to install Steering Packages."
        private set
    private var licence: LicenceVerifier.Licence? = null

    fun load() {
        readCatalogue()
        val cached = readLicenceFile()
        licence = cached
        detail = when {
            cached == null -> "No Steering Package licence on this phone."
            cached.templates.isEmpty() -> "Licence verified. No paid templates on this account."
            else -> "Licence verified offline. ${cached.templates.size} templates."
        }
    }

    fun covers(id: String): Boolean = licence?.templates?.contains(id) == true

    fun isPackTemplate(id: String): Boolean = id in packTemplates

    fun expectedHash(name: String): String? = licence?.files?.get(name)

    fun allows(id: String, premiumUnlocked: Boolean): Boolean {
        if (WheelCatalog.isFree(id)) return true
        if (covers(id) && contentReady(id)) return true
        if (isPackTemplate(id)) return false
        return premiumUnlocked
    }

    fun installedTemplateIds(): Set<String> {
        val current = licence ?: return emptySet()
        return current.templates.filter { contentReady(it) }.toSet()
    }

    /** Network call. Safe to run off the main thread. Keeps the last good licence if this attempt fails. */
    fun refresh(email: String, password: String) {
        val cached = licence ?: readLicenceFile()
        val token = login(email, password)
        prefs.edit().putString(SESSION, token).apply()
        val body = getJson("/api/entitlements", token)
        val envelope = body.getJSONObject("licence")
        val parsed = LicenceVerifier.verify(envelope, publicKey())
            ?: throw IllegalStateException("The licence signature was rejected.")
        val kept = LicenceCachePolicy.resolve(true, parsed, cached)
        licence = kept
        root.mkdirs()
        licenceFile.writeText(envelope.toString())
        val missed = downloadOwned(token, kept ?: parsed)
        detail = if (missed == 0) {
            "Licence verified. ${parsed.templates.size} templates."
        } else {
            "Licence saved. $missed file(s) still need a connection. Wheels already on the phone stay available offline."
        }
    }

    private fun contentReady(id: String): Boolean {
        val current = licence ?: return false
        if (id !in current.templates) return false
        val jsonName = "$id.json"
        val jsonHash = current.files[jsonName] ?: return false
        val jsonBytes = readMatching(jsonName, jsonHash) ?: return false
        val image = runCatching { JSONObject(jsonBytes.toString(Charsets.UTF_8)).optString("image") }.getOrDefault("")
        if (image.isBlank()) return false
        val imageHash = current.files[image] ?: return false
        return readMatching(image, imageHash) != null
    }

    private fun readMatching(name: String, expected: String): ByteArray? {
        val installed = File(files, name)
        if (installed.isFile) {
            val bytes = installed.readBytes()
            if (LicenceVerifier.sha256Hex(bytes) == expected) return bytes
        }
        val asset = runCatching { context.assets.open("wheels/$name").use { it.readBytes() } }.getOrNull() ?: return null
        return if (LicenceVerifier.sha256Hex(asset) == expected) asset else null
    }

    private fun downloadOwned(token: String, parsed: LicenceVerifier.Licence): Int {
        files.mkdirs()
        var missed = 0
        for (packageId in parsed.packages) {
            val listing = runCatching { getJson("/api/packs/$packageId", token) }.getOrNull()
            if (listing == null) {
                missed += 1
                continue
            }
            val listed = listing.optJSONArray("files") ?: continue
            for (index in 0 until listed.length()) {
                val name = listed.getJSONObject(index).getString("name")
                val expected = parsed.files[name] ?: continue
                if (readMatching(name, expected) != null && File(files, name).isFile) continue
                val bytes = runCatching { getBytes("/api/packs/$packageId/files/$name", token) }.getOrNull()
                if (bytes == null || LicenceVerifier.sha256Hex(bytes) != expected) {
                    missed += 1
                    continue
                }
                File(files, name).writeBytes(bytes)
            }
        }
        return missed
    }

    private fun readLicenceFile(): LicenceVerifier.Licence? {
        if (!licenceFile.isFile) return null
        val parsed = runCatching { LicenceVerifier.verify(JSONObject(licenceFile.readText()), publicKey()) }.getOrNull()
        return parsed
    }

    private fun readCatalogue() {
        packTemplates.clear()
        val text = runCatching { context.assets.open("steering-packages.json").bufferedReader().use { it.readText() } }.getOrNull()
            ?: return
        val json = JSONObject(text)
        for (key in listOf("manufacturers", "classes")) {
            val packs = json.optJSONArray(key) ?: continue
            for (index in 0 until packs.length()) {
                val templates = packs.getJSONObject(index).optJSONArray("templates") ?: continue
                for (slot in 0 until templates.length()) packTemplates.add(templates.getString(slot))
            }
        }
    }

    private fun publicKey(): String {
        return context.assets.open("dev-licence-public.b64").bufferedReader().use { it.readText() }.trim()
    }

    private fun login(email: String, password: String): String {
        val payload = JSONObject().put("email", email.trim()).put("password", password).toString()
        val connection = open("/api/auth/login", "POST", null)
        connection.doOutput = true
        connection.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
        val code = connection.responseCode
        val text = readText(connection)
        if (code !in 200..299) throw IllegalStateException(errorMessage(text, "Could not sign in"))
        val cookie = connection.headerFields["Set-Cookie"].orEmpty().joinToString(";")
        val match = Regex("rwp_session=([^;]+)").find(cookie) ?: throw IllegalStateException("Sign-in did not return a session")
        return match.groupValues[1]
    }

    private fun getJson(path: String, token: String): JSONObject {
        val connection = open(path, "GET", token)
        val code = connection.responseCode
        val text = readText(connection)
        if (code !in 200..299) throw IllegalStateException(errorMessage(text, "Request failed ($code)"))
        return JSONObject(text)
    }

    private fun getBytes(path: String, token: String): ByteArray {
        val connection = open(path, "GET", token)
        val code = connection.responseCode
        if (code !in 200..299) {
            val text = readText(connection)
            throw IllegalStateException(errorMessage(text, "Download failed ($code)"))
        }
        return connection.inputStream.use { it.readBytes() }
    }

    private fun open(path: String, method: String, token: String?): HttpURLConnection {
        val base = BuildConfig.API_BASE.trimEnd('/')
        val connection = URL(base + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("accept", "application/json")
        if (method == "POST") connection.setRequestProperty("content-type", "application/json")
        if (!token.isNullOrBlank()) connection.setRequestProperty("cookie", "rwp_session=$token")
        return connection
    }

    private fun readText(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode >= 400) connection.errorStream else connection.inputStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun errorMessage(text: String, fallback: String): String {
        return runCatching { JSONObject(text).optString("error") }.getOrNull().takeUnless { it.isNullOrBlank() } ?: fallback
    }

    companion object {
        private const val SESSION = "steering_session"
    }
}
