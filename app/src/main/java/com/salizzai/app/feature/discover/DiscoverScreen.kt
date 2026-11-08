package com.salizzai.app.feature.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salizzai.app.SaliZZaiApp
import com.salizzai.app.data.model.WebSearchResult
import kotlinx.coroutines.launch

private data class RecencyOption(val value: String?, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(modifier: Modifier = Modifier) {
    val app = SaliZZaiApp.get()
    val zaiClient = remember { app.zaiClient }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<WebSearchResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var recency by remember { mutableStateOf<RecencyOption?>(null) }

    val recencyOptions = remember {
        listOf(
            RecencyOption(null, "همه"),
            RecencyOption("oneDay", "۲۴ ساعت"),
            RecencyOption("oneWeek", "این هفته"),
            RecencyOption("oneMonth", "این ماه")
        )
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty() || loading) return
        loading = true
        error = null
        scope.launch {
            zaiClient.webSearch(q, count = 12, recency = recency?.value).fold(
                onSuccess = {
                    results = it
                    searched = true
                },
                onFailure = { e ->
                    error = e.message ?: "جستجو ناموفق بود"
                    results = emptyList()
                    searched = true
                }
            )
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("کشف در وب", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "موتور search-prime زد‌ای‌آی",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                onValueChange = { query = it },
                placeholder = { Text("چه چیزی را جستجو کنیم؟") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { runSearch() }) {
                            Icon(Icons.Filled.Search, contentDescription = "جستجو")
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recencyOptions, key = { it.label }) { option ->
                    FilterChip(
                        selected = recency?.value == option.value,
                        onClick = { recency = option },
                        label = { Text(option.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                error != null -> Text(
                    "❌ $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )

                searched && results.isEmpty() -> EmptyState(
                    title = "نتیجه‌ای پیدا نشد",
                    subtitle = "عبارت دیگری را امتحان کنید یا بازهٔ زمانی را بازتر کنید."
                )

                results.isEmpty() -> EmptyState(
                    title = "جستجوی وب زنده",
                    subtitle = "نتایج به‌صورت خلاصه و آمادهٔ استناد نمایش داده می‌شوند — همان موتوری که چت زد‌ای‌آی استفاده می‌کند."
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "count") {
                        Text(
                            "${results.size} نتیجه",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(results, key = { it.url }) { result ->
                        SearchResultCard(
                            result = result,
                            onOpen = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(result.url)
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.TravelExplore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchResultCard(result: WebSearchResult, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.hostName.ifBlank { "وب" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.date.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = result.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
