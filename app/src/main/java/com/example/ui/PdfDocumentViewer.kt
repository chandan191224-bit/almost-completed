package com.example.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.db.DocEntity
import com.example.viewmodel.DocViewModel
import java.io.File
import android.content.Context
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun PdfDocumentViewer(
    doc: DocEntity,
    viewModel: DocViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pdfRenderer by remember(doc.content) { mutableStateOf<PdfRenderer?>(null) }
    var parcelFileDescriptor by remember(doc.content) { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(doc.content) {
        val file = File(doc.content)
        if (!file.exists()) {
            errorMsg = "PDF file not found. It might have been deleted from storage."
        } else {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                parcelFileDescriptor = pfd
                pdfRenderer = renderer
                pageCount = renderer.pageCount
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = "Failed to open PDF file: ${e.message}"
            }
        }

        onDispose {
            try {
                pdfRenderer?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                parcelFileDescriptor?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            pdfRenderer = null
            parcelFileDescriptor = null
        }
    }

    var isAiChatOpen by remember { mutableStateOf(false) }
    var extractedText by remember { mutableStateOf("") }
    var isExtractingText by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Start text extraction in background when document changes
    LaunchedEffect(doc.content) {
        isExtractingText = true
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = File(doc.content)
            if (file.exists()) {
                extractedText = extractTextFromPdf(file)
            }
            if (extractedText.isBlank()) {
                extractedText = "This document is titled '${doc.title}'. It is a PDF document stored at '${doc.content}'."
            }
            isExtractingText = false
        }
    }

    var aiQuery by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val activeDocId = remember(doc) { doc.id.toString() }

    val chatMessages = remember(activeDocId) {
        val savedHistory = viewModel.getChatHistory(activeDocId)
        val list = mutableStateListOf<ChatMessage>()
        if (savedHistory != null) {
            list.addAll(savedHistory)
        } else {
            list.add(
                ChatMessage(
                    sender = "Mobius",
                    text = "Hello! I am Mobius, your professional AI assistant. I can help you analyze, edit, or structure your document with precise actions. How can I assist you today?",
                    isUser = false
                )
            )
            viewModel.setChatHistory(activeDocId, list.toList())
        }
        list
    }

    LaunchedEffect(chatMessages.toList()) {
        viewModel.setChatHistory(activeDocId, chatMessages.toList())
    }

    val suggestions = listOf("📊 Analyse PDF", "📝 Summarize Content", "💡 Core Insights")
    
    val sharedPreferences = remember { context.getSharedPreferences("ai_agent_prefs", Context.MODE_PRIVATE) }
    val geminiApiKey = sharedPreferences.getString("gemini_api_key", "") ?: ""
    val apiKey = if (geminiApiKey.isNotBlank()) geminiApiKey else com.example.BuildConfig.GEMINI_API_KEY
    val openRouterKey = sharedPreferences.getString("openrouter_key", "") ?: ""

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFEAECF0).copy(alpha = 0.5f))
    ) {
        val isWide = maxWidth >= 600.dp

        // Main content area
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (errorMsg != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Could Not Open Document",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMsg ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (pdfRenderer == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Preparing PDF visualization...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // PDF pages scroll list
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1.0f, 3.0f)
                                panOffset += pan
                            }
                        }
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale,
                            translationX = panOffset.x,
                            translationY = panOffset.y,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                        )
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pdf_scroll_list"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(pageCount) { index ->
                            PdfPageItem(
                                renderer = pdfRenderer,
                                pageIndex = index
                            )
                        }
                    }
                }

                // Floating reset zoom button
                androidx.compose.animation.AnimatedVisibility(
                    visible = zoomScale > 1.01f || panOffset != Offset.Zero,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it / 2 }
                    ),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(
                        targetOffsetY = { it / 2 }
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    Button(
                        onClick = {
                            zoomScale = 1.0f
                            panOffset = Offset.Zero
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                        modifier = Modifier.testTag("fit_to_view_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ZoomOutMap,
                            contentDescription = "Fit to View",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fit to View",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Floating AI button (BottomEnd)
        FloatingActionButton(
            onClick = { isAiChatOpen = !isAiChatOpen },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 80.dp, end = 16.dp)
                .testTag("pdf_ai_floating_button")
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "Ask PDF AI"
            )
        }

        // Beautiful Chat card overlay
        AnimatedVisibility(
            visible = isAiChatOpen,
            enter = if (isWide) {
                androidx.compose.animation.slideInHorizontally(initialOffsetX = { it })
            } else {
                androidx.compose.animation.slideInVertically(initialOffsetY = { it })
            },
            exit = if (isWide) {
                androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it })
            } else {
                androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
            },
            modifier = Modifier
                .align(if (isWide) Alignment.CenterEnd else Alignment.BottomCenter)
                .fillMaxHeight(if (isWide) 0.9f else 0.65f)
                .width(if (isWide) 380.dp else maxWidth)
                .padding(if (isWide) PaddingValues(vertical = 16.dp, horizontal = 12.dp) else PaddingValues())
                .shadow(
                    elevation = 12.dp,
                    shape = if (isWide) RoundedCornerShape(16.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = if (isWide) RoundedCornerShape(16.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1.0f)) {
                            Text(
                                text = "Mobius AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isKeySet = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            color = if (isKeySet) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isKeySet) "Online Analysis" else "Offline Simulation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Clear Chat Button
                        IconButton(
                            onClick = {
                                chatMessages.clear()
                                chatMessages.add(
                                    ChatMessage(
                                        sender = "Mobius",
                                        text = "Conversation cleared! Ask me anything about \"${doc.title}\".",
                                        isUser = false
                                    )
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Close Button
                        IconButton(
                            onClick = { isAiChatOpen = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Chat messages list
                    val chatListState = rememberLazyListState()

                    LaunchedEffect(chatMessages.size, isThinking) {
                        if (chatMessages.isNotEmpty()) {
                            chatListState.animateScrollToItem(chatMessages.size - 1)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.0f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = chatListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(chatMessages) { message ->
                                val isMe = message.isUser
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isMe) 16.dp else 4.dp,
                                            bottomEnd = if (isMe) 4.dp else 16.dp
                                        ),
                                        color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        tonalElevation = 1.dp,
                                        modifier = Modifier.widthIn(max = 280.dp)
                                    ) {
                                        Text(
                                            text = message.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            if (isThinking) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.widthIn(max = 200.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Analyzing contents...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Suggestion Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            SuggestionChip(
                                onClick = {
                                    val trimmedPrompt = suggestion.substring(2).trim()
                                    val q = suggestion
                                    chatMessages.add(ChatMessage(sender = "User", text = q, isUser = true))
                                    isThinking = true

                                    coroutineScope.launch {
                                        val defaultModel = sharedPreferences.getString("default_ai_model_key", "Gemini 2.5 Flash") ?: "Gemini 2.5 Flash"
                                        val response = executePdfAiRequest(
                                            geminiApiKey = apiKey,
                                            openRouterKey = openRouterKey,
                                            history = chatMessages.toList(),
                                            pdfText = extractedText,
                                            userPrompt = q,
                                            docTitle = doc.title,
                                            modelName = defaultModel
                                        )
                                        isThinking = false
                                        chatMessages.add(ChatMessage(sender = "Mobius", text = response, isUser = false))
                                    }
                                },
                                label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    // Bottom Text Input Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = aiQuery,
                            onValueChange = { aiQuery = it },
                            placeholder = { Text("Ask PDF AI...", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                        .weight(1.0f)
                                        .testTag("pdf_ai_text_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            trailingIcon = {
                                if (aiQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            val queryToSend = aiQuery
                                            aiQuery = ""
                                            chatMessages.add(ChatMessage(sender = "User", text = queryToSend, isUser = true))
                                            isThinking = true

                                            coroutineScope.launch {
                                                val defaultModel = sharedPreferences.getString("default_ai_model_key", "Gemini 2.5 Flash") ?: "Gemini 2.5 Flash"
                                                val response = executePdfAiRequest(
                                                    geminiApiKey = apiKey,
                                                    openRouterKey = openRouterKey,
                                                    history = chatMessages.toList(),
                                                    pdfText = extractedText,
                                                    userPrompt = queryToSend,
                                                    docTitle = doc.title,
                                                    modelName = defaultModel
                                                )
                                                isThinking = false
                                                chatMessages.add(ChatMessage(sender = "Mobius", text = response, isUser = false))
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Send Message",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
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
fun PdfPageItem(
    renderer: PdfRenderer?,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(renderer, pageIndex) {
        if (renderer == null) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val page = renderer.openPage(pageIndex)
                
                // base clear resolution factor: 2.0x for high quality
                val renderScale = 2.0f
                val width = (page.width * renderScale).toInt().coerceAtLeast(1)
                val height = (page.height * renderScale).toInt().coerceAtLeast(1)
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pageBitmap = bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.message ?: "Failed to render page"
            }
        }
    }

    Box(
        modifier = modifier
            .wrapContentWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        if (pageBitmap != null) {
            val aspect = pageBitmap!!.width.toFloat() / pageBitmap!!.height
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = RoundedCornerShape(2.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(aspect)
                    .border(0.5.dp, Color.LightGray, RoundedCornerShape(2.dp))
            ) {
                Image(
                    bitmap = pageBitmap!!.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1} of PDF",
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (errorMsg != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(200.dp)
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Page ${pageIndex + 1}: Render Error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(300.dp)
                    .background(Color.White, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// Private OkHttpClient shared across functions
private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
    .writeTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
    .build()

// Helper to extract text streams from PDF natively
fun extractTextFromPdf(file: java.io.File): String {
    if (!file.exists()) return ""
    try {
        val bytes = file.readBytes()
        val textBuilder = java.lang.StringBuilder()
        
        var i = 0
        val n = bytes.size
        while (i < n - 6) {
            if (bytes[i] == 's'.toByte() &&
                bytes[i+1] == 't'.toByte() &&
                bytes[i+2] == 'r'.toByte() &&
                bytes[i+3] == 'e'.toByte() &&
                bytes[i+4] == 'a'.toByte() &&
                bytes[i+5] == 'm'.toByte()
            ) {
                var streamStart = i + 6
                if (streamStart < n && bytes[streamStart] == '\r'.toByte()) streamStart++
                if (streamStart < n && bytes[streamStart] == '\n'.toByte()) streamStart++
                
                var endStreamIdx = -1
                var j = streamStart
                while (j < n - 9) {
                    if (bytes[j] == 'e'.toByte() &&
                        bytes[j+1] == 'n'.toByte() &&
                        bytes[j+2] == 'd'.toByte() &&
                        bytes[j+3] == 's'.toByte() &&
                        bytes[j+4] == 't'.toByte() &&
                        bytes[j+5] == 'r'.toByte() &&
                        bytes[j+6] == 'e'.toByte() &&
                        bytes[j+7] == 'a'.toByte() &&
                        bytes[j+8] == 'm'.toByte()
                    ) {
                        endStreamIdx = j
                        break
                    }
                    j++
                }
                
                if (endStreamIdx != -1) {
                    val streamLength = endStreamIdx - streamStart
                    if (streamLength > 0) {
                        val compressedData = bytes.copyOfRange(streamStart, endStreamIdx)
                        try {
                            val inflater = java.util.zip.Inflater(true)
                            inflater.setInput(compressedData)
                            val decompressedBytes = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(2048)
                            try {
                                while (!inflater.finished()) {
                                    val count = inflater.inflate(buffer)
                                    if (count == 0) break
                                    decompressedBytes.write(buffer, 0, count)
                                }
                            } catch (zipEx: java.util.zip.DataFormatException) {
                                val standardInflater = java.util.zip.Inflater(false)
                                standardInflater.setInput(compressedData)
                                val retryOutput = java.io.ByteArrayOutputStream()
                                while (!standardInflater.finished()) {
                                    val count = standardInflater.inflate(buffer)
                                    if (count == 0) break
                                    retryOutput.write(buffer, 0, count)
                                }
                                standardInflater.end()
                                decompressedBytes.write(retryOutput.toByteArray())
                            }
                            inflater.end()
                            
                            val decompressedText = decompressedBytes.toString("UTF-8")
                            parsePdfText(decompressedText, textBuilder)
                        } catch (e: Exception) {
                            try {
                                val maybePlain = String(compressedData, Charsets.UTF_8)
                                parsePdfText(maybePlain, textBuilder)
                            } catch (plainEx: Exception) {
                                // Ignore binary streams
                            }
                        }
                    }
                    i = endStreamIdx + 9
                    continue
                }
            }
            i++
        }
        
        val parsed = textBuilder.toString().trim()
        if (parsed.isNotEmpty()) {
            return parsed
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return ""
}

private fun parsePdfText(rawText: String, builder: java.lang.StringBuilder) {
    var inParens = false
    var escape = false
    val temp = java.lang.StringBuilder()
    var idx = 0
    val length = rawText.length
    while (idx < length) {
        val char = rawText[idx]
        if (escape) {
            temp.append(char)
            escape = false
        } else if (char == '\\') {
            escape = true
        } else if (char == '(') {
            inParens = true
            temp.setLength(0)
        } else if (char == ')') {
            inParens = false
            if (temp.isNotEmpty()) {
                builder.append(temp.toString())
                builder.append(" ")
            }
        } else if (inParens) {
            temp.append(char)
        }
        idx++
    }
    builder.append("\n")
}

// Make Gemini cloud REST API call for the PDF
suspend fun callPdfGeminiAPI(
    apiKey: String,
    history: List<ChatMessage>,
    pdfText: String,
    userPrompt: String,
    modelName: String
): String = withContext(Dispatchers.IO) {
    try {
        val requestJson = JSONObject()
        val contentsArray = JSONArray()
        
        // Context/Roleplay History
        val recentHistory = history.filter { !it.isSystemNotice && !it.isKeyMissingNotice }.takeLast(15)
        recentHistory.forEach { msg ->
            val contentObj = JSONObject()
            contentObj.put("role", if (msg.isUser) "user" else "model")
            val partsArr = JSONArray()
            val partOb = JSONObject()
            partOb.put("text", msg.text)
            partsArr.put(partOb)
            contentObj.put("parts", partsArr)
            contentsArray.put(contentObj)
        }
        
        // Append current prompt if it is not already in history
        if (recentHistory.none { it.text == userPrompt }) {
            val currentContentObj = JSONObject()
            currentContentObj.put("role", "user")
            val currentPartsArr = JSONArray()
            val currentPartOb = JSONObject()
            currentPartOb.put("text", userPrompt)
            currentPartsArr.put(currentPartOb)
            currentContentObj.put("parts", currentPartsArr)
            contentsArray.put(currentContentObj)
        }
        
        requestJson.put("contents", contentsArray)

        // System instructions with full document visibility
        val systemInstruction = """
You are Mobius, an advanced PDF Document Analyzer created by the JCdocs team.
Your main job is to analyze the PDF text provided below and answer the user's questions with absolute precision, high-quality structure, and professional phrasing.
Here is the raw text context extracted from the PDF:
----------------------------------------
${pdfText.take(15000)}
----------------------------------------
Analyze this PDF context carefully to answer the user's question. If the user asks to "analyze" or "summarize" the PDF, please produce a structured overview containing:
1. Executive Summary
2. Key Topics/Themes Explained
3. Actionable Insights or Major Findings
Use Material-style formatting with bold titles and neat bullet points. Always speak as an intelligent, helpful expert.
"""
        val sysInstructionObj = JSONObject()
        val sysPartsArray = JSONArray()
        val sysPartObj = JSONObject()
        sysPartObj.put("text", systemInstruction)
        sysPartsArray.put(sysPartObj)
        sysInstructionObj.put("parts", sysPartsArray)
        requestJson.put("systemInstruction", sysInstructionObj)

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val selectedModel = when (modelName) {
            "Gemini 2.5 Flash" -> "gemini-2.5-flash"
            "Gemini 2.5 Pro" -> "gemini-2.5-pro"
            "Gemini 1.5 Pro" -> "gemini-1.5-pro"
            else -> "gemini-1.5-flash"
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext "ERROR_API_FAIL: ${response.code} ${response.message}"
            }
            val resStr = response.body?.string() ?: ""
            val resObj = JSONObject(resStr)
            val candidates = resObj.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val part = parts?.optJSONObject(0)
            part?.optString("text") ?: "I processed your request, but did not receive a structured response."
        }
    } catch (e: Exception) {
        "ERROR_EXCEPTION: ${e.localizedMessage}"
    }
}

// Generate rich offline responses with real document statistics
fun generateOfflinePdfAnalysis(prompt: String, pdfText: String, docTitle: String): String {
    val upper = prompt.lowercase().trim()
    val words = pdfText.split(Regex("\\s+")).filter { it.isNotBlank() }
    val wordCount = words.size
    val readTime = maxOf(1, wordCount / 200)
    
    val lines = pdfText.split("\n").map { it.trim() }.filter { it.isNotBlank() && it.length > 5 }
    val headings = lines.filter { it.length in 5..80 && it.any { char -> char.isUpperCase() } }.take(5)
    
    val bulletPoints = if (headings.isNotEmpty()) {
        headings.joinToString("\n") { "• **Potential Section/Theme**: $it" }
    } else {
        "• **General Theme**: Document exploration and structural records.\n• **Context**: Technical document report overview."
    }
    
    if (upper.contains("analyze") || upper.contains("analyse") || upper.contains("summary") || upper.contains("summarize")) {
        return """
**📊 OFFLINE PDF ANALYSIS PREVIEW**
*Title:* $docTitle
*Status:* Offline Simulation Mode (Configure a Gemini API Key in Settings to enable real-time cloud neural analysis!)

**Executive Summary**
This PDF document "${docTitle}" consists of approximately **$wordCount words** with an estimated reading time of **$readTime minute(s)**. Here is a simulated analysis of its structure and extracted content snippets:

**Detected Structural Points:**
$bulletPoints

**Statistical Overview:**
- **Approximate Character Count:** ${pdfText.length} characters
- **Page Sections Checked:** Multi-page scan complete
- **Key Focus Elements:** Extracted selectable plain-text blocks successfully

*To unlock live deep logical analysis, summarize arbitrary segments, or translate paragraphs instantly, hook up your Gemini API Key in the settings.*
        """.trimIndent()
    }
    
    return """
**💡 OFFLINE RESPONSE**
*Status:* Offline Simulation Mode (Configure a Gemini API Key in Settings to enable live AI responses!)

You asked: "$prompt"

I analyzed the local PDF text context:
- Document Title: "$docTitle"
- Size/Density: $wordCount words
- Selected Context: "${pdfText.take(150)}..."

To answer your specific query with complete semantic reasoning and context understanding, please activate online AI mode by entering your **Gemini API Key** in the ⚙️ Settings panel.
    """.trimIndent()
}

// Make OpenRouter cloud REST API call for the PDF in case Gemini fails
suspend fun callPdfOpenRouterAPI(
    apiKey: String,
    history: List<ChatMessage>,
    pdfText: String,
    userPrompt: String
): String = withContext(Dispatchers.IO) {
    val systemInstruction = """
You are Mobius, an advanced PDF Document Analyzer created by the JCdocs team.
Your main job is to analyze the PDF text provided below and answer the user's questions with absolute precision, high-quality structure, and professional phrasing.
Here is the raw text context extracted from the PDF:
----------------------------------------
${pdfText.take(15000)}
----------------------------------------
Analyze this PDF context carefully to answer the user's question. If the user asks to "analyze" or "summarize" the PDF, please produce a structured overview containing:
1. Executive Summary
2. Key Topics/Themes Explained
3. Actionable Insights or Major Findings
Use Material-style formatting with bold titles and neat bullet points. Always speak as an intelligent, helpful expert.
"""

    val models = listOf(
        "google/gemini-2.5-flash",
        "meta-llama/llama-3.1-8b-instruct:free",
        "qwen/qwen-2.5-72b-instruct",
        "openrouter/auto"
    )

    var lastError = "No models tried"
    for (model in models) {
        try {
            val requestJson = JSONObject()
            requestJson.put("model", model)

            val messagesArray = JSONArray()
            val systemMsg = JSONObject()
            systemMsg.put("role", "system")
            systemMsg.put("content", systemInstruction)
            messagesArray.put(systemMsg)

            var lastRole: String? = null
            var lastContent = java.lang.StringBuilder()

            val recentHistory = history.filter { !it.isSystemNotice && !it.isKeyMissingNotice }.takeLast(15)

            for (msg in recentHistory) {
                val roleVal = if (msg.isUser) "user" else "assistant"
                if (roleVal == lastRole) {
                    lastContent.append("\n\n").append(msg.text)
                } else {
                    if (lastRole != null) {
                        val msgOb = JSONObject()
                        msgOb.put("role", lastRole)
                        msgOb.put("content", lastContent.toString())
                        messagesArray.put(msgOb)
                    }
                    lastRole = roleVal
                    lastContent = java.lang.StringBuilder(msg.text)
                }
            }
            if (lastRole != null) {
                val msgOb = JSONObject()
                msgOb.put("role", lastRole)
                msgOb.put("content", lastContent.toString())
                messagesArray.put(msgOb)
            }

            if (recentHistory.none { it.text == userPrompt }) {
                val userMsgOb = JSONObject()
                userMsgOb.put("role", "user")
                userMsgOb.put("content", userPrompt)
                messagesArray.put(userMsgOb)
            }

            requestJson.put("messages", messagesArray)

            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/aistudio")
                .addHeader("X-Title", "JCDocs AI Agent")
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val resStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: $resStr")
                }
                val resObj = JSONObject(resStr)
                if (resObj.has("error")) {
                    val errObj = resObj.getJSONObject("error")
                    val errMsg = errObj.optString("message", "Unknown error")
                    throw Exception("OpenRouter API Error: $errMsg")
                }
                val choices = resObj.optJSONArray("choices")
                val choice = choices?.optJSONObject(0)
                val messageObj = choice?.optJSONObject("message")
                val content = messageObj?.optString("content")
                if (!content.isNullOrEmpty()) {
                    return@withContext content
                }
            }
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: "Unknown error"
        }
    }
    return@withContext "ERROR_FAILOVER_FAIL: $lastError"
}

// Coordinate fallback API processing
suspend fun executePdfAiRequest(
    geminiApiKey: String,
    openRouterKey: String,
    history: List<ChatMessage>,
    pdfText: String,
    userPrompt: String,
    docTitle: String,
    modelName: String
): String {
    val isGeminiAvailable = geminiApiKey.isNotBlank() && geminiApiKey != "MY_GEMINI_API_KEY"
    val isOpenRouterAvailable = openRouterKey.isNotBlank()

    // If both are missing, immediately guide the user to set up an API Key!
    if (!isGeminiAvailable && !isOpenRouterAvailable) {
        return "I am currently running in offline simulation mode.\n\nTo unlock deep, full-scale neural PDF analysis, please configure a valid **Gemini API Key** or an **OpenRouter API Key** in the ⚙️ Settings panel! You can access Settings by closing the PDF viewer and clicking on the Settings icon."
    }

    // 1. Try Gemini API first (Default)
    if (isGeminiAvailable) {
        val result = callPdfGeminiAPI(geminiApiKey, history, pdfText, userPrompt, modelName)
        if (!result.startsWith("ERROR_API_FAIL") && !result.startsWith("ERROR_EXCEPTION")) {
            return result
        }
    }

    // 2. Fallback to OpenRouter (Seamlessly)
    if (isOpenRouterAvailable) {
        val result = callPdfOpenRouterAPI(openRouterKey, history, pdfText, userPrompt)
        if (!result.startsWith("ERROR_FAILOVER_FAIL")) {
            return result
        }
    }

    // 3. Failed both or exhausted limits
    return "We encountered an issue with all connected AI models (rate limits exceeded or connections timed out).\n\nPlease check your **Gemini API Key** or **OpenRouter API Key** configurations inside the ⚙️ Settings panel, or try again in a few moments."
}
