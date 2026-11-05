package com.salizzai.app.feature.imagegen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import com.salizzai.app.SaliZZaiApp
import com.salizzai.app.data.model.ImageGenerationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenScreen(modifier: Modifier = Modifier) {
    val app = SaliZZaiApp.get()
    val zaiClient = app.zaiClient
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var imageBase64 by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var size by remember { mutableStateOf("1024x1024") }
    val sizes = listOf("1024x1024", "768x1344", "864x1152", "1344x768", "1152x864")

    Scaffold(
        topBar = { TopAppBar(title = { Text("تولید تصویر") }) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("توضیح تصویر") },
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sizes) { s ->
                    FilterChip(
                        selected = size == s,
                        onClick = { size = s },
                        label = { Text(s) }
                    )
                }
            }

            Button(
                onClick = {
                    if (prompt.isNotBlank() && !loading) {
                        loading = true
                        error = null
                        imageBase64 = null
                        scope.launch {
                            val result = zaiClient.generateImage(
                                ImageGenerationRequest(prompt = prompt, size = size)
                            )
                            loading = false
                            result.fold(
                                onSuccess = { response ->
                                    imageBase64 = response.data.firstOrNull()?.base64
                                        ?: response.data.firstOrNull()?.url
                                },
                                onFailure = { e ->
                                    error = e.message ?: "خطای ناشناخته"
                                }
                            )
                        }
                    }
                },
                enabled = prompt.isNotBlank() && !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("تولید تصویر")
                }
            }

            error?.let {
                Text("❌ $it", color = MaterialTheme.colorScheme.error)
            }

            imageBase64?.let { data ->
                val isRemoteUrl = data.startsWith("https://") || data.startsWith("http://")
                if (!isRemoteUrl) {
                    val decoded by produceState<Result<android.graphics.Bitmap>?>(null, data) {
                        value = withContext(Dispatchers.IO) {
                            runCatching {
                                val cleaned = if (data.startsWith("data:image")) {
                                    data.substringAfter(",")
                                } else {
                                    data
                                }
                                val bytes = Base64.decode(cleaned, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    ?: error("دادهٔ تصویر قابل خواندن نیست")
                            }
                        }
                    }
                    when {
                        decoded == null || decoded?.isSuccess == true -> {
                            decoded?.getOrNull()?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = prompt,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } ?: Box(
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        else -> Text(
                            "خطا در نمایش تصویر: ${decoded?.exceptionOrNull()?.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        coil.compose.AsyncImage(
                            model = data,
                            contentDescription = prompt,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
