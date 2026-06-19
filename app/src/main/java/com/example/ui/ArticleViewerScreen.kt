package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Article
import com.example.ui.theme.*

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

fun parseHtmlToElements(html: String): List<HtmlElement> {
    if (html.isEmpty()) return emptyList()
    var footnoteCounter = 0

    val list = mutableListOf<HtmlElement>()
    val lines = html.split(Regex("<br\\s*/?>"))

    val spanRegex = Regex(
        """<span class="(title-1|title-2|aaya|hadith|tip)">(.*?)</span>""",
        RegexOption.DOT_MATCHES_ALL
    )
    val otherSpanRegex = Regex("""<span[^>]*>.*?</span>""", RegexOption.DOT_MATCHES_ALL)

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        // Check if this line has a title
        val titleMatch = spanRegex.find(trimmed)
        if (titleMatch != null) {
            val className = titleMatch.groupValues[1]
            val rawContent = titleMatch.groupValues[2].trim()
            val content = rawContent.replace(Regex("<[^>]*>"), "").trim()
            if (className == "title-1" && content.isNotEmpty()) {
                list.add(HtmlElement.Title1(content)); continue
            }
            if (className == "title-2" && content.isNotEmpty()) {
                list.add(HtmlElement.Title2(content)); continue
            }
        }

        // First pass: collect tip footnotes from this line
        val lineFootnotes = mutableListOf<String>()
        for (match in spanRegex.findAll(trimmed)) {
            if (match.groupValues[1] == "tip") {
                val tipText = match.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                if (tipText.isNotEmpty()) {
                    footnoteCounter++
                    lineFootnotes.add(tipText)
                }
            }
        }

        // Build rich paragraph EXCLUDING tip text
        val parts = mutableListOf<RichPart>()
        var lastEnd = 0

        for (match in spanRegex.findAll(trimmed)) {
            val className = match.groupValues[1]
            if (className == "aaya" || className == "hadith") {
                val before = trimmed.substring(lastEnd, match.range.first).trim()
                if (before.isNotEmpty()) {
                    val cleanBefore = before.replace(otherSpanRegex, "").replace(Regex("<[^>]*>"), "").trim()
                    if (cleanBefore.isNotEmpty()) parts.add(RichPart.Text(cleanBefore))
                }
                val content = match.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                if (content.isNotEmpty()) {
                    parts.add(if (className == "aaya") RichPart.Aaya("﴿ $content ﴾") else RichPart.Hadith("« $content »"))
                }
                lastEnd = match.range.last + 1
            } else if (className == "tip") {
                val before = trimmed.substring(lastEnd, match.range.first).trim()
                if (before.isNotEmpty()) {
                    val cleanBefore = before.replace(otherSpanRegex, "").replace(Regex("<[^>]*>"), "").trim()
                    if (cleanBefore.isNotEmpty()) parts.add(RichPart.Text(cleanBefore))
                }
                lastEnd = match.range.last + 1
            }
        }

        val after = trimmed.substring(lastEnd).trim()
        if (after.isNotEmpty()) {
            val cleanAfter = after.replace(otherSpanRegex, "").replace(Regex("<[^>]*>"), "").trim()
            if (cleanAfter.isNotEmpty()) parts.add(RichPart.Text(cleanAfter))
        }

        if (parts.isNotEmpty()) {
            list.add(HtmlElement.RichParagraph(parts))
        }
        // Add footnotes right after the paragraph
        if (lineFootnotes.isNotEmpty()) {
            lineFootnotes.forEachIndexed { fnIdx, text ->
                list.add(HtmlElement.Footnote(footnoteCounter - lineFootnotes.size + fnIdx + 1, text))
            }
        }
    }

    if (list.isEmpty()) {
        list.add(HtmlElement.RichParagraph(listOf(RichPart.Text(html.replace(Regex("<[^>]*>"), "")))))
    }

    return list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleViewerScreen(
    article: Article,
    onClose: () -> Unit
) {
    val items = remember(article.html) { parseHtmlToElements(article.html ?: "") }
    var selectedFootnote by remember { mutableStateOf<Pair<Int, String>?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosSurface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Clean Top Bar — minimal, under status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IosSurface)
                    .statusBarsPadding()
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onClose() }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "الموسوعة",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color(0xFF007AFF),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = article.title ?: "",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = IosTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = IosSeparator, thickness = 0.5.dp)

            // Article Content Flow body
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.Start
            ) {
                items(items.size) { index ->
                    when (val el = items[index]) {
                        is HtmlElement.Title1 -> {
                            Text(
                                text = el.text,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = IosTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                        is HtmlElement.Title2 -> {
                            Text(
                                text = el.text,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = IosTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        is HtmlElement.RichParagraph -> {
                            RichParagraphView(parts = el.parts)
                        }
                        is HtmlElement.Footnote -> {
                            FootnoteView(
                                num = el.num,
                                text = el.text,
                                onClick = { selectedFootnote = Pair(el.num, el.text) }
                            )
                        }
                    }
                }
            }
        }

        // Footnote bottom sheet
        if (selectedFootnote != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedFootnote = null },
                containerColor = IosSurface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "هامش (${selectedFootnote!!.first})",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = IosTextPrimary
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = selectedFootnote!!.second,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = IosTextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Justify
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun RichParagraphView(parts: List<RichPart>) {
    val annotatedText = remember(parts) {
        buildAnnotatedString {
            parts.forEach { part ->
                when (part) {
                    is RichPart.Text -> {
                        pushStyle(SpanStyle(color = IosTextPrimary))
                        append(part.text)
                        pop()
                    }
                    is RichPart.Aaya -> {
                        pushStyle(SpanStyle(color = IslamicDeepGreen, fontWeight = FontWeight.Medium))
                        append(part.text)
                        pop()
                    }
                    is RichPart.Hadith -> {
                        pushStyle(SpanStyle(color = Color(0xFF007AFF), fontWeight = FontWeight.Medium))
                        append(part.text)
                        pop()
                    }
                }
            }
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 15.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Justify
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FootnoteView(
    num: Int,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color(0xFFE5E5EA), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$num",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = IosTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        val truncated = if (text.length > 40) text.take(40) + "..." else text
        Text(
            text = truncated,
            style = MaterialTheme.typography.bodySmall.copy(
                color = IosTextSecondary,
                fontSize = 13.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
