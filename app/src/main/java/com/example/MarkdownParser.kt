package com.example

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class UnorderedList(val items: List<String>) : MarkdownBlock
    data class OrderedList(val items: List<String>) : MarkdownBlock
    data class TaskList(val items: List<TaskItem>) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    object HorizontalRule : MarkdownBlock
}

data class TaskItem(val checked: Boolean, val text: String)

object MarkdownParser {

    /**
     * Parses Markdown text into a list of MarkdownBlock elements.
     */
    fun parse(markdown: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = markdown.split("\n")
        var i = 0
        val totalLines = lines.size

        while (i < totalLines) {
            val line = lines[i]
            val trimmedLine = line.trim()

            // 1. Code block
            if (trimmedLine.startsWith("```")) {
                val lang = trimmedLine.substring(3).trim()
                val codeBuilder = StringBuilder()
                i++
                while (i < totalLines && !lines[i].trim().startsWith("```")) {
                    codeBuilder.append(lines[i]).append("\n")
                    i++
                }
                blocks.add(MarkdownBlock.CodeBlock(lang, codeBuilder.toString().trimEnd()))
                i++
                continue
            }

            // 2. Horizontal Rule
            if (trimmedLine == "---" || trimmedLine == "***" || trimmedLine == "___") {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
                continue
            }

            // 3. Headings
            if (trimmedLine.startsWith("#")) {
                val match = Regex("^(#{1,6})\\s+(.*)$").matchEntire(trimmedLine)
                if (match != null) {
                    val level = match.groupValues[1].length
                    val text = match.groupValues[2]
                    blocks.add(MarkdownBlock.Heading(level, text))
                    i++
                    continue
                }
            }

            // 4. BlockQuote
            if (trimmedLine.startsWith(">")) {
                val quoteBuilder = StringBuilder()
                while (i < totalLines && (lines[i].trim().startsWith(">") || (lines[i].isNotBlank() && quoteBuilder.isNotEmpty()))) {
                    val l = lines[i].trim()
                    if (l.startsWith(">")) {
                        quoteBuilder.append(l.removePrefix(">").trim()).append(" ")
                    } else {
                        quoteBuilder.append(l).append(" ")
                    }
                    i++
                }
                blocks.add(MarkdownBlock.BlockQuote(quoteBuilder.toString().trim()))
                continue
            }

            // 5. Unordered / Task List
            if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") || trimmedLine.startsWith("+ ")) {
                // Let's check if it's a task list or unordered list
                val listItems = mutableListOf<String>()
                val taskItems = mutableListOf<TaskItem>()
                var isTaskList = false

                // Peek if first is task list
                val firstClean = trimmedLine.substring(2).trim()
                if (firstClean.startsWith("[ ]") || firstClean.startsWith("[x]") || firstClean.startsWith("[X]")) {
                    isTaskList = true
                }

                while (i < totalLines && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* ") || lines[i].trim().startsWith("+ "))) {
                    val currentTrim = lines[i].trim()
                    val itemText = currentTrim.substring(2).trim()
                    
                    if (isTaskList) {
                        val checked = itemText.startsWith("[x]") || itemText.startsWith("[X]")
                        val taskText = if (itemText.startsWith("[ ]") || itemText.startsWith("[x]") || itemText.startsWith("[X]")) {
                            itemText.substring(3).trim()
                        } else {
                            itemText
                        }
                        taskItems.add(TaskItem(checked, taskText))
                    } else {
                        listItems.add(itemText)
                    }
                    i++
                }

                if (isTaskList) {
                    blocks.add(MarkdownBlock.TaskList(taskItems))
                } else {
                    blocks.add(MarkdownBlock.UnorderedList(listItems))
                }
                continue
            }

            // 6. Ordered List
            if (trimmedLine.isNotEmpty() && trimmedLine.first().isDigit()) {
                val match = Regex("^(\\d+)\\.\\s+(.*)$").matchEntire(trimmedLine)
                if (match != null) {
                    val listItems = mutableListOf<String>()
                    while (i < totalLines) {
                        val currentTrim = lines[i].trim()
                        val currentMatch = Regex("^(\\d+)\\.\\s+(.*)$").matchEntire(currentTrim)
                        if (currentMatch != null) {
                            listItems.add(currentMatch.groupValues[2])
                            i++
                        } else {
                            break
                        }
                    }
                    blocks.add(MarkdownBlock.OrderedList(listItems))
                    continue
                }
            }

            // 7. Table
            if (trimmedLine.startsWith("|") && trimmedLine.endsWith("|")) {
                val tableLines = mutableListOf<String>()
                while (i < totalLines && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                if (tableLines.size >= 2) {
                    // Header row
                    val headers = tableLines[0].split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    // Separator row (e.g. |---|---|)
                    val separator = tableLines[1]
                    if (separator.contains("-")) {
                        val rows = mutableListOf<List<String>>()
                        for (rowIndex in 2 until tableLines.size) {
                            val rowCells = tableLines[rowIndex].split("|").map { it.trim() }.filter { it.isNotEmpty() }
                            // Align cells to header count
                            if (rowCells.isNotEmpty()) {
                                val paddedRow = rowCells.take(headers.size).toMutableList()
                                while (paddedRow.size < headers.size) paddedRow.add("")
                                rows.add(paddedRow)
                            }
                        }
                        blocks.add(MarkdownBlock.Table(headers, rows))
                        continue
                    } else {
                        // Not a valid table structure, fallback to paragraph
                        tableLines.forEach { blocks.add(MarkdownBlock.Paragraph(it)) }
                        continue
                    }
                } else {
                    tableLines.forEach { blocks.add(MarkdownBlock.Paragraph(it)) }
                    continue
                }
            }

            // 8. Blank line
            if (trimmedLine.isEmpty()) {
                i++
                continue
            }

            // 9. Paragraph (combine adjacent lines that aren't other block types)
            val paragraphBuilder = StringBuilder()
            while (i < totalLines) {
                val currentLine = lines[i]
                val currentTrim = currentLine.trim()
                if (currentTrim.isEmpty() ||
                    currentTrim.startsWith("#") ||
                    currentTrim.startsWith("```") ||
                    currentTrim.startsWith(">") ||
                    currentTrim.startsWith("- ") ||
                    currentTrim.startsWith("* ") ||
                    currentTrim.startsWith("+ ") ||
                    currentTrim.startsWith("---") ||
                    currentTrim.startsWith("***") ||
                    (currentTrim.isNotEmpty() && currentTrim.first().isDigit() && Regex("^\\d+\\.\\s+").containsMatchIn(currentTrim)) ||
                    (currentTrim.startsWith("|") && currentTrim.endsWith("|"))
                ) {
                    break
                }
                paragraphBuilder.append(currentLine).append(" ")
                i++
            }
            if (paragraphBuilder.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(paragraphBuilder.toString().trim()))
            }
        }

