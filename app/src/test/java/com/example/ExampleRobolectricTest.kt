package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("JCdocs Suite", appName)
  }

  @Test
  fun testWrappingComparison() {
    val density = Density(3f, 1f) // example density
    val text = "This is a very long line of text that is designed to test how text wrapping behaves differently between Jetpack Compose and native StaticLayout. We want to see if they end up wrapping at exactly the same characters."
    
    // 1. Compose paragraph layout
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fontFamilyResolver = androidx.compose.ui.text.font.createFontFamilyResolver(context)
    val composeParagraph = Paragraph(
        text = text,
        style = TextStyle(
            fontSize = 16.sp,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.sp
        ),
        constraints = Constraints(maxWidth = (531 * 3f).toInt()),
        density = density,
        fontFamilyResolver = fontFamilyResolver
    )
    val composeLines = (0 until composeParagraph.lineCount).map { line ->
        text.substring(composeParagraph.getLineStart(line), composeParagraph.getLineEnd(line))
    }
    
    // 2. Native StaticLayout
    val textPaint = android.text.TextPaint().apply {
        textSize = 16f * 3f * 1f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
    }
    val staticLayout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, (531 * 3f).toInt())
        .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1f)
        .setIncludePad(false)
        .build()
        
    val staticLines = (0 until staticLayout.lineCount).map { line ->
        text.substring(staticLayout.getLineStart(line), staticLayout.getLineEnd(line))
    }
    
    println("--- COMPOSE LINES (${composeLines.size}) ---")
    composeLines.forEach { println("[\$it]") }
    
    println("--- STATIC LINES (${staticLines.size}) ---")
    staticLines.forEach { println("[\$it]") }
  }
}

