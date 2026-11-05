package com.salizzai.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salizzai.app.SaliZZaiApp
import com.salizzai.app.data.model.AvailableModels
import com.salizzai.app.feature.auth.AuthViewModel
import com.salizzai.app.ui.theme.SaliPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = SaliZZaiApp.get()
    val prefs = app.userPrefs
    val zaiClient = app.zaiClient
    val scope = rememberCoroutineScope()
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.provideFactory())
    val authState by authViewModel.state.collectAsState()

    val theme by prefs.theme.collectAsState(initial = "dark")
    val model by prefs.model.collectAsState(initial = "glm-4.6")
    val temperature by prefs.temperature.collectAsState(initial = 0.7f)
    val thinkingEnabled by prefs.thinkingEnabled.collectAsState(initial = false)
    val systemPrompt by prefs.systemPrompt.collectAsState(initial = "")
    val ttsEnabled by prefs.ttsEnabled.collectAsState(initial = false)
    val voiceInputEnabled by prefs.voiceInputEnabled.collectAsState(initial = true)
    var temperatureDraft by remember(temperature) { mutableStateOf(temperature) }
    var systemPromptDraft by remember(systemPrompt) { mutableStateOf(systemPrompt) }

    LaunchedEffect(systemPromptDraft) {
        if (systemPromptDraft != systemPrompt) {
            delay(400)
            prefs.setSystemPrompt(systemPromptDraft)
        }
    }

    var apiKey by remember { mutableStateOf("") }
    var googleClientId by remember { mutableStateOf(zaiClient.getGoogleWebClientId() ?: "") }
    var baseUrl by remember { mutableStateOf(zaiClient.getSavedBaseUrl()) }
    var hasApiKey by remember { mutableStateOf(zaiClient.hasCredentials()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("تنظیمات") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account section
            SettingsSection(title = "حساب کاربری") {
                when (val s = authState) {
                    is com.salizzai.app.data.model.AuthState.LoggedIn -> {
                        Text("واردشده به‌عنوان: ${s.user.email}")
                        Text("نام: ${s.user.name ?: "—"}")
                        Text("روش ورود: ${s.user.authProvider}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SaveButton("خروج") {
                                authViewModel.logout()
                                onLoggedOut()
                            }
                            SaveButton("حذف حساب") { showDeleteDialog = true }
                        }
                    }
                    else -> {
                        Text("وارد نشده‌اید")
                    }
                }
            }

            // Z.ai API config section
            SettingsSection(title = "حساب Z.ai") {
                if (hasApiKey) {
                    Text("✓ API Key تنظیم شده است", color = SaliPurple)
                    Spacer(modifier = Modifier.height(8.dp))
                    SaveButton("حذف API Key") {
                        scope.launch {
                            zaiClient.clearCredentials()
                            hasApiKey = false
                            snackbarHostState.showSnackbar("API Key حذف شد")
                        }
                    }
                } else {
                    Text(
                        "برای کارکرد چت و تحلیل، نیاز به Z.ai API Key دارید. از https://chat.z.ai بخش API Keys بگیرید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Z.ai API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SaveButton("ذخیره") {
                        scope.launch {
                            zaiClient.setCredentials(baseUrl, apiKey, null, null)
                            hasApiKey = true
                            snackbarHostState.showSnackbar("ذخیره شد")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = googleClientId,
                    onValueChange = {
                        googleClientId = it
                        scope.launch {
                            zaiClient.setCredentials(null, null, null, null, googleClientId)
                        }
                    },
                    label = { Text("Google OAuth Web Client ID (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "برای ورود با گوگل باید یک OAuth Web Client ID بسازید (Google Cloud Console → Credentials). بدون این، دکمه گوگل فقط راهنما نشان می‌دهد.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Appearance
            SettingsSection(title = "ظاهر") {
                Text("تم: $theme")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaveButton("روشن") { scope.launch { prefs.setTheme("light") } }
                    SaveButton("تیره") { scope.launch { prefs.setTheme("dark") } }
                    SaveButton("سیستمی") { scope.launch { prefs.setTheme("system") } }
                }
            }

            // AI Model
            SettingsSection(title = "هوش مصنوعی") {
                Text("مدل فعال: $model")
                AvailableModels.ALL.forEach { m ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SaveButton(
                            text = "${m.displayName} — ${m.description}" +
                                (m.badge?.let { "  [$it]" } ?: "")
                        ) {
                            scope.launch { prefs.setModel(m.id) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تفکر عمیق (Reasoning)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = thinkingEnabled,
                        onCheckedChange = { v ->
                            scope.launch { prefs.setThinkingEnabled(v) }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("دما: ${"%.1f".format(temperatureDraft)}")
                Slider(
                    value = temperatureDraft,
                    onValueChange = { temperatureDraft = it },
                    onValueChangeFinished = {
                        scope.launch { prefs.setTemperature(temperatureDraft) }
                    },
                    valueRange = 0f..2f
                )
            }

            // Voice settings
            SettingsSection(title = "صوت") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ورودی صوتی (میکروفون)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = voiceInputEnabled,
                        onCheckedChange = { v -> scope.launch { prefs.setVoiceInputEnabled(v) } }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("پخش خودکار پاسخ (TTS)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = ttsEnabled,
                        onCheckedChange = { v -> scope.launch { prefs.setTtsEnabled(v) } }
                    )
                }
            }

            // System Prompt
            SettingsSection(title = "دستورالعمل سیستم") {
                OutlinedTextField(
                    value = systemPromptDraft,
                    onValueChange = { systemPromptDraft = it },
                    label = { Text("دستورالعمل سفارشی برای همه گفتگوها") },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
            }

            // About
            SettingsSection(title = "درباره") {
                Text("Sali-ZZai v2.0.0")
                Text("اپ نیتیو اندروید برای Z.ai")
                Text("تمام داده‌ها به‌صورت محلی روی دستگاه ذخیره می‌شوند")
                Text("احراز هویت: ایمیل/گذرواژه، کد یک‌بار مصرف، گوگل")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("حذف حساب کاربری") },
            text = { Text("این عملیات قابل بازگشت نیست. تمام گفتگوها و داده‌های شما حذف خواهد شد.") },
            confirmButton = {
                TextButton(onClick = {
                    authViewModel.deleteAccount()
                    showDeleteDialog = false
                    onLoggedOut()
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("لغو") }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SaveButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, color = SaliPurple)
    }
}
