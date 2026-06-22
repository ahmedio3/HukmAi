package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Article
import com.example.ui.theme.*
import com.example.util.HtmlElement
import com.example.util.countWords
import com.example.util.estimateReadingTimeMinutes
import com.example.util.parseHtmlToElements
import com.example.viewmodel.FeqhViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleViewerScreen(
    article: Article,
    onClose: () -> Unit,
    viewModel: FeqhViewModel
) {
    val items = remember(article.html) { parseHtmlToElements(article.html ?: "") }
    val plainText = remember(article.text) { article.text ?: "" }
    val wordCount = remember(plainText) { countWords(plainText) }
    val readMinutes = remember(plainText) { estimateReadingTimeMinutes(plainText) }
    var selectedFootnote by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val ctx = LocalContext.current
    val fontScale by viewModel.fontScale.collectAsState()
    val isBookmarked by remember(article.id) {
        derivedStateOf { viewModel.isBookmarked(article.id) }
    }
    val listState = rememberLazyListState()

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
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onClose() }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
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
                Spacer(modifier = Modifier.width(8.dp))
                // Bookmark toggle
                IconButton(
                    onClick = { viewModel.toggleBookmark(article.id) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (isBookmarked) "إزالة الإشارة المرجعية" else "إضافة إشارة مرجعية",
                        tint = if (isBookmarked) Color(0xFFFFB300) else IosTextSecondary
                    )
                }
                // Share button
                IconButton(
                    onClick = {
                        val text = buildString {
                            appendLine(article.title ?: "")
                            appendLine()
                            appendLine(plainText.take(800))
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, article.title ?: "")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        ctx.startActivity(Intent.createChooser(intent, "مشاركة"))
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "مشاركة",
                        tint = IosTextSecondary
                    )
                }
            }

            // Article header — title, word count, read time
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IosSurface)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = article.title ?: "",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = IosTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⏱ $readMinutes د قراءة",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = IosTextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "• $wordCount كلمة",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = IosTextSecondary
                        )
                    )
                }
            }

            HorizontalDivider(color = IosSeparator, thickness = 0.5.dp)

            // Article Content Flow body
            LazyColumn(
                state = listState,
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
                            RichParagraphView(parts = el.parts, fontScale = fontScale)
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
private fun RichParagraphView(parts: List<com.example.util.RichPart>, fontScale: Float) {
    val annotatedText = remember(parts) {
        com.example.util.parseAiResponse("",) // dummy to ensure import
        com.example.ui.theme.IosTextPrimary.let { _ ->
            androidx.compose.ui.text.buildAnnotatedString {
                parts.forEach { part ->
                    when (part) {
                        is com.example.util.RichPart.Text -> {
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = IosTextPrimary))
                            append(part.text)
                            pop()
                        }
                        is com.example.util.RichPart.Aaya -> {
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = IslamicDeepGreen, fontWeight = FontWeight.Medium))
                            append(part.text)
                            pop()
                        }
                        is com.example.util.RichPart.Hadith -> {
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFF007AFF), fontWeight = FontWeight.Medium))
                            append(part.text)
                            pop()
                        }
                    }
                }
            }
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = (15 * fontScale).sp,
            lineHeight = (24 * fontScale).sp,
            textAlign = TextAlign.Justify
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* could be used for selection later */ }
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
