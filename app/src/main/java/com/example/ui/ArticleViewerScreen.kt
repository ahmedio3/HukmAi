package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.LaunchedEffect
import com.example.data.model.Article
import com.example.ui.theme.*
import com.example.util.HtmlElement
import com.example.util.countWords
import com.example.util.parseHtmlToElements
import com.example.viewmodel.FeqhViewModel
import java.util.Locale
import kotlin.math.roundToInt

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
    var selectedFootnote by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val ctx = LocalContext.current
    val fontScale by viewModel.fontScale.collectAsState()

    // FIX 1: Reactive bookmark state — use the StateFlow directly, not a derivedStateOf
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsState()
    val isBookmarked = article.id in bookmarkedIds
    var copyFeedback by remember { mutableStateOf(false) }

    // FIX 2: Real read time tracking
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.getArticleScrollIndex(article.id),
        initialFirstVisibleItemScrollOffset = viewModel.getArticleScrollOffset(article.id)
    )
    val totalItems = items.size
    val scrollProgress = remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty() || totalItems == 0) 0f
            else {
                val firstVisible = visible.first().index
                val lastVisible = visible.last().index
                val visibleCount = (lastVisible - firstVisible + 1).coerceAtLeast(1)
                val pct = (firstVisible + visibleCount / 2f) / totalItems
                pct.coerceIn(0f, 1f)
            }
        }
    }.value

    // Read time = total reading time minus remaining
    val readMinutes = remember(wordCount) {
        val wpm = 180  // average Arabic reading speed
        val totalSec = (wordCount * 60) / wpm
        val totalMin = (totalSec / 60.0).roundToInt().coerceAtLeast(1)
        totalMin
    }
    val remainingMinutes = remember(scrollProgress, readMinutes) {
        ((1f - scrollProgress) * readMinutes).toInt().coerceAtLeast(1)
    }

    // TTS state
    val context = LocalContext.current
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsAvailable by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale("ar")
            } else {
                ttsAvailable = false
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // Save scroll position when leaving the article
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val info = listState.layoutInfo
                if (info.visibleItemsInfo.isNotEmpty()) {
                    val firstVisible = info.visibleItemsInfo.first().index
                    val offset = info.visibleItemsInfo.first().offset
                    viewModel.saveArticleScroll(article.id, firstVisible, offset)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosSurface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact top bar — only buttons, title collapses on scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IosSurface)
                    .statusBarsPadding()
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button (compact)
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color(0xFF007AFF)
                    )
                }

                // In-bar title that fades on scroll
                Text(
                    text = article.title ?: "",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = IosTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .alpha(1f - scrollProgress)
                )

                // Font size controls (compact)
                IconButton(
                    onClick = { viewModel.setFontScale((fontScale - 0.1f).coerceAtLeast(0.8f)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextDecrease,
                        contentDescription = "تصغير الخط",
                        tint = IosTextSecondary
                    )
                }
                IconButton(
                    onClick = { viewModel.setFontScale((fontScale + 0.1f).coerceAtMost(1.4f)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextIncrease,
                        contentDescription = "تكبير الخط",
                        tint = IosTextSecondary
                    )
                }

                // TTS button
                if (ttsAvailable) {
                    IconButton(
                        onClick = {
                            val tts = ttsEngine
                            if (tts != null) {
                                if (isSpeaking) {
                                    tts.stop()
                                    isSpeaking = false
                                } else {
                                    tts.speak(plainText, TextToSpeech.QUEUE_FLUSH, null, "article_${article.id}")
                                    isSpeaking = true
                                }
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.RecordVoiceOver,
                            contentDescription = if (isSpeaking) "إيقاف القراءة" else "استمع للمقال",
                            tint = if (isSpeaking) Color(0xFFFF3B30) else IosTextSecondary
                        )
                    }
                }

                // Bookmark toggle (instant reactive state)
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

                // Copy button (replaces share)
                IconButton(
                    onClick = {
                        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = buildString {
                            appendLine(article.title ?: "")
                            appendLine("---")
                            append(plainText)
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText("مقال فقهي", text))
                        copyFeedback = true
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (copyFeedback) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "نسخ المقال",
                        tint = if (copyFeedback) Color(0xFF34C759) else IosTextSecondary
                    )
                }
            }

            // Reading progress bar (slim, real-time)
            LinearProgressIndicator(
                progress = { scrollProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color(0xFF007AFF),
                trackColor = IosSeparator.copy(alpha = 0.3f)
            )

            // Collapsible header — large title, metadata, fades on scroll
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IosSurface)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .alpha(1f - scrollProgress * 0.7f)
            ) {
                Text(
                    text = article.title ?: "",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = IosTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                )
                if (scrollProgress < 0.5f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Read time (real, scroll-aware)
                        Text(
                            text = if (scrollProgress > 0.05f) {
                                "⏱ باقي $remainingMinutes د"
                            } else {
                                "⏱ $readMinutes د قراءة"
                            },
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
            }

            // Article content
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
                                    fontSize = (20 * fontScale).sp
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
                                    fontSize = (17 * fontScale).sp
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

        // Snackbar-like copy feedback (overlay at top)
        AnimatedVisibility(
            visible = copyFeedback,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1C1C1E),
                shadowElevation = 6.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تم نسخ المقال",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
    // Auto-dismiss the copy feedback
    LaunchedEffect(copyFeedback) {
        if (copyFeedback) {
            kotlinx.coroutines.delay(1500)
            copyFeedback = false
        }
    }
}

@Composable
private fun RichParagraphView(parts: List<com.example.util.RichPart>, fontScale: Float) {
    val annotatedText = remember(parts) {
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

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = (15 * fontScale).sp,
            lineHeight = (24 * fontScale).sp,
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
