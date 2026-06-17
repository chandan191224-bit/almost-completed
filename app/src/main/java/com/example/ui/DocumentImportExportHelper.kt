package com.example.ui

import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.zip.Inflater
import java.util.regex.Pattern
import android.graphics.Paint
object DocumentImportExportHelper {

    fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val width = paint.measureText(testLine)
            if (width <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) "" else " ").append(word)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    fun parseDocxText(inputStream: InputStream): String {
        try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            var xmlContent = ""
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xmlContent = zip.reader(Charsets.UTF_8).readText()
                    break
                }
                entry = zip.nextEntry
            }
            zip.close()

            if (xmlContent.isEmpty()) return ""

            val sb = java.lang.StringBuilder()
            var pos = 0
            val len = xmlContent.length
            while (pos < len) {
                val openAngle = xmlContent.indexOf('<', pos)
                if (openAngle == -1) {
                    break
                }
                
                pos = openAngle
                val closeAngle = xmlContent.indexOf('>', pos)
                if (closeAngle == -1) {
                    break
                }
                
                val tag = xmlContent.substring(pos, closeAngle + 1)
                if (tag.startsWith("<w:t") && !tag.endsWith("/>")) {
                    val endTagIdx = xmlContent.indexOf("</w:t>", closeAngle + 1)
                    if (endTagIdx != -1) {
                        val text = xmlContent.substring(closeAngle + 1, endTagIdx)
                        sb.append(unescapeXml(text))
                        pos = endTagIdx + 6
                    } else {
                        pos = closeAngle + 1
                    }
                } else if (tag == "</w:p>" || tag == "<w:br/>" || tag == "<w:cr/>") {
                    sb.append("\n")
                    pos = closeAngle + 1
                } else {
                    pos = closeAngle + 1
                }
            }
            
            return prePaginateText(sb.toString().trim())
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    sealed interface PageDocElement {
        val y: Float
    }
    
    data class TextLineElement(
        val line: String,
        val lineStartAbs: Int,
        val lineEndAbs: Int,
        override val y: Float
    ) : PageDocElement

    data class PictureElement(
        val pic: DocPicture,
        override val y: Float
    ) : PageDocElement

    data class ShapeElement(
        val shape: DocShape,
        override val y: Float
    ) : PageDocElement

    data class TableElement(
        val table: DocTable,
        override val y: Float
    ) : PageDocElement

    private fun formatPageNumber(pageNumber: Int, format: String, totalPages: Int = 1): String {
        val formattedBase = when {
            format.contains("01") -> String.format("%02d", pageNumber)
            format.contains("001") -> String.format("%03d", pageNumber)
            format.contains("I") -> toRoman(pageNumber)
            format.contains("i") -> toRoman(pageNumber).lowercase()
            format.contains("A") -> toAlphabetic(pageNumber)
            format.contains("a") -> toAlphabetic(pageNumber).lowercase()
            else -> pageNumber.toString()
        }
        
        return when {
            format.contains("- 1 -") || format.startsWith("-") -> "- $formattedBase -"
            format.contains("Page X of Y") -> "Page $formattedBase of $totalPages"
            format.contains("Page X") -> "Page $formattedBase"
            else -> formattedBase
        }
    }

    private fun toRoman(num: Int): String {
        val romanMap = mapOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
        var temp = num
        val sb = java.lang.StringBuilder()
        for ((k, v) in romanMap) {
            while (temp >= k) {
                sb.append(v)
                temp -= k
            }
        }
        return sb.toString()
    }

    private fun toAlphabetic(num: Int): String {
        val sb = java.lang.StringBuilder()
        var temp = num - 1
        while (temp >= 0) {
            sb.append(('A'.code + (temp % 26)).toChar())
            temp = (temp / 26) - 1
        }
        return sb.reverse().toString()
    }

    fun generateDocx(
        title: String,
        content: String,
        outputStream: OutputStream,
        pageFormat: String = "Letter",
        customWidth: Float = 8.5f,
        customHeight: Float = 11.0f,
        isLandscape: Boolean = false,
        marginLeft: Float = 24f,
        marginTop: Float = 24f,
        marginRight: Float = 24f,
        marginBottom: Float = 24f,
        columnCount: Int = 1,
        fontSize: Float = 16f,
        spans: List<DocFormatSpan> = emptyList(),
        pictures: List<DocPicture> = emptyList(),
        shapes: List<DocShape> = emptyList(),
        tables: List<DocTable> = emptyList(),
        pageBackgroundColorHex: String = "",
        pageBorderType: String = "None",
        pageBorderColorHex: String = "",
        watermarkText: String = "",
        watermarkColorHex: String = "",
        watermarkType: String = "",
        headerText: String = "",
        footerText: String = "",
        headerAlignment: String = "Left",
        footerAlignment: String = "Center",
        pageNumberStartAt: Int = 1,
        pageNumberFormat: String = "1",
        showPageNumberOnFirstPage: Boolean = true,
        showHeaderFooterOnFirstPage: Boolean = true,
        pageNumberPosition: String? = null,
        loadImage: ((String) -> android.graphics.Bitmap?)? = null
    ) {
        val zip = ZipOutputStream(outputStream)
        
        val imageBytesMap = mutableMapOf<String, ByteArray>()
        val uriToRelId = mutableMapOf<String, String>()
        
        var imgIndex = 1
        pictures.forEach { pic ->
            if (!uriToRelId.containsKey(pic.uri)) {
                val bitmap = loadImage?.invoke(pic.uri)
                if (bitmap != null) {
                    val bos = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
                    val bytes = bos.toByteArray()
                    val relId = "rIdImg$imgIndex"
                    uriToRelId[pic.uri] = relId
                    imageBytesMap[pic.uri] = bytes
                    imgIndex++
                }
            }
        }

        // 1. [Content_Types].xml
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        val contentTypesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Default Extension="png" ContentType="image/png"/>
                <Default Extension="jpeg" ContentType="image/jpeg"/>
                <Default Extension="jpg" ContentType="image/jpeg"/>
                <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
        """.trimIndent()
        zip.write(contentTypesXml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // 2. _rels/.rels
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        val relsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()
        zip.write(relsXml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // Write word/media/imageX.png
        imageBytesMap.forEach { (uri, bytes) ->
            val relId = uriToRelId[uri] ?: return@forEach
            val idx = relId.removePrefix("rIdImg")
            zip.putNextEntry(ZipEntry("word/media/image$idx.png"))
            zip.write(bytes)
            zip.closeEntry()
        }

        // Write document.xml relationships
        zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
        val docRelsBuilder = StringBuilder()
        docRelsBuilder.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        docRelsBuilder.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        imageBytesMap.forEach { (uri, _) ->
            val relId = uriToRelId[uri] ?: return@forEach
            val idx = relId.removePrefix("rIdImg")
            docRelsBuilder.append("""<Relationship Id="$relId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image$idx.png"/>""")
        }
        docRelsBuilder.append("""</Relationships>""")
        zip.write(docRelsBuilder.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // 3. word/document.xml
        zip.putNextEntry(ZipEntry("word/document.xml"))
        val docXmlBuilder = StringBuilder()
        val namespacesDeclaration = """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/wordprocessingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture" xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape" xmlns:wpc="http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas">"""
        docXmlBuilder.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append(namespacesDeclaration)
        
        if (pageBackgroundColorHex.isNotEmpty() && pageBackgroundColorHex != "default") {
            val bgCln = pageBackgroundColorHex.replace("#", "")
            docXmlBuilder.append("<w:background w:color=\"$bgCln\"/>")
        }

        docXmlBuilder.append("<w:body>")
        
        val pagesList = content.split("\u000C")
        
        pagesList.forEachIndexed { pageIndex, pageContent ->
            val shouldShowPageNum = showPageNumberOnFirstPage || pageIndex > 0
            val shouldShowHeaderFooter = showHeaderFooterOnFirstPage || pageIndex > 0
            val shouldShowHeader = shouldShowHeaderFooter && (headerText.isNotEmpty() || (shouldShowPageNum && pageNumberPosition?.startsWith("Top") == true))
            val shouldShowFooter = shouldShowHeaderFooter && (footerText.isNotEmpty() || (shouldShowPageNum && pageNumberPosition?.startsWith("Bottom") == true))

            if (shouldShowHeader) {
                val computedPageNumber = formatPageNumber(pageIndex + pageNumberStartAt, pageNumberFormat, pagesList.size)
                val headerTextToShow = when (headerAlignment) {
                    "Left" -> headerText + (if (shouldShowPageNum && pageNumberPosition != null && pageNumberPosition.startsWith("Top") && pageNumberPosition.contains("Left")) "   $computedPageNumber" else "")
                    "Center" -> headerText + (if (shouldShowPageNum && pageNumberPosition != null && pageNumberPosition.startsWith("Top") && !pageNumberPosition.contains("Left") && !pageNumberPosition.contains("Right")) "   $computedPageNumber" else "")
                    "Right" -> headerText + (if (shouldShowPageNum && pageNumberPosition != null && pageNumberPosition.startsWith("Top") && pageNumberPosition.contains("Right")) "   $computedPageNumber" else "")
                    else -> headerText
                }
                
                val jcVal = when (headerAlignment) {
                    "Center" -> "center"
                    "Right" -> "right"
                    else -> "left"
                }
                
                docXmlBuilder.append("<w:p>")
                docXmlBuilder.append("<w:pPr>")
                if (jcVal != "left") {
                    docXmlBuilder.append("<w:jc w:val=\"$jcVal\"/>")
                }
                docXmlBuilder.append("""<w:pBdr><w:bottom w:val="single" w:sz="6" w:space="4" w:color="D3D3D3"/></w:pBdr>""")
                docXmlBuilder.append("</w:pPr>")
                docXmlBuilder.append("<w:r><w:rPr><w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/><w:color w:val=\"808080\"/><w:b/></w:rPr><w:t>${escapeXml(headerTextToShow)}</w:t></w:r>")
                docXmlBuilder.append("</w:p>")
            }

            if (watermarkText.isNotEmpty()) {
                val watermarkColorClean = if (watermarkColorHex.startsWith("#")) watermarkColorHex.replace("#", "") else watermarkColorHex
                val clr = if (watermarkColorClean == "default" || watermarkColorClean.isEmpty()) "E0E0E0" else watermarkColorClean
                docXmlBuilder.append("<w:p>")
                docXmlBuilder.append("<w:pPr><w:jc w:val=\"center\"/></w:pPr>")
                docXmlBuilder.append("<w:r><w:rPr><w:sz w:val=\"120\"/><w:szCs w:val=\"120\"/><w:color w:val=\"$clr\"/><w:b/></w:rPr><w:t>${escapeXml(watermarkText)}</w:t></w:r>")
                docXmlBuilder.append("</w:p>")
            }

            val pageElements = mutableListOf<PageDocElement>()
            
            val lines = pageContent.split("\n")
            var currentAbsoluteOffset = pagesList.take(pageIndex).sumOf { it.length + 1 }
            
            lines.forEachIndexed { lineIdx, line ->
                val lineLength = line.length
                val lineStart = currentAbsoluteOffset
                val lineEnd = lineStart + lineLength
                
                val lineY = marginTop + (lineIdx * (fontSize * 1.5f))
                pageElements.add(
                    TextLineElement(
                        line = line,
                        lineStartAbs = lineStart,
                        lineEndAbs = lineEnd,
                        y = lineY
                    )
                )
                currentAbsoluteOffset += lineLength + 1
            }

            pictures.filter { it.pageIndex == pageIndex }.forEach { pic ->
                pageElements.add(PictureElement(pic, pic.y.value))
            }
            shapes.filter { it.pageIndex == pageIndex }.forEach { shp ->
                pageElements.add(ShapeElement(shp, shp.y.value))
            }
            tables.filter { it.pageIndex == pageIndex }.forEach { tbl ->
                pageElements.add(TableElement(tbl, tbl.y.value))
            }

            pageElements.sortBy { it.y }

            pageElements.forEach { element ->
                when (element) {
                    is TextLineElement -> {
                        val line = element.line
                        val lineLength = line.length
                        val lineStart = element.lineStartAbs
                        val lineEnd = element.lineEndAbs
                        val lineSpans = spans.filter { it.start < lineEnd && it.end > lineStart }
                        
                        var alignmentVal = "left"
                        val alignSpan = lineSpans.firstOrNull { it.type == "alignment" }
                        if (alignSpan != null) {
                            alignmentVal = alignSpan.value
                        }
                        val jcVal = when (alignmentVal) {
                            "center" -> "center"
                            "right" -> "right"
                            "justify" -> "both"
                            else -> "left"
                        }

                        docXmlBuilder.append("<w:p>")
                        docXmlBuilder.append("<w:pPr>")
                        if (jcVal != "left") {
                            docXmlBuilder.append("<w:jc w:val=\"$jcVal\"/>")
                        }
                        docXmlBuilder.append("</w:pPr>")

                        if (line.isEmpty()) {
                            val fontHalfPoints = (fontSize * 2).toInt()
                            val rPr = "<w:rPr><w:sz w:val=\"$fontHalfPoints\"/><w:szCs w:val=\"$fontHalfPoints\"/></w:rPr>"
                            docXmlBuilder.append("<w:r>$rPr<w:t></w:t></w:r>")
                        } else {
                            val boundaries = java.util.TreeSet<Int>()
                            boundaries.add(0)
                            boundaries.add(lineLength)
                            lineSpans.forEach { span ->
                                val relStart = (span.start - lineStart).coerceIn(0, lineLength)
                                val relEnd = (span.end - lineStart).coerceIn(0, lineLength)
                                boundaries.add(relStart)
                                boundaries.add(relEnd)
                            }
                            
                            val boundaryList = boundaries.toList()
                            for (i in 0 until boundaryList.size - 1) {
                                val sIdx = boundaryList[i]
                                val eIdx = boundaryList[i + 1]
                                if (sIdx == eIdx) continue
                                val subText = line.substring(sIdx, eIdx)
                                val escapedText = escapeXml(subText)
                                
                                val absMidPoint = lineStart + sIdx
                                val activeSpans = lineSpans.filter { absMidPoint >= it.start && absMidPoint < it.end }
                                
                                val isBold = activeSpans.any { it.type == "bold" }
                                val isItalic = activeSpans.any { it.type == "italic" }
                                val isUnderline = activeSpans.any { it.type == "underline" }
                                val isStrikethrough = activeSpans.any { it.type == "strikethrough" }
                                val colorSpan = activeSpans.firstOrNull { it.type == "color" }
                                val fontSizeSpan = activeSpans.firstOrNull { it.type == "fontSize" }
                                
                                val runProps = StringBuilder()
                                runProps.append("<w:rPr>")
                                
                                val runFontSize = fontSizeSpan?.value?.toFloatOrNull() ?: fontSize
                                val runHalfPoints = (runFontSize * 2).toInt()
                                runProps.append("<w:sz w:val=\"$runHalfPoints\"/><w:szCs w:val=\"$runHalfPoints\"/>")
                                
                                if (isBold) runProps.append("<w:b/>")
                                if (isItalic) runProps.append("<w:i/>")
                                if (isUnderline) runProps.append("<w:u w:val=\"single\"/>")
                                if (isStrikethrough) runProps.append("<w:strike/>")
                                if (colorSpan != null) {
                                    val cVal = colorSpan.value.replace("#", "")
                                    runProps.append("<w:color w:val=\"$cVal\"/>")
                                }
                                
                                runProps.append("</w:rPr>")
                                docXmlBuilder.append("<w:r>${runProps.toString()}<w:t xml:space=\"preserve\">$escapedText</w:t></w:r>")
                            }
                        }
                        docXmlBuilder.append("</w:p>")
                    }
                    is PictureElement -> {
                        val pic = element.pic
                        val relId = uriToRelId[pic.uri]
                        if (relId != null) {
                            val emuWidth = (pic.width.value * 12700).toLong()
                            val emuHeight = (pic.height.value * 12700).toLong()
                            val idVal = (pic.id.hashCode().toLong().let { if (it <= 0) -it else it }) % 100000
                            val picIndentDxa = maxOf(0, ((pic.x.value - marginLeft) * 20).toInt())
                            
                            docXmlBuilder.append("<w:p>")
                            docXmlBuilder.append("<w:pPr>")
                            docXmlBuilder.append("<w:ind w:left=\"$picIndentDxa\"/>")
                            docXmlBuilder.append("<w:jc w:val=\"left\"/>")
                            docXmlBuilder.append("</w:pPr>")
                            docXmlBuilder.append("""
                                <w:r>
                                    <w:drawing>
                                        <wp:inline distT="0" distB="0" distL="0" distR="0">
                                            <wp:extent cx="$emuWidth" cy="$emuHeight"/>
                                            <wp:effectExtent l="0" t="0" r="0" b="0"/>
                                            <wp:docPr id="$idVal" name="Image_${idVal}"/>
                                            <wp:cNvGraphicFramePr>
                                                <a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/>
                                            </wp:cNvGraphicFramePr>
                                            <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                                                <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                                    <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                                        <pic:nvPicPr>
                                                            <pic:cNvPr id="$idVal" name="Image_${idVal}"/>
                                                            <pic:cNvPicPr/>
                                                        </pic:nvPicPr>
                                                        <pic:blipFill>
                                                            <a:blip r:embed="$relId"/>
                                                            <a:stretch>
                                                                <a:fillRect/>
                                                            </a:stretch>
                                                        </pic:blipFill>
                                                        <pic:spPr>
                                                            <a:xfrm>
                                                                <a:off x="0" y="0"/>
                                                                <a:ext cx="$emuWidth" cy="$emuHeight"/>
                                                            </a:xfrm>
                                                            <a:prstGeom prst="rect">
                                                                <a:avLst/>
                                                            </a:prstGeom>
                                                        </pic:spPr>
                                                    </pic:pic>
                                                </a:graphicData>
                                            </a:graphic>
                                        </wp:inline>
                                    </wp:drawing>
                                </w:r>
                            """.trimIndent())
                            docXmlBuilder.append("</w:p>")
                        } else {
                            val picIndentDxa = maxOf(0, ((pic.x.value - marginLeft) * 20).toInt())
                            docXmlBuilder.append("<w:p>")
                            docXmlBuilder.append("<w:pPr>")
                            docXmlBuilder.append("<w:ind w:left=\"$picIndentDxa\"/>")
                            docXmlBuilder.append("<w:jc w:val=\"left\"/>")
                            docXmlBuilder.append("<w:pBdr>")
                            docXmlBuilder.append("""<w:top w:val="single" w:sz="6" w:space="8" w:color="BCBCBC"/>""")
                            docXmlBuilder.append("""<w:left w:val="single" w:sz="6" w:space="8" w:color="BCBCBC"/>""")
                            docXmlBuilder.append("""<w:bottom w:val="single" w:sz="6" w:space="8" w:color="BCBCBC"/>""")
                            docXmlBuilder.append("""<w:right w:val="single" w:sz="6" w:space="8" w:color="BCBCBC"/>""")
                            docXmlBuilder.append("</w:pBdr>")
                            docXmlBuilder.append("""<w:shd w:val="clear" w:color="auto" w:fill="F5F5F5"/>""")
                            docXmlBuilder.append("</w:pPr>")
                            docXmlBuilder.append("<w:r><w:rPr><w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/><w:color w:val=\"333333\"/><w:b/></w:rPr><w:t>📷 [Image: ${escapeXml(pic.uri.substringAfterLast("/"))} (Width: ${pic.width.value.toInt()}pt, Height: ${pic.height.value.toInt()}pt)]</w:t></w:r>")
                            docXmlBuilder.append("</w:p>")
                        }
                    }
                    is ShapeElement -> {
                        val shape = element.shape
                        val emuWidth = (shape.width.value * 12700).toLong()
                        val emuHeight = (shape.height.value * 12700).toLong()
                        val idVal = (shape.id.hashCode().toLong().let { if (it <= 0) -it else it }) % 100000 + 400000
                        val fillHex = if (shape.fillColorHex.isBlank() || shape.fillColorHex == "default") "4F81BD" else shape.fillColorHex.replace("#", "")
                        val borderHex = if (shape.borderColorHex.isBlank() || shape.borderColorHex == "default") "1B365D" else shape.borderColorHex.replace("#", "")
                        val textHex = if (shape.textColorHex.isBlank() || shape.textColorHex == "default") "FFFFFF" else shape.textColorHex.replace("#", "")
                        val shapeIndentDxa = maxOf(0, ((shape.x.value - marginLeft) * 20).toInt())
                        
                        val presetName = when (shape.type) {
                            "rectangle" -> "rect"
                            "round_rectangle" -> "roundRect"
                            "ellipse", "circle" -> "ellipse"
                            "diamond" -> "diamond"
                            "hexagon" -> "hexagon"
                            "cloud" -> "cloud"
                            "heart" -> "heart"
                            "star_5" -> "star5"
                            "smiley" -> "smiley"
                            "triangle" -> "triangle"
                            "right_triangle" -> "rtTriangle"
                            "right_arrow" -> "rightArrow"
                            "left_arrow" -> "leftArrow"
                            "up_arrow" -> "upArrow"
                            "down_arrow" -> "downArrow"
                            else -> "rect"
                        }
                        
                        val textHalfPoints = (shape.textSizeSp * 2).toInt()
                        val boldTag = if (shape.isBold) "<w:b/>" else ""
                        val italicTag = if (shape.isItalic) "<w:i/>" else ""
                        val underlineTag = if (shape.isUnderline) "<w:u w:val=\"single\"/>" else ""
                        val alignment = when (shape.textAlignment) {
                            "center" -> "center"
                            "right" -> "right"
                            else -> "left"
                        }
                        val borderWidthEmu = (shape.borderWidthDp.value * 12700).toLong()

                        docXmlBuilder.append("<w:p>")
                        docXmlBuilder.append("<w:pPr>")
                        docXmlBuilder.append("<w:ind w:left=\"$shapeIndentDxa\"/>")
                        docXmlBuilder.append("<w:jc w:val=\"left\"/>")
                        docXmlBuilder.append("</w:pPr>")
                        docXmlBuilder.append("<w:r>")
                        docXmlBuilder.append("<w:drawing>")
                        docXmlBuilder.append("""
                            <wp:inline distT="0" distB="0" distL="0" distR="0">
                                <wp:extent cx="$emuWidth" cy="$emuHeight"/>
                                <wp:effectExtent l="0" t="0" r="0" b="0"/>
                                <wp:docPr id="$idVal" name="Shape_${idVal}"/>
                                <wp:cNvGraphicFramePr/>
                                <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                                    <a:graphicData uri="http://schemas.microsoft.com/office/word/2010/wordprocessingShape">
                                        <wps:wsp xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                            <wps:cNvSpPr/>
                                            <wps:spPr>
                                                <a:xfrm>
                                                    <a:off x="0" y="0"/>
                                                    <a:ext cx="$emuWidth" cy="$emuHeight"/>
                                                </a:xfrm>
                                                <a:prstGeom prst="$presetName">
                                                    <a:avLst/>
                                                </a:prstGeom>
                                                <a:solidFill>
                                                    <a:srgbClr val="$fillHex"/>
                                                </a:solidFill>
                                                <a:ln w="$borderWidthEmu">
                                                    <a:solidFill>
                                                        <a:srgbClr val="$borderHex"/>
                                                    </a:solidFill>
                                                </a:ln>
                                            </wps:spPr>
                                            <wps:txbx>
                                                <w:txbxContent>
                                                    <w:p>
                                                        <w:pPr>
                                                            <w:jc w:val="$alignment"/>
                                                        </w:pPr>
                                                        <w:r>
                                                            <w:rPr>
                                                                <w:color w:val="$textHex"/>
                                                                <w:sz w:val="$textHalfPoints"/>
                                                                <w:szCs w:val="$textHalfPoints"/>
                                                                $boldTag
                                                                $italicTag
                                                                $underlineTag
                                                            </w:rPr>
                                                            <w:t>${escapeXml(shape.textInside)}</w:t>
                                                        </w:r>
                                                    </w:p>
                                                </w:txbxContent>
                                            </wps:txbx>
                                        </wps:wsp>
                                    </a:graphicData>
                                </a:graphic>
                            </wp:inline>
                        """.trimIndent())
                        docXmlBuilder.append("</w:drawing>")
                        docXmlBuilder.append("</w:r>")
                        docXmlBuilder.append("</w:p>")
                    }
                    is TableElement -> {
                        val table = element.table
                        val tblThemeColor = if (table.themeColorHex.isBlank() || table.themeColorHex == "default") "5B9BD5" else table.themeColorHex.replace("#", "")
                        
                        val tableWidthDxa = (table.width.value * 20).toInt()
                        val cellWidthDxa = tableWidthDxa / maxOf(1, table.columns)
                        val indentDxa = maxOf(0, ((table.x.value - marginLeft) * 20).toInt())

                        docXmlBuilder.append("<w:tbl>")
                        docXmlBuilder.append("""
                            <w:tblPr>
                                <w:tblW w:w="$tableWidthDxa" w:type="dxa"/>
                                <w:tblInd w:w="$indentDxa" w:type="dxa"/>
                                <w:jc w:val="left"/>
                                <w:tblBorders>
                                    <w:top w:val="single" w:sz="12" w:space="0" w:color="$tblThemeColor"/>
                                    <w:left w:val="single" w:sz="12" w:space="0" w:color="$tblThemeColor"/>
                                    <w:bottom w:val="single" w:sz="12" w:space="0" w:color="$tblThemeColor"/>
                                    <w:right w:val="single" w:sz="12" w:space="0" w:color="$tblThemeColor"/>
                                    <w:insideH w:val="single" w:sz="6" w:space="0" w:color="$tblThemeColor"/>
                                    <w:insideV w:val="single" w:sz="6" w:space="0" w:color="$tblThemeColor"/>
                                </w:tblBorders>
                            </w:tblPr>
                        """.trimIndent())

                        docXmlBuilder.append("<w:tblGrid>")
                        for (colIdx in 0 until maxOf(1, table.columns)) {
                            docXmlBuilder.append("<w:gridCol w:w=\"$cellWidthDxa\"/>")
                        }
                        docXmlBuilder.append("</w:tblGrid>")
                        
                        for (r in 0 until table.rows) {
                            docXmlBuilder.append("<w:tr>")
                            for (c in 0 until table.columns) {
                                val isHeader = table.hasHeaderRow && r == 0
                                val customBg = table.cellBgColors["$r,$c"]?.replace("#", "")
                                val cellFill = if (customBg != null) {
                                    customBg
                                } else {
                                    if (isHeader) {
                                        tblThemeColor
                                    } else if (table.alternateRows && r % 2 == 1) {
                                        "EBF2F9"
                                    } else {
                                        "FFFFFF"
                                    }
                                }
                                
                                val cellText = table.getCellText(r, c)
                                val isBold = table.cellBold["$r,$c"] ?: isHeader
                                val isItalic = table.cellItalic["$r,$c"] ?: false
                                val isUnderline = table.cellUnderline["$r,$c"] ?: false
                                val cellTextColor = if (isHeader && customBg == null) "FFFFFF" else "1E1F22"
                                
                                docXmlBuilder.append("<w:tc>")
                                docXmlBuilder.append("""
                                    <w:tcPr>
                                        <w:tcW w:w="$cellWidthDxa" w:type="dxa"/>
                                        <w:shd w:val="clear" w:color="auto" w:fill="$cellFill"/>
                                    </w:tcPr>
                                """.trimIndent())
                                
                                docXmlBuilder.append("<w:p>")
                                docXmlBuilder.append("<w:pPr><w:jc w:val=\"left\"/></w:pPr>")
                                docXmlBuilder.append("<w:r>")
                                docXmlBuilder.append("<w:rPr>")
                                docXmlBuilder.append("<w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/>")
                                if (isBold) docXmlBuilder.append("<w:b/>")
                                if (isItalic) docXmlBuilder.append("<w:i/>")
                                if (isUnderline) docXmlBuilder.append("<w:u w:val=\"single\"/>")
                                docXmlBuilder.append("""<w:color w:val="$cellTextColor"/>""")
                                docXmlBuilder.append("</w:rPr>")
                                docXmlBuilder.append("<w:t>${escapeXml(cellText)}</w:t>")
                                docXmlBuilder.append("</w:r>")
                                docXmlBuilder.append("</w:p>")
                                docXmlBuilder.append("</w:tc>")
                            }
                            docXmlBuilder.append("</w:tr>")
                        }
                        docXmlBuilder.append("</w:tbl>")
                    }
                }
            }

            if (shouldShowFooter) {
                val computedPageNumber = formatPageNumber(pageIndex + pageNumberStartAt, pageNumberFormat, pagesList.size)
                val footerTextToShow = when (footerAlignment) {
                    "Left" -> footerText + (if (shouldShowPageNum && pageNumberPosition != null && pageNumberPosition.startsWith("Bottom") && pageNumberPosition.contains("Left")) "   $computedPageNumber" else "")
                    "Center" -> footerText + (if (shouldShowPageNum && pageNumberPosition != null && pageNumberPosition.startsWith("Bottom") && !pageNumberPosition.contains("Left") && !pageNumberPosition.contains("Right")) "   $computedPageNumber" else "")
                    "Right" -> footerText + (if (shouldShowPageNum && pageNumberPosition != null && pageNumberPosition.startsWith("Bottom") && pageNumberPosition.contains("Right")) "   $computedPageNumber" else "")
                    else -> footerText
                }
                
                val jcVal = when (footerAlignment) {
                    "Center" -> "center"
                    "Right" -> "right"
                    else -> "left"
                }
                
                docXmlBuilder.append("<w:p>")
                docXmlBuilder.append("<w:pPr>")
                if (jcVal != "left") {
                    docXmlBuilder.append("<w:jc w:val=\"$jcVal\"/>")
                }
                docXmlBuilder.append("""<w:pBdr><w:top w:val="single" w:sz="6" w:space="4" w:color="D3D3D3"/></w:pBdr>""")
                docXmlBuilder.append("</w:pPr>")
                docXmlBuilder.append("<w:r><w:rPr><w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/><w:color w:val=\"808080\"/><w:b/></w:rPr><w:t>${escapeXml(footerTextToShow)}</w:t></w:r>")
                docXmlBuilder.append("</w:p>")
            }

            if (pageIndex < pagesList.size - 1) {
                docXmlBuilder.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            }
        }

        // Calculate page layout structure for DOCX in twips (1 point = 20 twips)
        val cleanFormat = pageFormat.substringBefore(" ").trim()
        val (pw, ph) = when (cleanFormat) {
            "A3" -> 842 to 1191
            "A4" -> 595 to 842
            "A5" -> 420 to 595
            "Letter" -> 612 to 792
            "Legal" -> 612 to 1008
            "Executive" -> 522 to 756
            "Custom" -> (customWidth * 72f).toInt() to (customHeight * 72f).toInt()
            else -> 595 to 842 // A4 default
        }
        var widthPoints = pw
        var heightPoints = ph
        if (isLandscape) {
            widthPoints = ph
            heightPoints = pw
        }

        val widthTwips = widthPoints * 20
        val heightTwips = heightPoints * 20
        val leftMarginTwips = (marginLeft * 20).toInt()
        val topMarginTwips = (marginTop * 20).toInt()
        val rightMarginTwips = (marginRight * 20).toInt()
        val bottomMarginTwips = (marginBottom * 20).toInt()

        val orientAttr = if (isLandscape) " w:orient=\"landscape\"" else ""

        val borderVal = when (pageBorderType) {
            "Thin Box", "Medium Box", "Thick Box" -> "single"
            "Dashed Line" -> "dashed"
            "Double Line" -> "double"
            else -> "none"
        }
        val borderSz = when (pageBorderType) {
            "Thin Box" -> "4"
            "Medium Box", "Dashed Line", "Double Line" -> "12"
            "Thick Box" -> "24"
            else -> "0"
        }
        val borderClr = if (pageBorderColorHex.startsWith("#")) pageBorderColorHex.replace("#", "") else pageBorderColorHex
        val borderClrClean = if (borderClr == "default" || borderClr.isEmpty()) "GRAY" else borderClr

        val borderXml = if (borderVal != "none") {
            """
            <w:pgBorders w:offset-from="page">
                <w:top w:val="$borderVal" w:sz="$borderSz" w:space="24" w:color="$borderClrClean"/>
                <w:left w:val="$borderVal" w:sz="$borderSz" w:space="24" w:color="$borderClrClean"/>
                <w:bottom w:val="$borderVal" w:sz="$borderSz" w:space="24" w:color="$borderClrClean"/>
                <w:right w:val="$borderVal" w:sz="$borderSz" w:space="24" w:color="$borderClrClean"/>
            </w:pgBorders>
            """.trimIndent()
        } else ""

        val sectPr = """
            <w:sectPr>
                <w:pgSz w:w="$widthTwips" w:h="$heightTwips"$orientAttr/>
                <w:pgMar w:top="$topMarginTwips" w:bottom="$bottomMarginTwips" w:left="$leftMarginTwips" w:right="$rightMarginTwips" w:header="720" w:footer="720" w:gutter="0"/>
                <w:cols w:num="$columnCount" w:space="720"/>
                $borderXml
            </w:sectPr>
        """.trimIndent()
        
        docXmlBuilder.append(sectPr)
        docXmlBuilder.append("""</w:body></w:document>""")
        zip.write(docXmlBuilder.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.close()
    }

    fun parsePdfText(inputStream: InputStream): String {
        val bytes = inputStream.readBytes()
        val sb = StringBuilder()
        var pos = 0
        while (pos < bytes.size - 6) {
            if (bytes[pos] == 's'.code.toByte() &&
                bytes[pos+1] == 't'.code.toByte() &&
                bytes[pos+2] == 'r'.code.toByte() &&
                bytes[pos+3] == 'e'.code.toByte() &&
                bytes[pos+4] == 'a'.code.toByte() &&
                bytes[pos+5] == 'm'.code.toByte()) {
                
                var streamStart = pos + 6
                while (streamStart < bytes.size && (bytes[streamStart] == '\r'.code.toByte() || bytes[streamStart] == '\n'.code.toByte())) {
                    streamStart++
                }
                
                var streamEnd = streamStart
                while (streamEnd < bytes.size - 9) {
                    if (bytes[streamEnd] == 'e'.code.toByte() &&
                        bytes[streamEnd+1] == 'n'.code.toByte() &&
                        bytes[streamEnd+2] == 'd'.code.toByte() &&
                        bytes[streamEnd+3] == 's'.code.toByte() &&
                        bytes[streamEnd+4] == 't'.code.toByte() &&
                        bytes[streamEnd+5] == 'r'.code.toByte() &&
                        bytes[streamEnd+6] == 'e'.code.toByte() &&
                        bytes[streamEnd+7] == 'a'.code.toByte() &&
                        bytes[streamEnd+8] == 'm'.code.toByte()) {
                        break
                    }
                    streamEnd++
                }
                
                if (streamEnd > streamStart) {
                    val streamBytes = bytes.copyOfRange(streamStart, streamEnd)
                    try {
                        val inflater = Inflater(true)
                        var decompressedText = ""
                        try {
                            inflater.setInput(streamBytes)
                            val outputStream = ByteArrayOutputStream()
                            val buffer = ByteArray(2048)
                            while (!inflater.finished()) {
                                val count = inflater.inflate(buffer)
                                if (count == 0) break
                                outputStream.write(buffer, 0, count)
                            }
                            decompressedText = outputStream.toString("UTF-8")
                        } finally {
                            inflater.end()
                        }
                        
                        if (decompressedText.isEmpty()) {
                            val stdInflater = Inflater()
                            try {
                                stdInflater.setInput(streamBytes)
                                val stdOutput = ByteArrayOutputStream()
                                val buffer = ByteArray(2048)
                                while (!stdInflater.finished()) {
                                    val count = stdInflater.inflate(buffer)
                                    if (count == 0) break
                                    stdOutput.write(buffer, 0, count)
                                }
                                decompressedText = stdOutput.toString("UTF-8")
                            } finally {
                                stdInflater.end()
                            }
                        }
                        
                        extractPdfStrings(decompressedText, sb)
                    } catch (e: Exception) {
                        val rawText = String(streamBytes, Charsets.UTF_8)
                        extractPdfStrings(rawText, sb)
                    }
                }
                pos = streamEnd + 9
            } else {
                pos++
            }
        }
        
        if (sb.isEmpty()) {
            val rawText = String(bytes, Charsets.UTF_8)
            extractPdfStrings(rawText, sb)
        }
        
        return prePaginateText(sb.toString().trim())
    }

    fun prePaginateText(rawText: String): String {
        if (rawText.isEmpty()) return ""
        if (rawText.contains('\u000C') || rawText.contains("\\u000c") || rawText.contains("\\u000C")) {
            return rawText
        }
        
        val lines = rawText.split('\n')
        val pages = mutableListOf<String>()
        val currentPage = StringBuilder()
        var currentLineCount = 0
        var currentLength = 0
        
        for (line in lines) {
            val wrappedLines = maxOf(1, (line.length + 79) / 80)
            if (currentLength > 0 && (currentLineCount + wrappedLines > 32 || currentLength + line.length > 1800)) {
                pages.add(currentPage.toString())
                currentPage.clear()
                currentLineCount = 0
                currentLength = 0
            }
            if (currentPage.isNotEmpty()) {
                currentPage.append("\n")
                currentLineCount += 1
                currentLength += 1
            }
            currentPage.append(line)
            currentLineCount += wrappedLines
            currentLength += line.length
        }
        if (currentPage.isNotEmpty()) {
            pages.add(currentPage.toString())
        }
        return pages.joinToString("\u000C")
    }

    private fun extractPdfStrings(text: String, sb: StringBuilder) {
        val pattern = Pattern.compile("\\(([^)]*)\\)")
        val matcher = pattern.matcher(text)
        var lastWasSpace = false
        while (matcher.find()) {
            val str = matcher.group(1).orEmpty()
            val cleanStr = str.replace("\\(", "(")
                              .replace("\\)", ")")
                              .replace("\\r", "\n")
                              .replace("\\n", "\n")
            if (cleanStr.isNotEmpty()) {
                sb.append(cleanStr)
                lastWasSpace = false
            } else if (!lastWasSpace) {
                sb.append(" ")
                lastWasSpace = true
            }
        }
        if (text.contains("ET") || text.contains("Tj") || text.contains("TJ")) {
            sb.append("\n")
        }
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
