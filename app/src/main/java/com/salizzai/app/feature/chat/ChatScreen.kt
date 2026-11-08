package com.salizzai.app.feature.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salizzai.app.SaliZZaiApp
import com.salizzai.app.data.db.entity.Attachment
import com.salizzai.app.data.db.entity.Message
import com.salizzai.app.data.model.AvailableModels
import com.salizzai.app.data.model.ReasoningEffort
import com.salizzai.app.data.model.WebSearchResult
import com.salizzai.app.ui.components.MarkdownText
import com.salizzai.app.ui.components.ReasoningCard
import com.salizzai.app.ui.components.ThinkingIndicator
import com.salizzai.app.ui.theme.SaliGradients
import com.salizzai.app.ui.theme.SuccessGreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String?,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.provideFactory())
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    val prefs = remember { SaliZZaiApp.get(context).userPrefs }
    val remoteModelIds by prefs.availableModelIds.collectAsState(initial = emptySet())

    var showModelSheet by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) { viewModel.setConversationId(conversationId) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val selectedUris = uris.take(MAX_PICKED_ATTACHMENTS)
            if (uris.size > MAX_PICKED_ATTACHMENTS) {
                snackbarHostState.showSnackbar("حداکثر $MAX_PICKED_ATTACHMENTS فایل در هر بار انتخاب پردازش می‌شود.")
            }
            var totalLoadedBytes = 0L
            for (uri in selectedUris) {
                val remainingBytes = (MAX_TOTAL_ATTACHMENT_BYTES - totalLoadedBytes).coerceAtLeast(0L)
                if (remainingBytes <= 0L) {
                    snackbarHostState.showSnackbar("سقف مجموع فایل‌های انتخاب‌شده (۵۰ مگابایت) تکمیل شد.")
                    break
                }
                try {
                    val loaded = withContext(Dispatchers.IO) {
                        val filename = queryFilename(context, uri) ?: "file"
                        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val perFileLimit = minOf(MAX_ATTACHMENT_BYTES.toLong(), remainingBytes).toInt()
                        val bytes = context.contentResolver.openInputStream(uri)?.use {
                            it.readBytesLimited(perFileLimit)
                        }
                        if (bytes == null) null else Triple(filename, mimeType, bytes)
                    } ?: continue

                    val (filename, mimeType, bytes) = loaded
                    totalLoadedBytes += bytes.size
                    val lower = filename.lowercase()
                    val isZip = lower.endsWith(".zip") ||
                        mimeType == "application/zip" ||
                        mimeType == "application/x-zip-compressed"
                    val isImage = mimeType.startsWith("image/")
                    when {
                        isZip -> viewModel.processZipFile(filename, mimeType, bytes)
                        isImage -> viewModel.processImageFile(filename, mimeType, bytes)
                        else -> viewModel.processRegularFile(filename, mimeType, bytes)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(
                        error.message ?: "خواندن فایل ناموفق بود؛ فایل دیگری را انتخاب کنید."
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val listState = rememberLazyListState()
    val lastMsgLen by remember {
        derivedStateOf { uiState.messages.lastOrNull()?.content?.length ?: 0 }
    }
    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(uiState.messages.size, lastMsgLen) {
        if (uiState.messages.isNotEmpty() && isNearBottom) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.conversationTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ModelChip(
                            modelId = uiState.model,
                            onClick = { showModelSheet = true }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "تاریخچه گفتگوها")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "گزینه‌های بیشتر")
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("گفتگوی جدید") },
                                onClick = { viewModel.newChat(); showOverflow = false }
                            )
                            DropdownMenuItem(
                                text = { Text("پاسخ دوباره") },
                                onClick = { viewModel.regenerate(); showOverflow = false }
                            )
                            DropdownMenuItem(
                                text = { Text("اشتراک‌گذاری") },
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "https://github.com/SaliSalvia/Sali-ZZ"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"))
                                    showOverflow = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تنظیمات") },
                                onClick = { onOpenSettings(); showOverflow = false }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(data) } },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (uiState.messages.isEmpty()) {
                        item(key = "welcome") {
                            WelcomeContent(
                                onSuggestion = { prompt ->
                                    viewModel.sendMessage(prompt)
                                }
                            )
                        }
                    } else {
                        val lastAssistantId = uiState.messages.lastOrNull { it.role == "assistant" }?.id
                        items(
                            items = uiState.messages,
                            key = { it.id },
                            contentType = { message ->
                                when {
                                    message.role == "user" -> "user"
                                    message.isError -> "error"
                                    else -> "assistant"
                                }
                            }
                        ) { message ->
                            val isLive = uiState.isStreaming && message.id == lastAssistantId
                            MessageItem(
                                message = message,
                                reasoningText = if (isLive && uiState.pendingReasoning.isNotBlank()) {
                                    uiState.pendingReasoning
                                } else message.reasoning,
                                reasoningStreaming = isLive && uiState.pendingReasoning.isNotBlank(),
                                reasoningMs = if (isLive) uiState.pendingReasoningMs else message.reasoningMs,
                                streaming = isLive && message.content.isNotBlank(),
                                isSpeaking = uiState.speakingMessageId == message.id,
                                canRegenerate = !uiState.isStreaming && message.id == lastAssistantId,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(message.content))
                                    scope.launch { snackbarHostState.showSnackbar("کپی شد") }
                                },
                                onSpeak = {
                                    if (uiState.speakingMessageId == message.id) {
                                        viewModel.stopSpeaking()
                                    } else {
                                        viewModel.speakReply(message.content, message.id)
                                    }
                                },
                                onRegenerate = { viewModel.regenerate() }
                            )
                        }
                    }
                    if (uiState.isSearchingWeb) {
                        item(key = "searching") { SearchingRow() }
                    }
                }

                AnimatedVisibility(
                    visible = uiState.attachments.isNotEmpty(),
                    enter = slideInVertically(tween(180)) + fadeIn(),
                    exit = slideOutVertically(tween(140)) + fadeOut()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.attachments, key = { it.id }) { att ->
                            val index = uiState.attachments.indexOfFirst { it.id == att.id }
                            AttachmentChip(
                                attachment = att,
                                onRemove = {
                                    val dataUrl = uiState.pendingImageUrls.getOrNull(index)
                                    if (att.fileType == "image" && dataUrl != null) {
                                        viewModel.removeImageAttachment(att.id, dataUrl)
                                    } else {
                                        viewModel.removeAttachment(att.id)
                                    }
                                }
                            )
                        }
                    }
                }

                ChatInputBar(
                    text = if (uiState.voicePartial.isNotBlank()) uiState.voicePartial
                    else uiState.draftInput,
                    onTextChange = viewModel::updateDraftInput,
                    onSend = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.sendMessage(it)
                    },
                    isStreaming = uiState.isStreaming,
                    isListening = uiState.isListening,
                    webSearchEnabled = uiState.webSearchEnabled,
                    onToggleWebSearch = { viewModel.toggleWebSearch() },
                    onUploadClick = { filePicker.launch(arrayOf("*/*")) },
                    onMicClick = {
                        if (uiState.isListening) viewModel.stopVoiceInput()
                        else viewModel.startVoiceInput()
                    },
                    onStopClick = { viewModel.stopGeneration() }
                )
            }

            // Floating "jump to newest" pill when the user scrolled up mid-stream.
            AnimatedVisibility(
                visible = !isNearBottom && uiState.messages.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 108.dp)
            ) {
                Surface(
                    onClick = {
                        scope.launch {
                            if (uiState.messages.isNotEmpty()) {
                                listState.animateScrollToItem(uiState.messages.lastIndex)
                            }
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 6.dp
                ) {
                    Icon(
                        Icons.Filled.ArrowDownward,
                        contentDescription = "رفتن به آخرین پیام",
                        modifier = Modifier.padding(10.dp).size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showModelSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            ModelPickerSheet(
                models = AvailableModels.catalog(remoteModelIds),
                selectedId = uiState.model,
                effort = uiState.reasoningEffort,
                onSelect = {
                    viewModel.setModel(it)
                    showModelSheet = false
                },
                onSelectEffort = viewModel::setReasoningEffort,
                onOpenSettings = {
                    showModelSheet = false
                    onOpenSettings()
                }
            )
        }
    }
}

// ─── Top bar chip ───────────────────────────────────────────────────

@Composable
private fun ModelChip(modelId: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(SuccessGreen)
        )
        Spacer(modifier = Modifier.size(5.dp))
        Text(
            text = AvailableModels.displayName(modelId),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "▾",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ─── Model picker sheet ─────────────────────────────────────────────

@Composable
private fun ModelPickerSheet(
    models: List<com.salizzai.app.data.model.ZaiModel>,
    selectedId: String,
    effort: ReasoningEffort,
    onSelect: (String) -> Unit,
    onSelectEffort: (ReasoningEffort) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .heightIn(max = 640.dp)
    ) {
        Text(
            "انتخاب مدل",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "مدل‌های رایگان GLM-5 در بالای فهرست هستند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        // Reasoning depth is the only knob GLM-5.3 exposes (thinking cannot be off).
        Text(
            "عمق تفکر",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReasoningEffort.SELECTABLE.forEach { option ->
                ValChip(
                    text = option.label,
                    selected = option == effort,
                    onClick = { onSelectEffort(option) }
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(models, key = { it.id }) { model ->
                val selected = model.id.equals(selectedId, ignoreCase = true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { onSelect(model.id) }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    RadioButton(selected = selected, onClick = { onSelect(model.id) })
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                model.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            model.badge?.let { badge ->
                                Spacer(modifier = Modifier.size(6.dp))
                                MiniBadge(
                                    text = badge,
                                    color = when {
                                        model.isFreeTier -> SuccessGreen
                                        badge == "بینایی" -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                            if (model.supportsVision) {
                                Spacer(modifier = Modifier.size(4.dp))
                                MiniBadge("تصویر", MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Text(
                            model.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("تنظیمات کامل مدل و کلید API")
        }
    }
}

@Composable
private fun ValChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

// ─── Welcome ────────────────────────────────────────────────────────

@Composable
private fun WelcomeContent(onSuggestion: (String) -> Unit) {
    val suggestions = listOf(
        "یک برنامهٔ اندروید با Jetpack Compose برای پیگیری عادت‌ها بنویس" to "کدنویسی",
        "این مقاله را نقد کن و نقاط ضعف روش‌شناسی‌اش را بگو" to "تحلیل",
        "تفاوت GLM-5.3 و GLM-5.3-Flash چیست؟" to "مقایسه",
        "یک ایمیل رسمی برای درخواست تخفیف بنویس" to "نوشتن"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SaliGradients.brand(true)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Z",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text("Sali-ZZai", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "دستیار هوشمند Z.ai روی اندروید — چت، تفکر عمیق، جستجوی وب، تصویر و تحلیل فایل",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(22.dp))

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { (prompt, tag) ->
                        SuggestionCard(
                            tag = tag,
                            prompt = prompt,
                            onClick = { onSuggestion(prompt) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    tag: String,
    prompt: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 92.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Message ────────────────────────────────────────────────────────

@Composable
private fun MessageItem(
    message: Message,
    reasoningText: String,
    reasoningStreaming: Boolean,
    reasoningMs: Long,
    streaming: Boolean,
    isSpeaking: Boolean,
    canRegenerate: Boolean,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.role == "user"
    val showActions = !isUser && message.content.isNotBlank() && !message.isError
    val sources = remember(message.sources) { parseSources(message.sources) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser && reasoningText.isNotBlank()) {
            ReasoningCard(
                reasoning = reasoningText,
                durationMs = reasoningMs,
                isStreaming = reasoningStreaming,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .padding(bottom = 6.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            val bubbleShape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            )
            val bubbleModifier = Modifier
                .widthIn(max = 560.dp)
                .clip(bubbleShape)
                .then(
                    if (isUser) Modifier.background(SaliGradients.userBubble(isDark(MaterialTheme.colorScheme.background)))
                    else Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                )
                .then(
                    if (!isUser && message.isError) {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                            bubbleShape
                        )
                    } else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)

            Box(modifier = bubbleModifier) {
                when {
                    isUser -> Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    message.content.isBlank() -> ThinkingIndicator(
                        label = if (reasoningText.isNotBlank()) "در حال نوشتن پاسخ…" else null
                    )
                    else -> MarkdownText(
                        markdown = message.content,
                        streaming = streaming
                    )
                }
            }
        }

        if (sources.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sources, key = { it.url }) { source ->
                    SourceChip(source)
                }
            }
        }

        if (showActions) {
            Row(
                modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "کپی",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSpeak, modifier = Modifier.size(30.dp)) {
                    Icon(
                        if (isSpeaking) Icons.Filled.StopCircle else Icons.Filled.VolumeUp,
                        contentDescription = if (isSpeaking) "توقف پخش" else "پخش صوتی",
                        modifier = Modifier.size(15.dp),
                        tint = if (isSpeaking) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canRegenerate) {
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "پاسخ دوباره",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (message.tokensUsed > 0) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        "${message.tokensUsed} توکن",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceChip(source: WebSearchResult) {
    val context = LocalContext.current
    Surface(
        onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
            }
        },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.widthIn(max = 200.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                source.hostName.ifBlank { "منبع" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(
                source.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            "در حال جستجو در وب…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Attachments ────────────────────────────────────────────────────

@Composable
private fun AttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    val icon = when (attachment.fileType) {
        "zip" -> Icons.Filled.AttachFile
        "image" -> Icons.Filled.Image
        else -> Icons.Filled.AttachFile
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column(modifier = Modifier.widthIn(max = 160.dp)) {
                Text(
                    text = attachment.originalName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (attachment.fileType) {
                        "zip" -> "ZIP • ${attachment.fileCount} فایل"
                        "image" -> "تصویر"
                        else -> "${attachment.size / 1024} KB"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "حذف",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Input bar ──────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    isStreaming: Boolean,
    isListening: Boolean,
    webSearchEnabled: Boolean,
    onToggleWebSearch: () -> Unit,
    onUploadClick: () -> Unit,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .imePadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(26.dp)
                    )
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                IconButton(onClick = onUploadClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "افزودن فایل، ZIP یا تصویر",
                        modifier = Modifier.size(20.dp)
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            "پیام خود را بنویسید…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    maxLines = 6,
                    shape = RoundedCornerShape(22.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                IconButton(onClick = onToggleWebSearch, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = "جستجوی وب",
                        modifier = Modifier.size(19.dp),
                        tint = if (webSearchEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onMicClick, modifier = Modifier.size(40.dp)) {
                    if (isListening) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "ورودی صوتی",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val sendEnabled = text.isNotBlank() && !isStreaming
                val sendSize by animateDpAsState(
                    targetValue = if (sendEnabled || isStreaming) 40.dp else 36.dp,
                    animationSpec = tween(140),
                    label = "send-size"
                )
                val sendBrush: androidx.compose.ui.graphics.Brush = when {
                    isStreaming -> androidx.compose.ui.graphics.SolidColor(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    )
                    sendEnabled -> SaliGradients.brand(isDark(MaterialTheme.colorScheme.background))
                    else -> androidx.compose.ui.graphics.SolidColor(
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }
                Box(
                    modifier = Modifier
                        .size(sendSize)
                        .clip(CircleShape)
                        .background(sendBrush)
                        .clickable(enabled = isStreaming || sendEnabled) {
                            if (isStreaming) onStopClick() else onSend(text)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Filled.StopCircle else Icons.Filled.Send,
                        contentDescription = if (isStreaming) "توقف" else "ارسال",
                        modifier = Modifier.size(18.dp),
                        tint = when {
                            isStreaming -> MaterialTheme.colorScheme.error
                            sendEnabled -> Color.White
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (webSearchEnabled) {
                Text(
                    "جستجوی وب روشن است — پاسخ با منابع وب پشتیبانی می‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }
        }
    }
}

private fun isDark(color: Color): Boolean =
    (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue) < 0.5f

private fun parseSources(raw: String?): List<WebSearchResult> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        Json.decodeFromString(ListSerializer(WebSearchResult.serializer()), raw)
    }.getOrDefault(emptyList())
}

private fun queryFilename(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && it.moveToFirst()) return it.getString(nameIndex)
    }
    return uri.lastPathSegment
}


private const val MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024
private const val MAX_TOTAL_ATTACHMENT_BYTES = 50L * 1024 * 1024
private const val MAX_PICKED_ATTACHMENTS = 8

/** Reads a provider stream without allowing an oversized document to exhaust app memory. */
private fun InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    require(maxBytes > 0) { "حد فایل باید بیشتر از صفر باشد." }
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) continue
        require(total <= maxBytes - count) {
            "حجم فایل از سقف مجاز این نوبت بیشتر است؛ فایل کوچک‌تری انتخاب کنید."
        }
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}
