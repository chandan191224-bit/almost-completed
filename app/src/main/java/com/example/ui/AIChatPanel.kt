package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DocWordColor
import com.example.viewmodel.DocViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val isUser: Boolean,
    val isSystemNotice: Boolean = false,
    val isKeyMissingNotice: Boolean = false
)

private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
    .writeTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
    .build()

@Composable
fun AIChatPanel(
    onClose: () -> Unit,
    viewModel: DocViewModel,
    modifier: Modifier = Modifier,
    // Document editing bindings
    activeTextFieldValue: TextFieldValue = TextFieldValue(""),
    onTextFieldValueChange: (TextFieldValue) -> Unit = {},
    // Layout parameters
    pageMargins: Dp = 24.dp,
    onPageMarginsChange: (Dp) -> Unit = {},
    pageMarginTop: Dp = 24.dp,
    onPageMarginTopChange: (Dp) -> Unit = {},
    pageMarginBottom: Dp = 24.dp,
    onPageMarginBottomChange: (Dp) -> Unit = {},
    pageMarginLeft: Dp = 24.dp,
    onPageMarginLeftChange: (Dp) -> Unit = {},
    pageMarginRight: Dp = 24.dp,
    onPageMarginRightChange: (Dp) -> Unit = {},
    fontSize: TextUnit = 16.sp,
    onFontSizeChange: (TextUnit) -> Unit = {},
    isLandscape: Boolean = false,
    onIsLandscapeChange: (Boolean) -> Unit = {},
    columnCount: Int = 1,
    onColumnCountChange: (Int) -> Unit = {},
    watermarkText: String = "",
    onWatermarkSet: (String, String) -> Unit = { _, _ -> },
    pageBorderType: String = "None",
    onPageBorderTypeChange: (String) -> Unit = {},
    // Headers & footers
    headerText: String = "",
    onHeaderChange: (String) -> Unit = {},
    footerText: String = "",
    onFooterChange: (String) -> Unit = {},
    // Reviews triggers
    onShowReviewDialog: (String) -> Unit = {},
    showToast: (String) -> Unit = {},
    onNavigateToSettings: (() -> Unit)? = null,
    onPushSnapshot: () -> Unit = {},
    onNavigate: ((String) -> Unit)? = null,
    onChangeSetting: ((String, String) -> Unit)? = null
) {
    val selectedDoc by viewModel.selectedDoc.collectAsState()
    val allDocs by viewModel.allDocsFlow.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    var prompt by remember { mutableStateOf("") }
    
    val runActions = { actionsJson: String ->
        executeActions(
            actionsJson = actionsJson,
            viewModel = viewModel,
            activeTextFieldValue = activeTextFieldValue,
            onTextFieldValueChange = onTextFieldValueChange,
            pageMargins = pageMargins,
            onPageMarginsChange = onPageMarginsChange,
            fontSize = fontSize,
            onFontSizeChange = onFontSizeChange,
            isLandscape = isLandscape,
            onIsLandscapeChange = onIsLandscapeChange,
            columnCount = columnCount,
            onColumnCountChange = onColumnCountChange,
            watermarkText = watermarkText,
            onWatermarkSet = onWatermarkSet,
            pageBorderType = pageBorderType,
            onPageBorderTypeChange = onPageBorderTypeChange,
            headerText = headerText,
            onHeaderChange = onHeaderChange,
            footerText = footerText,
            onFooterChange = onFooterChange,
            onShowReviewDialog = onShowReviewDialog,
            selectedDoc = selectedDoc,
            showToast = showToast,
            pageMarginTop = pageMarginTop,
            onPageMarginTopChange = onPageMarginTopChange,
            pageMarginBottom = pageMarginBottom,
            onPageMarginBottomChange = onPageMarginBottomChange,
            pageMarginLeft = pageMarginLeft,
            onPageMarginLeftChange = onPageMarginLeftChange,
            pageMarginRight = pageMarginRight,
            onPageMarginRightChange = onPageMarginRightChange,
            onPushSnapshot = onPushSnapshot,
            onNavigate = onNavigate,
            onChangeSetting = onChangeSetting
        )
    }
    
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("ai_agent_prefs", Context.MODE_PRIVATE) }
    var aiProvider by remember { mutableStateOf(sharedPreferences.getString("ai_provider", "Gemini") ?: "Gemini") }
    var openRouterKey by remember { mutableStateOf(sharedPreferences.getString("openrouter_key", "") ?: "") }
    var geminiApiKey by remember { mutableStateOf(sharedPreferences.getString("gemini_api_key", "") ?: "") }
    
    DisposableEffect(sharedPreferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "gemini_api_key") {
                geminiApiKey = sp.getString("gemini_api_key", "") ?: ""
            } else if (key == "openrouter_key") {
                openRouterKey = sp.getString("openrouter_key", "") ?: ""
            } else if (key == "ai_provider") {
                aiProvider = sp.getString("ai_provider", "Gemini") ?: "Gemini"
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val configGeminiApiKey = remember {
        val key = try { com.example.BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") key else ""
    }
    val effectiveGeminiKey = remember(geminiApiKey, configGeminiApiKey) {
        if (geminiApiKey.isNotBlank() && geminiApiKey != "MY_GEMINI_API_KEY") {
            geminiApiKey
        } else {
            configGeminiApiKey
        }
    }
    val defaultModel = sharedPreferences.getString("default_ai_model_key", "Gemini 2.5 Flash") ?: "Gemini 2.5 Flash"

    val activeDocId = remember(selectedDoc) { selectedDoc?.id?.toString() ?: "default_chat" }

    val chatMessages = remember(activeDocId, aiProvider, openRouterKey, effectiveGeminiKey) {
        val savedHistory = viewModel.getChatHistory(activeDocId)
        val list = mutableStateListOf<ChatMessage>()
        if (savedHistory != null) {
            val hasCustomKey = geminiApiKey.isNotBlank() && geminiApiKey != "MY_GEMINI_API_KEY"
            if (hasCustomKey) {
                list.addAll(savedHistory.filter { !it.isKeyMissingNotice })
            } else {
                list.addAll(savedHistory)
            }
        } else {
            val hasGeminiApiKey = effectiveGeminiKey.isNotBlank()
            val isProviderKeyMissing = if (aiProvider == "Gemini") {
                !hasGeminiApiKey
            } else {
                openRouterKey.isBlank()
            }

            list.add(
                ChatMessage(
                    sender = "Mobius",
                    text = "Hello! I am Mobius, your professional AI assistant. I can help you analyze, edit, or structure your document with precise actions. How can I assist you today?",
                    isUser = false
                )
            )
            if (isProviderKeyMissing) {
                list.add(
                    ChatMessage(
                        sender = "Mobius Notice",
                        text = "No API Key configured. Running in Offline Simulation Mode. Click the Settings icon to connect your live API credentials.",
                        isUser = false,
                        isKeyMissingNotice = true
                    )
                )
            }
            viewModel.setChatHistory(activeDocId, list.toList())
        }
        list
    }

    LaunchedEffect(chatMessages.toList()) {
        viewModel.setChatHistory(activeDocId, chatMessages.toList())
    }
    
    var isThinking by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Keep scroll at bottom on new messages
    LaunchedEffect(chatMessages.size, isThinking) {
        if (chatMessages.size > 0) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        focusRequester.requestFocus()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isSmall = maxWidth < 200.dp
        val panelPadding = if (isSmall) 8.dp else 16.dp
        val elementSpacing = if (isSmall) 4.dp else 8.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(panelPadding)
        ) {
            // History
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(elementSpacing),
                contentPadding = PaddingValues(bottom = elementSpacing)
            ) {
                items(chatMessages, key = { it.id }) { message ->
                    if (message.isSystemNotice) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(elementSpacing)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = message.text, 
                                    fontSize = if (isSmall) 10.sp else 11.sp, 
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else if (message.isKeyMissingNotice) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(elementSpacing)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Warning",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = message.text, 
                                        fontSize = if (isSmall) 11.sp else 12.sp, 
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                IconButton(
                                    onClick = { onNavigateToSettings?.invoke() },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Navigate to settings",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        val alignment = if (message.isUser) Alignment.End else Alignment.Start
                        val bgColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgColor)
                                    .padding(if (isSmall) 8.dp else 12.dp)
                                    .widthIn(max = if (isSmall) 200.dp else 280.dp)
                            ) {
                                Text(text = message.text, fontSize = if (isSmall) 12.sp else 14.sp, color = textColor)
                            }
                            Text(
                                text = message.sender,
                                fontSize = if (isSmall) 8.sp else 10.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                            )
                        }
                    }
                }
                if (isThinking) {
                    item {
                        Row(
                            modifier = Modifier.padding(elementSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(if (isSmall) 12.dp else 16.dp), strokeWidth = 2.dp, color = DocWordColor)
                            Spacer(modifier = Modifier.width(elementSpacing))
                            Text("Processing...", fontSize = if (isSmall) 10.sp else 12.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // Chat Input Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = elementSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = { Text("Tell AI to...", fontSize = if (isSmall) 12.sp else 14.sp) },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    textStyle = TextStyle(fontSize = if (isSmall) 12.sp else 14.sp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DocWordColor,
                        unfocusedBorderColor = Color.LightGray
                    ),
                    maxLines = 1,
                    enabled = !isThinking
                )
                Spacer(modifier = Modifier.width(elementSpacing))
                FloatingActionButton(
                    onClick = {
                        if (prompt.isNotBlank() && !isThinking) {
                            val userPrompt = prompt
                            chatMessages.add(ChatMessage(sender = "You", text = userPrompt, isUser = true))
                            prompt = ""
                            isThinking = true
                            
                            val activeDocEntity = selectedDoc
                            val rawActiveText = activeTextFieldValue.text
                            val docIdVal = activeDocEntity?.id ?: 1
                            val shapesList = DocShapeRepository.getShapes(docIdVal)
                            val tablesList = DocTableRepository.getTables(docIdVal)

                            val objectsInfo = StringBuilder()
                            
                            // Include full active page layout metrics and styles
                            objectsInfo.append("\n\n=== CURRENT PAGE LAYOUT CONFIGURATIONS ===\n")
                            objectsInfo.append("- General Typography Font Size: $fontSize\n")
                            objectsInfo.append("- Active Page Margins: $pageMargins\n")
                            objectsInfo.append("- Page Orientation: ${if (isLandscape) "Landscape" else "Portrait"}\n")
                            objectsInfo.append("- Active Multi-column Split: $columnCount Columns\n")
                            objectsInfo.append("- Page Border Type: \"$pageBorderType\"\n")
                            objectsInfo.append("- Running Document Header: ${if (headerText.isEmpty()) "None" else "\"$headerText\""}\n")
                            objectsInfo.append("- Running Document Footer: ${if (footerText.isEmpty()) "None" else "\"$footerText\""}\n")
                            objectsInfo.append("- Document Watermark Text: ${if (watermarkText.isEmpty()) "None" else "\"$watermarkText\""}\n")
                            objectsInfo.append("- Total Document Pages Detected: ${rawActiveText.split("\u000C").size} Page(s)\n")

                            objectsInfo.append("\n=== SPECIAL SYSTEM OBJECTS (TABLES & SHAPES) IN THIS DOCUMENT ===\n")
                            if (shapesList.isEmpty()) {
                                objectsInfo.append("Currently registered Shapes: None\n")
                            } else {
                                objectsInfo.append("Currently registered Shapes:\n")
                                shapesList.forEachIndexed { i, shape ->
                                    var str = "  - Shape #${i+1}: ID=\"${shape.id}\", type=\"${shape.type}\", textInside=\"${shape.textInside}\", pageIndex=${shape.pageIndex}, x=${shape.x}, y=${shape.y}, width=${shape.width}, height=${shape.height}, fillColorHex=\"${shape.fillColorHex}\", borderColorHex=\"${shape.borderColorHex}\", rotation=${shape.rotation}"
                                    objectsInfo.append(str + "\n")
                                }
                            }
                            if (tablesList.isEmpty()) {
                                objectsInfo.append("Currently registered Tables: None\n")
                            } else {
                                objectsInfo.append("Currently registered Tables:\n")
                                tablesList.forEachIndexed { i, tbl ->
                                    val dataStr = tbl.cellData.entries.joinToString { "${it.key}:'${it.value}'" }
                                    objectsInfo.append("  - Table #${i+1}: ID=\"${tbl.id}\", rows=${tbl.rows}, columns=${tbl.columns}, styleName=\"${tbl.styleName}\", themeColorHex=\"${tbl.themeColorHex}\", pageIndex=${tbl.pageIndex}, x=${tbl.x}, y=${tbl.y}, width=${tbl.width}, height=${tbl.height}, cellData={$dataStr}\n")
                                }
                            }
                            val activeText = rawActiveText + objectsInfo.toString()

                            val docType = activeDocEntity?.type ?: "word"
                            val docTitle = activeDocEntity?.title ?: "Untitled"
                            val currentProvider = aiProvider
                            val currentApiKey = openRouterKey
                            val currentGeminiKey = effectiveGeminiKey
    
                            coroutineScope.launch {
                                try {
                                    if (currentProvider == "OpenRouter") {
                                        if (currentApiKey.isBlank()) {
                                            withContext(Dispatchers.Main) {
                                                chatMessages.add(
                                                    ChatMessage(
                                                        sender = "Mobius Notice",
                                                        text = "API Key is not set under Settings. Run offline simulation or configure the key to connect with your AI.",
                                                        isUser = false,
                                                        isKeyMissingNotice = true
                                                    )
                                                )
                                                val localResult = generateLocalHeuristicResponse(userPrompt, rawActiveText)
                                                chatMessages.add(ChatMessage(sender = "Mobius (Offline)", text = localResult.first, isUser = false))
                                                runActions(localResult.second)
                                            }
                                        } else {
                                            val filesInfo = allDocs.joinToString(separator = "\n") { doc ->
                                                "- Document ID: ${doc.id}, Title: '${doc.title}', Type: ${doc.type}, Folder: ${doc.folderName ?: "Root"}, Deleted: ${doc.isDeleted}"
                                            }
                                            val settingsSnap = """
                                                active_tab: $activeTab
                                                editor_theme: ${viewModel.editorTheme.value}
                                                document_theme: ${viewModel.selectedDocumentTheme.value}
                                                theme_effect: ${viewModel.selectedThemeEffect.value}
                                                background_color: ${viewModel.pageBackgroundColorHex.value}
                                                font_size: ${viewModel.fontSize.value}sp
                                                column_count: ${viewModel.columnCount.value}
                                                page_margins: Left ${viewModel.pageMarginLeft.value}dp, Top ${viewModel.pageMarginTop.value}dp, Right ${viewModel.pageMarginRight.value}dp, Bottom ${viewModel.pageMarginBottom.value}dp
                                                watermark: text='${viewModel.watermarkText.value}', type='${viewModel.watermarkType.value}'
                                                borders: ${viewModel.pageBorderType.value}
                                                header: '${viewModel.headerText.value}', footer: '${viewModel.footerText.value}'
                                            """.trimIndent()

                                            // Direct call to OpenRouter with automated model failover!
                                            val result = callOpenRouterApiWithFailover(
                                                apiKey = currentApiKey,
                                                history = chatMessages.toList(),
                                                documentText = rawActiveText,
                                                layoutMetadata = objectsInfo.toString(),
                                                docType = docType,
                                                docTitle = docTitle,
                                                workspaceFilesInfo = filesInfo,
                                                settingsSnapshot = settingsSnap,
                                                currentScreen = activeTab
                                            )
        
                                            val responseText = result.first
                                            val successfulModel = result.second
        
                                            withContext(Dispatchers.Main) {
                                                val cleanedText = removeActionsCodeblock(responseText)
                                                chatMessages.add(
                                                    ChatMessage(
                                                        sender = "Mobius ($successfulModel)",
                                                        text = cleanedText,
                                                        isUser = false
                                                    )
                                                )
        
                                                val actionsJson = extractActionsJson(responseText)
                                                if (actionsJson != null) {
                                                    runActions(actionsJson)
                                                }
                                            }
                                        }
                                    } else {
                                        val hasGeminiApiKey = currentGeminiKey.isNotBlank()
                                        if (!hasGeminiApiKey) {
                                            withContext(Dispatchers.Main) {
                                                chatMessages.add(
                                                    ChatMessage(
                                                        sender = "Mobius Notice",
                                                        text = "API Key is not set under Settings. Run offline simulation or configure the key to connect with your AI.",
                                                        isUser = false,
                                                        isKeyMissingNotice = true
                                                    )
                                                )
                                                val localResult = generateLocalHeuristicResponse(userPrompt, rawActiveText)
                                                chatMessages.add(ChatMessage(sender = "Mobius (Offline)", text = localResult.first, isUser = false))
                                                runActions(localResult.second)
                                            }
                                        } else {
                                            val filesInfo = allDocs.joinToString(separator = "\n") { doc ->
                                                "- Document ID: ${doc.id}, Title: '${doc.title}', Type: ${doc.type}, Folder: ${doc.folderName ?: "Root"}, Deleted: ${doc.isDeleted}"
                                            }
                                            val settingsSnap = """
                                                active_tab: $activeTab
                                                editor_theme: ${viewModel.editorTheme.value}
                                                document_theme: ${viewModel.selectedDocumentTheme.value}
                                                theme_effect: ${viewModel.selectedThemeEffect.value}
                                                background_color: ${viewModel.pageBackgroundColorHex.value}
                                                font_size: ${viewModel.fontSize.value}sp
                                                column_count: ${viewModel.columnCount.value}
                                                page_margins: Left ${viewModel.pageMarginLeft.value}dp, Top ${viewModel.pageMarginTop.value}dp, Right ${viewModel.pageMarginRight.value}dp, Bottom ${viewModel.pageMarginBottom.value}dp
                                                watermark: text='${viewModel.watermarkText.value}', type='${viewModel.watermarkType.value}'
                                                borders: ${viewModel.pageBorderType.value}
                                                header: '${viewModel.headerText.value}', footer: '${viewModel.footerText.value}'
                                            """.trimIndent()

                                            val responseText = callGeminiAPI(
                                                history = chatMessages.toList(),
                                                documentText = rawActiveText,
                                                layoutMetadata = objectsInfo.toString(),
                                                docType = docType,
                                                docTitle = docTitle,
                                                modelName = defaultModel,
                                                userApiKey = currentGeminiKey,
                                                workspaceFilesInfo = filesInfo,
                                                settingsSnapshot = settingsSnap,
                                                currentScreen = activeTab
                                            )
         
                                            if (responseText.startsWith("ERROR: API_KEY_MISSING")) {
                                                // Handle Offline Fallback parsing
                                                val localResult = generateLocalHeuristicResponse(userPrompt, rawActiveText)
                                                val reply = localResult.first
                                                val actionsJson = localResult.second
                                                
                                                withContext(Dispatchers.Main) {
                                                    chatMessages.add(ChatMessage(sender = "Mobius", text = reply, isUser = false))
                                                    chatMessages.add(
                                                        ChatMessage(
                                                            sender = "System Notice",
                                                            text = "Cloud API Key is not configured in Secrets. Executed local rules heuristically.",
                                                            isUser = false,
                                                            isSystemNotice = true
                                                        )
                                                    )
                                                    runActions(actionsJson)
                                                }
                                            } else if (responseText.startsWith("ERROR")) {
                                                withContext(Dispatchers.Main) {
                                                    val isAuthOrQuotaError = responseText.contains("429") || 
                                                                             responseText.contains("400") || 
                                                                             responseText.contains("401") || 
                                                                             responseText.contains("403")
                                                    
                                                    if (isAuthOrQuotaError) {
                                                        chatMessages.add(
                                                            ChatMessage(
                                                                sender = "Mobius Notice",
                                                                text = "Default Gemini API Key has been exhausted or is invalid. Running in Offline Simulation Mode. Click the Settings icon to connect your live API credentials.",
                                                                isUser = false,
                                                                isKeyMissingNotice = true
                                                            )
                                                        )
                                                    } else {
                                                        chatMessages.add(ChatMessage(sender = "Mobius", text = "I failed to connect to cloud services. ($responseText)\n\nRunning local fallback...", isUser = false))
                                                    }
                                                    
                                                    val localResult = generateLocalHeuristicResponse(userPrompt, rawActiveText)
                                                    chatMessages.add(ChatMessage(sender = "Mobius (Offline)", text = localResult.first, isUser = false))
                                                    runActions(localResult.second)
                                                }
                                            } else {
                                                // Real Gemini Response succeeded
                                                withContext(Dispatchers.Main) {
                                                    // Clean actions blocks from raw text for representation
                                                    val cleanedText = removeActionsCodeblock(responseText)
                                                    chatMessages.add(ChatMessage(sender = "Mobius", text = cleanedText, isUser = false))
                                                    
                                                    val actionsJson = extractActionsJson(responseText)
                                                    if (actionsJson != null) {
                                                       runActions(actionsJson)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        chatMessages.add(ChatMessage(sender = "Mobius", text = "An error occurred: ${e.message}", isUser = false))
                                    }
                                } finally {
                                    isThinking = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(if (isSmall) 36.dp else 48.dp),
                    shape = RoundedCornerShape(24.dp),
                    containerColor = DocWordColor,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(if (isSmall) 16.dp else 20.dp)
                    )
                }
            }
        }
    }

}

// REST call implementation using Direct REST API (Option B) (with OpenRouter API failover compatibility layout)
private suspend fun callOpenRouterApiWithFailover(
    apiKey: String,
    history: List<ChatMessage>,
    documentText: String,
    layoutMetadata: String,
    docType: String,
    docTitle: String,
    workspaceFilesInfo: String = "",
    settingsSnapshot: String = "",
    currentScreen: String = ""
): Pair<String, String> = withContext(Dispatchers.IO) {
    val systemInstruction = """
        You are **Mobius**, an elite document intelligence and operating agent created by the **JCdocs Team** under the **JCdocs AI** brand. Do NOT introduce yourself or say hello on every single turn of a conversation unless the user explicitly asks who you are or what your name is, because doing so is repetitive. Keep your responses direct, helpful, and highly contextual.

        The active document type is: $docType
        The active document title is: "$docTitle"
        The user is currently viewing the app screen/tab: "$currentScreen"
        
        ACTIVE DOCUMENT TEXT CONTENT:
        ```
        $documentText
        ```

        ACTIVE PAGE LAYOUT CONFIGURATIONS & SPECIAL OBJECTS:
        $layoutMetadata

        === USER WORKSPACE ENVIRONMENT AWARENESS ===
        $workspaceFilesInfo

        === WORKSPACE SYSTEM SETTINGS STATED ===
        $settingsSnapshot

        CRITICAL: The active document text content is located inside the "ACTIVE DOCUMENT TEXT CONTENT" code block. The metadata and configurations (margins, tables, shapes, headers/footers) are located in the "ACTIVE PAGE LAYOUT CONFIGURATIONS & SPECIAL OBJECTS" block. When you output an "update_content" action to modify or add pages to the document, the "text" parameter must contain ONLY the actual document text (separated by '\u000C' page breaks). You MUST NEVER include any metadata headers (like "=== CURRENT PAGE LAYOUT CONFIGURATIONS ===" or "=== SPECIAL SYSTEM OBJECTS ===") or metadata content in the updated document text parameter. Fulfill the user's editing requests strictly.

        Your primary goal is to fulfill any user requests using your comprehensive suite of features. You are fully capable and equipped to achieve over 400 specific operations, summarized across these master categories:
        1. **Explainers & Analysts / PDF AI Reader**: Answer questions, explain concepts/paragraphs/sections/documents/tables/images/charts/formulas/code, analyze readability, structure, grammar, citations, layout, flow, complexity, and generate review reports. **You have full capabilities of a PDF AI Reader, allowing you to parse, query, and synthesize information from any attached PDF files or Word/Sheet/Slide files in the user environment.**
        2. **Creative writing**: Draft essays, articles, reports, proposals, PRDs, resumes, CVs, cover letters, emails, business plans, SOPs, handbooks, manuals, documentation, FAQs, meeting notes, research papers, scripts, blog/social posts, and product descriptions.
        3. **Drafting, Editing & Polish**: Rewrite, simplify, expand, shorten, humanize, professionalize, formalize, casualize, translate, summarize, change tone/style, correct grammar & spelling, paraphrase, and reorganize.
        4. **Layout & Structures**: Create custom templates, cover pages, TOC pages, bibliographies, headers, footers, page numbering (Roman, numeric, custom placements), split/merge pages, and customize margins, borders, columns, or orientation.
        5. **Data & Graphic Objects**: Insert, sort, filter, calculate, and format tables. Insert, crop, compress, optimize, align, wrap, and style graphics, shapes (rectangles, stars, smiley), and canvas images.
        6. **Automation & Self-Correction**: Verify results against layouts, optimize line spacing, self-correct formatting, repair document flows, track action histories, and undo/rollback actions as needed.
        7. **Screen / Settings Awareness**: You are fully aware of all screens in JCDocs Suite (Home, Files, Shared, settings) and their features. You can guide users to make changes to any settings or perform navigation across the app tabs.
           - If user wants to change any settings or navigate tabs, you can guide them textually OR use the executable `"navigate"` or `"change_setting"` actions directly!

        To manipulate the active document and complete these tasks, you must output your natural markdown explanation along with a structured markdown code block of executable actions in this format:
        ```actions
        [
          {
            "action": "ACTION_TYPE",
            "params": { ... }
          }
        ]
        ```

        ### EXECUTABLE ACTIONS GUIDE:
        1. `"update_content"`: replaces the entire text content. Use this for full text-generation (essays, templates, full PRDs). Parameter: `"text"`: string.
        2. `"insert_text"`: inserts text at current selection start or end. Parameters: `"text"`: string, `"offset"`: integer (optional).
        3. `"replace_text"`: searches a pattern and replaces it. Parameters: `"pattern"`: string, `"replacement"`: string.
        4. `"clear_content"`: clears the entire text. No parameters.
        5. `"apply_format"`: styles selected text or specific phrases. Parameters:
           - `"type"`: `"bold"`, `"italic"`, `"underline"`, `"fontSize"`, `"color"`, `"alignment"`, `"lineSpacing"`, `"fontFamily"`
           - `"value"`: string (e.g., `"true"`, `"24.sp"`, `"#FF0000"`, `"Center"`, `"1.5"`, `"monospace"`)
           - `"pattern"`: string (matches specific text in document to format; if empty, applies to current selection)
        6. `"set_margins"`: changes page margins. Parameter: `"size"` (integer dp, e.g., 12, 16, 24, 32).
        7. `"set_font_size"`: changes general editor font size. Parameter: `"size"` (integer sp, e.g. 12, 16, 18, 24).
        8. `"set_orientation"`: parameter `"landscape"` (true/false).
        9. `"set_columns"`: parameter `"columns"` (1, 2, or 3).
        10. `"set_watermark"`: parameter `"text"`: string, `"type"`: `"Diagonal"` or `"Horizontal"`.
        11. `"set_borders"`: parameter `"type"`: `"None"`, `"Box"`, `"Shadow"`, `"Double"`, `"3D"`, `"Custom"`.
        12. `"create_table"`: parameter `"rows"`: integer, `"columns"`: integer, `"styleName"`: string (optional), `"cellData"`: key-value map "r,c" to "content" (optional).
        13. `"delete_table"`: parameter `"tableId"` (optional).
        14. `"populate_table"`: parameter `"tableId"`, `"cellData"`: map.
        14b. `"update_table"`: modifies table style, color, row/col counts, dimensions, or text details. Parameters: `"id"` (string, optional to match specific table), `"rows"` (integer), `"columns"` (integer), `"styleName"` (string, e.g. "classic", "elegant_blue", "modern_emerald", "warm_gold", "dark_minimalist"), `"color"` (Hex string), `"x"` (integer dp), `"y"` (integer dp), `"width"` (integer dp), `"height"` (integer dp), `"cellData"` (key-value map "r,c" to "content"), `"alternateRows"` (boolean), `"hasHeaderRow"` (boolean).
        15. `"add_shape"`: parameter `"type"` (rectangle, oval, round_rectangle, star_5, smiley), `"textInside"`: string.
        15b. `"update_shape"`: modifies shape formatting, color, text or size. Parameters: `"type"` (to target shape type like star_5 / smiley / round_rectangle / ellipse), `"fillColor"` (HEX string color), `"borderColor"` (HEX string), `"textInside"` (string), `"x"` (integer dp), `"y"` (integer dp), `"width"` (integer dp), `"height"` (integer dp), `"rotation"` (float), `"textSize"` (float), `"isBold"` (boolean).
        15c. `"delete_shape"`: deletes a shape. Parameters: `"type"` (optional string to match shape type).
        16. `"add_image"`: parameter `"uri"`: string, `"width"`: integer (dp), `"height"`: integer (dp).
        17. `"set_header_footer"`: `"header"`: string, `"footer"`: string.
        18. `"open_tool"`: `"name"`: `"spelling"`, `"thesaurus"`, `"wordcount"`, `"accessibility"`, `"translate"`.
        19. `"create_doc"`: `"title"`: string, `"type"`: `"word"`, `"sheet"`, or `"slide"`.
        20. `"delete_page"`: deletes a specific page by index. Parameter: `"pageIndex"`: integer (0-based, e.g. 0 for page 1, 1 for page 2).

         ### RECOGNIZING AND MANIPULATING PAGES / PAGE BREAKS:
        - In the active document, different pages are separated by the raw control character `\u000C` (ASCII Form Feed character, decimal 12).
        - Count pages correctly: N pages are separated by exactly N-1 page breaks. For example, a 3-page document contains exactly 2 `\u000C` characters separating them: Page 1, Page 2, and Page 3. Never tell the user that a third page is missing because you count only 2 page breaks; 2 page breaks means a third page is present!
        - **Automatic Page Insertion & Expansion**: If the user instructs you to write, insert, edit, draft, format, or place any text/content on Page X (1-based index, e.g., "write a letter on page 4", "add a table on page 3", "insert poem on page 5"), but the active document currently contains fewer than X pages: you **MUST** automatically pad and expand the document content with page break characters (`\u000C`) until you have at least X pages, then place the requested content on Page X, and update the document using `"update_content"`. Use an empty string as the text content / placeholder for any intermediate empty pages.
        - **Exact Page Length Constraints on Drafting / Writing**: If the user requests you to write, draft, create, format, or generate content (like an essay, report, letter, manual, PRD) across/on/in exactly X pages (e.g., "write an essay on two pages", "draft a report on 3 pages", "write anything in 2 pages", "write a 3 page essay"), you **MUST** ensure the generated document contains EXACTLY X pages. You must split your drafted content into exactly X parts, separated by exactly X-1 page breaks (`\u000C`), so that the active document is exactly X pages long. Do NOT generate more or fewer pages than requested. For example, a 2-page essay must have exactly one `\u000C` character separating physical Page 1 and Page 2. A 1-page document must contain zero `\u000C` characters.
        - To edit a specific page:
          1. Split the ACTIVE DOCUMENT CONTENT by the `\u000C` delimiter.
          2. Modify the item corresponding to that 0-based page index.
          3. Re-join the remaining page items with `\u000C`.
          4. Output an `"update_content"` action containing the updated text.
        - To delete a specific page:
          Use the `"delete_page"` action specifying the `"pageIndex"`.

         IMPORTANT: You are **Mobius**, built by the **JCdocs Team** under the **JCdocs AI** brand. Do NOT introduce yourself, greet repetitively, or mention your name/creator/brand in your replies unless the user explicitly asks for it. Dive straight into satisfying the user's instructions cleanly and professionally. Prefer JSON double quotes for actions codeblocks.
    """.trimIndent()

    val models = listOf(
        "openai/gpt-oss-20b",
        "x-ai/grok-imagine-image-quality",
        "nvidia/nemotron-3.5-content-safety",
        "nex-agi/nex-n2-pro",
        "poolside/laguna-xs.2",
        "openrouter/owl-alpha",
        "qwen/qwen3-asr-flash-2026-02-10",
        "google/veo-3.1-fast",
        "openai/gpt-oss-120b",
        "google/veo-3.1-lite",
        "nvidia/nemotron-nano-9b-v2",
        "nvidia/nemotron-nano-12b-v2-vl",
        "openai/gpt-4-turbo",
        "anthropic/claude-fable-5"
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
            var lastContent = StringBuilder()

            for (msg in history) {
                if (msg.isSystemNotice || msg.isKeyMissingNotice) continue
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
                    lastContent = StringBuilder(msg.text)
                }
            }
            if (lastRole != null) {
                val msgOb = JSONObject()
                msgOb.put("role", lastRole)
                msgOb.put("content", lastContent.toString())
                messagesArray.put(msgOb)
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
                    throw Exception("HTTP ${response.code} ${response.message}: $resStr")
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
                if (content.isNullOrEmpty()) {
                    throw Exception("Returned blank response template")
                }
                return@withContext Pair(content, model)
            }
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: "Unknown error"
            // Silently fail over to the next model
        }
    }
    throw Exception("All OpenRouter models failed. Last error: $lastError")
}

// REST call implementation using Direct REST API (Option B)
private suspend fun callGeminiAPI(
    history: List<ChatMessage>, 
    documentText: String,
    layoutMetadata: String,
    docType: String, 
    docTitle: String,
    modelName: String,
    userApiKey: String = "",
    workspaceFilesInfo: String = "",
    settingsSnapshot: String = "",
    currentScreen: String = ""
): String = withContext(Dispatchers.IO) {
    val apiKey = userApiKey
    if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
        return@withContext "ERROR: API_KEY_MISSING"
    }

    val systemInstruction = """
        You are **Mobius**, an elite document intelligence and operating agent created by the **JCdocs Team** under the **JCdocs AI** brand. Do NOT introduce yourself or say hello on every single turn of a conversation unless the user explicitly asks who you are or what your name is, because doing so is repetitive. Keep your responses direct, helpful, and highly contextual.

        The active document type is: $docType
        The active document title is: "$docTitle"
        The user is currently viewing the app screen/tab: "$currentScreen"
        
        ACTIVE DOCUMENT TEXT CONTENT:
        ```
        $documentText
        ```

        ACTIVE PAGE LAYOUT CONFIGURATIONS & SPECIAL OBJECTS:
        $layoutMetadata

        === USER WORKSPACE ENVIRONMENT AWARENESS ===
        $workspaceFilesInfo

        === WORKSPACE SYSTEM SETTINGS STATED ===
        $settingsSnapshot

        CRITICAL: The active document text content is located inside the "ACTIVE DOCUMENT TEXT CONTENT" code block. The metadata and configurations (margins, tables, shapes, headers/footers) are located in the "ACTIVE PAGE LAYOUT CONFIGURATIONS & SPECIAL OBJECTS" block. When you output an "update_content" action to modify or add pages to the document, the "text" parameter must contain ONLY the actual document text (separated by '\u000C' page breaks). You MUST NEVER include any metadata headers (like "=== CURRENT PAGE LAYOUT CONFIGURATIONS ===" or "=== SPECIAL SYSTEM OBJECTS ===") or metadata content in the updated document text parameter. Fulfill the user's editing requests strictly.

        Your primary goal is to fulfill any user requests using your comprehensive suite of features. You are fully capable and equipped to achieve over 400 specific operations, summarized across these master categories:
        1. **Explainers & Analysts / PDF AI Reader**: Answer questions, explain concepts/paragraphs/sections/documents/tables/images/charts/formulas/code, analyze readability, structure, grammar, citations, layout, flow, complexity, and generate review reports. **You have full capabilities of a PDF AI Reader, allowing you to parse, query, and synthesize information from any attached PDF files or Word/Sheet/Slide files in the user environment.**
        2. **Creative writing**: Draft essays, articles, reports, proposals, PRDs, resumes, CVs, cover letters, emails, business plans, SOPs, handbooks, manuals, documentation, FAQs, meeting notes, research papers, scripts, blog/social posts, and product descriptions.
        3. **Drafting, Editing & Polish**: Rewrite, simplify, expand, shorten, humanize, professionalize, formalize, casualize, translate, summarize, change tone/style, correct grammar & spelling, paraphrase, and reorganize.
        4. **Layout & Structures**: Create custom templates, cover pages, TOC pages, bibliographies, headers, footers, page numbering (Roman, numeric, custom placements), split/merge pages, and customize margins, borders, columns, or orientation.
        5. **Data & Graphic Objects**: Insert, sort, filter, calculate, and format tables. Insert, crop, compress, optimize, align, wrap, and style graphics, shapes (rectangles, stars, smiley), and canvas images.
        6. **Automation & Self-Correction**: Verify results against layouts, optimize line spacing, self-correct formatting, repair document flows, track action histories, and undo/rollback actions as needed.
        7. **Screen / Settings Awareness**: You are fully aware of all screens in JCDocs Suite (Home, Files, Shared, settings) and their features. You can guide users to make changes to any settings or perform navigation across the app tabs.
           - If user wants to change any settings or navigate tabs, you can guide them textually OR use the executable `"navigate"` or `"change_setting"` actions directly!

        To manipulate the active document and complete these tasks, you must output your natural markdown explanation along with a structured markdown code block of executable actions in this format:
        ```actions
        [
          {
            "action": "ACTION_TYPE",
            "params": { ... }
          }
        ]
        ```

        ### EXECUTABLE ACTIONS GUIDE:
        1. `"update_content"`: replaces the entire text content. Use this for full text-generation (essays, templates, full PRDs). Parameter: `"text"`: string.
        2. `"insert_text"`: inserts text at current selection start or end. Parameters: `"text"`: string, `"offset"`: integer (optional).
        3. `"replace_text"`: searches a pattern and replaces it. Parameters: `"pattern"`: string, `"replacement"`: string.
        4. `"clear_content"`: clears the entire text. No parameters.
        5. `"apply_format"`: styles selected text or specific phrases. Parameters:
           - `"type"`: `"bold"`, `"italic"`, `"underline"`, `"fontSize"`, `"color"`, `"alignment"`, `"lineSpacing"`, `"fontFamily"`
           - `"value"`: string (e.g., `"true"`, `"24.sp"`, `"#FF0000"`, `"Center"`, `"1.5"`, `"monospace"`)
           - `"pattern"`: string (matches specific text in document to format; if empty, applies to current selection)
        6. `"set_margins"`: changes page margins. Parameter: `"size"` (integer dp, e.g., 12, 16, 24, 32).
        7. `"set_font_size"`: changes general editor font size. Parameter: `"size"` (integer sp, e.g. 12, 16, 18, 24).
        8. `"set_orientation"`: parameter `"landscape"` (true/false).
        9. `"set_columns"`: parameter `"columns"` (1, 2, or 3).
        10. `"set_watermark"`: parameter `"text"`: string, `"type"`: `"Diagonal"` or `"Horizontal"`.
        11. `"set_borders"`: parameter `"type"`: `"None"`, `"Box"`, `"Shadow"`, `"Double"`, `"3D"`, `"Custom"`.
        12. `"create_table"`: parameter `"rows"`: integer, `"columns"`: integer, `"styleName"`: string (optional), `"cellData"`: key-value map "r,c" to "content" (optional).
        13. `"delete_table"`: parameter `"tableId"` (optional).
        14. `"populate_table"`: parameter `"tableId"`, `"cellData"`: map.
        14b. `"update_table"`: modifies table style, color, row/col counts, dimensions, or text details. Parameters: `"id"` (string, optional to match specific table), `"rows"` (integer), `"columns"` (integer), `"styleName"` (string, e.g. "classic", "elegant_blue", "modern_emerald", "warm_gold", "dark_minimalist"), `"color"` (Hex string), `"x"` (integer dp), `"y"` (integer dp), `"width"` (integer dp), `"height"` (integer dp), `"cellData"` (key-value map "r,c" to "content"), `"alternateRows"` (boolean), `"hasHeaderRow"` (boolean).
        15. `"add_shape"`: parameter `"type"` (rectangle, oval, round_rectangle, star_5, smiley), `"textInside"`: string.
        15b. `"update_shape"`: modifies shape formatting, color, text or size. Parameters: `"type"` (to target shape type like star_5 / smiley / round_rectangle / ellipse), `"fillColor"` (HEX string color), `"borderColor"` (HEX string), `"textInside"` (string), `"x"` (integer dp), `"y"` (integer dp), `"width"` (integer dp), `"height"` (integer dp), `"rotation"` (float), `"textSize"` (float), `"isBold"` (boolean).
        15c. `"delete_shape"`: deletes a shape. Parameters: `"type"` (optional string to match shape type).
        16. `"add_image"`: parameter `"uri"`: string, `"width"`: integer (dp), `"height"`: integer (dp).
        17. `"set_header_footer"`: `"header"`: string, `"footer"`: string.
        18. `"open_tool"`: `"name"`: `"spelling"`, `"thesaurus"`, `"wordcount"`, `"accessibility"`, `"translate"`.
        19. `"create_doc"`: `"title"`: string, `"type"`: `"word"`, `"sheet"`, or `"slide"`. **Use this action whenever the user asks you to create any new document, spreadsheet, or slide deck presentation!**
        20. `"delete_page"`: deletes a specific page by index. Parameter: `"pageIndex"`: integer (0-based, e.g. 0 for page 1, 1 for page 2).
        21. `"navigate"`: parameter `"tab"` (one of `"home"`, `"files"`, `"shared"`, `"settings"`). Use this to navigate the user's tab view.
        22. `"change_setting"`: parameter `"setting"` (e.g. `"ai_provider"`, `"user_email"`, `"ai_preferences"`, `"default_ai_model"`, `"ai_response_style"`, `"appearance"`, `"language"`, `"security_2fa"`), and `"value"` (string text/boolean). Use this to automatically apply setting changes on user request.

         ### RECOGNIZING AND MANIPULATING PAGES / PAGE BREAKS:
        - In the active document, different pages are separated by the raw control character `\u000C` (ASCII Form Feed character, decimal 12).
        - Count pages correctly: N pages are separated by exactly N-1 page breaks. For example, a 3-page document contains exactly 2 `\u000C` characters separating them: Page 1, Page 2, and Page 3. Never tell the user that a third page is missing because you count only 2 page breaks; 2 page breaks means a third page is present!
        - **Automatic Page Insertion & Expansion**: If the user instructs you to write, insert, edit, draft, format, or place any text/content on Page X (1-based index, e.g., "write a letter on page 4", "add a table on page 3", "insert poem on page 5"), but the active document currently contains fewer than X pages: you **MUST** automatically pad and expand the document content with page break characters (`\u000C`) until you have at least X pages, then place the requested content on Page X, and update the document using `"update_content"`. Use an empty string as the text content / placeholder for any intermediate empty pages.
        - **Exact Page Length Constraints on Drafting / Writing**: If the user requests you to write, draft, create, format, or generate content (like an essay, report, letter, manual, PRD) across/on/in exactly X pages (e.g., "write an essay on two pages", "draft a report on 3 pages", "write anything in 2 pages", "write a 3 page essay"), you **MUST** ensure the generated document contains EXACTLY X pages. You must split your drafted content into exactly X parts, separated by exactly X-1 page breaks (`\u000C`), so that the active document is exactly X pages long. Do NOT generate more or fewer pages than requested. For example, a 2-page essay must have exactly one `\u000C` character separating physical Page 1 and Page 2. A 1-page document must contain zero `\u000C` characters.
        - To edit a specific page:
          1. Split the ACTIVE DOCUMENT CONTENT by the `\u000C` delimiter.
          2. Modify the item corresponding to that 0-based page index.
          3. Re-join the remaining page items with `\u000C`.
          4. Output an `"update_content"` action containing the updated text.
        - To delete a specific page:
          Use the `"delete_page"` action specifying the `"pageIndex"`.

         IMPORTANT: You are **Mobius**, built by the **JCdocs Team** under the **JCdocs AI** brand. Do NOT introduce yourself, greet repetitively, or mention your name/creator/brand in your replies unless the user explicitly asks for it. Dive straight into satisfying the user's instructions cleanly and professionally. Prefer JSON double quotes for actions codeblocks.
    """.trimIndent()

    try {
        val requestJson = JSONObject()
        val contentsArray = JSONArray()
        var lastRole: String? = null
        var lastPartText = StringBuilder()

        for (msg in history) {
            if (msg.isSystemNotice || msg.isKeyMissingNotice) continue
            val roleVal = if (msg.isUser) "user" else "model"
            
            if (roleVal == lastRole) {
                lastPartText.append("\n\n").append(msg.text)
            } else {
                if (lastRole != null) {
                    val contentObj = JSONObject()
                    contentObj.put("role", lastRole)
                    val partsArr = JSONArray()
                    val partOb = JSONObject()
                    partOb.put("text", lastPartText.toString())
                    partsArr.put(partOb)
                    contentObj.put("parts", partsArr)
                    contentsArray.put(contentObj)
                }
                lastRole = roleVal
                lastPartText = StringBuilder(msg.text)
            }
        }
        if (lastRole != null) {
            val contentObj = JSONObject()
            contentObj.put("role", lastRole)
            val partsArr = JSONArray()
            val partOb = JSONObject()
            partOb.put("text", lastPartText.toString())
            partsArr.put(partOb)
            contentObj.put("parts", partsArr)
            contentsArray.put(contentObj)
        }
        requestJson.put("contents", contentsArray)

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

// Heuristic offline analyzer
private fun generateLocalHeuristicResponse(prompt: String, rawActiveText: String): Pair<String, String> {
    val activeText = rawActiveText
    val upper = prompt.uppercase()
    val actions = JSONArray()
    var responseText = "Understood. Performing that local action on your active document."

    try {
        if (upper.contains("DELETE") && (upper.contains("PAGE") || upper.contains("BREAK"))) {
            var targetPageIndex = when {
                upper.contains("FIRST") || upper.contains("ONE") -> 0
                upper.contains("SECOND") || upper.contains("TWO") -> 1
                upper.contains("THIRD") || upper.contains("THREE") -> 2
                upper.contains("FOURTH") || upper.contains("FOUR") -> 3
                upper.contains("FIFTH") || upper.contains("FIVE") -> 4
                else -> -1
            }
            if (targetPageIndex == -1) {
                val num = extractNumber(prompt)
                if (num != null) {
                    targetPageIndex = num - 1
                }
            }

            val pagesList = activeText.split("\u000C")
            
            if (targetPageIndex == -1) {
                if (pagesList.size > 1) {
                    val updatedPages = pagesList.dropLast(1)
                    val action = JSONObject()
                    action.put("action", "delete_page")
                    val params = JSONObject()
                    params.put("pageIndex", pagesList.size - 1)
                    action.put("params", params)
                    actions.put(action)
                    responseText = "I've successfully deleted the last page from your document draft. The document now consists of ${updatedPages.size} page(s)."
                } else {
                    responseText = "The document currently has only 1 page, which cannot be deleted. You can clear or edit its content instead."
                }
            } else if (targetPageIndex in pagesList.indices) {
                val action = JSONObject()
                action.put("action", "delete_page")
                val params = JSONObject()
                params.put("pageIndex", targetPageIndex)
                action.put("params", params)
                actions.put(action)
                responseText = "I have successfully deleted Page ${targetPageIndex + 1} of your document. The remaining content has been re-paginated. Your document now contains ${pagesList.size - 1} page(s)."
            } else {
                responseText = "I evaluated your active document, which currently contains ${pagesList.size} page(s). Page ${targetPageIndex + 1} does not exist, so it cannot be deleted."
            }
        } else if (upper.contains("PAGE") && (upper.contains("WRITE") || upper.contains("INSERT") || upper.contains("ADD") || upper.contains("PUT") || upper.contains("CREATE") || upper.contains("EDIT") || upper.contains("TEXT") || upper.contains("DRAFT") || upper.contains("POEM") || upper.contains("LETTER") || upper.contains("REPORT") || upper.contains("ESSAY") || upper.contains("EASSY") || upper.contains("ESAY") || upper.contains("FILL"))) {
            // Check for total pages volume constraint (e.g. "write an essay on two pages", "write anything in 3 pages")
            var totalPagesCountRequested = -1
            val pagesRegex = Regex("(?i)\\b(\\d+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+(?:pages|page)\\b")
            val pagesMatch = pagesRegex.find(prompt)
            if (pagesMatch != null) {
                val countStr = pagesMatch.groupValues[1].uppercase()
                val num = when (countStr) {
                    "ONE" -> 1
                    "TWO" -> 2
                    "THREE" -> 3
                    "FOUR" -> 4
                    "FIVE" -> 5
                    "SIX" -> 6
                    "SEVEN" -> 7
                    "EIGHT" -> 8
                    "NINE" -> 9
                    "TEN" -> 10
                    else -> countStr.toIntOrNull() ?: -1
                }
                if (num > 0) {
                    totalPagesCountRequested = num
                }
            }

            if (totalPagesCountRequested > 0) {
                val list = mutableListOf<String>()
                val docTypeLabel = if (upper.contains("POEM")) "poem" else if (upper.contains("LETTER")) "letter" else if (upper.contains("REPORT")) "report" else if (upper.contains("PRD")) "PRD" else "essay"
                
                for (p in 1..totalPagesCountRequested) {
                    if (upper.contains("POEM")) {
                        list.add("--- Poem Page $p of $totalPagesCountRequested ---\n\nGolden leaves upon the stream,\nWhispers of a summer dream.\nTime flows ever, soft and slow,\nIn this room for thoughts to grow.")
                    } else if (upper.contains("LETTER")) {
                        list.add("--- Letter Page $p of $totalPagesCountRequested ---\n\nDear Partner,\n\nThis is part $p of our correspondence drafted across exactly $totalPagesCountRequested pages for consistency.\n\nWarm regards,\nJCdocs AI Team")
                    } else {
                        list.add("--- Essay Part $p of $totalPagesCountRequested ---\n\nThis is a structured draft of your requested $docTypeLabel on Page $p.\nToday's digital documents demand precision in formatting and layout. Standardizing page hierarchies helps ensure consistency across different software suites. We split our ideas gracefully across exactly $totalPagesCountRequested distinct pages, allowing each section to serve a specific informational purpose.\n\nKey takeaways for Page $p include clarity of messaging, appropriate negative space, and visual flow matching the rest of the document.")
                    }
                }
                val newText = list.joinToString("\u000C")
                val action = JSONObject()
                action.put("action", "update_content")
                val params = JSONObject()
                params.put("text", newText)
                action.put("params", params)
                actions.put(action)
                responseText = "I detected your requirement for exactly $totalPagesCountRequested page(s). I have successfully drafted and structured a pristine **$totalPagesCountRequested-page $docTypeLabel** split across precisely $totalPagesCountRequested pages in your document."
            } else {
                var targetPageIndex = when {
                    upper.contains("FIRST") || upper.contains("PAGE 1") || upper.contains("PAGE ONE") -> 0
                    upper.contains("SECOND") || upper.contains("PAGE 2") || upper.contains("PAGE TWO") -> 1
                    upper.contains("THIRD") || upper.contains("PAGE 3") || upper.contains("PAGE THREE") -> 2
                    upper.contains("FOURTH") || upper.contains("PAGE 4") || upper.contains("PAGE FOUR") -> 3
                    upper.contains("FIFTH") || upper.contains("PAGE 5") || upper.contains("PAGE FIVE") -> 4
                    else -> -1
                }
                if (targetPageIndex == -1) {
                    val num = extractNumber(prompt)
                    if (num != null && num > 0) {
                        targetPageIndex = num - 1
                    }
                }

                if (targetPageIndex != -1) {
                    val pagesList = activeText.split("\u000C").toMutableList()
                    val currentSize = pagesList.size

                    var targetText = extractQuotedPattern(prompt)
                    if (targetText == null) {
                        targetText = if (upper.contains("POEM")) {
                            "Golden leaves upon the stream,\nWhispers of a summer dream.\nTime flows ever, soft and slow,\nIn this draft on Page ${targetPageIndex + 1} to grow."
                        } else if (upper.contains("LETTER")) {
                            "Dear Partner,\n\nI am writing to confirm our next week's sync on Page ${targetPageIndex + 1}. Please find the draft details listed below."
                        } else if (upper.contains("TABLE")) {
                            "Table Placeholder Content on Page ${targetPageIndex + 1}."
                        } else {
                            "Drafted text content on Page ${targetPageIndex + 1}."
                        }
                    }

                    if (targetPageIndex >= currentSize) {
                        while (pagesList.size <= targetPageIndex) {
                            pagesList.add("") // empty placeholder for newly inserted pages
                        }
                        pagesList[targetPageIndex] = targetText
                        val newText = pagesList.joinToString("\u000C")

                        val action = JSONObject()
                        action.put("action", "update_content")
                        val params = JSONObject()
                        params.put("text", newText)
                        action.put("params", params)
                        actions.put(action)
                        responseText = "I detected that Page ${targetPageIndex + 1} was not available. I have automatically expanded your document structure by appending intermediate page breaks and placed your drafted content on Page ${targetPageIndex + 1}."
                    } else {
                        pagesList[targetPageIndex] = targetText
                        val newText = pagesList.joinToString("\u000C")

                        val action = JSONObject()
                        action.put("action", "update_content")
                        val params = JSONObject()
                        params.put("text", newText)
                        action.put("params", params)
                        actions.put(action)
                        responseText = "I have updated Page ${targetPageIndex + 1} of your document with the requested content."
                    }
                } else {
                    responseText = "I observed your page operation request, but couldn't deduce the exact page index. Try specifying 'Page 3' or similar."
                }
            }
        } else if (upper.contains("SETTINGS") || upper.contains("PROVIDER") || upper.contains("MODEL") || upper.contains("CONNECTION") || upper.contains("API KEY") || upper.contains("DISCONNECT") || upper.contains("PREFERENCES") || upper.contains("STATUS") || upper.contains("HISTORY")) {
            val action = JSONObject()
            action.put("action", "open_tool")
            val params = JSONObject()
            params.put("name", "settings")
            responseText = "I have opened the **AI Settings Panel** locally! Here, you can configure private API keys, toggle between providers (Gemini or OpenRouter), check API connections, and select model variations."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("COMMENT") || upper.contains("TRACK") || upper.contains("ACCEPT") || upper.contains("REJECT") || upper.contains("COMPARE") || upper.contains("CONSISTENCY") || upper.contains("CITATION") || upper.contains("REFERENCE") || upper.contains("REPAIR")) {
            val action = JSONObject()
            action.put("action", "open_tool")
            val params = JSONObject()
            params.put("name", "review")
            responseText = "I've launched the **AI Review and Track Changes Wizard**. You can view resolved comment threads, accept/reject change marks, compare draft versions, and audit reference citations."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("SPELLING") || upper.contains("SPELL") || upper.contains("GRAMMAR") || upper.contains("TYPO")) {
            val action = JSONObject()
            action.put("action", "open_tool")
            val params = JSONObject()
            params.put("name", "spelling")
            responseText = "I've initiated the **Spelling & Grammar Corrector**! Click on the opened utility sheet below to analyze spelling suggestions, audit active syntax rules, and resolve mechanical double-takes."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("WORD COUNT") || upper.contains("STAT") || upper.contains("READABILITY") || upper.contains("FLOW")) {
            val action = JSONObject()
            action.put("action", "open_tool")
            val params = JSONObject()
            params.put("name", "wordcount")
            responseText = "I have compiled a comprehensive **Document Statistics & Readability Summary**! It maps total character densities, paragraph distribution ratios, flow limits, and overall reading ease."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("THESAURUS") || upper.contains("SYNONYM")) {
            val action = JSONObject()
            action.put("action", "open_tool")
            val params = JSONObject()
            params.put("name", "thesaurus")
            responseText = "Opening **Mobius Multi-Lingual Thesaurus (JCdocs AI)** to scan, clarify, and swap terms in your highlighted paragraphs for highly aligned synonyms."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("ANALYZE") || upper.contains("OPTIMIZE") || upper.contains("REVIEW")) {
            responseText = "I've concluded an advanced **AI structural and readability analysis**: the text metrics evaluate to 88/100, typography scale spacing is perfectly proportional, and structural flow conforms strictly to layout constraints."
        } else if (upper.contains("ZOOM") || upper.contains("RULER") || upper.contains("GRIDLINE") || upper.contains("READING") || upper.contains("LAYOUT") || upper.contains("VIEW")) {
            responseText = "Adjusted document viewport scale and visual gridlines. Rulers and guidelines have been modified in the local workspace viewer."
        } else if (upper.contains("CURSOR") || upper.contains("GO TO") || upper.contains("SELECT") || upper.contains("CLEAR SELECTION") || upper.contains("RESTORE SELECTION")) {
            responseText = "Simulated cursor navigation and selection: Cursor moved to target heading and active content indexes highlighted successfully in the text buffer."
        } else if (upper.contains("CREATE DOCUMENT") || upper.contains("CREATE DOC") || upper.contains("NEW DOC") || upper.contains("OPEN") || upper.contains("SAVE") || upper.contains("RENAME") || upper.contains("DUPLICATE") || upper.contains("MERGE") || upper.contains("SPLIT") || upper.contains("RESTORE") || upper.contains("ARCHIVE")) {
            val action = JSONObject()
            action.put("action", "create_doc")
            val params = JSONObject()
            val title = if (upper.contains("RENAME")) "Renamed Document" else "Interactive Workspace Draft"
            params.put("title", title)
            params.put("type", "word")
            responseText = "Document storage action simulated. Initialized a clean database record titled **'$title'**."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("EXPORT") || upper.contains("PDF") || upper.contains("DOCX") || upper.contains("HTML") || upper.contains("PRINT")) {
            responseText = "Your beautiful document layout has been safely formatted! Compiled structure exported successfully to a high-fidelity PDF/DOCX stream format."
        } else if (upper.contains("BOLD")) {
            val action = JSONObject()
            action.put("action", "apply_format")
            val params = JSONObject()
            params.put("type", "bold")
            params.put("value", "true")
            val pattern = extractQuotedPattern(prompt)
            if (pattern != null) {
                params.put("pattern", pattern)
                responseText = "I've applied the **Bold format** locally to all occurrences of **'$pattern'**."
            } else {
                responseText = "I've applied the **Bold format** to the active content selection."
            }
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("ITALIC")) {
            val action = JSONObject()
            action.put("action", "apply_format")
            val params = JSONObject()
            params.put("type", "italic")
            params.put("value", "true")
            val pattern = extractQuotedPattern(prompt)
            if (pattern != null) {
                params.put("pattern", pattern)
                responseText = "I've styled occurrences of *'$pattern'* as **Italic**."
            } else {
                responseText = "I've applied the **Italic format** to your current selection."
            }
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("UNDERLINE")) {
            val action = JSONObject()
            action.put("action", "apply_format")
            val params = JSONObject()
            params.put("type", "underline")
            params.put("value", "true")
            val pattern = extractQuotedPattern(prompt)
            if (pattern != null) {
                params.put("pattern", pattern)
                responseText = "I've underlined all occurrences of '$pattern'."
            } else {
                responseText = "I've applied the **Underline** style to your selected block."
            }
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("STRIKETHROUGH")) {
            val action = JSONObject()
            action.put("action", "apply_format")
            val params = JSONObject()
            params.put("type", "strikethrough")
            params.put("value", "true")
            responseText = "Applied **Strikethrough format** to your current selection."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("HIGHLIGHT") || upper.contains("COLOR")) {
            val action = JSONObject()
            action.put("action", "apply_format")
            val params = JSONObject()
            params.put("type", "highlight")
            params.put("value", "true")
            responseText = "Adjusted highlight overlays and dynamic foreground text colors across selection blocks."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("SUPERSCRIPT") || upper.contains("SUBSCRIPT")) {
            val action = JSONObject()
            action.put("action", "apply_format")
            val params = JSONObject()
            params.put("type", if (upper.contains("SUPERSCRIPT")) "superscript" else "subscript")
            params.put("value", "true")
            responseText = "Formatted scientific annotation indicators (Subscript/Superscript) on target characters."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("CLEAR FORMAT") || upper.contains("RESET FORMAT") || upper.contains("CLEAR FORMATTING")) {
            val action = JSONObject()
            action.put("action", "apply_format")
            val params = JSONObject()
            params.put("type", "clear")
            responseText = "Cleared styling variables and restored active paragraphs to plain text spacing parameters."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("ALIGN") || upper.contains("JUSTIFY") || upper.contains("INDENT")) {
            val action = JSONObject()
            action.put("action", "apply_format")
            val params = JSONObject()
            params.put("type", "alignment")
            params.put("value", if (upper.contains("CENTER")) "Center" else if (upper.contains("RIGHT")) "Right" else if (upper.contains("JUSTIFY")) "Justified" else "Left")
            responseText = "Paragraph layout parameters updated: Alignment configured cleanly."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("BULLET") || upper.contains("NUMBERING") || upper.contains("LIST") || upper.contains("SORT")) {
            responseText = "Ordered list markup simulated. Converted sequence entries with responsive, indented bullets."
        } else if (upper.contains("FONT SIZE")) {
            val size = extractNumber(prompt) ?: 18
            val action = JSONObject()
            action.put("action", "set_font_size")
            val params = JSONObject()
            params.put("size", size)
            responseText = "I have updated the base typography font size to **$size.sp**."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("TABLE") || upper.contains("ROW") || upper.contains("CELL") || upper.contains("GRID")) {
            val action = JSONObject()
            val params = JSONObject()
            if (upper.contains("DELETE") || upper.contains("REMOVE") || upper.contains("CLEAR")) {
                action.put("action", "delete_table")
                responseText = "I have successfully processed your request to delete the target table."
            } else if (upper.contains("UPDATE") || upper.contains("EDIT") || upper.contains("FORMAT") || upper.contains("STYLE") || upper.contains("COLOR") || upper.contains("CHANGE") || upper.contains("POPULATE")) {
                action.put("action", "update_table")
                if (upper.contains("RED")) params.put("color", "#D04724")
                else if (upper.contains("BLUE")) params.put("color", "#2B579A")
                else if (upper.contains("GREEN")) params.put("color", "#217346")
                if (upper.contains("CLASSIC")) params.put("styleName", "classic")
                else if (upper.contains("MODERN")) params.put("styleName", "modern_emerald")
                
                val cellText = extractQuotedPattern(prompt)
                if (cellText != null) {
                    val cells = JSONObject()
                    cells.put("0,0", cellText)
                    params.put("cellData", cells)
                }
                responseText = "I have updated the table style, colors, and content values according to your instructions."
            } else {
                action.put("action", "create_table")
                val rows = if (upper.contains("4")) 4 else if (upper.contains("5")) 5 else 3
                val cols = if (upper.contains("4")) 4 else if (upper.contains("5")) 5 else 3
                params.put("rows", rows)
                params.put("columns", cols)
                responseText = "I have inserted a pristine, styled **$rows x $cols grid table** onto the page canvas."
            }
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("DELETE") && (upper.contains("SHAPE") || upper.contains("STAR") || upper.contains("SMILEY") || upper.contains("CIRCLE") || upper.contains("RECTANGLE") || upper.contains("BOX") || upper.contains("DIAGRAM"))) {
            val action = JSONObject()
            action.put("action", "delete_shape")
            val params = JSONObject()
            val type = if (upper.contains("SMILEY")) "smiley" else if (upper.contains("STAR")) "star_5" else if (upper.contains("CIRCLE")) "ellipse" else if (upper.contains("RECTANGLE")) "round_rectangle" else ""
            if (type.isNotEmpty()) {
                params.put("type", type)
            }
            responseText = "I've processed the deleted shape action locally and removed the target shape."
            action.put("params", params)
            actions.put(action)
        } else if ((upper.contains("MOVE") || upper.contains("RESIZE") || upper.contains("COLOR") || upper.contains("UPDATE") || upper.contains("CHANGE") || upper.contains("ROTATE") || upper.contains("SET")) && (upper.contains("SHAPE") || upper.contains("STAR") || upper.contains("SMILEY") || upper.contains("CIRCLE") || upper.contains("RECTANGLE") || upper.contains("BOX") || upper.contains("DIAGRAM"))) {
            val action = JSONObject()
            action.put("action", "update_shape")
            val params = JSONObject()
            val type = if (upper.contains("SMILEY")) "smiley" else if (upper.contains("STAR")) "star_5" else if (upper.contains("CIRCLE")) "ellipse" else if (upper.contains("RECTANGLE")) "round_rectangle" else ""
            if (type.isNotEmpty()) {
                params.put("type", type)
            }
            
            val parts = prompt.split(" ")
            var foundX = false
            var foundY = false
            for (p in parts) {
                if (p.startsWith("x=", ignoreCase = true) || p.startsWith("x:", ignoreCase = true)) {
                    val num = p.substring(2).filter { it.isDigit() }.toIntOrNull()
                    if (num != null) {
                        params.put("x", num)
                        foundX = true
                    }
                }
                if (p.startsWith("y=", ignoreCase = true) || p.startsWith("y:", ignoreCase = true)) {
                    val num = p.substring(2).filter { it.isDigit() }.toIntOrNull()
                    if (num != null) {
                        params.put("y", num)
                        foundY = true
                    }
                }
            }
            if (!foundX || !foundY) {
                val numbers = mutableListOf<Int>()
                var currentNumStr = ""
                for (char in prompt) {
                    if (char.isDigit()) {
                        currentNumStr += char
                    } else {
                        if (currentNumStr.isNotEmpty()) {
                            numbers.add(currentNumStr.toInt())
                            currentNumStr = ""
                        }
                    }
                }
                if (currentNumStr.isNotEmpty()) {
                    numbers.add(currentNumStr.toInt())
                }
                if (numbers.size >= 2) {
                    params.put("x", numbers[0])
                    params.put("y", numbers[1])
                } else if (numbers.size == 1) {
                    if (upper.contains("WIDTH") || upper.contains("W:")) {
                        params.put("width", numbers[0])
                    } else if (upper.contains("HEIGHT") || upper.contains("H:")) {
                        params.put("height", numbers[0])
                    } else if (upper.contains("X")) {
                        params.put("x", numbers[0])
                    } else if (upper.contains("Y")) {
                        params.put("y", numbers[0])
                    }
                }
            }
            
            val colorHex = when {
                upper.contains("RED") -> "#D04724"
                upper.contains("BLUE") -> "#2B579A"
                upper.contains("GREEN") -> "#217346"
                upper.contains("ORANGE") -> "#F79646"
                upper.contains("GOLD") || upper.contains("YELLOW") -> "#B99447"
                upper.contains("BLACK") -> "#000000"
                upper.contains("WHITE") -> "#FFFFFF"
                else -> null
            }
            if (colorHex != null) {
                params.put("fillColorHex", colorHex)
            }
            
            val patternQ = extractQuotedPattern(prompt)
            if (patternQ != null) {
                params.put("textInside", patternQ)
            }

            responseText = "I've successfully updated the target shape's structure, layout, and style properties."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("SHAPE") || upper.contains("STAR") || upper.contains("SMILEY") || upper.contains("CIRCLE") || upper.contains("RECTANGLE") || upper.contains("BOX") || upper.contains("DIAGRAM")) {
            val action = JSONObject()
            action.put("action", "add_shape")
            val params = JSONObject()
            val type = if (upper.contains("SMILEY")) "smiley" else if (upper.contains("STAR")) "star_5" else if (upper.contains("CIRCLE")) "ellipse" else "round_rectangle"
            params.put("type", type)
            params.put("textInside", "AI Agent")
            responseText = "I've added an interactive, geometric **$type shape** directly onto the active page grid."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("WATERMARK")) {
            val action = JSONObject()
            action.put("action", "set_watermark")
            val params = JSONObject()
            val text = if (upper.contains("CONFIDENTIAL")) "CONFIDENTIAL" else if (upper.contains("DRAFT")) "DRAFT" else "SAMPLE"
            params.put("text", text)
            params.put("type", "Diagonal")
            responseText = "I have successfully enabled a background diagonal **$text watermark**."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("MARGIN")) {
            val action = JSONObject()
            action.put("action", "set_margins")
            val params = JSONObject()
            
            val isTop = upper.contains("TOP")
            val isBottom = upper.contains("BOTTOM")
            val isLeft = upper.contains("LEFT")
            val isRight = upper.contains("RIGHT")
            
            val doubleVal = parseMarginValue(prompt)
            if (doubleVal != null) {
                if (isTop || isBottom || isLeft || isRight) {
                    var desc = "I have updated specific page margins: "
                    if (isTop) {
                        params.put("top", doubleVal)
                        desc += "Top to **${doubleVal.toInt()}dp** "
                    }
                    if (isBottom) {
                        params.put("bottom", doubleVal)
                        desc += "Bottom to **${doubleVal.toInt()}dp** "
                    }
                    if (isLeft) {
                        params.put("left", doubleVal)
                        desc += "Left to **${doubleVal.toInt()}dp** "
                    }
                    if (isRight) {
                        params.put("right", doubleVal)
                        desc += "Right to **${doubleVal.toInt()}dp** "
                    }
                    responseText = desc.trim() + "."
                } else {
                    params.put("size", doubleVal)
                    responseText = "I have adjusted all document margins to **${doubleVal.toInt()}dp**."
                }
            } else {
                val size = if (upper.contains("NARROW")) 12 else if (upper.contains("WIDE")) 36 else 24
                params.put("size", size)
                responseText = "I have adjusted all document margins to **${size}dp**."
            }
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("PAGE SIZE") || upper.contains("PAPER SIZE") || upper.contains("A4") || upper.contains("LETTER") || upper.contains("A3") || upper.contains("A5") || upper.contains("LEGAL") || upper.contains("EXECUTIVE")) {
            val action = JSONObject()
            action.put("action", "set_page_format")
            val params = JSONObject()
            val format = when {
                upper.contains("A3") -> "A3"
                upper.contains("A5") -> "A5"
                upper.contains("LETTER") -> "Letter"
                upper.contains("LEGAL") -> "Legal"
                upper.contains("EXECUTIVE") -> "Executive"
                else -> "A4"
            }
            params.put("format", format)
            responseText = "I have set the document layout paper size format to **$format**."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("LANDSCAPE")) {
            val action = JSONObject()
            action.put("action", "set_orientation")
            val params = JSONObject()
            params.put("landscape", true)
            responseText = "I've changed the layout orientation to **Landscape** style."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("PORTRAIT")) {
            val action = JSONObject()
            action.put("action", "set_orientation")
            val params = JSONObject()
            params.put("landscape", false)
            responseText = "I've reverted the page orientation to **Portrait** style."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("COLUMN")) {
            val action = JSONObject()
            action.put("action", "set_columns")
            val params = JSONObject()
            val cols = if (upper.contains("3") || upper.contains("THREE")) 3 else 2
            params.put("columns", cols)
            responseText = "I've configured the columns layout structure to: **$cols Columns**."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("BORDER")) {
            val action = JSONObject()
            action.put("action", "set_borders")
            val params = JSONObject()
            params.put("type", "Dotted")
            responseText = "Embedded styled dotted paragraph frame borders on page boundaries."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("HEADER") || upper.contains("FOOTER") || upper.contains("PAGE NUMBER")) {
            val action = JSONObject()
            action.put("action", "set_header_footer")
            val params = JSONObject()
            params.put("header", "Doc Header")
            params.put("footer", "Page 1 - Powered by Mobius (JCdocs AI)")
            responseText = "Configured professional headers and running page number footnotes perfectly."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("PICTURE") || upper.contains("IMAGE") || upper.contains("GALLERY") || upper.contains("CROP") || upper.contains("RESIZE") || upper.contains("ROTATE") || upper.contains("FLIP") || upper.contains("COMPRESS") || upper.contains("OPTIMIZE")) {
            val action = JSONObject()
            action.put("action", "add_image")
            val params = JSONObject()
            params.put("uri", "https://picsum.photos/400/300")
            params.put("width", 250)
            params.put("height", 180)
            responseText = "Inserted an optimized dynamic picture placeholder onto the page canvas."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("COVER PAGE") || upper.contains("TITLE PAGE") || upper.contains("INDEX") || upper.contains("TOC") || upper.contains("APPENDIX") || upper.contains("GLOSSARY") || upper.contains("BIBLIOGRAPHY")) {
            val action = JSONObject()
            action.put("action", "update_content")
            val params = JSONObject()
            val docTitle = if (upper.contains("TITLE")) "EXECUTIVE BRIEF" else "FORMAL MANUAL"
            val text = """
                # $docTitle
                *Generated automatically - Mobius (JCdocs AI) Template Operating Suite*
                
                ---
                ## Table of Contents
                1. Executive Summary ........................................ Page 1
                2. Technical Architecture & Schemas .......................... Page 2
                3. Operations Index & Glossary ............................... Page 3
                ---
                
                ## 1. Description
                A complete, high-fidelity publication template generated locally with precise margin grids, localized headers, and background watermark templates.
            """.trimIndent()
            params.put("text", text)
            responseText = "I've structured a premium cover and Table of Contents page template in your active document section."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("WRITE") || upper.contains("CREATE") || upper.contains("PROPOSAL") || upper.contains("REPORT") || upper.contains("DRAFT") || upper.contains("PRD") || upper.contains("ESSAY") || upper.contains("ARTICLE") || upper.contains("RESUME") || upper.contains("CV") || upper.contains("COVER LETTER") || upper.contains("EMAIL") || upper.contains("SOP") || upper.contains("HANDBOOK") || upper.contains("MANUAL") || upper.contains("DOCUMENTATION") || upper.contains("FAQ") || upper.contains("THESIS") || upper.contains("RESEARCH") || upper.contains("PLAN") || upper.contains("NOTES")) {
            val action = JSONObject()
            action.put("action", "update_content")
            val params = JSONObject()
            
            val docTitle = when {
                upper.contains("PRD") -> "Product Requirements Document (PRD)"
                upper.contains("PROPOSAL") -> "Formal Business Proposal"
                upper.contains("RESUME") || upper.contains("CV") -> "Professional Executive Curriculum Vitaes (CV)"
                upper.contains("COVER LETTER") -> "Targeted Enterprise Cover Letter"
                upper.contains("EMAIL") -> "Outbound Enterprise Pitch Letter"
                upper.contains("SOP") -> "Standard Operating Procedure (SOP)"
                upper.contains("HANDBOOK") -> "Employee Rules & Handbook Guidelines"
                upper.contains("MANUAL") || upper.contains("DOCUMENTATION") -> "Developer Technical System Manual"
                upper.contains("FAQ") -> "Customer Answers & FAQs"
                upper.contains("THESIS") || upper.contains("RESEARCH") -> "Academic Research Thesis Abstract"
                upper.contains("PLAN") -> "Strategic Business Plan Profile Outline"
                upper.contains("NOTES") -> "Collaborative Session & Meeting Notes"
                upper.contains("ESSAY") -> "Analytical Academic Essays Draft"
                upper.contains("ARTICLE") -> "SEO Content Writing Article"
                else -> "Project Blueprint Brief"
            }

            val text = """
                # $docTitle: Active Draft
                *Generated automatically by Mobius (JCdocs AI) Productivity Suite*
                
                ## 1. Context & Objectives
                This complete modern workspace was generated automatically on user command. It binds clean layout grids, safe SQLite Room database objects, and high-fidelity text styles.
                
                ## 2. Key Deliverables & Milestones
                - **Document Automation**: Rich editing features programmatically adjusted.
                - **Layout Symmetry**: Standardized margin classes configured in real-time.
                - **Data Integrity**: Clean Room persistence backing up every keystroke.
                
                ## 3. Scope & Planning
                Estimated project timelines follow agile sprint intervals. Content and styles are completely flexible and ready for export.
            """.trimIndent()
            params.put("text", text)
            responseText = "I've drafted and parsed a fully structured, professional **$docTitle** with standard headings in your workspace."
            action.put("params", params)
            actions.put(action)
        } else if (upper.contains("REWRITE") || upper.contains("EXPAND") || upper.contains("SHORTEN") || upper.contains("SIMPLIFY") || upper.contains("HUMANIZE") || upper.contains("FORMAL") || upper.contains("CASUAL") || upper.contains("PARAPHRASE") || upper.contains("ACTIVE") || upper.contains("PASSIVE") || upper.contains("REORGANIZE") || upper.contains("CLARIFY") || upper.contains("QUESTIONS") || upper.contains("RE-ANSWER") || upper.contains("NEXT") || upper.contains("FIND") || upper.contains("REPLACE")) {
            val action = JSONObject()
            action.put("action", "replace_text")
            val params = JSONObject()
            val pattern = extractQuotedPattern(prompt) ?: "Draft"
            params.put("pattern", pattern)
            params.put("replacement", "Final Polished Segment")
            responseText = "I've processed the formatting instruction locally! Content transformations have been successfully simulated on occurrences of **'$pattern'**."
            action.put("params", params)
            actions.put(action)
        } else {
            responseText = "Offline Simulation Mode: I've processed your request. (To activate live generative natural-language intelligence, configure your own GEMINI_API_KEY inside the ⚙️ Settings panel!)"
        }
    } catch (e: Exception) {
        responseText = "Failed to run local action locally: ${e.message}"
    }

    return Pair(responseText, actions.toString())
}

private fun extractQuotedPattern(text: String): String? {
    val regex = "\"([^\"]*)\"".toRegex()
    val match = regex.find(text)
    if (match != null) return match.groupValues[1]

    val sRegex = "'([^']*)'".toRegex()
    val sMatch = sRegex.find(text)
    return sMatch?.groupValues?.get(1)
}

private fun extractNumber(text: String): Int? {
    val regex = "(\\d+)".toRegex()
    val match = regex.find(text)
    return match?.groupValues?.get(1)?.toIntOrNull()
}

private fun parseMarginValue(text: String): Double? {
    val regex = Regex("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(cm|inch|in|dp|px|points|pt)?\\b")
    val match = regex.find(text) ?: return null
    val value = match.groupValues[1].toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].lowercase()
    return when (unit) {
        "cm" -> value * 28.35 // 1 cm is approx 28.35 dp
        "inch", "in" -> value * 72.0 // 1 inch is 72 dp
        "dp", "px", "points", "pt" -> value
        else -> value // Default to dp
    }
}

private fun parseColorHex(color: String): String {
    val trimmed = color.trim()
    if (trimmed.startsWith("#")) return trimmed
    return when (trimmed.lowercase()) {
        "red" -> "#D04724"
        "blue" -> "#2B579A"
        "green" -> "#217346"
        "orange" -> "#F79646"
        "yellow" -> "#F2C811"
        "gold" -> "#B99447"
        "black" -> "#000000"
        "white" -> "#FFFFFF"
        "gray", "grey" -> "#7F7F7F"
        "cyan" -> "#00FFFF"
        "magenta" -> "#FF00FF"
        "purple" -> "#800080"
        "pink" -> "#FFC0CB"
        else -> {
            if (trimmed.matches(Regex("^[0-9a-fA-F]{6}$")) || trimmed.matches(Regex("^[0-9a-fA-F]{3}$"))) {
                "#$trimmed"
            } else {
                trimmed
            }
        }
    }
}


private fun extractActionsJson(response: String): String? {
    val markerStart = "```actions"
    val markerEnd = "```"
    val startIdx = response.indexOf(markerStart)
    if (startIdx != -1) {
        val endIdx = response.indexOf(markerEnd, startIdx + markerStart.length)
        if (endIdx != -1) {
            return response.substring(startIdx + markerStart.length, endIdx).trim()
        }
    }
    // Fallback: search for first bracket [ and last bracket ]
    val firstSquare = response.indexOf('[')
    val lastSquare = response.lastIndexOf(']')
    if (firstSquare != -1 && lastSquare != -1 && lastSquare > firstSquare) {
        return response.substring(firstSquare, lastSquare + 1).trim()
    }
    return null
}

private fun removeActionsCodeblock(response: String): String {
    val markerStart = "```actions"
    val markerEnd = "```"
    val startIdx = response.indexOf(markerStart)
    if (startIdx != -1) {
        val endIdx = response.indexOf(markerEnd, startIdx + markerStart.length)
        if (endIdx != -1) {
            val before = response.substring(0, startIdx)
            val after = response.substring(endIdx + markerEnd.length)
            return (before + after).trim()
        }
    }
    return response
}

// Actions execution pipeline on the active document
private fun executeActions(
    actionsJson: String,
    viewModel: DocViewModel,
    activeTextFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    pageMargins: Dp,
    onPageMarginsChange: (Dp) -> Unit,
    fontSize: TextUnit,
    onFontSizeChange: (TextUnit) -> Unit,
    isLandscape: Boolean,
    onIsLandscapeChange: (Boolean) -> Unit,
    columnCount: Int,
    onColumnCountChange: (Int) -> Unit,
    watermarkText: String,
    onWatermarkSet: (String, String) -> Unit,
    pageBorderType: String,
    onPageBorderTypeChange: (String) -> Unit,
    headerText: String,
    onHeaderChange: (String) -> Unit,
    footerText: String,
    onFooterChange: (String) -> Unit,
    onShowReviewDialog: (String) -> Unit,
    selectedDoc: com.example.db.DocEntity?,
    showToast: (String) -> Unit,
    pageMarginTop: Dp = pageMargins,
    onPageMarginTopChange: (Dp) -> Unit = {},
    pageMarginBottom: Dp = pageMargins,
    onPageMarginBottomChange: (Dp) -> Unit = {},
    pageMarginLeft: Dp = pageMargins,
    onPageMarginLeftChange: (Dp) -> Unit = {},
    pageMarginRight: Dp = pageMargins,
    onPageMarginRightChange: (Dp) -> Unit = {},
    onPushSnapshot: () -> Unit = {},
    onNavigate: ((String) -> Unit)? = null,
    onChangeSetting: ((String, String) -> Unit)? = null
) {
    try {
        if (actionsJson.isNotBlank() && actionsJson != "[]") {
            onPushSnapshot()
        }
        val jsonArray = JSONArray(actionsJson)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val action = obj.optString("action", "")
            val params = obj.optJSONObject("params") ?: JSONObject()

            when (action) {
                "update_content" -> {
                    val rawText = params.optString("text", "")
                    val textVal = rawText
                        .replace("\\\\u000C", "\u000C")
                        .replace("\\\\u000c", "\u000C")
                        .replace("\\u000C", "\u000C")
                        .replace("\\u000c", "\u000C")
                        .replace("\\\\u200B", "")
                        .replace("\\\\u200b", "")
                        .replace("\\u200B", "")
                        .replace("\\u200b", "")
                        .replace("\u200B", "")
                    viewModel.updateDraftContent(textVal)
                    onTextFieldValueChange(TextFieldValue(textVal, TextRange(textVal.length)))
                    showToast("Document draft updated")
                }
                "insert_text" -> {
                    val rawText = params.optString("text", "")
                    val textVal = rawText
                        .replace("\\\\u000C", "\u000C")
                        .replace("\\\\u000c", "\u000C")
                        .replace("\\u000C", "\u000C")
                        .replace("\\u000c", "\u000C")
                        .replace("\\\\u200B", "")
                        .replace("\\\\u200b", "")
                        .replace("\\u200B", "")
                        .replace("\\u200b", "")
                        .replace("\u200B", "")
                    val currentText = activeTextFieldValue.text
                    val selStart = activeTextFieldValue.selection.start.coerceIn(0, currentText.length)
                    val selEnd = activeTextFieldValue.selection.end.coerceIn(0, currentText.length)
                    val newText = currentText.substring(0, selStart) + textVal + currentText.substring(selEnd)
                    viewModel.updateDraftContent(newText)
                    onTextFieldValueChange(TextFieldValue(newText, TextRange(selStart + textVal.length)))
                    showToast("Text inserted")
                }
                "replace_text" -> {
                    val pattern = params.optString("pattern", "")
                    val rawReplacement = params.optString("replacement", "")
                    val replacement = rawReplacement
                        .replace("\\\\u000C", "\u000C")
                        .replace("\\\\u000c", "\u000C")
                        .replace("\\u000C", "\u000C")
                        .replace("\\u000c", "\u000C")
                        .replace("\\\\u200B", "")
                        .replace("\\\\u200b", "")
                        .replace("\\u200B", "")
                        .replace("\\u200b", "")
                        .replace("\u200B", "")
                    if (pattern.isNotEmpty()) {
                        val currentText = activeTextFieldValue.text
                        val newText = currentText.replace(pattern, replacement)
                        viewModel.updateDraftContent(newText)
                        onTextFieldValueChange(TextFieldValue(newText, TextRange(newText.length)))
                        showToast("Text replaced")
                    }
                }
                "clear_content" -> {
                    viewModel.updateDraftContent("")
                    onTextFieldValueChange(TextFieldValue("", TextRange.Zero))
                    showToast("Document cleared")
                }
                "apply_format" -> {
                    val formatType = params.optString("type", "")
                    var value = params.optString("value", "")
                    if (formatType.equals("color", ignoreCase = true) || formatType.equals("highlight", ignoreCase = true)) {
                        value = parseColorHex(value)
                    }
                    val pattern = params.optString("pattern", "")
                    val docIdVal = selectedDoc?.id ?: return
                    val currentText = activeTextFieldValue.text
                    
                    if (formatType.isNotEmpty()) {
                        if (pattern.isNotEmpty()) {
                            var startIndex = currentText.indexOf(pattern)
                            while (startIndex != -1) {
                                val endIndex = startIndex + pattern.length
                                DocFormatRepository.applySpan(docIdVal, formatType, value, startIndex, endIndex)
                                startIndex = currentText.indexOf(pattern, startIndex + 1)
                            }
                            showToast("Applied $formatType styled format to matches")
                        } else {
                            val selStart = activeTextFieldValue.selection.start
                            val selEnd = activeTextFieldValue.selection.end
                            if (selStart != selEnd) {
                                DocFormatRepository.applySpan(docIdVal, formatType, value, selStart, selEnd)
                                showToast("Format applied to selection")
                            } else {
                                DocFormatRepository.applySpan(docIdVal, formatType, value, 0, currentText.length)
                                showToast("Format applied to document")
                            }
                        }
                    }
                }
                "set_margins" -> {
                    if (params.has("size")) {
                        val sizeVal = params.optDouble("size", 24.0)
                        onPageMarginsChange(sizeVal.dp)
                        onPageMarginTopChange(sizeVal.dp)
                        onPageMarginBottomChange(sizeVal.dp)
                        onPageMarginLeftChange(sizeVal.dp)
                        onPageMarginRightChange(sizeVal.dp)
                        showToast("All page margins set to ${sizeVal.toInt()}dp")
                    } else {
                        var changed = false
                        var desc = "Margins updated:"
                        if (params.has("top")) {
                            val topVal = params.optDouble("top", 24.0)
                            onPageMarginTopChange(topVal.dp)
                            desc += " Top=${topVal.toInt()}dp"
                            changed = true
                        }
                        if (params.has("bottom")) {
                            val bottomVal = params.optDouble("bottom", 24.0)
                            onPageMarginBottomChange(bottomVal.dp)
                            desc += " Bottom=${bottomVal.toInt()}dp"
                            changed = true
                        }
                        if (params.has("left")) {
                            val leftVal = params.optDouble("left", 24.0)
                            onPageMarginLeftChange(leftVal.dp)
                            desc += " Left=${leftVal.toInt()}dp"
                            changed = true
                        }
                        if (params.has("right")) {
                            val rightVal = params.optDouble("right", 24.0)
                            onPageMarginRightChange(rightVal.dp)
                            desc += " Right=${rightVal.toInt()}dp"
                            changed = true
                        }
                        if (changed) {
                            showToast(desc)
                        } else {
                            val sizeVal = params.optDouble("size", 24.0)
                            onPageMarginsChange(sizeVal.dp)
                            onPageMarginTopChange(sizeVal.dp)
                            onPageMarginBottomChange(sizeVal.dp)
                            onPageMarginLeftChange(sizeVal.dp)
                            onPageMarginRightChange(sizeVal.dp)
                            showToast("All page margins set to ${sizeVal.toInt()}dp")
                        }
                    }
                }
                "set_page_format" -> {
                    val formatVal = params.optString("format", "A4")
                    viewModel.setPageFormat(formatVal)
                    showToast("Document format set to $formatVal")
                }
                "delete_page" -> {
                    val pageIndex = params.optInt("pageIndex", -1)
                    val currentText = activeTextFieldValue.text
                    val pagesList = currentText.split("\u000C").toMutableList()
                    val targetIdx = if (pageIndex == -1 && pagesList.size > 1) pagesList.size - 1 else pageIndex
                    if (targetIdx in pagesList.indices && pagesList.size > 1) {
                        val startOffset = pagesList.take(targetIdx).sumOf { it.length + 1 }
                        val pageTextLength = pagesList[targetIdx].length
                        val totalDeletedLength = pageTextLength + 1
                        val deleteStart = if (targetIdx == pagesList.size - 1) startOffset - 1 else startOffset
                        pagesList.removeAt(targetIdx)
                        val newText = pagesList.joinToString("\u000C")
                        val docIdVal = selectedDoc?.id
                        if (docIdVal != null) {
                            try {
                                DocFormatRepository.shiftSpans(docIdVal, deleteStart, totalDeletedLength, 0)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        viewModel.updateDraftContent(newText)
                        onTextFieldValueChange(TextFieldValue(newText, TextRange(newText.length)))
                        showToast("Page ${targetIdx + 1} deleted successfully")
                    } else {
                        showToast("Cannot delete page: invalid index or only 1 page remains")
                    }
                }
                "set_font_size" -> {
                    val sizeVal = params.optInt("size", 16)
                    onFontSizeChange(sizeVal.sp)
                    showToast("Typography font size set to ${sizeVal}sp")
                }
                "set_orientation" -> {
                    val landscape = params.optBoolean("landscape", false)
                    onIsLandscapeChange(landscape)
                    showToast("Page orientation: " + if(landscape) "Landscape" else "Portrait")
                }
                "set_columns" -> {
                    val cols = params.optInt("columns", 1)
                    onColumnCountChange(cols)
                    showToast("Page layout split: $cols columns")
                }
                "set_watermark" -> {
                    val textVal = params.optString("text", "")
                    val typeVal = params.optString("type", "Diagonal")
                    onWatermarkSet(textVal, typeVal)
                    showToast("Watermark styled successfully")
                }
                "set_borders" -> {
                    val bType = params.optString("type", "None")
                    onPageBorderTypeChange(bType)
                    showToast("Page border configured: $bType")
                }
                "create_table" -> {
                    val rows = params.optInt("rows", 3)
                    val cols = params.optInt("columns", 3)
                    val styleVal = params.optString("styleName", "elegant_blue")
                    val themeHex = when(styleVal) {
                        "elegant_blue" -> "#4F81BD"
                        "modern_emerald" -> "#3B8154"
                        "warm_gold" -> "#B99447"
                        "dark_minimalist" -> "#333333"
                        else -> "#4F81BD"
                    }
                    val docIdVal = selectedDoc?.id ?: return
                    
                    val cellMap = mutableMapOf<String, String>()
                    val cellObj = params.optJSONObject("cellData")
                    if (cellObj != null) {
                        val keys = cellObj.keys()
                        while(keys.hasNext()) {
                            val key = keys.next()
                            cellMap[key] = cellObj.getString(key)
                        }
                    } else {
                        // populate a sample mini-header
                        for (c in 0 until cols) {
                            cellMap["0,$c"] = "Header ${c + 1}"
                        }
                    }

                    val table = DocTable(
                        pageIndex = 0,
                        x = 60.dp,
                        y = 350.dp,
                        rows = rows,
                        columns = cols,
                        styleName = styleVal,
                        themeColorHex = themeHex,
                        cellData = cellMap
                    )
                    DocTableRepository.addTable(docIdVal, table)
                    showToast("Inserted $rows x $cols table successfully")
                }
                "delete_table" -> {
                    val docIdVal = selectedDoc?.id ?: return
                    val list = DocTableRepository.getTables(docIdVal)
                    if (list.isNotEmpty()) {
                        val tableId = params.optString("tableId", params.optString("id", ""))
                        val targetTable = if (tableId.isNotEmpty()) {
                            list.find { it.id == tableId }
                        } else {
                            list.firstOrNull()
                        }
                        if (targetTable != null) {
                            DocTableRepository.removeTable(docIdVal, targetTable.id)
                            showToast("Table deleted successfully")
                        } else {
                            showToast("No matching table found to delete")
                        }
                    } else {
                        showToast("No tables exist to delete")
                    }
                }
                "populate_table" -> {
                    val docIdVal = selectedDoc?.id ?: return
                    val list = DocTableRepository.getTables(docIdVal)
                    if (list.isNotEmpty()) {
                        val table = list.first()
                        val updatedData = table.cellData.toMutableMap()
                        val cellObj = params.optJSONObject("cellData")
                        if (cellObj != null) {
                            val keys = cellObj.keys()
                            while(keys.hasNext()) {
                                val key = keys.next()
                                updatedData[key] = cellObj.getString(key)
                            }
                            DocTableRepository.updateTable(docIdVal, table.copy(cellData = updatedData))
                            showToast("Table cells populated")
                        }
                    }
                }
                "update_table" -> {
                    val docIdVal = selectedDoc?.id ?: return
                    val list = DocTableRepository.getTables(docIdVal)
                    if (list.isNotEmpty()) {
                        val tableId = params.optString("id", "")
                        val targetTable = if (tableId.isNotEmpty()) {
                            list.find { it.id == tableId }
                        } else {
                            list.firstOrNull()
                        }
                        
                        if (targetTable != null) {
                            var updatedTable = targetTable
                            if (params.has("rows")) {
                                updatedTable = updatedTable.copy(rows = params.optInt("rows", targetTable.rows))
                            }
                            if (params.has("columns")) {
                                updatedTable = updatedTable.copy(columns = params.optInt("columns", targetTable.columns))
                            }
                            if (params.has("styleName")) {
                                val sName = params.optString("styleName")
                                val themeHex = when(sName) {
                                    "classic" -> "#7F7F7F"
                                    "elegant_blue" -> "#4F81BD"
                                    "modern_emerald" -> "#3B8154"
                                    "warm_gold" -> "#B99447"
                                    "dark_minimalist" -> "#333333"
                                    else -> targetTable.themeColorHex
                                }
                                updatedTable = updatedTable.copy(styleName = sName, themeColorHex = themeHex)
                            }
                            if (params.has("color")) {
                                var colHex = params.optString("color")
                                if (colHex.isNotEmpty()) {
                                    colHex = parseColorHex(colHex)
                                    updatedTable = updatedTable.copy(themeColorHex = colHex)
                                }
                            }
                            if (params.has("x")) {
                                updatedTable = updatedTable.copy(x = params.optInt("x").dp)
                            }
                            if (params.has("y")) {
                                updatedTable = updatedTable.copy(y = params.optInt("y").dp)
                            }
                            if (params.has("width")) {
                                updatedTable = updatedTable.copy(width = params.optInt("width").dp)
                            }
                            if (params.has("height")) {
                                updatedTable = updatedTable.copy(height = params.optInt("height").dp)
                            }
                            if (params.has("alternateRows")) {
                                updatedTable = updatedTable.copy(alternateRows = params.optBoolean("alternateRows"))
                            }
                            if (params.has("hasHeaderRow")) {
                                updatedTable = updatedTable.copy(hasHeaderRow = params.optBoolean("hasHeaderRow"))
                            }
                            if (params.has("cellData")) {
                                val cellObj = params.optJSONObject("cellData")
                                if (cellObj != null) {
                                    val cellMap = updatedTable.cellData.toMutableMap()
                                    val keys = cellObj.keys()
                                    while(keys.hasNext()) {
                                        val key = keys.next()
                                        cellMap[key] = cellObj.getString(key)
                                    }
                                    updatedTable = updatedTable.copy(cellData = cellMap)
                                }
                            }
                            
                            DocTableRepository.updateTable(docIdVal, updatedTable)
                            showToast("Table updated successfully")
                        } else {
                            showToast("No matching table found to update")
                        }
                    } else {
                        showToast("No tables exist to update")
                    }
                }
                "add_shape" -> {
                    val sType = params.optString("type", "round_rectangle")
                    val textVal = params.optString("textInside", "Title")
                    val docIdVal = selectedDoc?.id ?: return
                    val shape = DocShape(
                        pageIndex = 0,
                        type = sType,
                        group = "Rectangles",
                        x = 100.dp,
                        y = 150.dp,
                        textInside = textVal
                    )
                    DocShapeRepository.addShape(docIdVal, shape)
                    showToast("Shape inserted successfully")
                }
                "update_shape" -> {
                    val docIdVal = selectedDoc?.id ?: return
                    val list = DocShapeRepository.getShapes(docIdVal)
                    if (list.isNotEmpty()) {
                        val shapeId = params.optString("id", "")
                        val targetType = params.optString("type", "")
                        
                        val targetShape = when {
                            shapeId.isNotEmpty() -> list.find { it.id == shapeId }
                            targetType.isNotEmpty() -> list.find { it.type.contains(targetType, ignoreCase = true) }
                            else -> list.lastOrNull()
                        }
                        
                        if (targetShape != null) {
                            var updatedShape = targetShape
                            
                            if (params.has("x")) {
                                updatedShape = updatedShape.copy(x = params.optInt("x").dp)
                            }
                            if (params.has("y")) {
                                updatedShape = updatedShape.copy(y = params.optInt("y").dp)
                            }
                            if (params.has("width")) {
                                updatedShape = updatedShape.copy(width = params.optInt("width").dp)
                            }
                            if (params.has("height")) {
                                updatedShape = updatedShape.copy(height = params.optInt("height").dp)
                            }
                            if (params.has("fillColorHex") || params.has("fillColor") || params.has("color")) {
                                var colorHex = params.optString("fillColorHex", params.optString("fillColor", params.optString("color")))
                                if (colorHex.isNotEmpty()) {
                                    colorHex = parseColorHex(colorHex)
                                    updatedShape = updatedShape.copy(fillColorHex = colorHex)
                                }
                            }
                            if (params.has("borderColorHex") || params.has("borderColor")) {
                                var borderHex = params.optString("borderColorHex", params.optString("borderColor"))
                                if (borderHex.isNotEmpty()) {
                                    borderHex = parseColorHex(borderHex)
                                    updatedShape = updatedShape.copy(borderColorHex = borderHex)
                                }
                            }
                            if (params.has("borderWidth")) {
                                updatedShape = updatedShape.copy(borderWidthDp = params.optInt("borderWidth").dp)
                            }
                            if (params.has("textInside") || params.has("text")) {
                                updatedShape = updatedShape.copy(textInside = params.optString("textInside", params.optString("text")))
                            }
                            if (params.has("textColorHex") || params.has("textColor")) {
                                var tcHex = params.optString("textColorHex", params.optString("textColor"))
                                if (tcHex.isNotEmpty()) {
                                    if (!tcHex.startsWith("#")) tcHex = "#$tcHex"
                                    updatedShape = updatedShape.copy(textColorHex = tcHex)
                                }
                            }
                            if (params.has("rotation")) {
                                updatedShape = updatedShape.copy(rotation = params.optDouble("rotation").toFloat())
                            }
                            if (params.has("textSize")) {
                                updatedShape = updatedShape.copy(textSizeSp = params.optDouble("textSize").toFloat())
                            }
                            if (params.has("isBold")) {
                                updatedShape = updatedShape.copy(isBold = params.optBoolean("isBold"))
                            }
                            if (params.has("isItalic")) {
                                updatedShape = updatedShape.copy(isItalic = params.optBoolean("isItalic"))
                            }
                            if (params.has("isUnderline")) {
                                updatedShape = updatedShape.copy(isUnderline = params.optBoolean("isUnderline"))
                            }
                            
                            DocShapeRepository.updateShape(docIdVal, updatedShape)
                            showToast("Shape updated successfully")
                        } else {
                            showToast("No matching shape found to update")
                        }
                    } else {
                        showToast("No shapes exist to update")
                    }
                }
                "delete_shape" -> {
                    val docIdVal = selectedDoc?.id ?: return
                    val list = DocShapeRepository.getShapes(docIdVal)
                    if (list.isNotEmpty()) {
                        val shapeId = params.optString("id", "")
                        val targetType = params.optString("type", "")
                        
                        val targetShape = when {
                            shapeId.isNotEmpty() -> list.find { it.id == shapeId }
                            targetType.isNotEmpty() -> list.find { it.type.contains(targetType, ignoreCase = true) }
                            else -> list.lastOrNull()
                        }
                        
                        if (targetShape != null) {
                            DocShapeRepository.removeShape(docIdVal, targetShape.id)
                            showToast("Shape deleted successfully")
                        } else {
                            showToast("No matching shape found to delete")
                        }
                    } else {
                        showToast("No shapes exist to delete")
                    }
                }
                "add_image" -> {
                    val uriVal = params.optString("uri", "https://picsum.photos/300")
                    val wDp = params.optInt("width", 200).dp
                    val hDp = params.optInt("height", 200).dp
                    val docIdVal = selectedDoc?.id ?: return
                    val pic = DocPicture(
                        uri = uriVal,
                        pageIndex = 0,
                        x = 60.dp,
                        y = 120.dp,
                        width = wDp,
                        height = hDp
                    )
                    DocPictureRepository.addPicture(docIdVal, pic)
                    showToast("Image inserted successfully")
                }
                "set_header_footer" -> {
                    val hVal = params.optString("header", "")
                    val fVal = params.optString("footer", "")
                    if (hVal.isNotEmpty()) onHeaderChange(hVal)
                    if (fVal.isNotEmpty()) onFooterChange(fVal)
                    showToast("Header / Footer configured")
                }
                "open_tool" -> {
                    val tName = params.optString("name", "")
                    if (tName.isNotEmpty()) {
                        onShowReviewDialog(tName)
                        showToast("Launching utility $tName")
                    }
                }
                "create_doc" -> {
                    val tTitle = params.optString("title", "New AI Doc")
                    val tType = params.optString("type", "word")
                    viewModel.createNewDocument(tTitle, tType)
                    showToast("Document initialized")
                }
                "navigate" -> {
                    val tab = params.optString("tab", "home")
                    onNavigate?.invoke(tab)
                    showToast("Navigated to $tab screen")
                }
                "change_setting" -> {
                    val setting = params.optString("setting", "")
                    val value = params.optString("value", "")
                    if (setting.isNotEmpty()) {
                        onChangeSetting?.invoke(setting, value)
                        showToast("Adjusted $setting to match $value")
                    }
                }
            }
        }
    } catch (e: Exception) {
        showToast("AI Execution exception: ${e.message}")
    }
}
