package com.racingwheelpro

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.racingwheelpro.session.RaceSession
import com.racingwheelpro.ui.AppRoot
import com.racingwheelpro.ui.RacingTheme

class MainActivity : ComponentActivity() {
    private lateinit var session: RaceSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        session = RaceSession(applicationContext)
        session.start()
        setContent {
            RacingTheme {
                AppRoot(session)
            }
        }
    }

    override fun onDestroy() {
        if (::session.isInitialized) session.stop()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (::session.isInitialized && session.onKey(keyCode, true)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (::session.isInitialized && session.onKey(keyCode, false)) return true
        return super.onKeyUp(keyCode, event)
    }
}
