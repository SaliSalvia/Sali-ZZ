package com.salizzai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
                var showBrandIntro by rememberSaveable { mutableStateOf(true) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showBrandIntro) {
                        BrandIntroScreen(onFinished = { showBrandIntro = false })
                    } else {
                        SaliZZaiAppNavigation()
                    }
                }
            }
        }
    }
}
