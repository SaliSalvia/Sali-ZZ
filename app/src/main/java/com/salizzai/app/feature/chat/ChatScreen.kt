package com.salizzai.app.feature.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salizzai.app.data.db.entity.Attachment
import com.salizzai.app.data.db.entity.Message
import com.salizzai.app.ui.components.MarkdownText
import com.salizzai.app.ui.components.ThinkingIndicator
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String?,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.provideFactory())
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(conversationId) {
        viewModel.setConversationId(conversationId)
    }

    // Multi-pick: ZIPs, images, text files
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            for (uri in uris) {
                val file = withContext(Dispatchers.IO) {
                    val filename = queryFilename(context, uri) ?: "file"
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) null else Triple(filename, mimeType, bytes)
                } ?: continue

                val (filename, mimeType, bytes) = file
                val isZip = filename.lowercase().endsWith(".zip") ||
                    mimeType == "application/zip" ||
                    mimeType == "application/x-zip-compressed"
                val isImage = mimeType.startsWith("image/")
                when {
                    isZip -> viewModel.processZipFile(filename, mimeType, bytes)
                    isImage -> viewModel.processImageFile(filename, mimeType, bytes)
                    else -> viewModel.processRegularFile(filename, mimeType, bytes)
                }
            }
            viewModel.clearError()
        }
    }

    // Error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll: triggered on either message count or last message length
    val lastMsgLen by remember {
        derivedStateOf { uiState.messages.lastOrNull()?.content?.length ?: 0 }
    }
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(uiState.messages.size, lastMsgLen) {
        if (uiState.messages.isNotEmpty() && isNearBottom) {
            // Use animateScrollToItem for smooth, edge-safe scrolling.
            // A positive scrollOffset (0) is safer than negative values which can throw
            // IllegalArgumentException on certain Compose versions when the target item
            // is not yet measured.
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.conversationTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = uiState.model,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "تاریخچه گفتگوها")
                    }
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "گزینه‌های بیشتر")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("گفتگوی جدید") },
                                onClick = { viewModel.newChat(); menuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("اشتراک‌گذاری") },
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT,
                                            "https://github.com/SaliSalvia/Sali-ZZ")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"))
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تنظیمات") },
                                onClick = { onOpenSettings(); menuExpanded = false }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(data) } },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            // Reasoning panel (collapsible, like DeepSeek's "thinking" panel)
            AnimatedVisibility(
                visible = uiState.pendingReasoning.isNotBlank(),
                enter = slideInVertically(tween(200)) + fadeIn(),
                exit = slideOutVertically(tween(200)) + fadeOut(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                "در حال تفکر عمیق…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = uiState.pendingReasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.messages.isEmpty()) {
                    item { WelcomeContent() }
                } else {
                    items(
                        items = uiState.messages,
                        key = { it.id },
                        contentType = { "chat_message" }
                    ) { message ->
                        MessageItem(
                            message = message,
                            onCopy = {
                                clipboard.setText(AnnotatedString(message.content))
                                scope.launch { snackbarHostState.showSnackbar("کپی شد") }
                            },
                            onSpeak = { viewModel.speakReply(message.content) },
                            onStopSpeak = { viewModel.stopSpeaking() },
                            isSpeaking = uiState.isSpeaking
                        )
                    }
                }
            }

            // Attachments preview
            AnimatedVisibility(
                visible = uiState.attachments.isNotEmpty(),
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
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

            // Input bar
            ChatInputBar(
                text = if (uiState.voicePartial.isNotBlank()) uiState.voicePartial
                       else uiState.draftInput,
                onTextChange = viewModel::updateDraftInput,
                onSend = { viewModel.sendMessage(it) },
                isStreaming = uiState.isStreaming,
                isListening = uiState.isListening,
                onUploadClick = { filePicker.launch(arrayOf("*/*")) },
                onMicClick = {
                    if (uiState.isListening) viewModel.stopVoiceInput()
                    else viewModel.startVoiceInput()
                },
                onStopClick = { viewModel.stopGeneration() }
            )
        }
    }
}

@Composable
private fun WelcomeContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Z",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sali-ZZai",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "دستیار هوشمند Z.ai — چت، تحلیل ZIP، تولید تصویر و جستجوی وب",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("💡 چند پیشنهاد:", style = MaterialTheme.typography.labelLarge)
                Text("• چند فایل ZIP آپلود کن و تحلیل مقایسه‌ای بگیر", style = MaterialTheme.typography.bodySmall)
                Text("• عکس از دست‌نوشته یا سند بگیر و تحلیل کن", style = MaterialTheme.typography.bodySmall)
                Text("• با میکروفون صحبت کن و پاسخ صوتی بگیر", style = MaterialTheme.typography.bodySmall)
                Text("• مدل GLM-4.6 را امتحان کن — با تفکر عمیق", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: Message,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onStopSpeak: () -> Unit,
    isSpeaking: Boolean
) {
    val isUser = message.role == "user"
    val showActions = !isUser && message.content.isNotBlank() && !message.isError

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 340.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 20.dp
                )
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    if (isUser) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else if (message.content.isBlank()) {
                        ThinkingIndicator()
                    } else {
                        MarkdownText(markdown = message.content)
                    }
                }
            }
            if (showActions) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "کپی",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { if (isSpeaking) onStopSpeak() else onSpeak() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (isSpeaking) Icons.Filled.StopCircle else Icons.Filled.VolumeUp,
                            contentDescription = "پخش صوتی",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: Attachment,
    onRemove: () -> Unit
) {
    val icon = when (attachment.fileType) {
        "zip" -> Icons.Filled.AttachFile
        "image" -> Icons.Filled.Image
        else -> Icons.Filled.AttachFile
    }
    Card(
        modifier = Modifier.padding(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column {
                Text(
                    text = attachment.originalName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
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
            Spacer(modifier = Modifier.size(8.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "حذف")
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    isStreaming: Boolean,
    isListening: Boolean,
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
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onUploadClick) {
                Icon(Icons.Filled.Add, contentDescription = "آپلود فایل")
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("پیام بنویسید…") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                maxLines = 5,
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(
                onClick = onMicClick,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                if (isListening) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(Icons.Filled.Mic, contentDescription = "ورودی صوتی")
                }
            }
            if (isStreaming) {
                IconButton(onClick = onStopClick) {
                    Icon(Icons.Filled.StopCircle, contentDescription = "توقف")
                }
            } else {
                IconButton(
                    onClick = { onSend(text) },
                    enabled = text.isNotBlank()
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "ارسال",
                        tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun queryFilename(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && it.moveToFirst()) return it.getString(nameIndex)
    }
    return uri.lastPathSegment
}
