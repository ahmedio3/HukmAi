package com.example.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color

/**
 * Article HTML parser — converts encyclopedia HTML to a structured list of
 * elements (titles, paragraphs with rich spans, footnotes).
 *
 * Extracted from ArticleViewerScreen.kt for clean separation of concerns.
 */

sealed class HtmlElement {
    data class Title1(val text: String) : HtmlElement()
    data class Title2(val text: String) : HtmlElement()
    data class RichParagraph(val parts: List<RichPart>) : HtmlElement()
    data class Footnote(val num: Int, val text: String) : HtmlElement()
}

sealed class RichPart {
    data class Text(val text: String) : RichPart()
    data class Aaya(val text: String) : RichPart()
    data class Hadith(val text: String) : RichPart()
}

private val spanRegex = Regex(
    """<span class="(title-1|title-2|aaya|hadith|tip)">(.*?)</span>""",
    RegexOption.DOT_MATCHES_ALL
)
private val otherSpanRegex = Regex("""<span[^>]*>.*?</span>""", RegexOption.DOT_MATCHES_ALL)
private val tagRegex = Regex("<[^>]*>")
private val lineSplitRegex = Regex("<br\\s*/?>")
private val whitespaceRegex = Regex("\\s+")

fun parseHtmlToElements(html: String): List<HtmlElement> {
    if (html.isEmpty()) return emptyList()
    var footnoteCounter = 0
    val list = mutableListOf<HtmlElement>()

    for (rawLine in html.split(lineSplitRegex)) {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) continue

        // Title detection
        val titleMatch = spanRegex.find(trimmed)
        if (titleMatch != null) {
            val className = titleMatch.groupValues[1]
            val content = titleMatch.groupValues[2].replace(tagRegex, "").trim()
            when (className) {
                "title-1" -> if (content.isNotEmpty()) { list.add(HtmlElement.Title1(content)); continue }
                "title-2" -> if (content.isNotEmpty()) { list.add(HtmlElement.Title2(content)); continue }
            }
        }

        // Collect tip footnotes in this line
        val lineFootnotes = mutableListOf<String>()
        for (match in spanRegex.findAll(trimmed)) {
            if (match.groupValues[1] == "tip") {
                val tipText = match.groupValues[2].replace(tagRegex, "").trim()
                if (tipText.isNotEmpty()) {
                    footnoteCounter++
                    lineFootnotes.add(tipText)
                }
            }
        }

        // Build rich paragraph excluding tip text
        val parts = mutableListOf<RichPart>()
        var lastEnd = 0
        for (match in spanRegex.findAll(trimmed)) {
            val className = match.groupValues[1]
            if (className == "aaya" || className == "hadith" || className == "tip") {
                val before = trimmed.substring(lastEnd, match.range.first).trim()
                if (before.isNotEmpty()) {
                    val cleanBefore = before.replace(otherSpanRegex, "").replace(tagRegex, "").trim()
                    if (cleanBefore.isNotEmpty()) parts.add(RichPart.Text(cleanBefore))
                }
                if (className != "tip") {
                    val content = match.groupValues[2].replace(tagRegex, "").trim()
                    if (content.isNotEmpty()) {
                        parts.add(
                            if (className == "aaya") RichPart.Aaya("﴿ $content ﴾")
                            else RichPart.Hadith("« $content »")
                        )
                    }
                }
                lastEnd = match.range.last + 1
            }
        }
        val after = trimmed.substring(lastEnd).trim()
        if (after.isNotEmpty()) {
            val cleanAfter = after.replace(otherSpanRegex, "").replace(tagRegex, "").trim()
            if (cleanAfter.isNotEmpty()) parts.add(RichPart.Text(cleanAfter))
        }
        if (parts.isNotEmpty()) list.add(HtmlElement.RichParagraph(parts))

        if (lineFootnotes.isNotEmpty()) {
            lineFootnotes.forEachIndexed { fnIdx, text ->
                list.add(HtmlElement.Footnote(footnoteCounter - lineFootnotes.size + fnIdx + 1, text))
            }
        }
    }

    if (list.isEmpty()) {
        list.add(HtmlElement.RichParagraph(listOf(RichPart.Text(html.replace(tagRegex, "")))))
    }
    return list
}

