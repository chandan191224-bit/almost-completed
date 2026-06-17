package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.DocEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONException

data class SlideItem(
    val title: String = "Slide Title",
    val body: String = "Slide Body Content",
    val theme: String = "classic", // colors: "classic", "indigo", "orange", "dark"
    val layout: String = "title_body" // layouts: "title_body", "title_slide", "title_only"
)

class DocViewModel(private val context: android.content.Context) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedTypeFilter = MutableStateFlow("all")

    val customFolders = MutableStateFlow<List<String>>(emptyList())
    val deletedCustomFolders = MutableStateFlow<List<String>>(emptyList())
    val favoriteFolders = MutableStateFlow<Set<String>>(emptySet())

    private fun saveFoldersToPrefs() {
        try {
            val prefs = context.getSharedPreferences("jc_folders_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putStringSet("custom_folders", customFolders.value.toSet())
                .putStringSet("deleted_custom_folders", deletedCustomFolders.value.toSet())
                .putStringSet("favorite_folders", favoriteFolders.value)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _allDocs = MutableStateFlow<List<DocEntity>>(emptyList())
    val allDocsFlow = _allDocs.asStateFlow()

    init {
        val prefs = context.getSharedPreferences("jc_folders_prefs", android.content.Context.MODE_PRIVATE)
        val savedCustom = prefs.getStringSet("custom_folders", setOf("Project Documents", "Marketing Assets")) ?: setOf("Project Documents", "Marketing Assets")
        customFolders.value = savedCustom.toList().sorted()
        val savedDeleted = prefs.getStringSet("deleted_custom_folders", emptySet()) ?: emptySet()
        deletedCustomFolders.value = savedDeleted.toList().sorted()
        val savedFavs = prefs.getStringSet("favorite_folders", emptySet()) ?: emptySet()
        favoriteFolders.value = savedFavs

        loadDocsFromDisk()
    }

    val allFolders: StateFlow<List<String>> = allDocsFlow
        .map { list -> list.filter { !it.isDeleted } }
        .combine(customFolders) { docs, custom ->
            val dbFolders = docs.mapNotNull { it.folderName }.filter { it.isNotBlank() }.distinct()
            (custom + dbFolders).distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Project Documents", "Marketing Assets"))

    val documents: StateFlow<List<DocEntity>> = allDocsFlow
        .map { list -> list.filter { !it.isDeleted } }
        .combine(searchQuery) { docs, query ->
            if (query.isBlank()) docs else docs.filter { it.title.contains(query, ignoreCase = true) }
        }
        .combine(selectedTypeFilter) { docs, filter ->
            if (filter == "all") docs else docs.filter { it.type.equals(filter, ignoreCase = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedDocuments: StateFlow<List<DocEntity>> = allDocsFlow
        .map { list -> list.filter { it.isDeleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedDoc = MutableStateFlow<DocEntity?>(null)
    val draftTitle = MutableStateFlow("")
    val draftContent = MutableStateFlow("")
    val pageFormat = MutableStateFlow("Letter")
    val customPageDimensions = MutableStateFlow(Pair(8.5f, 11f))
    val isLandscape = MutableStateFlow(false)
    val pageMarginLeft = MutableStateFlow(24f)
    val pageMarginTop = MutableStateFlow(24f)
    val pageMarginRight = MutableStateFlow(24f)
    val pageMarginBottom = MutableStateFlow(24f)
    val columnCount = MutableStateFlow(1)
    val editorTheme = MutableStateFlow("white")
    val selectedDocumentTheme = MutableStateFlow("Office Classic")
    val selectedThemeEffect = MutableStateFlow("None")
    val pageBackgroundColorHex = MutableStateFlow("")
    val watermarkText = MutableStateFlow("")
    val watermarkType = MutableStateFlow("Diagonal")
    val watermarkColorHex = MutableStateFlow("#33CCCCCC")
    val pageBorderType = MutableStateFlow("None")
    val pageBorderColorHex = MutableStateFlow("default")
    val isPlayingPresentation = MutableStateFlow(false)
    val fontSize = MutableStateFlow(16f)
    val headerText = MutableStateFlow("")
    val footerText = MutableStateFlow("")
    val headerAlignment = MutableStateFlow("Center")
    val footerAlignment = MutableStateFlow("Center")
    val showHeaderFooterOnFirstPage = MutableStateFlow(true)
    val pageNumberPosition = MutableStateFlow<String?>(null)
    val pageNumberFormat = MutableStateFlow("1, 2, 3...")
    val pageNumberStartAt = MutableStateFlow(1)
    val showPageNumberOnFirstPage = MutableStateFlow(true)

    // Spreadsheet state
    val selectedCell = MutableStateFlow<String?>(null)
    val sheetData = MutableStateFlow<Map<String, String>>(emptyMap())

    // Slides state
    val slides = MutableStateFlow<List<SlideItem>>(emptyList())
    val currentSlideIndex = MutableStateFlow(0)
    
    // Global navigation tab inside EmptyWorkspaceState
    val activeTab = MutableStateFlow("home")

    fun changeActiveTab(tab: String) {
        activeTab.value = tab
    }

    fun togglePresenterMode(isPlaying: Boolean) {
        isPlayingPresentation.value = isPlaying
    }

    // Chat memory map per Doc ID (String) to list of ChatMessage instances
    private val _chatHistoryMap = MutableStateFlow<Map<String, List<com.example.ui.ChatMessage>>>(emptyMap())
    val chatHistoryMap = _chatHistoryMap.asStateFlow()

    fun getChatHistory(docId: String): List<com.example.ui.ChatMessage>? {
        return _chatHistoryMap.value[docId]
    }

    fun setChatHistory(docId: String, history: List<com.example.ui.ChatMessage>) {
        val current = _chatHistoryMap.value.toMutableMap()
        current[docId] = history
        _chatHistoryMap.value = current
    }

    fun clearChatHistory(docId: String) {
        val current = _chatHistoryMap.value.toMutableMap()
        current.remove(docId)
        _chatHistoryMap.value = current
    }

    fun selectDocument(doc: DocEntity?) {
        selectedDoc.value = doc
        draftTitle.value = doc?.title ?: ""
        
        val content = doc?.content ?: ""
        val sanitized = content
            .replace("\\\\u000C", "\u000C")
            .replace("\\\\u000c", "\u000C")
            .replace("\\u000C", "\u000C")
            .replace("\\u000c", "\u000C")
        draftContent.value = sanitized

        if (doc != null) {
            val layout = deserializeLayout(doc.layoutJson)
            pageFormat.value = layout["pageFormat"] ?: "Letter"
            val cw = layout["customWidth"]?.toFloatOrNull() ?: 8.5f
            val ch = layout["customHeight"]?.toFloatOrNull() ?: 11.0f
            customPageDimensions.value = Pair(cw, ch)
            isLandscape.value = layout["isLandscape"]?.toBoolean() ?: false
            pageMarginLeft.value = layout["pageMarginLeft"]?.toFloatOrNull() ?: 24f
            pageMarginTop.value = layout["pageMarginTop"]?.toFloatOrNull() ?: 24f
            pageMarginRight.value = layout["pageMarginRight"]?.toFloatOrNull() ?: 24f
            pageMarginBottom.value = layout["pageMarginBottom"]?.toFloatOrNull() ?: 24f
            columnCount.value = layout["columnCount"]?.toIntOrNull() ?: 1
            editorTheme.value = layout["editorTheme"] ?: "white"
            selectedDocumentTheme.value = layout["selectedDocumentTheme"] ?: "Office Classic"
            selectedThemeEffect.value = layout["selectedThemeEffect"] ?: "None"
            pageBackgroundColorHex.value = layout["pageBackgroundColorHex"] ?: ""
            watermarkText.value = layout["watermarkText"] ?: ""
            watermarkType.value = layout["watermarkType"] ?: "Diagonal"
            watermarkColorHex.value = layout["watermarkColorHex"] ?: "#33CCCCCC"
            pageBorderType.value = layout["pageBorderType"] ?: "None"
            pageBorderColorHex.value = layout["pageBorderColorHex"] ?: "default"
            fontSize.value = layout["fontSize"]?.toFloatOrNull() ?: 16f
            headerText.value = layout["headerText"] ?: ""
            footerText.value = layout["footerText"] ?: ""
            headerAlignment.value = layout["headerAlignment"] ?: "Center"
            footerAlignment.value = layout["footerAlignment"] ?: "Center"
            showHeaderFooterOnFirstPage.value = layout["showHeaderFooterOnFirstPage"]?.toBoolean() ?: true
            val rawPos = layout["pageNumberPosition"]
            pageNumberPosition.value = if (rawPos == "null") null else rawPos
            pageNumberFormat.value = layout["pageNumberFormat"] ?: "1, 2, 3..."
            pageNumberStartAt.value = layout["pageNumberStartAt"]?.toIntOrNull() ?: 1
            showPageNumberOnFirstPage.value = layout["showPageNumberOnFirstPage"]?.toBoolean() ?: true

            when (doc.type.lowercase()) {
                "sheet" -> {
                    sheetData.value = deserializeSheetData(doc.content)
                    selectedCell.value = null
                }
                "slide" -> {
                    slides.value = deserializeSlides(doc.content)
                    currentSlideIndex.value = 0
                }
            }
        } else {
            pageFormat.value = "Letter"
            customPageDimensions.value = Pair(8.5f, 11f)
            isLandscape.value = false
            pageMarginLeft.value = 24f
            pageMarginTop.value = 24f
            pageMarginRight.value = 24f
            pageMarginBottom.value = 24f
            columnCount.value = 1
            editorTheme.value = "white"
            selectedDocumentTheme.value = "Office Classic"
            selectedThemeEffect.value = "None"
            pageBackgroundColorHex.value = ""
            watermarkText.value = ""
            watermarkType.value = "Diagonal"
            watermarkColorHex.value = "#33CCCCCC"
            pageBorderType.value = "None"
            pageBorderColorHex.value = "default"
            fontSize.value = 16f
            headerText.value = ""
            footerText.value = ""
            headerAlignment.value = "Center"
            footerAlignment.value = "Center"
            showHeaderFooterOnFirstPage.value = true
            pageNumberPosition.value = null
            pageNumberFormat.value = "1, 2, 3..."
            pageNumberStartAt.value = 1
            showPageNumberOnFirstPage.value = true

            sheetData.value = emptyMap()
            selectedCell.value = null
            slides.value = emptyList()
            currentSlideIndex.value = 0
        }
    }

    fun updateDraftTitle(title: String) {
        draftTitle.value = title
        val cur = selectedDoc.value
        if (cur != null) {
            deleteExportedFile(cur)
            val updated = cur.copy(title = title, updatedAt = System.currentTimeMillis())
            selectedDoc.value = updated
            saveDocToDisk(updated)
        }
    }

    fun updateDraftContent(content: String) {
        val sanitized = content
            .replace("\\\\u000C", "\u000C")
            .replace("\\\\u000c", "\u000C")
            .replace("\\u000C", "\u000C")
            .replace("\\u000c", "\u000C")
        draftContent.value = sanitized
        val cur = selectedDoc.value
        if (cur != null) {
            val updated = cur.copy(content = sanitized, updatedAt = System.currentTimeMillis())
            selectedDoc.value = updated
            saveDocToDisk(updated)
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setTypeFilter(filter: String) {
        selectedTypeFilter.value = filter
    }

    fun deleteDocument(doc: DocEntity) {
        val updated = doc.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
        saveDocToDisk(updated)
        if (selectedDoc.value?.id == doc.id) {
            selectDocument(null)
        }
    }

    fun permanentlyDeleteDocument(doc: DocEntity) {
        deleteDocFromDisk(doc)
        if (doc.type.lowercase() == "pdf" && doc.content.isNotEmpty()) {
            try {
                val file = java.io.File(doc.content)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (selectedDoc.value?.id == doc.id) {
            selectDocument(null)
        }
    }

    fun restoreDocument(doc: DocEntity) {
        val updated = doc.copy(isDeleted = false, updatedAt = System.currentTimeMillis())
        saveDocToDisk(updated)
    }

    fun deleteFolderEntirely(folderName: String) {
        _allDocs.value.forEach { doc ->
            if (doc.folderName == folderName && !doc.isDeleted) {
                val updated = doc.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
                saveDocToDisk(updated)
            }
        }
        if (customFolders.value.contains(folderName)) {
            customFolders.value = customFolders.value.filter { it != folderName }
        }
        if (!deletedCustomFolders.value.contains(folderName)) {
            deletedCustomFolders.value = (deletedCustomFolders.value + folderName).sorted()
        }
        saveFoldersToPrefs()
    }

    fun restoreFolderEntirely(folderName: String) {
        _allDocs.value.forEach { doc ->
            if (doc.folderName == folderName && doc.isDeleted) {
                val updated = doc.copy(isDeleted = false, updatedAt = System.currentTimeMillis())
                saveDocToDisk(updated)
            }
        }
        if (deletedCustomFolders.value.contains(folderName)) {
            deletedCustomFolders.value = deletedCustomFolders.value.filter { it != folderName }
        }
        if (!customFolders.value.contains(folderName)) {
            customFolders.value = (customFolders.value + folderName).sorted()
        }
        saveFoldersToPrefs()
    }

    fun permanentlyDeleteFolder(folderName: String) {
        _allDocs.value.forEach { doc ->
            if (doc.folderName == folderName && doc.isDeleted) {
                permanentlyDeleteDocument(doc)
            }
        }
        if (deletedCustomFolders.value.contains(folderName)) {
            deletedCustomFolders.value = deletedCustomFolders.value.filter { it != folderName }
            saveFoldersToPrefs()
        }
    }

    fun toggleFavorite(doc: DocEntity) {
        val updated = doc.copy(isFavorite = !doc.isFavorite, updatedAt = System.currentTimeMillis())
        saveDocToDisk(updated)
        if (selectedDoc.value?.id == doc.id) {
            selectedDoc.value = updated
        }
    }

    fun renameDocument(doc: DocEntity, newTitle: String) {
        if (newTitle.isBlank() || doc.title == newTitle) return
        val updated = doc.copy(title = newTitle, updatedAt = System.currentTimeMillis())
        saveDocToDisk(updated)
        if (selectedDoc.value?.id == doc.id) {
            selectedDoc.value = updated
        }
    }

    fun createFolder(name: String) {
        if (name.isNotBlank() && !customFolders.value.contains(name)) {
            customFolders.value = (customFolders.value + name).sorted()
            saveFoldersToPrefs()
        }
    }

    fun toggleFolderFavorite(folderName: String) {
        val updatedFavs = favoriteFolders.value.toMutableSet()
        if (updatedFavs.contains(folderName)) {
            updatedFavs.remove(folderName)
        } else {
            updatedFavs.add(folderName)
        }
        favoriteFolders.value = updatedFavs
        saveFoldersToPrefs()
    }

    fun renameFolder(oldName: String, newName: String) {
        if (oldName == newName || newName.isBlank()) return
        
        // Update documents under old folder name
        _allDocs.value.forEach { doc ->
            if (doc.folderName == oldName) {
                val updated = doc.copy(folderName = newName, updatedAt = System.currentTimeMillis())
                saveDocToDisk(updated)
            }
        }
        
        // Update custom folders
        val updatedCustom = customFolders.value.map { if (it == oldName) newName else it }.distinct().sorted()
        customFolders.value = updatedCustom
        
        // Update favorite folders
        if (favoriteFolders.value.contains(oldName)) {
            val updatedFavs = favoriteFolders.value.toMutableSet()
            updatedFavs.remove(oldName)
            updatedFavs.add(newName)
            favoriteFolders.value = updatedFavs
        }
        
        saveFoldersToPrefs()
    }

    fun updateDocumentFolder(doc: DocEntity, folderName: String?) {
        deleteExportedFile(doc)
        val updated = doc.copy(folderName = folderName, updatedAt = System.currentTimeMillis())
        saveDocToDisk(updated)
        if (selectedDoc.value?.id == doc.id) {
            selectedDoc.value = updated
        }
    }

    fun createNewDocument(title: String, type: String, initialContent: String = "", folderName: String? = null) {
        viewModelScope.launch {
            val contentStr = when (type.lowercase()) {
                "sheet" -> initialContent
                "slide" -> if (initialContent.isNotBlank()) initialContent else serializeSlides(listOf(
                    SlideItem("Title Slide", "Double click to edit subtitle", "indigo", "title_slide")
                ))
                else -> initialContent
            }
            val newId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            val newDoc = DocEntity(
                id = newId,
                title = title.ifBlank { "Untitled ${type.uppercase()}" },
                type = type.lowercase(),
                content = contentStr,
                folderName = folderName,
                updatedAt = System.currentTimeMillis()
            )
            saveDocToDisk(newDoc)
            selectDocument(newDoc)
        }
    }

    fun setPageFormat(format: String) {
        val curId = selectedDoc.value?.id ?: 0
        updateLayoutInDb(
            docId = curId,
            pageFormat = format,
            customWidth = customPageDimensions.value.first,
            customHeight = customPageDimensions.value.second,
            isLandscape = isLandscape.value,
            marginLeft = pageMarginLeft.value,
            marginTop = pageMarginTop.value,
            marginRight = pageMarginRight.value,
            marginBottom = pageMarginBottom.value,
            columnCount = columnCount.value,
            editorTheme = editorTheme.value,
            selectedDocTheme = selectedDocumentTheme.value,
            selectedThemeEffect = selectedThemeEffect.value,
            pageBgColorHex = pageBackgroundColorHex.value,
            watermarkText = watermarkText.value,
            watermarkType = watermarkType.value,
            watermarkColorHex = watermarkColorHex.value,
            pageBorderType = pageBorderType.value,
            pageBorderColorHex = pageBorderColorHex.value
        )
    }

    fun setCustomPageDimensions(width: Float, height: Float) {
        val curId = selectedDoc.value?.id ?: 0
        updateLayoutInDb(
            docId = curId,
            pageFormat = pageFormat.value,
            customWidth = width,
            customHeight = height,
            isLandscape = isLandscape.value,
            marginLeft = pageMarginLeft.value,
            marginTop = pageMarginTop.value,
            marginRight = pageMarginRight.value,
            marginBottom = pageMarginBottom.value,
            columnCount = columnCount.value,
            editorTheme = editorTheme.value,
            selectedDocTheme = selectedDocumentTheme.value,
            selectedThemeEffect = selectedThemeEffect.value,
            pageBgColorHex = pageBackgroundColorHex.value,
            watermarkText = watermarkText.value,
            watermarkType = watermarkType.value,
            watermarkColorHex = watermarkColorHex.value,
            pageBorderType = pageBorderType.value,
            pageBorderColorHex = pageBorderColorHex.value
        )
    }

    fun selectCell(cell: String?) {
        selectedCell.value = cell
    }

    fun updateCellExpression(cell: String?, expression: String) {
        if (cell != null) {
            val updatedMap = sheetData.value.toMutableMap()
            updatedMap[cell] = expression
            sheetData.value = updatedMap
            val curDoc = selectedDoc.value
            if (curDoc != null && curDoc.type == "sheet") {
                val serialized = serializeSheetData(updatedMap)
                val updatedDoc = curDoc.copy(content = serialized, updatedAt = System.currentTimeMillis())
                selectedDoc.value = updatedDoc
                saveDocToDisk(updatedDoc)
            }
        }
    }

    fun getCellValue(cellRef: String): String {
        val expr = sheetData.value[cellRef] ?: return ""
        if (expr.startsWith("=")) {
            val rawFormula = expr.substring(1).trim().uppercase()
            if (rawFormula.startsWith("SUM(")) {
                val range = rawFormula.removePrefix("SUM(").removeSuffix(")")
                return evaluateSum(range).toString()
            }
            if (rawFormula.startsWith("AVERAGE(")) {
                val range = rawFormula.removePrefix("AVERAGE(").removeSuffix(")")
                val (sum, count) = evaluateSumAndCount(range)
                return if (count > 0) String.format("%.2f", sum / count) else "0"
            }
            return expr
        }
        return expr
    }

    private fun getCellsInRange(range: String): List<String> {
        val pts = range.split(":")
        if (pts.size != 2) return emptyList()
        val start = pts[0].trim()
        val end = pts[1].trim()
        val startCol = start.firstOrNull { it.isLetter() } ?: return emptyList()
        val startRow = start.filter { it.isDigit() }.toIntOrNull() ?: return emptyList()
        val endCol = end.firstOrNull { it.isLetter() } ?: return emptyList()
        val endRow = end.filter { it.isDigit() }.toIntOrNull() ?: return emptyList()

        val list = mutableListOf<String>()
        val colStart = minOf(startCol.code, endCol.code)
        val colEnd = maxOf(startCol.code, endCol.code)
        val rowStart = minOf(startRow, endRow)
        val rowEnd = maxOf(startRow, endRow)

        for (c in colStart..colEnd) {
            for (r in rowStart..rowEnd) {
                list.add("${c.toChar()}$r")
            }
        }
        return list
    }

    private fun evaluateSum(range: String): Double {
        var sum = 0.0
        getCellsInRange(range).forEach { cell ->
            val v = sheetData.value[cell] ?: "0"
            sum += v.toDoubleOrNull() ?: 0.0
        }
        return sum
    }

    private fun evaluateSumAndCount(range: String): Pair<Double, Int> {
        var sum = 0.0
        var count = 0
        getCellsInRange(range).forEach { cell ->
            val v = sheetData.value[cell] ?: "0"
            sum += v.toDoubleOrNull() ?: 0.0
            count++
        }
        return Pair(sum, count)
    }

    // slides logic
    fun addNewSlide() {
        val updatedSlides = slides.value.toMutableList()
        updatedSlides.add(SlideItem("New Slide", "Double click to edit text", "indigo", "title_body"))
        slides.value = updatedSlides
        saveSlidesToDoc(updatedSlides)
        currentSlideIndex.value = updatedSlides.size - 1
    }

    fun deleteSlide(index: Int) {
        val updatedSlides = slides.value.toMutableList()
        if (updatedSlides.size > 1 && index in updatedSlides.indices) {
            updatedSlides.removeAt(index)
            slides.value = updatedSlides
            saveSlidesToDoc(updatedSlides)
            currentSlideIndex.value = maxOf(0, index - 1)
        }
    }

    fun selectSlide(index: Int) {
        if (index in slides.value.indices) {
            currentSlideIndex.value = index
        }
    }

    fun updateSlideContent(title: String, body: String, theme: String, layout: String) {
        val idx = currentSlideIndex.value
        val updatedSlides = slides.value.toMutableList()
        if (idx in updatedSlides.indices) {
            updatedSlides[idx] = SlideItem(title, body, theme, layout)
            slides.value = updatedSlides
            saveSlidesToDoc(updatedSlides)
        }
    }

    private fun saveSlidesToDoc(list: List<SlideItem>) {
        val curDoc = selectedDoc.value
        if (curDoc != null && curDoc.type == "slide") {
            val serialized = serializeSlides(list)
            val updatedDoc = curDoc.copy(content = serialized, updatedAt = System.currentTimeMillis())
            selectedDoc.value = updatedDoc
            saveDocToDisk(updatedDoc)
        }
    }

    // Helper functions
    private fun serializeSheetData(data: Map<String, String>): String {
        return data.entries.joinToString(";;") { "${it.key}=${it.value}" }
    }

    private fun deserializeSheetData(content: String): Map<String, String> {
        if (content.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        try {
            content.split(";;").forEach { entry ->
                val pts = entry.split("=")
                if (pts.size >= 2) {
                    map[pts[0]] = pts.subList(1, pts.size).joinToString("=")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private fun serializeSlides(slides: List<SlideItem>): String {
        return slides.joinToString(";;;") { "${it.title}||${it.body}||${it.theme}||${it.layout}" }
    }

    private fun deserializeSlides(content: String): List<SlideItem> {
        if (content.isBlank()) {
            return listOf(
                SlideItem("Title Slide", "Double click to edit subtitle", "indigo", "title_slide"),
                SlideItem("Overview", "1. Beautiful styles\n2. Modern presentation\n3. High-fidelity layouts", "orange", "title_body")
            )
        }
        val list = mutableListOf<SlideItem>()
        try {
            content.split(";;;").forEach { entry ->
                val pts = entry.split("||")
                if (pts.size == 4) {
                    list.add(SlideItem(pts[0], pts[1], pts[2], pts[3]))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (list.isEmpty()) {
            list.add(SlideItem("Title Slide", "Double click to edit subtitle", "indigo", "title_slide"))
        }
        return list
    }

    private fun serializeLayout(
        pageFormat: String,
        customWidth: Float,
        customHeight: Float,
        isLandscape: Boolean,
        marginLeft: Float,
        marginTop: Float,
        marginRight: Float,
        marginBottom: Float,
        columnCount: Int,
        editorTheme: String,
        selectedDocTheme: String,
        selectedThemeEffect: String,
        pageBgColorHex: String,
        watermarkText: String,
        watermarkType: String,
        watermarkColorHex: String,
        pageBorderType: String,
        pageBorderColorHex: String,
        fontSize: Float,
        headerText: String,
        footerText: String,
        headerAlignment: String,
        footerAlignment: String,
        showHeaderFooterOnFirstPage: Boolean,
        pageNumberPosition: String?,
        pageNumberFormat: String,
        pageNumberStartAt: Int,
        showPageNumberOnFirstPage: Boolean
    ): String {
        try {
            val jsonObject = JSONObject()
            jsonObject.put("pageFormat", pageFormat)
            jsonObject.put("customWidth", customWidth.toDouble())
            jsonObject.put("customHeight", customHeight.toDouble())
            jsonObject.put("isLandscape", isLandscape)
            jsonObject.put("pageMarginLeft", marginLeft.toDouble())
            jsonObject.put("pageMarginTop", marginTop.toDouble())
            jsonObject.put("pageMarginRight", marginRight.toDouble())
            jsonObject.put("pageMarginBottom", marginBottom.toDouble())
            jsonObject.put("columnCount", columnCount)
            jsonObject.put("editorTheme", editorTheme)
            jsonObject.put("selectedDocumentTheme", selectedDocTheme)
            jsonObject.put("selectedThemeEffect", selectedThemeEffect)
            jsonObject.put("pageBackgroundColorHex", pageBgColorHex)
            jsonObject.put("watermarkText", watermarkText)
            jsonObject.put("watermarkType", watermarkType)
            jsonObject.put("watermarkColorHex", watermarkColorHex)
            jsonObject.put("pageBorderType", pageBorderType)
            jsonObject.put("pageBorderColorHex", pageBorderColorHex)
            jsonObject.put("fontSize", fontSize.toDouble())
            jsonObject.put("headerText", headerText)
            jsonObject.put("footerText", footerText)
            jsonObject.put("headerAlignment", headerAlignment)
            jsonObject.put("footerAlignment", footerAlignment)
            jsonObject.put("showHeaderFooterOnFirstPage", showHeaderFooterOnFirstPage)
            jsonObject.put("pageNumberPosition", pageNumberPosition ?: JSONObject.NULL)
            jsonObject.put("pageNumberFormat", pageNumberFormat)
            jsonObject.put("pageNumberStartAt", pageNumberStartAt)
            jsonObject.put("showPageNumberOnFirstPage", showPageNumberOnFirstPage)
            return jsonObject.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return "{}"
        }
    }

    fun deserializeLayout(json: String?): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (json.isNullOrBlank()) return map
        try {
            val jsonObject = JSONObject(json)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObject.opt(key)
                if (value != null && value != JSONObject.NULL) {
                    map[key] = value.toString()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    fun updateLayoutInDb(
        docId: Int,
        pageFormat: String,
        customWidth: Float,
        customHeight: Float,
        isLandscape: Boolean,
        marginLeft: Float,
        marginTop: Float,
        marginRight: Float,
        marginBottom: Float,
        columnCount: Int,
        editorTheme: String,
        selectedDocTheme: String,
        selectedThemeEffect: String,
        pageBgColorHex: String,
        watermarkText: String,
        watermarkType: String,
        watermarkColorHex: String,
        pageBorderType: String,
        pageBorderColorHex: String
    ) {
        this.pageFormat.value = pageFormat
        this.customPageDimensions.value = Pair(customWidth, customHeight)
        this.isLandscape.value = isLandscape
        this.pageMarginLeft.value = marginLeft
        this.pageMarginTop.value = marginTop
        this.pageMarginRight.value = marginRight
        this.pageMarginBottom.value = marginBottom
        this.columnCount.value = columnCount
        this.editorTheme.value = editorTheme
        this.selectedDocumentTheme.value = selectedDocTheme
        this.selectedThemeEffect.value = selectedThemeEffect
        this.pageBackgroundColorHex.value = pageBgColorHex
        this.watermarkText.value = watermarkText
        this.watermarkType.value = watermarkType
        this.watermarkColorHex.value = watermarkColorHex
        this.pageBorderType.value = pageBorderType
        this.pageBorderColorHex.value = pageBorderColorHex

        saveLayoutToDb()
    }

    private fun saveLayoutToDb() {
        val curDoc = selectedDoc.value
        if (curDoc != null) {
            val newJson = serializeLayout(
                pageFormat = pageFormat.value,
                customWidth = customPageDimensions.value.first,
                customHeight = customPageDimensions.value.second,
                isLandscape = isLandscape.value,
                marginLeft = pageMarginLeft.value,
                marginTop = pageMarginTop.value,
                marginRight = pageMarginRight.value,
                marginBottom = pageMarginBottom.value,
                columnCount = columnCount.value,
                editorTheme = editorTheme.value,
                selectedDocTheme = selectedDocumentTheme.value,
                selectedThemeEffect = selectedThemeEffect.value,
                pageBgColorHex = pageBackgroundColorHex.value,
                watermarkText = watermarkText.value,
                watermarkType = watermarkType.value,
                watermarkColorHex = watermarkColorHex.value,
                pageBorderType = pageBorderType.value,
                pageBorderColorHex = pageBorderColorHex.value,
                fontSize = fontSize.value,
                headerText = headerText.value,
                footerText = footerText.value,
                headerAlignment = headerAlignment.value,
                footerAlignment = footerAlignment.value,
                showHeaderFooterOnFirstPage = showHeaderFooterOnFirstPage.value,
                pageNumberPosition = pageNumberPosition.value,
                pageNumberFormat = pageNumberFormat.value,
                pageNumberStartAt = pageNumberStartAt.value,
                showPageNumberOnFirstPage = showPageNumberOnFirstPage.value
            )

            if (curDoc.layoutJson == newJson) return

            val updatedDoc = curDoc.copy(layoutJson = newJson, updatedAt = System.currentTimeMillis())
            selectedDoc.value = updatedDoc
            saveDocToDisk(updatedDoc)
        }
    }

    fun setIsLandscape(landscape: Boolean) {
        isLandscape.value = landscape
        saveLayoutToDb()
    }

    fun setPageMargins(left: Float, top: Float, right: Float, bottom: Float) {
        pageMarginLeft.value = left
        pageMarginTop.value = top
        pageMarginRight.value = right
        pageMarginBottom.value = bottom
        saveLayoutToDb()
    }

    fun setColumnCount(count: Int) {
        columnCount.value = count
        saveLayoutToDb()
    }

    fun setEditorTheme(theme: String) {
        editorTheme.value = theme
        saveLayoutToDb()
    }

    fun setSelectedDocumentTheme(theme: String) {
        selectedDocumentTheme.value = theme
        saveLayoutToDb()
    }

    fun setSelectedThemeEffect(effect: String) {
        selectedThemeEffect.value = effect
        saveLayoutToDb()
    }

    fun setPageBackgroundColorHex(colorHex: String) {
        pageBackgroundColorHex.value = colorHex
        saveLayoutToDb()
    }

    fun setWatermark(text: String, type: String, colorHex: String) {
        watermarkText.value = text
        watermarkType.value = type
        watermarkColorHex.value = colorHex
        saveLayoutToDb()
    }

    fun setPageBorder(type: String, colorHex: String) {
        pageBorderType.value = type
        pageBorderColorHex.value = colorHex
        saveLayoutToDb()
    }

    fun setFontSize(size: Float) {
        fontSize.value = size
        saveLayoutToDb()
    }

    fun setHeaderFooter(header: String, footer: String, hAlign: String, fAlign: String, showOnFirst: Boolean) {
        headerText.value = header
        footerText.value = footer
        headerAlignment.value = hAlign
        footerAlignment.value = fAlign
        showHeaderFooterOnFirstPage.value = showOnFirst
        saveLayoutToDb()
    }

    fun setPageNumbering(position: String?, format: String, startAt: Int, showOnFirst: Boolean) {
        pageNumberPosition.value = position
        pageNumberFormat.value = format
        pageNumberStartAt.value = startAt
        showPageNumberOnFirstPage.value = showOnFirst
        saveLayoutToDb()
    }

    fun getStorageDir(): java.io.File {
        // All backend data of our app will be inside the standard "Android" folder of the user's storage
        val dir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Android/data/${context.packageName}/backend_data")
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (dir.exists() && dir.canWrite()) {
            return dir
        } else {
            val fallback = java.io.File(context.getExternalFilesDir(null), "backend_data")
            try {
                if (!fallback.exists()) {
                    fallback.mkdirs()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return fallback
        }
    }

    fun getDownloadStorageDir(): java.io.File {
        // All folders and files that user creates are stored under the "Download/JCdocs" folder
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val dir = java.io.File(downloadsDir, "JCdocs")
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return dir
    }

    fun getExportedFileForDoc(doc: DocEntity): java.io.File {
        val jcDocsDownloadDir = getDownloadStorageDir()
        val folder = doc.folderName ?: ""
        val targetDir = if (folder.isNotBlank()) {
            java.io.File(jcDocsDownloadDir, folder)
        } else {
            jcDocsDownloadDir
        }
        try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val extension = when (doc.type.lowercase()) {
            "sheet" -> "csv"
            "slide" -> "txt"
            "pdf" -> "pdf"
            else -> "txt" // word & others
        }
        
        val safeTitle = doc.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Untitled" }
        return java.io.File(targetDir, "$safeTitle.$extension")
    }

    fun deleteExportedFile(doc: DocEntity) {
        try {
            val file = getExportedFileForDoc(doc)
            val path = file.absolutePath
            if (file.exists()) {
                file.delete()
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(path),
                    null,
                    null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSheetAsCsvString(docContent: String): String {
        val data = deserializeSheetData(docContent)
        if (data.isEmpty()) return ""
        
        var maxRow = 1
        var maxColChar = 'A'
        data.keys.forEach { cellRef ->
            val col = cellRef.firstOrNull { it.isLetter() } ?: 'A'
            val row = cellRef.filter { it.isDigit() }.toIntOrNull() ?: 1
            if (row > maxRow) maxRow = row
            if (col > maxColChar) maxColChar = col
        }
        
        val sb = java.lang.StringBuilder()
        for (r in 1..maxRow) {
            val rowList = mutableListOf<String>()
            for (c in 'A'..maxColChar) {
                val cellRef = "$c$r"
                val rawValue = data[cellRef] ?: ""
                val escapedValue = if (rawValue.contains(",") || rawValue.contains("\n") || rawValue.contains("\"")) {
                    "\"" + rawValue.replace("\"", "\"\"") + "\""
                } else {
                    rawValue
                }
                rowList.add(escapedValue)
            }
            sb.append(rowList.joinToString(",")).append("\n")
        }
        return sb.toString()
    }

    fun getSlidesAsFormattedText(docContent: String): String {
        val list = deserializeSlides(docContent)
        val sb = java.lang.StringBuilder()
        list.forEachIndexed { index, slide ->
            sb.append("--- Slide ${index + 1} (${slide.theme.uppercase()}, ${slide.layout.uppercase()}) ---\n")
            sb.append("Title: ${slide.title}\n")
            sb.append("Body:\n${slide.body}\n")
            sb.append("\n")
        }
        return sb.toString()
    }

    fun writeExportedFile(doc: DocEntity) {
        try {
            val file = getExportedFileForDoc(doc)
            val parentFile = file.parentFile
            if (parentFile != null && !parentFile.exists()) {
                parentFile.mkdirs()
            }
            
            when (doc.type.lowercase()) {
                "sheet" -> {
                    val csvStr = getSheetAsCsvString(doc.content)
                    file.writeText(csvStr)
                }
                "slide" -> {
                    val slideStr = getSlidesAsFormattedText(doc.content)
                    file.writeText(slideStr)
                }
                "pdf" -> {
                    val srcFile = java.io.File(doc.content)
                    if (srcFile.exists() && srcFile.absolutePath != file.absolutePath) {
                        srcFile.inputStream().use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                else -> {
                    file.writeText(doc.content)
                }
            }
            if (file.exists()) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    null,
                    null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadDocsFromDisk() {
        viewModelScope.launch {
            val docs = mutableListOf<DocEntity>()
            try {
                val dir = getStorageDir()
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles { _, name -> name.endsWith(".json") }
                    files?.forEach { file ->
                        try {
                            val jsonStr = file.readText()
                            val json = JSONObject(jsonStr)
                            val id = json.optInt("id", file.nameWithoutExtension.removePrefix("doc_").toIntOrNull() ?: file.hashCode())
                            val title = json.optString("title", "Untitled")
                            val type = json.optString("type", "word")
                            val content = json.optString("content", "")
                            val isFavorite = json.optBoolean("isFavorite", false)
                            val updatedAt = json.optLong("updatedAt", file.lastModified())
                            val layoutJson = json.optString("layoutJson", "")
                            val folderName = if (json.isNull("folderName")) null else json.optString("folderName", null)
                            val isDeleted = json.optBoolean("isDeleted", false)
                            
                            docs.add(DocEntity(id, title, type, content, isFavorite, updatedAt, layoutJson, folderName, isDeleted))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _allDocs.value = docs.sortedByDescending { it.updatedAt }
        }
    }

    fun saveDocToDisk(doc: DocEntity) {
        viewModelScope.launch {
            try {
                val dir = getStorageDir()
                val file = java.io.File(dir, "doc_${doc.id}.json")
                val json = JSONObject()
                json.put("id", doc.id)
                json.put("title", doc.title)
                json.put("type", doc.type)
                json.put("content", doc.content)
                json.put("isFavorite", doc.isFavorite)
                json.put("updatedAt", doc.updatedAt)
                json.put("layoutJson", doc.layoutJson)
                if (doc.folderName == null) {
                    json.put("folderName", JSONObject.NULL)
                } else {
                    json.put("folderName", doc.folderName)
                }
                json.put("isDeleted", doc.isDeleted)
                file.writeText(json.toString())
                
                // Write/sync the user-created organized file to Download/JCdocs if it's not deleted
                if (doc.isDeleted) {
                    deleteExportedFile(doc)
                } else {
                    writeExportedFile(doc)
                }

                val currentList = _allDocs.value.filter { it.id != doc.id }.toMutableList()
                currentList.add(doc)
                _allDocs.value = currentList.sortedByDescending { it.updatedAt }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteDocFromDisk(doc: DocEntity) {
        viewModelScope.launch {
            try {
                val dir = getStorageDir()
                val file = java.io.File(dir, "doc_${doc.id}.json")
                if (file.exists()) {
                    file.delete()
                }
                
                // Also delete the user-created file from Download/JCdocs
                deleteExportedFile(doc)
                
                _allDocs.value = _allDocs.value.filter { it.id != doc.id }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
