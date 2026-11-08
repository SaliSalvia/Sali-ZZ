package com.salizzai.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                listOf(
                    Tab.Chat, Tab.History, Tab.Discover, Tab.ImageGen, Tab.Settings
                ).forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab.route == tab.route,
                        onClick = { currentTabRoute = tab.route },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
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
