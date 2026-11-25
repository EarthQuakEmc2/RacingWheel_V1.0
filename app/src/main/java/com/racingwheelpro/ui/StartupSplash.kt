package com.racingwheelpro.ui

import android.content.Context
import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racingwheelpro.BuildConfig
import com.racingwheelpro.session.RaceSession
import com.racingwheelpro.startup.SiteClient
import com.racingwheelpro.startup.StartupChecks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Startup overlay. The bar moves only when a check finishes. Continue always
 * opens the wheel, including when a check fails.
 */
@Composable
fun StartupSplash(session: RaceSession, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var steps by remember { mutableStateOf(emptyList<StartupChecks.Step>()) }
    var running by remember { mutableStateOf("Checking the secure connection…") }
    var stopped by remember { mutableStateOf(false) }
    var finished by remember { mutableIntStateOf(0) }
    val sink = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        val client = SiteClient()
        val health = withContext(Dispatchers.IO) { client.get(StartupChecks.HEALTH_URL) }
        val secure = StartupChecks.secureStep(health.tlsAccepted, health.reached)
        steps = listOf(secure)
        finished = 1
        if (secure.stopped) {
            running = ""
            stopped = true
        } else {
            running = "Checking server health…"
            val body = StartupChecks.parseHealth(health.body)
            val healthStep = StartupChecks.healthStep(health.code, body)
            steps = steps + healthStep
            finished = 2
            if (healthStep.stopped) {
                running = ""
                stopped = true
            } else {
                running = "Checking for a saved account…"
                val cookie = savedSessionCookie(context)
                val account = if (cookie == null) {
                    StartupChecks.accountStep(null, null, null)
                } else {
                    val me = withContext(Dispatchers.IO) { client.get(StartupChecks.ME_URL, cookie) }
                    val email = if (me.code == 200) StartupChecks.parseEmail(me.body) else null
                    val code = if (me.reached) me.code else 0
                    StartupChecks.accountStep(cookie, code, email)
                }
                steps = steps + account
                finished = 3
                if (account.stopped) {
                    running = ""
                    stopped = true
                } else {
                    running = "Comparing versions…"
                    val version = StartupChecks.versionStep(BuildConfig.VERSION_NAME, body?.version.orEmpty())
                    steps = steps + version
                    finished = 4
                    running = "Looking for the PC receiver…"
                    val names = waitForReceiver(session)
                    steps = steps + StartupChecks.receiverStep(names)
                    finished = 5
                    running = ""
                }
            }
        }
        val elapsed = System.currentTimeMillis() - started
        val remain = StartupChecks.MIN_VISIBLE_MS - elapsed
        if (!stopped && remain > 0) delay(remain)
        if (!stopped) onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Carbon)
            .clickable(interactionSource = sink, indication = null) {}
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "RACING WHEEL PRO",
                color = Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
            )
            Text(
                if (stopped) "A check stopped" else "Starting",
                color = SignalYellow,
                fontSize = 13.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            ProgressTrack(StartupChecks.progress(finished), stopped)
            Spacer(Modifier.height(8.dp))
            steps.forEach { step ->
                Text(step.title, color = if (step.ok) SignalGreen else SignalRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(step.detail, color = Ink, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
            }
            if (running.isNotEmpty()) {
                Text(running, color = Muted, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (stopped) "CONTINUE OFFLINE" else "CONTINUE",
                color = if (stopped) ColorBlack else Carbon,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(SignalYellow)
                    .clickable(onClick = onDone)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Text(
                "The wheel still drives if the site or the PC is out of reach.",
                color = Muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ProgressTrack(fraction: Float, failed: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(PanelRaised),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(10.dp)
                .background(if (failed) SignalRed else SignalGreen),
        )
    }
}

private fun savedSessionCookie(context: Context): String? {
    val pref = context.getSharedPreferences("rwp", Context.MODE_PRIVATE)
        .getString(StartupChecks.ACCOUNT_PREF, null)
    val jar = runCatching { CookieManager.getInstance().getCookie(StartupChecks.SITE) }.getOrNull()
    return StartupChecks.sessionCookie(pref, jar)
}

private suspend fun waitForReceiver(session: RaceSession): List<String> {
    val deadline = System.currentTimeMillis() + StartupChecks.RECEIVER_WAIT_MS
    while (true) {
        val found = session.hosts.map { host ->
            if (host.name.isBlank()) host.address else "${host.name} at ${host.address}"
        }
        if (found.isNotEmpty() || System.currentTimeMillis() >= deadline) return found
        delay(100)
    }
}

private val ColorBlack = androidx.compose.ui.graphics.Color.Black