/**
 * Estimate Arabic reading time in minutes (≈180 words per minute).
 */
fun estimateReadingTimeMinutes(text: String): Int {
    if (text.isBlank()) return 0
    val words = text.split(whitespaceRegex).count { it.isNotBlank() }
    return maxOf(1, (words / 180))
}

/**
 * Count words for an article's plain text.
 */
fun countWords(text: String?): Int {
    if (text.isNullOrBlank()) return 0
    return text.split(whitespaceRegex).count { it.isNotBlank() }
}

/**
 * AI response parser — converts Gemini's marker-based text to a rich
 * AnnotatedString for display in chat bubbles.
 */
fun parseAiResponse(
    text: String,
    boldColor: Color = Color.Black,
    hadithColor: Color = Color(0xFF007AFF),
    quranColor: Color = Color(0xFFD32F2F),
    citationColor: Color = Color(0xFF8E8E93),
    citationBg: Color? = null
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        // **bold** — important terms
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end != -1) {
                val content = text.substring(i + 2, end)
                val style = SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)
                pushStringAnnotation(tag = "bold", annotation = content)
                pushStyle(style)
                append(content)
                pop()
                pop()
                i = end + 2
                continue
            }
        }
        // ((hadith)) — hadith text
        if (text.startsWith("((", i)) {
            val end = text.indexOf("))", i + 2)
            if (end != -1) {
                val content = text.substring(i + 2, end)
                val style = SpanStyle(color = hadithColor, fontWeight = FontWeight.Medium)
                pushStringAnnotation(tag = "hadith", annotation = content)
                pushStyle(style)
                append(content)
                pop()
                pop()
                i = end + 2
                continue
            }
        }
        // ﴾ ﴿ quran aaya
        if (text.startsWith("\uFDF0", i)) {
            val end = text.indexOf("\uFDF1", i + 1)
            if (end != -1) {
                val content = text.substring(i + 1, end)
                val style = SpanStyle(color = quranColor, fontWeight = FontWeight.Medium)
                pushStringAnnotation(tag = "quran", annotation = content)
                pushStyle(style)
                append(content)
                pop()
                pop()
                i = end + 1
                continue
            }
        }
        // ££citation££ — citations/references
        if (text.startsWith("££", i)) {
            val end = text.indexOf("££", i + 2)
            if (end != -1) {
                val content = text.substring(i + 2, end)
                val builder = SpanStyle(color = citationColor)
                val finalStyle = if (citationBg != null) {
                    SpanStyle(color = citationColor, background = citationBg)
                } else builder
                pushStringAnnotation(tag = "citation", annotation = content)
                pushStyle(finalStyle)
                append(content)
                pop()
                pop()
                i = end + 2
                continue
            }
        }
        append(text[i])
        i++
    }
}

/**
 * Extracts the §§...§§ summary section from an AI response.
 * Returns Pair(summary, remaining) — summary is null if no marker is found.
 */
fun extractSummarySection(text: String): Pair<String?, String> {
    val marker = "§§"
    val startIndex = text.indexOf(marker)
    if (startIndex == -1) return Pair(null, text)
    val contentStart = startIndex + marker.length
    val endIndex = text.indexOf(marker, contentStart)
    if (endIndex == -1) return Pair(null, text)
    val summary = text.substring(contentStart, endIndex).trim()
    if (summary.isEmpty()) return Pair(null, text)
    val remaining = (text.substring(0, startIndex) + text.substring(endIndex + marker.length)).trim()
    return Pair(summary, remaining)
}
