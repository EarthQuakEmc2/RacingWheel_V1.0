package com.racingwheelpro.startup

import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

/**
 * One HTTPS GET. A response means Android accepted the certificate with its
 * normal trust store. This class does not install a custom trust manager.
 */
class SiteClient {
    data class Fetch(
        val tlsAccepted: Boolean,
        val reached: Boolean,
        val code: Int,
        val body: String,
    )

    fun get(url: String, cookie: String? = null, timeoutMs: Int = StartupChecks.REQUEST_TIMEOUT_MS): Fetch {
        val connection = URL(url).openConnection() as HttpURLConnection
        if (connection !is HttpsURLConnection) {
            connection.disconnect()
            return Fetch(tlsAccepted = false, reached = false, code = 0, body = "")
        }
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        if (!cookie.isNullOrBlank()) connection.setRequestProperty("Cookie", cookie)
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Fetch(tlsAccepted = true, reached = true, code = code, body = body)
        } catch (_: SSLException) {
            Fetch(tlsAccepted = false, reached = false, code = 0, body = "")
        } catch (_: SocketTimeoutException) {
            Fetch(tlsAccepted = false, reached = false, code = 0, body = "")
        } catch (_: Exception) {
            Fetch(tlsAccepted = false, reached = false, code = 0, body = "")
        } finally {
            connection.disconnect()
        }
    }
}
