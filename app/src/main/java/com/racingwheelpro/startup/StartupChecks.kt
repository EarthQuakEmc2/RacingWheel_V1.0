package com.racingwheelpro.startup

import org.json.JSONObject

/**
 * Words and decisions for the startup splash.
 *
 * Every line here is the result of a check the app actually ran: an HTTPS
 * request that Android accepted, a JSON health body, a look in the phone's
 * own cookie jar and preferences, a comparison of version strings, or the
 * list of PCs that answered discovery. Nothing in this file invents a scan.
 */
object StartupChecks {
    const val SITE = "https://myracingwheel.site"
    const val HEALTH_URL = "$SITE/api/health"
    const val ME_URL = "$SITE/api/auth/me"
    const val MIN_VISIBLE_MS = 2500L
    const val REQUEST_TIMEOUT_MS = 2000
    const val RECEIVER_WAIT_MS = 700L

    /** Preference key. The phone does not write this today; the splash only reads it. */
    const val ACCOUNT_PREF = "accountToken"

    const val STEP_COUNT = 5

    enum class Kind { SECURE, HEALTH, ACCOUNT, VERSION, RECEIVER }

    data class Step(
        val kind: Kind,
        val title: String,
        val detail: String,
        val ok: Boolean,
        /** When true, later checks are not run. The wheel is still reachable. */
        val stopped: Boolean,
    )

    data class HealthBody(val ok: Boolean, val version: String)

    fun parseHealth(body: String): HealthBody? {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return HealthBody(o.optBoolean("ok", false), o.optString("version", "").trim())
    }

    fun parseEmail(body: String): String? {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val email = o.optString("email", "").trim()
        return email.ifBlank { null }
    }

    /**
     * A saved website session is either the app preference or the app's own
     * cookie jar. The browser's cookies are not visible to the app.
     */
    fun sessionCookie(prefToken: String?, cookieHeader: String?): String? {
        val token = prefToken?.trim().orEmpty()
        if (token.isNotEmpty()) return "rwp_session=$token"
        val header = cookieHeader?.trim().orEmpty()
        if (header.contains("rwp_session=")) return header
        return null
    }

    fun secureStep(tlsAccepted: Boolean, reached: Boolean): Step {
        if (tlsAccepted && reached) {
            return Step(Kind.SECURE, "Secure connection", "Secure connection verified", ok = true, stopped = false)
        }
        if (!tlsAccepted && reached) {
            return Step(
                Kind.SECURE,
                "Secure connection",
                "The server certificate was not accepted",
                ok = false,
                stopped = true,
            )
        }
        return Step(Kind.SECURE, "Secure connection", "Can't reach server – offline mode", ok = false, stopped = true)
    }

    fun healthStep(httpCode: Int, body: HealthBody?): Step {
        if (body != null && body.ok && httpCode in 200..299) {
            val version = body.version.ifBlank { "no version field" }
            return Step(
                Kind.HEALTH,
                "Server health",
                "myracingwheel.site answered ok ($version).",
                ok = true,
                stopped = false,
            )
        }
        return Step(
            Kind.HEALTH,
            "Server health",
            "The server answered, but the health check failed.",
            ok = false,
            stopped = true,
        )
    }

    fun accountStep(savedCookie: String?, httpCode: Int?, email: String?): Step {
        if (savedCookie.isNullOrBlank()) {
            return Step(
                Kind.ACCOUNT,
                "Account",
                "No account is saved on this phone. The wheel does not need one.",
                ok = true,
                stopped = false,
            )
        }
        if (httpCode == 200 && !email.isNullOrBlank()) {
            return Step(Kind.ACCOUNT, "Account", "Signed in as $email.", ok = true, stopped = false)
        }
        if (httpCode == 401) {
            return Step(
                Kind.ACCOUNT,
                "Account",
                "The saved sign-in was rejected. You can keep driving without it.",
                ok = false,
                stopped = true,
            )
        }
        return Step(
            Kind.ACCOUNT,
            "Account",
            "Couldn't check the saved sign-in.",
            ok = false,
            stopped = true,
        )
    }

    fun versionStep(appVersion: String, serverVersion: String): Step {
        return Step(Kind.VERSION, "Published version", versionNote(appVersion, serverVersion), ok = true, stopped = false)
    }

    fun versionNote(appVersion: String, serverVersion: String): String {
        if (serverVersion.isBlank()) return "The server did not report a version. This app is $appVersion."
        return when (compareVersions(appVersion, serverVersion)) {
            0 -> "This app matches the published version $serverVersion."
            in 1..Int.MAX_VALUE -> "This app is $appVersion. The published site reports $serverVersion."
            else -> "A newer version is published ($serverVersion). This app is $appVersion."
        }
    }

    /** Compares the numeric part. 0.6.0-preview is newer than 0.5.0. */
    fun compareVersions(app: String, server: String): Int {
        fun nums(s: String) = s.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = nums(app)
        val b = nums(server)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    fun receiverStep(names: List<String>): Step {
        val detail = if (names.isEmpty()) {
            "No PC receiver answered on this network yet. Connect later from Link."
        } else {
            "Found ${names.first()}."
        }
        return Step(Kind.RECEIVER, "PC receiver", detail, ok = true, stopped = false)
    }

    fun progress(finished: Int): Float = (finished.toFloat() / STEP_COUNT).coerceIn(0f, 1f)
}
