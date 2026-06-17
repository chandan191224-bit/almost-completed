package com.example

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToLog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.db.DocDatabase
import com.example.db.DocEntity
import com.example.db.DocRepository
import com.example.ui.DocEditorScreen
import com.example.ui.DocFormatRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DocViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class DocEditorScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun test_auto_page_generation() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val viewModel = DocViewModel(context)

    // Create a word document
    viewModel.createNewDocument(
      title = "Test Auto Page Generation",
      type = "word",
      initialContent = "Initial short text"
    )
    composeTestRule.waitForIdle()
    val docId = viewModel.selectedDoc.value?.id ?: 0

    val lifecycleOwner = object : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle = registry
    }

    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
        MyApplicationTheme {
          DocEditorScreen(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }

    composeTestRule.waitForIdle()

    // 1. Initial screenshot
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/doc_editor_initial.png")

    // 2. Perform text input to overflow the first page and trigger auto-page generation
    val builder = StringBuilder("Initial short text")
    for (i in 1..40) {
      builder.append("\nLine number $i of overflow text to force the page height limit to exceed the constraint limit.")
    }
    
    composeTestRule.onNodeWithText("Initial short text").performTextInput(builder.toString().substring("Initial short text".length))
    composeTestRule.waitForIdle()

    // Capture the state after overflow text entry
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/doc_editor_overflow.png")

    // Assert that content has split into multiple pages (contains '\u000C')
    val content = viewModel.draftContent.value
    assertTrue("Content should contain page break \\u000C indicating auto-page generation", content.contains("\u000C"))
  }

  @Test
  fun test_manual_page_break_backspace_merging() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val viewModel = DocViewModel(context)

    // Create a word document with manual page break
    val contentWithBreak = "Page One Content\u000CPage Two Content"
    viewModel.createNewDocument(
      title = "Test Manual Break Merge",
      type = "word",
      initialContent = contentWithBreak
    )
    composeTestRule.waitForIdle()
    val docId = viewModel.selectedDoc.value?.id ?: 0

    // Apply the manual page break formatting span on the \u000C character (index 16)
    DocFormatRepository.applySpan(docId, "manual_page_break", "", 16, 17)

    val lifecycleOwner = object : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle = registry
    }

    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
        MyApplicationTheme {
          DocEditorScreen(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }

    composeTestRule.waitForIdle()

    // Focus the start of Page 2 text field
    val page2Node = composeTestRule.onNodeWithText("Page Two Content", substring = true)
    page2Node.performClick()
    page2Node.performSemanticsAction(SemanticsActions.RequestFocus)
    composeTestRule.waitForIdle()

    // Explicitly place cursor at index 0 of Page 2 text field
    page2Node.performSemanticsAction(SemanticsActions.SetSelection) {
        it(0, 0, true)
    }
    composeTestRule.waitForIdle()

    // Press Backspace on Page 2 (should delete the manual break and merge pages)
    page2Node.performKeyInput {
        pressKey(Key.Backspace)
    }
    composeTestRule.waitForIdle()

    // Capture the merged screenshot
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/doc_editor_merged.png")

    val mergedContent = viewModel.draftContent.value
    assertEquals("Page One ContentPage Two Content", mergedContent.replace("\u200B", ""))
  }
}
