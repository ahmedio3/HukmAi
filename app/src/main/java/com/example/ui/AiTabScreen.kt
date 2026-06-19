package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.AiProgress
import com.example.data.model.Article
import com.example.ui.theme.*
import com.example.viewmodel.FeqhViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class AiModel {
    object Gemini : AiModel()
    object NvidiaDeepSeek : AiModel()
    
    val displayName: String
        get() = when (this) {
            is Gemini -> "Gemini"
            is NvidiaDeepSeek -> "DeepSeek V4 (NVIDIA)"
        }
    
    val icon: androidx.compose.ui.graphics.vector.ImageVector
        get() = when (this) {
            is Gemini -> Icons.Default.AutoAwesome
            is NvidiaDeepSeek -> Icons.Default.Memory
        }
    
    companion object {
        fun values(): List<AiModel> = listOf(Gemini, NvidiaDeepSeek)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTabScreen(viewModel: FeqhViewModel) {
    var questionText by remember { mutableStateOf("") }
    val aiProgress by viewModel.aiProgress.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    var showSourcesSheet by remember { mutableStateOf(false) }
    var sourcesForSheet by remember { mutableStateOf<List<Article>>(emptyList()) }
    val focusManager = LocalFocusManager.current
    val savedIndex = viewModel.aiScrollIndex.collectAsState().value
    val savedOffset = viewModel.aiScrollOffset.collectAsState().value
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedIndex,
        initialFirstVisibleItemScrollOffset = savedOffset
    )
    val isAiThinking = aiProgress !is AiProgress.Idle
    val coroutineScope = rememberCoroutineScope()

    // Save scroll position to ViewModel for tab-switch persistence
    LaunchedEffect(Unit) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            viewModel.saveAiScrollPosition(index, offset)
        }
    }

    // Track whether user is at the bottom for scroll-to-bottom button
    val isNearBottom = remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total == 0 || lastVisible >= total - 2
        }.collect { near ->
            isNearBottom.value = near
        }
    }
    
    // AI Model Selection
    var selectedModel by remember { mutableStateOf<AiModel>(AiModel.Gemini) }
    var showModelSelector by remember { mutableStateOf(false) }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(chatMessages.size, aiProgress) {
        if (chatMessages.isNotEmpty()) {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            val nearBottom = total == 0 || lastVisible >= total - 2
            if (nearBottom) {
                delay(100)
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Messages area
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (chatMessages.isEmpty() && !isAiThinking) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF007AFF),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "اسأل المفتي الذكي أي سؤال فقهي،\nوسيجيبك بالاعتماد على الموسوعة الفقهية فقط.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge.copy(color = IosTextSecondary)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 85.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(chatMessages) { index, msg ->
                            if (msg.role == "user") {
                                UserChatBubble(
                                    message = msg.content,
                                    timestamp = msg.timestamp
                                )
                            } else {
                                val isError = msg.content.startsWith("⚠️")
                                AiChatMessage(
                                    message = msg.content,
                                    sourcesJson = if (!isError) msg.sourcesJson else null,
                                    timestamp = msg.timestamp,
                                    isError = isError,
                                    onViewSources = {
                                        coroutineScope.launch {
                                            val articles = viewModel.loadSourcesFromJson(msg.sourcesJson)
                                            if (articles.isNotEmpty()) {
                                                sourcesForSheet = articles
                                                showSourcesSheet = true
                                            }
                                        }
                                    },
                                    onRetry = { viewModel.retryLastQuestion() }
                                )
                            }
                        }
                        if (isAiThinking) {
                            item {
                                AiThinkingIndicator(progress = aiProgress)
                            }
                        }
                    }
                }

                // Bottom fade gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    IosBackground.copy(alpha = 0f),
                                    IosBackground.copy(alpha = 0.45f),
                                    IosBackground
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )

                // Top fade gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(75.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    IosBackground,
                                    IosBackground.copy(alpha = 0f)
                                )
                            )
                        )
                )
            }

            // Scroll-to-Bottom Button
            AnimatedVisibility(
                visible = !isNearBottom.value && chatMessages.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 130.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(6.dp, CircleShape)
                        .background(IosSurface, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = "التمرير لأسفل",
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // iOS-Style Floating Input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = IosSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Send Button (RTL = right side)
                        val canSend = questionText.isNotBlank() && !isAiThinking
                        val scaleAnim = remember { Animatable(1f) }
                        
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer {
                                    scaleX = scaleAnim.value
                                    scaleY = scaleAnim.value
                                }
                                .background(
                                    if (canSend)
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF007AFF), Color(0xFF0055CC))
                                        )
                                    else
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFFE2E2E7), Color(0xFFE2E2E7))
                                        ),
                                    shape = CircleShape
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = canSend
                                ) {
                                    coroutineScope.launch {
                                        scaleAnim.snapTo(1f)
                                        scaleAnim.animateTo(0.85f, tween(80))
                                        scaleAnim.animateTo(1.05f, tween(100))
                                        scaleAnim.animateTo(1f, tween(60))
                                    }
                                    focusManager.clearFocus()
                                    viewModel.submitAiQuestion(questionText)
                                    questionText = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = "إرسال",
                                tint = if (canSend) Color.White else IosTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Text Input
                        Box(modifier = Modifier.weight(1f)) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = questionText,
                                onValueChange = { questionText = it },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = IosTextPrimary,
                                    fontSize = 16.sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 32.dp, max = 100.dp),
                                maxLines = 5,
                                decorationBox = { innerTextField ->
                                    if (questionText.isEmpty()) {
                                        Text(
                                            text = "اسأل المفتي الذكي...",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = IosTextSecondary,
                                                fontSize = 16.sp
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Model Selector Button
                        Box {
                            IconButton(
                                onClick = { showModelSelector = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = selectedModel.icon,
                                    contentDescription = "اختيار النموذج",
                                    tint = Color(0xFF007AFF),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showModelSelector,
                                onDismissRequest = { showModelSelector = false }
                            ) {
                                AiModel.values().forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = model.icon,
                                                    contentDescription = null,
                                                    tint = if (model == selectedModel) Color(0xFF007AFF) else IosTextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = model.displayName,
                                                    fontWeight = if (model == selectedModel) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedModel = model
                                            showModelSelector = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sources Bottom Sheet
        if (showSourcesSheet && sourcesForSheet.isNotEmpty()) {
            ModalBottomSheet(
                onDismissRequest = { showSourcesSheet = false },
                containerColor = IosSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "المصادر المعتمدة",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = IosTextPrimary
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    sourcesForSheet.forEach { src ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSourcesSheet = false
                                    src.id?.let { viewModel.openArticle(it) }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = null,
                                tint = Color(0xFF007AFF),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = src.title ?: "مصدر غير معروف",
                                style = MaterialTheme.typography.bodyMedium.copy(color = IosTextPrimary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserChatBubble(message: String, timestamp: Long) {
    val timeText = remember(timestamp) { formatTimestamp(timestamp) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start // User on right side (RTL start)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    Color(0xFF007AFF),
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 4.dp
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            )
        }
        Text(
            text = timeText,
            color = Color(0xFFC7C7CC),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}

@Composable
fun AiChatMessage(
    message: String,
    sourcesJson: String?,
    timestamp: Long,
    isError: Boolean,
    onViewSources: () -> Unit,
    onRetry: () -> Unit
) {
    val timeText = remember(timestamp) { formatTimestamp(timestamp) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start // AI on left side (RTL start)
    ) {
        if (isError) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .background(
                            Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.removePrefix("⚠️ ").removePrefix("⚠️"),
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 24.sp
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onRetry,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = "↻ إعادة المحاولة",
                    color = Color(0xFF007AFF),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        } else {
            val (summary, remainingText) = remember(message) { extractSummarySection(message) }
            val formattedMainText = remember(remainingText) { parseAiResponse(remainingText) }

            if (summary != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F0E8) // Warm beige
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFFD4A84B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "خلاصة القول",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = IosTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = parseAiResponse(summary),
                            color = IosTextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 26.sp,
                            textAlign = TextAlign.Justify
                        )
                    }
                }
            }

            if (remainingText.isNotEmpty()) {
                ClickableText(
                    text = formattedMainText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 28.sp,
                        textAlign = TextAlign.Justify,
                        color = IosTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    onClick = { offset ->
                        formattedMainText.getStringAnnotations("citation", offset, offset)
                            .firstOrNull()?.let {
                                onViewSources()
                            }
                    }
                )
            }

            if (sourcesJson != null && sourcesJson != "[]" && sourcesJson != "null") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFC8E6C9),
                        modifier = Modifier.clickable { onViewSources() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = Color(0xFF1B5E20),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "المصادر",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF1B5E20),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.KeyboardArrowLeft,
                                contentDescription = null,
                                tint = Color(0xFF1B5E20),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = timeText,
            color = Color(0xFFC7C7CC),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }
}

private fun formatTimestamp(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
    return if (diff < 60_000) {
        "الآن"
    } else if (diff < 3600_000) {
        "${diff / 60_000} د"
    } else if (diff < 86_400_000) {
        sdf.format(java.util.Date(millis))
    } else {
        java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.US).format(java.util.Date(millis))
    }
}

@Composable
fun AiThinkingIndicator(progress: AiProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            repeat(3) { index ->
                val infiniteTransition = rememberInfiniteTransition(label = "dots")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600, delayMillis = index * 200),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dotAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(horizontal = 2.dp)
                        .background(Color(0xFF007AFF).copy(alpha = alpha), shape = CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            val progressText = when (progress) {
                is AiProgress.ReceivedQuestion -> "تم استلام السؤال..."
                is AiProgress.AnalyzingScope -> "جاري تحليل السؤال..."
                is AiProgress.BooksSelected -> "تم تحديد الكتب: ${progress.bookNames.take(2).joinToString("، ")}"
                is AiProgress.SelectingBabs -> "جاري البحث في الأبواب..."
                is AiProgress.BabsSelected -> "تم تحديد ${progress.babNames.size} أبواب"
                is AiProgress.SelectingTopics -> "جاري تحديد الموضوعات..."
                is AiProgress.TopicsSelected -> "تم اختيار ${progress.count} موضوعات"
                is AiProgress.PreparingSources -> "تجهيز المصادر..."
                is AiProgress.GeneratingAnswer -> "توليد الإجابة..."
                is AiProgress.OutOfScope -> progress.reason
                else -> ""
            }

            Text(
                text = progressText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = IosTextSecondary,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

private fun parseAiResponse(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    val content = text.substring(i + 2, end)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        pushStringAnnotation("bold", content)
                        append(content)
                        pop()
                    }
                    i = end + 2
                    continue
                }
            }
            if (text.startsWith("((", i)) {
                val end = text.indexOf("))", i + 2)
                if (end != -1) {
                    val content = text.substring(i + 2, end)
                    withStyle(SpanStyle(color = Color(0xFF007AFF), fontWeight = FontWeight.Medium)) {
                        pushStringAnnotation("hadith", content)
                        append(content)
                        pop()
                    }
                    i = end + 2
                    continue
                }
            }
            if (text.startsWith("\uFDF0", i)) {
                val end = text.indexOf("\uFDF1", i + 1)
                if (end != -1) {
                    val content = text.substring(i + 1, end)
                    withStyle(SpanStyle(color = Color(0xFFD32F2F), fontWeight = FontWeight.Medium)) {
                        pushStringAnnotation("quran", content)
                        append(content)
                        pop()
                    }
                    i = end + 1
                    continue
                }
            }
            if (text.startsWith("££", i)) {
                val end = text.indexOf("££", i + 2)
                if (end != -1) {
                    val content = text.substring(i + 2, end)
                    withStyle(SpanStyle(color = Color(0xFF8E8E93))) {
                        pushStringAnnotation("citation", content)
                        append(content)
                        pop()
                    }
                    i = end + 2
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}

private fun extractSummarySection(text: String): Pair<String?, String> {
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
