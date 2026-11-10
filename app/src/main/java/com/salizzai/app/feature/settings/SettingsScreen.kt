@file:OptIn(ExperimentalMaterial3Api::class)

package com.salizzai.app.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salizzai.app.BuildConfig
import com.salizzai.app.SaliZZaiApp
import com.salizzai.app.data.api.ZaiApiClient
import com.salizzai.app.data.model.AvailableModels
import com.salizzai.app.data.model.ReasoningEffort
import com.salizzai.app.data.model.ZaiModel
import com.salizzai.app.feature.auth.AuthViewModel
import com.salizzai.app.ui.components.SaliGradientButton
import com.salizzai.app.ui.components.SaliIconTile
import com.salizzai.app.ui.components.SaliTopBar
import com.salizzai.app.ui.components.isDarkTheme
import com.salizzai.app.ui.theme.ErrorRed
import com.salizzai.app.ui.theme.SaliGradients
import com.salizzai.app.ui.theme.SaliPurple
import com.salizzai.app.ui.theme.SuccessGreen
import com.salizzai.app.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** UI state of the "verify key" flow. */
private sealed interface ApiStatus {
    data object Idle : ApiStatus
    data object Checking : ApiStatus
    data class Valid(
        val modelIds: List<String>,
        val fromApi: Boolean,
        val workingModel: String,
        val latencyMs: Long
    ) : ApiStatus
    data class Failed(val message: String) : ApiStatus
}

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = SaliZZaiApp.get()
    val prefs = app.userPrefs
    val zaiClient = app.zaiClient
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.provideFactory())
    val authState by authViewModel.state.collectAsState()

    val theme by prefs.theme.collectAsState(initial = "dark")
    val model by prefs.model.collectAsState(initial = AvailableModels.DEFAULT_MODEL)
    val temperature by prefs.temperature.collectAsState(initial = 0.7f)
    val thinkingEnabled by prefs.thinkingEnabled.collectAsState(initial = true)
    val reasoningEffortValue by prefs.reasoningEffort.collectAsState(initial = ReasoningEffort.MAX.apiValue)
    val webSearchEnabled by prefs.webSearchEnabled.collectAsState(initial = false)
    val systemPrompt by prefs.systemPrompt.collectAsState(initial = "")
    val ttsEnabled by prefs.ttsEnabled.collectAsState(initial = false)
    val voiceInputEnabled by prefs.voiceInputEnabled.collectAsState(initial = true)
    val remoteModelIds by prefs.availableModelIds.collectAsState(initial = emptySet())
    val validatedAt by prefs.apiValidatedAt.collectAsState(initial = 0L)

    var temperatureDraft by remember(temperature) { mutableStateOf(temperature) }
    var systemPromptDraft by remember(systemPrompt) { mutableStateOf(systemPrompt) }

    LaunchedEffect(systemPromptDraft) {
        if (systemPromptDraft != systemPrompt) {
            delay(400)
            prefs.setSystemPrompt(systemPromptDraft)
        }
    }

    var apiKeyDraft by remember { mutableStateOf("") }
    var baseUrlDraft by remember { mutableStateOf(zaiClient.getSavedBaseUrl()) }
    var googleClientId by remember { mutableStateOf(zaiClient.getGoogleWebClientId() ?: "") }
    var hasApiKey by remember { mutableStateOf(zaiClient.hasCredentials()) }
    var editingKey by remember { mutableStateOf(!zaiClient.hasCredentials()) }
    var showKey by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<ApiStatus>(ApiStatus.Idle) }
    var modelsLoading by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val models = remember(remoteModelIds) { AvailableModels.catalog(remoteModelIds) }

    /** Verifies the key against Z.ai; only persists it when the API accepts it. */
    fun checkAndSave(base: String, key: String) {
        if (status is ApiStatus.Checking) return
        scope.launch {
            status = ApiStatus.Checking
            when (val result = zaiClient.validateKey(base, key, model)) {
                is ZaiApiClient.KeyCheckResult.Success -> {
                    zaiClient.setCredentials(base, key, null, null)
                    val ids = result.modelIds.ifEmpty { AvailableModels.ALL.map { it.id } }.toSet()
                    prefs.setAvailableModelIds(ids)
                    prefs.setApiValidatedAt(System.currentTimeMillis())
                    if (model !in ids) {
                        AvailableModels.bestAvailable(ids)?.let { prefs.setModel(it) }
                    }
                    baseUrlDraft = zaiClient.getSavedBaseUrl()
                    apiKeyDraft = ""
                    hasApiKey = true
                    editingKey = false
                    showKey = false
                    status = ApiStatus.Valid(
                        modelIds = result.modelIds,
                        fromApi = result.modelListFromApi,
                        workingModel = result.workingModel,
                        latencyMs = result.latencyMs
                    )
                    snackbarHostState.showSnackbar("کلید تأیید و ذخیره شد ✓")
                }
                is ZaiApiClient.KeyCheckResult.Invalid -> status = ApiStatus.Failed(result.message)
                is ZaiApiClient.KeyCheckResult.Failed -> status = ApiStatus.Failed(result.message)
            }
        }
    }

    /** Re-reads the live model list for the saved key. */
    fun refreshModels() {
        if (modelsLoading) return
        scope.launch {
            modelsLoading = true
            val key = zaiClient.getSavedApiKey().orEmpty()
            val result = zaiClient.fetchModelIds(baseUrlDraft, key)
            modelsLoading = false
            result.onSuccess { ids ->
                if (ids.isEmpty()) {
                    snackbarHostState.showSnackbar("API فهرست مدلی برنگرداند؛ فهرست داخلی نمایش داده می‌شود.")
                } else {
                    prefs.setAvailableModelIds(ids.toSet())
                    if (model !in ids) AvailableModels.bestAvailable(ids)?.let { prefs.setModel(it) }
                    snackbarHostState.showSnackbar("${ids.size} مدل دریافت شد ✓")
                }
            }.onFailure {
                snackbarHostState.showSnackbar(it.message ?: "دریافت فهرست مدل‌ها ناموفق بود")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SaliTopBar(
                title = "تنظیمات",
                subtitle = "Sali-ZZai v${BuildConfig.VERSION_NAME}"
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ─── Account ────────────────────────────────────────────────
            SettingsSection(title = "حساب کاربری", icon = Icons.Filled.AccountCircle) {
                when (val s = authState) {
                    is com.salizzai.app.data.model.AuthState.LoggedIn -> {
                        InfoRow("ایمیل", s.user.email)
                        InfoRow("نام", s.user.name ?: "—")
                        InfoRow("روش ورود", s.user.authProvider)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                authViewModel.logout()
                                onLoggedOut()
                            }) { Text("خروج") }
                            TextButton(onClick = { showDeleteDialog = true }) {
                                Text("حذف حساب", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    else -> Text(
                        "وارد نشده‌اید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ─── Z.ai account / API key ────────────────────────────────
            SettingsSection(title = "اتصال به Z.ai", icon = Icons.Filled.Key) {
                if (hasApiKey && !editingKey) {
                    ConnectedSummary(
                        baseUrl = zaiClient.getSavedBaseUrl(),
                        maskedKey = maskKey(zaiClient.getSavedApiKey()),
                        validatedAt = validatedAt,
                        checking = status is ApiStatus.Checking,
                        onTest = {
                            checkAndSave(
                                zaiClient.getSavedBaseUrl(),
                                zaiClient.getSavedApiKey().orEmpty()
                            )
                        },
                        onChange = {
                            apiKeyDraft = ""
                            baseUrlDraft = zaiClient.getSavedBaseUrl()
                            editingKey = true
                            status = ApiStatus.Idle
                        },
                        onRemove = {
                            scope.launch {
                                zaiClient.clearCredentials()
                                prefs.setAvailableModelIds(emptySet())
                                prefs.setApiValidatedAt(0L)
                                hasApiKey = false
                                editingKey = true
                                status = ApiStatus.Idle
                                snackbarHostState.showSnackbar("کلید API حذف شد")
                            }
                        }
                    )
                } else {
                    Text(
                        "برای استفاده از چت، کلید API خود را وارد کنید. کلید پیش از ذخیره " +
                            "به‌صورت واقعی تست می‌شود و فقط در صورت تأیید Z.ai ذخیره می‌گردد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = baseUrlDraft,
                        onValueChange = { baseUrlDraft = it },
                        label = { Text("Base URL") },
                        supportingText = { Text("پیش‌فرض: ${ZaiApiClient.DEFAULT_BASE_URL}") },
                        trailingIcon = {
                            TextButton(onClick = { baseUrlDraft = ZaiApiClient.DEFAULT_BASE_URL }) {
                                Text("پیش‌فرض", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = { apiKeyDraft = it },
                        label = { Text("Z.ai API Key") },
                        placeholder = { Text("کلید خود را اینجا بچسبانید") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = if (showKey) "پنهان‌کردن کلید" else "نمایش کلید"
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SaliGradientButton(
                            text = if (status is ApiStatus.Checking) "در حال بررسی…" else "بررسی و ذخیره",
                            icon = Icons.Filled.CheckCircle,
                            loading = status is ApiStatus.Checking,
                            enabled = apiKeyDraft.isNotBlank(),
                            onClick = { checkAndSave(baseUrlDraft, apiKeyDraft) }
                        )
                        if (hasApiKey) {
                            TextButton(onClick = {
                                editingKey = false
                                status = ApiStatus.Idle
                            }) { Text("انصراف") }
                        }
                    }
                    TextButton(onClick = { openUrl(context, "https://chat.z.ai") }) {
                        Text(
                            "دریافت کلید API از chat.z.ai ↗",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                ApiStatusMessage(status)

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))
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
                    "برای ورود با گوگل باید یک OAuth Web Client ID بسازید (Google Cloud Console → Credentials).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ─── Appearance ────────────────────────────────────────────
            SettingsSection(title = "ظاهر", icon = Icons.Filled.Palette) {
                Text(
                    "تم برنامه",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoicePill("روشن", theme == "light") {
                        scope.launch { prefs.setTheme("light") }
                    }
                    ChoicePill("تیره", theme == "dark") {
                        scope.launch { prefs.setTheme("dark") }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoicePill("سیستمی", theme == "system") {
                        scope.launch { prefs.setTheme("system") }
                    }
                    ChoicePill("AMOLED", theme == "amoled") {
                        scope.launch { prefs.setTheme("amoled") }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "حالت AMOLED پس‌زمینه را کاملاً مشکی می‌کند و در پنل‌های OLED باعث صرفه‌جویی باتری می‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ─── Model ─────────────────────────────────────────────────
            SettingsSection(title = "مدل هوش مصنوعی", icon = Icons.Filled.AutoAwesome) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "مدل فعال",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            AvailableModels.displayName(model),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Badge("رایگان", SuccessGreen)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (hasApiKey) {
                        "فهرست زیر از حساب Z.ai شما خوانده می‌شود؛ مدل‌های رایگان GLM-5 در ابتدا هستند."
                    } else {
                        "پس از تأیید کلید API، فهرست مدل‌های در دسترس حساب شما به‌صورت خودکار دریافت می‌شود."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasApiKey) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = { refreshModels() }, enabled = !modelsLoading) {
                        if (modelsLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("دریافت خودکار فهرست مدل‌ها")
                    }
                    if (modelsLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(4.dp))
                models.forEach { m ->
                    ModelRow(
                        model = m,
                        selected = m.id.equals(model, ignoreCase = true),
                        onSelect = { scope.launch { prefs.setModel(m.id) } }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                // GLM-5.3 / 5.3-Flash cannot turn reasoning off — Z.ai returns an
                // error for thinking.type="disabled" — so those models expose depth
                // (reasoning_effort) instead of an on/off switch.
                Text("عمق تفکر", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (AvailableModels.requiresThinking(model)) {
                        "GLM-5.3 همیشه فکر می‌کند؛ این گزینه فقط میزان عمق استدلال را تعیین می‌کند."
                    } else {
                        "مدل پیش از پاسخ، مراحل استدلال را نشان می‌دهد."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReasoningEffort.SELECTABLE.forEach { option ->
                        ChoicePill(option.label, reasoningEffortValue == option.apiValue) {
                            scope.launch { prefs.setReasoningEffort(option.apiValue) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    ReasoningEffort.from(reasoningEffortValue).description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (AvailableModels.byId(model)?.canToggleThinking == true) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ToggleRow(
                        title = "فعال‌بودن تفکر",
                        subtitle = "برای این مدل می‌توانید استدلال را کاملاً خاموش کنید",
                        checked = thinkingEnabled,
                        onCheckedChange = { v -> scope.launch { prefs.setThinkingEnabled(v) } }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("دما", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "%.1f".format(temperatureDraft),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = temperatureDraft.coerceIn(0f, 1f),
                    onValueChange = { temperatureDraft = it },
                    onValueChangeFinished = {
                        scope.launch { prefs.setTemperature(temperatureDraft.coerceIn(0f, 1f)) }
                    },
                    valueRange = 0f..1f
                )
                Text(
                    "بازهٔ مجاز Z.ai ۰ تا ۱ است. مقدار کم = دقیق‌تر، مقدار زیاد = خلاقانه‌تر.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ─── Voice ─────────────────────────────────────────────────
            SettingsSection(title = "صوت", icon = Icons.Filled.Mic) {
                ToggleRow(
                    title = "ورودی صوتی (میکروفون)",
                    subtitle = "تبدیل گفتار به متن داخل چت",
                    checked = voiceInputEnabled,
                    onCheckedChange = { v -> scope.launch { prefs.setVoiceInputEnabled(v) } }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                ToggleRow(
                    title = "پخش خودکار پاسخ (TTS)",
                    subtitle = "خواندن پاسخ مدل با صدا",
                    checked = ttsEnabled,
                    onCheckedChange = { v -> scope.launch { prefs.setTtsEnabled(v) } }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                ToggleRow(
                    title = "جستجوی وب پیش‌فرض",
                    subtitle = "هر پیام با نتایج زندهٔ وب (search-prime) پشتیبانی شود",
                    checked = webSearchEnabled,
                    onCheckedChange = { v -> scope.launch { prefs.setWebSearchEnabled(v) } }
                )
            }

            // ─── System prompt ─────────────────────────────────────────
            SettingsSection(title = "دستورالعمل سیستم", icon = Icons.Filled.Info) {
                OutlinedTextField(
                    value = systemPromptDraft,
                    onValueChange = { systemPromptDraft = it },
                    label = { Text("دستورالعمل سفارشی برای همه گفتگوها") },
                    placeholder = { Text("مثلاً: پاسخ‌ها را کوتاه و فنی بنویس.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }

            // ─── About ─────────────────────────────────────────────────
            SettingsSection(title = "درباره", icon = Icons.Filled.Info) {
                Text("Sali-ZZai v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "اپ نیتیو اندروید برای Z.ai — بدون سرور؛ داده‌ها و کلید API فقط روی دستگاه ذخیره می‌شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = { openUrl(context, "https://github.com/SaliSalvia/Sali-ZZ") }) {
                    Text(
                        "github.com/SaliSalvia/Sali-ZZ ↗",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
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

// ─── Components ─────────────────────────────────────────────────────

@Composable
private fun ConnectedSummary(
    baseUrl: String,
    maskedKey: String,
    validatedAt: Long,
    checking: Boolean,
    onTest: () -> Unit,
    onChange: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = SuccessGreen.copy(alpha = 0.10f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "کلید API معتبر و ذخیره‌شده است",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "کلید: $maskedKey",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "سرور: $baseUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            formatValidatedAt(validatedAt)?.let {
                Text(
                    "آخرین تأیید: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onTest, enabled = !checking) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("تست اتصال")
                }
                TextButton(onClick = onChange) { Text("تغییر کلید") }
                TextButton(onClick = onRemove) {
                    Text("حذف", color = ErrorRed)
                }
            }
        }
    }
}

@Composable
private fun ApiStatusMessage(status: ApiStatus) {
    AnimatedVisibility(
        visible = status !is ApiStatus.Idle,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column {
            Spacer(modifier = Modifier.height(10.dp))
            when (status) {
                is ApiStatus.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "در حال بررسی کلید و دریافت فهرست مدل‌ها…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ApiStatus.Valid -> Surface(
                    color = SuccessGreen.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp)) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "کلید معتبر است ✓",
                                style = MaterialTheme.typography.labelLarge,
                                color = SuccessGreen
                            )
                            Text(
                                buildString {
                                    if (status.fromApi) {
                                        append("${status.modelIds.size} مدل در دسترس")
                                    } else {
                                        append("ارتباط برقرار شد (فهرست مدل از API خوانده نشد)")
                                    }
                                    append(" • ${status.latencyMs} میلی‌ثانیه")
                                    append("\nمدل تست‌شده: ${status.workingModel}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is ApiStatus.Failed -> Surface(
                    color = ErrorRed.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp)) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "کلید ذخیره نشد",
                                style = MaterialTheme.typography.labelLarge,
                                color = ErrorRed
                            )
                            Text(
                                status.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                ApiStatus.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: ZaiModel,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val container =
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else Color.Transparent
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .then(
                if (selected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                model.badge?.let { badge ->
                    Spacer(modifier = Modifier.width(6.dp))
                    Badge(
                        text = badge,
                        color = when {
                            model.isFreeTier -> SuccessGreen
                            badge == "بینایی" -> WarningAmber
                            else -> SaliPurple
                        }
                    )
                }
            }
            Text(
                model.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit) {
    // Selected pills carry the brand gradient; the rest stay quiet and flat.
    val background: Brush =
        if (selected) SaliGradients.brand(isDarkTheme())
        else SolidColor(MaterialTheme.colorScheme.surfaceVariant)
    val foreground =
        if (selected) Color.White
        else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SaliIconTile(icon = icon, size = 34.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────────

private fun maskKey(key: String?): String {
    if (key.isNullOrBlank()) return "—"
    if (key.length <= 8) return "•".repeat(key.length)
    return key.take(4) + "…" + key.takeLast(4)
}

private fun formatValidatedAt(timestamp: Long): String? {
    if (timestamp <= 0L) return null
    val minutes = (System.currentTimeMillis() - timestamp) / 60_000
    return when {
        minutes < 1 -> "همین حالا"
        minutes < 60 -> "$minutes دقیقه پیش"
        minutes < 24 * 60 -> "${minutes / 60} ساعت پیش"
        else -> "${minutes / (24 * 60)} روز پیش"
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
