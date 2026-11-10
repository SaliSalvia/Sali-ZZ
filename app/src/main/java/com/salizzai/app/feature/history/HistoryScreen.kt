package com.salizzai.app.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salizzai.app.SaliZZaiApp
import com.salizzai.app.data.db.entity.Conversation
import com.salizzai.app.ui.components.SaliEmptyState
import com.salizzai.app.ui.components.SaliIconTile
import com.salizzai.app.ui.components.SaliTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryViewModel : ViewModel() {
    private val app = SaliZZaiApp.get()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<Conversation>> =
        app.userPrefs.loggedInUserId
            .flatMapLatest { userId ->
                app.database.conversationDao().observeForUser(userId).combine(_query) { convs, q ->
                    if (q.isBlank()) convs
                    else convs.filter { it.title.contains(q, ignoreCase = true) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateQuery(q: String) { _query.value = q }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            app.database.conversationDao().deleteById(id)
        }
    }

    fun togglePin(id: String, pinned: Boolean) {
        viewModelScope.launch {
            app.database.conversationDao().setPinned(id, pinned)
        }
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HistoryViewModel() as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.provideFactory())
    val query by viewModel.query.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SaliTopBar(
                title = "تاریخچه گفتگوها",
                subtitle = if (conversations.isEmpty()) "هنوز گفتگویی نیست"
                else "${conversations.size} گفتگو",
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(Icons.Filled.Add, contentDescription = "گفتگوی جدید")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.updateQuery(it) },
                placeholder = { Text("جستجو در گفتگوها…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (conversations.isEmpty()) {
                    item(key = "empty") {
                        SaliEmptyState(
                            icon = if (query.isBlank()) Icons.Filled.History else Icons.Filled.Search,
                            title = if (query.isBlank()) "هنوز گفتگویی ندارید" else "نتیجه‌ای پیدا نشد",
                            subtitle = if (query.isBlank()) "اولین گفتگویتان را از صفحه چت شروع کنید."
                            else "عبارت جستجو را کوتاه‌تر یا متفاوت وارد کنید."
                        )
                    }
                }
                items(conversations, key = { it.id }) { conv ->
                    var menuOpen by remember(conv.id) { mutableStateOf(false) }
                    var confirmDelete by remember(conv.id) { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .animateItem(),
                        onClick = { onOpenConversation(conv.id) },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            1.dp,
                            if (conv.pinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SaliIconTile(
                                icon = Icons.Filled.ChatBubbleOutline,
                                size = 42.dp,
                                gradient = conv.pinned
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (conv.pinned) {
                                        Icon(
                                            Icons.Filled.PushPin,
                                            contentDescription = "سنجاق‌شده",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = conv.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = dateFormatter.format(Date(conv.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { menuOpen = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "گزینه‌ها",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (conv.pinned) "برداشتن سنجاق" else "سنجاق به بالا") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.PushPin, contentDescription = null)
                                        },
                                        onClick = {
                                            viewModel.togglePin(conv.id, !conv.pinned)
                                            menuOpen = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("حذف گفتگو", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            confirmDelete = true
                                            menuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (confirmDelete) {
                        AlertDialog(
                            onDismissRequest = { confirmDelete = false },
                            title = { Text("حذف گفتگو") },
                            text = { Text("«${conv.title}» و همهٔ پیام‌های آن حذف شود؟") },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.deleteConversation(conv.id)
                                    confirmDelete = false
                                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmDelete = false }) { Text("لغو") }
                            }
                        )
                    }
                }
            }
        }
    }
}
