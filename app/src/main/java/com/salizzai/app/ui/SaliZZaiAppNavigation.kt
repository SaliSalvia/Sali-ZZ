package com.salizzai.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salizzai.app.MainActivity
import com.salizzai.app.SaliZZaiApp
import com.salizzai.app.data.model.AuthState
import com.salizzai.app.feature.auth.AuthScreen
import com.salizzai.app.feature.auth.AuthViewModel
import com.salizzai.app.feature.chat.ChatScreen
import com.salizzai.app.feature.discover.DiscoverScreen
import com.salizzai.app.feature.history.HistoryScreen
import com.salizzai.app.feature.imagegen.ImageGenScreen
import com.salizzai.app.feature.settings.SettingsScreen
import com.salizzai.app.ui.theme.SaliGradients

sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    object Chat : Tab("chat", "چت", Icons.Filled.Message)
    object History : Tab("history", "تاریخچه", Icons.Filled.History)
    object Discover : Tab("discover", "کشف", Icons.Filled.Search)
    object ImageGen : Tab("image", "تصویر", Icons.Filled.Image)
    object Settings : Tab("settings", "تنظیمات", Icons.Filled.AccountCircle)
}

@Composable
fun SaliZZaiAppNavigation() {
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.provideFactory())
    val authState by authViewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? MainActivity
    // Read the Google OAuth Web Client ID from the encrypted prefs (set in Settings)
    // instead of the always-empty field on MainActivity, so the Google button
    // actually works once the user has configured their Client ID.
    val googleWebClientId = remember {
        SaliZZaiApp.get(context).zaiClient.getGoogleWebClientId() ?: ""
    }

    when (authState) {
        is AuthState.Loading -> LoadingScreen()
        is AuthState.LoggedOut -> AuthScreen(
            onAuthed = { /* state will refresh automatically */ },
            onContinueAsGuest = { authViewModel.continueAsGuest() },
            googleWebClientId = googleWebClientId,
            activity = activity
        )
        is AuthState.LoggedIn -> MainAppNavigation(
            onLoggedOut = { /* state will refresh automatically */ }
        )
    }
}

@Composable
private fun LoadingScreen() {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { p ->
        Box(
            modifier = Modifier.padding(p).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MainAppNavigation(
    onLoggedOut: () -> Unit
) {
    // Save the route String, not the Tab object: Tab objects are not
    // Serializable/Parcelable, so rememberSaveable<Tab> threw
    // IllegalArgumentException ("cannot be saved") whenever the state was
    // persisted (rotation, backgrounding, process recreation).
    var currentTabRoute by rememberSaveable { mutableStateOf(Tab.Chat.route) }
    val currentTab: Tab = when (currentTabRoute) {
        Tab.History.route -> Tab.History
        Tab.Discover.route -> Tab.Discover
        Tab.ImageGen.route -> Tab.ImageGen
        Tab.Settings.route -> Tab.Settings
        else -> Tab.Chat
    }
    var pendingConversationId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            SaliBottomBar(
                tabs = listOf(Tab.Chat, Tab.History, Tab.Discover, Tab.ImageGen, Tab.Settings),
                currentRoute = currentTab.route,
                onSelect = { tab -> currentTabRoute = tab.route }
            )
        }
    ) { padding ->
        when (currentTab) {
            is Tab.Chat -> ChatScreen(
                conversationId = pendingConversationId,
                onOpenHistory = { currentTabRoute = Tab.History.route },
                onOpenSettings = { currentTabRoute = Tab.Settings.route },
                modifier = Modifier.padding(padding)
            )
            is Tab.History -> HistoryScreen(
                onOpenConversation = { id ->
                    pendingConversationId = id
                    currentTabRoute = Tab.Chat.route
                },
                onNewChat = {
                    pendingConversationId = null
                    currentTabRoute = Tab.Chat.route
                },
                modifier = Modifier.padding(padding)
            )
            is Tab.Discover -> DiscoverScreen(modifier = Modifier.padding(padding))
            is Tab.ImageGen -> ImageGenScreen(modifier = Modifier.padding(padding))
            is Tab.Settings -> SettingsScreen(
                onLoggedOut = onLoggedOut,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

/**
 * Floating, rounded navigation bar. The selected tab gets a gradient pill that
 * fades and scales in, which reads as "this is the active surface" without any
 * per-frame layout work.
 */
@Composable
private fun SaliBottomBar(
    tabs: List<Tab>,
    currentRoute: String,
    onSelect: (Tab) -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.let { c ->
        c.red * 0.299f + c.green * 0.587f + c.blue * 0.114f < 0.5f
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            tabs.forEach { tab ->
                val selected = tab.route == currentRoute
                val emphasis by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(durationMillis = 220),
                    label = "tab-emphasis"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(tab) }
                        )
                        .padding(vertical = 5.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(width = 46.dp, height = 28.dp)
                                .graphicsLayer { alpha = emphasis }
                                .clip(RoundedCornerShape(50))
                                .background(SaliGradients.brand(dark))
                        )
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    val scale = 0.9f + emphasis * 0.1f
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
