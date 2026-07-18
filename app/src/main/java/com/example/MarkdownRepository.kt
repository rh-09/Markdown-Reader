package com.example

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MarkdownRepository(
    private val context: Context,
    private val dao: MarkdownDocumentDao
) {
    val recentDocuments: Flow<List<MarkdownDocument>> = dao.getRecentDocuments()
    val bookmarkedDocuments: Flow<List<MarkdownDocument>> = dao.getBookmarkedDocuments()

    fun observeDocumentByUri(uri: String): Flow<MarkdownDocument?> = dao.observeDocumentByUri(uri)

    suspend fun getDocumentByUri(uri: String): MarkdownDocument? = dao.getDocumentByUri(uri)

    suspend fun insertOrUpdateDocument(document: MarkdownDocument) = withContext(Dispatchers.IO) {
        dao.insertOrUpdateDocument(document)
    }

    suspend fun updateBookmarkState(uri: String, isBookmarked: Boolean) = withContext(Dispatchers.IO) {
        dao.updateBookmarkState(uri, isBookmarked)
    }

    suspend fun updateReadingProgress(uri: String, position: Int, percentage: Float) = withContext(Dispatchers.IO) {
        dao.updateReadingProgress(uri, position, percentage)
    }

    suspend fun deleteDocument(uri: String) = withContext(Dispatchers.IO) {
        dao.deleteDocument(uri)
    }

    /**
     * Reads markdown text content from either a local file path or a SAF content URI.
     */
    suspend fun readDocumentContent(uriString: String): String = withContext(Dispatchers.IO) {
        try {
            if (uriString.startsWith("content://")) {
                val uri = Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: throw Exception("Could not open input stream for content URI")
            } else {
                val file = File(uriString)
                if (file.exists()) {
                    file.readText()
                } else {
                    // Fallback to checking assets if it represents an asset
                    if (uriString.startsWith("assets/")) {
                        val assetPath = uriString.removePrefix("assets/")
                        context.assets.open(assetPath).use { inputStream ->
                            inputStream.bufferedReader().use { it.readText() }
                        }
                    } else {
                        throw Exception("File not found at: $uriString")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error loading document: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    /**
     * Extracts name and file size from a content URI or a file path.
     */
    suspend fun resolveDocumentDetails(uriString: String): DocumentDetails = withContext(Dispatchers.IO) {
        var name = "Untitled.md"
        var size = 0L

        try {
            if (uriString.startsWith("content://")) {
                val uri = Uri.parse(uriString)
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                        if (sizeIndex != -1) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } else {
                val file = File(uriString)
                if (file.exists()) {
                    name = file.name
                    size = file.length()
                } else if (uriString.startsWith("assets/")) {
                    name = uriString.substringAfterLast("/")
                    // Approximate size
                    try {
                        context.assets.open(uriString.removePrefix("assets/")).use {
                            size = it.available().toLong()
                        }
                    } catch (e: Exception) {
                        size = 0L
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        DocumentDetails(name, size)
    }

    /**
     * Pre-installs beautiful sample markdown files in internal storage so the user has immediate
     * content to read and play with on their first start.
     */
    suspend fun initializeSamplesIfNeeded() = withContext(Dispatchers.IO) {
        val samplesDir = File(context.filesDir, "samples")
        if (!samplesDir.exists()) {
            samplesDir.mkdirs()
        }

        // Check if samples exist in DB
        val hasSamples = dao.getAllDocuments().let {
            // Check if there are documents in the DB
            // (We can check if database table is completely empty)
            true
        }

        val sampleFiles = listOf(
            SampleFileData(
                filename = "01_welcome_guide.md",
                title = "Welcome & App Guide",
                content = welcomeGuideContent()
            ),
            SampleFileData(
                filename = "02_markdown_cheatsheet.md",
                title = "Markdown Syntax Reference",
                content = markdownCheatSheetContent()
            ),
            SampleFileData(
                filename = "03_minimalist_philosophy.md",
                title = "The Minimalist Reader",
                content = minimalistPhilosophyContent()
            )
        )

        for (sample in sampleFiles) {
            val file = File(samplesDir, sample.filename)
            if (!file.exists()) {
                file.writeText(sample.content)
            }

            // Check if already in DB
            val existing = dao.getDocumentByUri(file.absolutePath)
            if (existing == null) {
                val words = countWords(sample.content)
                val doc = MarkdownDocument(
                    uri = file.absolutePath,
                    title = sample.title,
                    filePath = "Internal / Samples / ${sample.filename}",
                    lastOpened = if (sample.filename == "01_welcome_guide.md") System.currentTimeMillis() else 0L,
                    isBookmarked = false,
                    fileSize = file.length(),
                    wordCount = words,
                    isSample = true
                )
                dao.insertOrUpdateDocument(doc)
            }
        }
    }

    companion object {
        fun countWords(text: String): Int {
            if (text.isBlank()) return 0
            // Simple split by whitespace
            return text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        }
    }
}

data class DocumentDetails(val name: String, val size: Long)
data class SampleFileData(val filename: String, val title: String, val content: String)

// --- Sample Markdown Contents ---

private fun welcomeGuideContent() = """
# Welcome to Markdown Reader 📖

Welcome to your modern, minimalist **Markdown Reader** APK. This application is dedicated exclusively to the pure, distraction-free reading of Markdown files.

> "A room without books is like a body without a soul." 
> — *Marcus Tullius Cicero*

---

## 🚀 Key Features

This reader is packed with featureful tools crafted for bookworms, students, and developers:

1. **Table of Contents (Outline)**: Tap the list icon in the top bar to slide open a dynamic Table of Contents. Tap any heading to scroll straight there!
2. **Immersive Themes**: Choose between *Nord Light*, *Deep Slate*, *Warm Sepia*, or *Amoled Black* to protect your eyes at any hour.
3. **Advanced Typography**: Adjust text size and toggle between clean *Sans-Serif*, elegant *Serif*, or precise *Monospace* fonts.
4. **Interactive Checklists**: Keep track of read lists or document tasks directly on screen.
5. **Text-to-Speech (TTS)**: Let the app read your documents out loud! Perfect for multi-tasking.
6. **Smart Search**: Find text instantly inside the document with real-time highlighting.
7. **Document Stats**: Track your reading time, word count, and file size in a single panel.

---

## 💡 How to open files

Since this is a **reader only**, you can open any markdown file on your device:
* Tap the **Open File** button at the bottom of your library screen.
* Select any `.md`, `.txt`, or `.markdown` file using the standard Android System Picker.
* Once opened, it will automatically save in your **Recents** so you can pick up exactly where you left off.
* Tap the **Bookmark** star icon on the top right to save the file into your library shortcuts!

---

## 🛠️ Reading Controls Cheat Sheet

* **Bookmark Star**: Toggles the file in your Favorites tab.
* **AA Text settings**: Instantly scales your layout up to 30sp and changes fonts.
* **Outline (List)**: Opens the document index.
* **TTS (Speaker)**: Speaks the active file.
* **Stats (Info)**: Calculates exact word count and reading speed.

Enjoy reading in peace!
"""

private fun markdownCheatSheetContent() = """
# Markdown Syntax Cheat Sheet 📝

This guide showcases the rich parsing and rendering capabilities of our engine. Every element below is rendered natively using high-performance Compose text and layouts.

---

## Headings

Headings from level 1 to 6 are fully supported with modern responsive typography.

# Heading 1
## Heading 2
### Heading 3
#### Heading 4
##### Heading 5
###### Heading 6

---

## Inline Formatting

You can format text with standard inline rules:
* This is **Bold Text** (`**Bold Text**`)
* This is *Italic Text* (`*Italic Text*`)
* This is ***Bold & Italic*** (`***Bold & Italic***`)
* This is ~~Strikethrough Text~~ (`~~Strikethrough Text~~`)
* This is an `inline code snippet` (`` `inline code snippet` ``)
* This is a [Clickable Link](https://markdownlivepreview.com) (`[Clickable Link](url)`)

---

## Blockquotes

Blockquotes are styled with a distinct accent margin, elevated background, and italicized fonts:

> "The details are not the details. They make the design."
> — *Charles Eames*

---

## Code Blocks

Code blocks support monospaced fonts and a scrollable background canvas:

```kotlin
// Beautiful Kotlin Code
fun main() {
    val greeting = "Hello, Markdown!"
    println(greeting)
}
```

```markdown
# Nested Markdown Example
* Unordered bullet 1
* Unordered bullet 2
```

---

## Lists & Checklists

### Unordered Lists
* Milk and eggs
* Fresh green tea
* Dark chocolate
  * Optional: 85% Cocoa

### Ordered Lists
1. Wake up at sunrise
2. Drink a glass of water
3. Read 10 pages of your book

### Interactive Checklists (Task Lists)
- [x] Create a beautiful Android app
- [x] Style it with modern Material 3 design
- [ ] Read all my favorite markdown books
- [ ] Share this app with friends

---

## Beautiful Minimalist Tables

Tables are rendered using thin lines and a clean grid layout with styled headers:

| Component | Style | Highlight |
| :--- | :--- | :--- |
| Background | Nord Light / Sepia | Soft Contrast |
| Typography | Playfair Display | High Polish |
| UI Elements | Material 3 / Round | Smooth Ripple |

---

Thank you for testing our custom Jetpack Compose Markdown Renderer!
"""

private fun minimalistPhilosophyContent() = """
# The Art of Focus & Minimalism 🍃

In an era of endless push notifications, algorithmic feeds, and digital clutter, finding quiet spaces is increasingly rare. 

## The Philosophy of "Less"

Minimalism isn't about owning nothing; it is about making room for what truly matters. When we strip away unnecessary visuals, sidebars, and edit fields, our focus shifts entirely to the **ideas** contained within the text.

> "Simplicity is the ultimate sophistication."
> — *Leonardo da Vinci*

### Why a "Reader Only" App?

By dividing creation and consumption, we build a cognitive boundary:
* **Writing Mode**: Active, high-energy, analytical.
* **Reading Mode**: Passive, reflective, open.

By eliminating formatting panels, cursor highlights, and edit buttons, your brain instantly relaxes into **comprehension mode**. There is nothing to click, nothing to fix, nothing to change. Just you and the words.

---

## Three Steps to Digital Calm

1. **Turn off Notifications**: Silence your device for 20 minutes.
2. **Find a comfortable font**: Use our **Serif** typeface at **18sp** or **20sp** for a book-like feel.
3. **Immerse in Dark or Sepia**: Turn on **Nord Sepia** to replicate physical paper pages.

Enjoy the silence.
"""
