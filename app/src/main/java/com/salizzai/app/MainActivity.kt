package com.salizzai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.salizzai.app.ui.BrandIntroScreen
import com.salizzai.app.ui.SaliZZaiAppNavigation
import com.salizzai.app.ui.theme.SaliZZaiTheme

/**
 * Main Activity — single-activity Compose app.
 * Hosts the Auth flow, Chat, History, Discover, Image Gen, Settings.
 *
 * For Google sign-in, set `googleWebClientId` to the OAuth Web Client ID
 * you created in Google Cloud Console. If left empty, the Google button
 * falls back to an explanatory message — the user can still sign in
 * via email/password or OTP.
 */
class MainActivity : ComponentActivity() {

    // Override this in a forked product flavor, or wire via BuildConfig,
    // to enable one-tap Google sign-in.
    var googleWebClientId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SaliZZaiTheme {
                val app = SaliZZaiApp.get()
                // The animated brand intro is a first-run experience only. Playing
                // it on every cold start added ~2 s of perceived launch time.
                var showBrandIntro by rememberSaveable {
                    mutableStateOf(!app.encryptedPrefs.getBoolean(KEY_INTRO_SEEN, false))
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showBrandIntro) {
                        BrandIntroScreen(
                            onFinished = {
                                app.encryptedPrefs.edit()
                                    .putBoolean(KEY_INTRO_SEEN, true)
                                    .apply()
                                showBrandIntro = false
                            }
                        )
                    } else {
                        SaliZZaiAppNavigation()
                    }
                }
            }
        }
    }

    private companion object {
        const val KEY_INTRO_SEEN = "intro_seen"
    }
}