        return blocks
    }

    /**
     * Parses inline markdown formatting (bold, italic, code, links) and builds an AnnotatedString.
     */
    fun parseInline(
        text: String,
        primaryColor: androidx.compose.ui.graphics.Color,
        linkColor: androidx.compose.ui.graphics.Color,
        codeBgColor: androidx.compose.ui.graphics.Color
    ): AnnotatedString {
        val builder = AnnotatedString.Builder()
        var index = 0
        val length = text.length

        while (index < length) {
            // 1. Inline Code: `code`
            if (text[index] == '`') {
                val closingIndex = text.indexOf('`', index + 1)
                if (closingIndex != -1) {
                    val codeContent = text.substring(index + 1, closingIndex)
                    builder.pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            background = codeBgColor,
                            color = primaryColor
                        )
                    )
                    builder.append(codeContent)
                    builder.pop()
                    index = closingIndex + 1
                    continue
                }
            }

            // 2. Bold-Italic: ***text*** or ___text___ or **_text_**
            if (index + 2 < length && text.substring(index, index + 3) == "***") {
                val closingIndex = text.indexOf("***", index + 3)
                if (closingIndex != -1) {
                    val content = text.substring(index + 3, closingIndex)
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                    // Recursively parse inline formatting inside bold-italic
                    builder.append(parseInline(content, primaryColor, linkColor, codeBgColor))
                    builder.pop()
                    index = closingIndex + 3
                    continue
                }
            }

            // 3. Bold: **text** or __text__
            if (index + 1 < length && (text.substring(index, index + 2) == "**" || text.substring(index, index + 2) == "__")) {
                val token = text.substring(index, index + 2)
                val closingIndex = text.indexOf(token, index + 2)
                if (closingIndex != -1) {
                    val content = text.substring(index + 2, closingIndex)
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    builder.append(parseInline(content, primaryColor, linkColor, codeBgColor))
                    builder.pop()
                    index = closingIndex + 2
                    continue
                }
            }

            // 4. Italic: *text* or _text_
            if (text[index] == '*' || text[index] == '_') {
                val token = text[index].toString()
                val closingIndex = text.indexOf(token, index + 1)
                if (closingIndex != -1 && closingIndex > index + 1) {
                    val content = text.substring(index + 1, closingIndex)
                    builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    builder.append(parseInline(content, primaryColor, linkColor, codeBgColor))
                    builder.pop()
                    index = closingIndex + 1
                    continue
                }
            }

            // 5. Strikethrough: ~~text~~
            if (index + 1 < length && text.substring(index, index + 2) == "~~") {
                val closingIndex = text.indexOf("~~", index + 2)
                if (closingIndex != -1) {
                    val content = text.substring(index + 2, closingIndex)
                    builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    builder.append(parseInline(content, primaryColor, linkColor, codeBgColor))
                    builder.pop()
                    index = closingIndex + 2
                    continue
                }
            }

            // 6. Hyperlink: [text](url)
            if (text[index] == '[') {
                val closingBracket = text.indexOf(']', index + 1)
                if (closingBracket != -1 && closingBracket + 1 < length && text[closingBracket + 1] == '(') {
                    val closingParenthesis = text.indexOf(')', closingBracket + 2)
                    if (closingParenthesis != -1) {
                        val linkText = text.substring(index + 1, closingBracket)
                        val url = text.substring(closingBracket + 2, closingParenthesis)

                        builder.pushStringAnnotation(tag = "URL", annotation = url)
                        builder.pushStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        builder.append(linkText)
                        builder.pop()
                        builder.pop()

                        index = closingParenthesis + 1
                        continue
                    }
                }
            }

            // Append regular character
            builder.append(text[index].toString())
            index++
        }

        return builder.toAnnotatedString()
    }
}
