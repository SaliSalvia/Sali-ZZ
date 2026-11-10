package com.salizzai.app.feature.imagegen

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.salizzai.app.SaliZZaiApp
import com.salizzai.app.data.model.ImageGenerationRequest
import com.salizzai.app.data.model.ImageModels
import com.salizzai.app.ui.components.BrandOrb
import com.salizzai.app.ui.components.SaliGradientButton
import com.salizzai.app.ui.components.SaliTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenScreen(modifier: Modifier = Modifier) {
    val app = SaliZZaiApp.get()
    val zaiClient = remember { app.zaiClient }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var prompt by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var imageData by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var size by remember { mutableStateOf(ImageModels.SIZES.first()) }
    var modelId by remember { mutableStateOf(ImageModels.GLM_IMAGE) }

    fun generate() {
        if (prompt.isBlank() || loading) return
        loading = true
        error = null
        imageData = null
        scope.launch {
            val result = zaiClient.generateImage(
                ImageGenerationRequest(
                    prompt = prompt.trim(),
                    size = size,
                    model = modelId
                )
            )
            loading = false
            result.fold(
                onSuccess = { response ->
                    val first = response.data.firstOrNull()
                    imageData = first?.base64 ?: first?.url
                    if (imageData == null) error = "پاسخی از سرور تصویر دریافت نشد."
                },
                onFailure = { e -> error = e.message ?: "خطای ناشناخته" }
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SaliTopBar(
                title = "تولید تصویر",
                subtitle = "GLM-Image / CogView-4"
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                placeholder = { Text("مثلاً: گربه‌ای روی طاقچهٔ آفتابی، سبک آبرنگ") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(16.dp)
            )

            Text(
                "مدل",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ImageModels.ALL, key = { it.id }) { option ->
                    FilterChip(
                        selected = modelId == option.id,
                        onClick = { modelId = option.id },
                        label = { Text(option.label) }
                    )
                }
            }

            Text(
                "اندازه",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ImageModels.SIZES, key = { it }) { option ->
                    FilterChip(
                        selected = size == option,
                        onClick = { size = option },
                        label = { Text(option) }
                    )
                }
            }

            SaliGradientButton(
                text = if (loading) "در حال ساخت تصویر…" else "تولید تصویر",
                icon = Icons.Filled.AutoAwesome,
                loading = loading,
                enabled = prompt.isNotBlank(),
                onClick = { generate() },
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Text("❌ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            imageData?.let { data ->
                val isRemoteUrl = data.startsWith("http://") || data.startsWith("https://")
                if (isRemoteUrl) {
                    coil.compose.AsyncImage(
                        model = data,
                        contentDescription = prompt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                    )
                } else {
                    val decoded by produceState<Result<Bitmap>?>(null, data) {
                        value = withContext(Dispatchers.IO) {
                            runCatching {
                                val cleaned = if (data.startsWith("data:image")) data.substringAfter(",") else data
                                val bytes = Base64.decode(cleaned, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    ?: error("دادهٔ تصویر قابل خواندن نیست")
                            }
                        }
                    }
                    when {
                        decoded == null -> Box(
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }

                        decoded?.isSuccess == true -> {
                            val bitmap = decoded!!.getOrThrow()
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = prompt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val saved = withContext(Dispatchers.IO) {
                                                saveToGallery(context, bitmap)
                                            }
                                            snackbarHostState.showSnackbar(
                                                if (saved) "تصویر در گالری ذخیره شد ✓"
                                                else "ذخیرهٔ تصویر ناموفق بود"
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("ذخیره در گالری")
                                }
                            }
                        }

                        else -> Text(
                            "خطا در نمایش تصویر: ${decoded?.exceptionOrNull()?.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (imageData == null && !loading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BrandOrb(size = 64.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "تصویر ساخته‌شده اینجا نمایش داده می‌شود",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Writes a generated bitmap into the device gallery (scoped storage aware). */
private fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = runCatching {
    val name = "sali_zzai_${System.currentTimeMillis()}.png"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/SaliZZai"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        true
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.insertImage(
            context.contentResolver,
            bitmap,
            name,
            "Sali-ZZai"
        ) != null
    }
}.getOrDefault(false)
