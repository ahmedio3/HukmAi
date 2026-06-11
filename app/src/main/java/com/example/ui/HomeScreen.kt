package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Article
import com.example.data.model.TreeNode
import com.example.ui.theme.*
import com.example.viewmodel.AppTab
import com.example.viewmodel.FeqhViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MainAppScreen(viewModel: FeqhViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val activeArticle by viewModel.activeArticle.collectAsState()
    val categoryStack by viewModel.categoryStack.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    // Enforce full RTL layout direction matching requested Sharia design guidelines
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        
        // Handle android system back button stack navigation interceptors
        if (activeArticle != null) {
            BackHandler { viewModel.closeArticle() }
        } else if (categoryStack.isNotEmpty()) {
            BackHandler { viewModel.popCategory() }
        } else if (isSearching) {
            BackHandler { viewModel.clearSearch() }
        }

        val view = androidx.compose.ui.platform.LocalView.current
        val context = view.context
        if (!view.isInEditMode) {
            SideEffect {
                val window = (context as? android.app.Activity)?.window
                if (window != null) {
                    val color = if (activeArticle != null || currentTab == AppTab.AI) {
                        IosSurface
                    } else {
                        IosBackground
                    }
                    window.statusBarColor = android.graphics.Color.rgb(
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt()
                    )
                    val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = true
                }
            }
        }

        Scaffold(
            bottomBar = {
                if (!androidx.compose.foundation.layout.WindowInsets.isImeVisible) {
                    ElegantBottomBar(
                        currentTab = currentTab,
                        onTabSelected = { viewModel.setTab(it) }
                    )
                }
            },
            containerColor = BookParchmentBg,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Main visual tabs routing
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "tab_transitions"
                ) { targetTab ->
                    when (targetTab) {
                        AppTab.HOME -> HomeTabScreen(viewModel = viewModel)
                        AppTab.AI -> AiTabScreen(viewModel = viewModel)
                        AppTab.SETTINGS -> SettingsTabScreen()
                    }
                }

                // Foreground overlay layer for Article Viewer Screen
                AnimatedVisibility(
                    visible = activeArticle != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    activeArticle?.let { article ->
                        ArticleViewerScreen(
                            article = article,
                            onClose = { viewModel.closeArticle() }
                        )
                }
            }
        }
        }
    }

    // ── Sources Bottom Sheet ──
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
fun AiThinkingIndicator(progress: com.example.data.api.AiProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Animated dots
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            // Pulsing dots
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

            // Progress text
            val progressText = when (progress) {
                is com.example.data.api.AiProgress.ReceivedQuestion -> "تم استلام السؤال..."
                is com.example.data.api.AiProgress.AnalyzingScope -> "جاري تحليل السؤال..."
                is com.example.data.api.AiProgress.BooksSelected -> "تم تحديد الكتب: ${progress.bookNames.take(2).joinToString("، ")}"
                is com.example.data.api.AiProgress.SelectingBabs -> "جاري البحث في الأبواب..."
                is com.example.data.api.AiProgress.BabsSelected -> "تم تحديد ${progress.babNames.size} أبواب"
                is com.example.data.api.AiProgress.SelectingTopics -> "جاري تحديد الموضوعات..."
                is com.example.data.api.AiProgress.TopicsSelected -> "تم اختيار ${progress.count} موضوعات"
                is com.example.data.api.AiProgress.PreparingSources -> "تجهيز المصادر..."
                is com.example.data.api.AiProgress.GeneratingAnswer -> "توليد الإجابة..."
                is com.example.data.api.AiProgress.OutOfScope -> progress.reason
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

// ==========================================
// TAB 3: SETTINGS TAB STATIC VIEW
// ==========================================
@Composable
fun SettingsTabScreen() {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var scaleValue by remember { mutableFloatStateOf(1.0f) }
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .padding(top = 24.dp)
    ) {
        Text(
            text = "الإعدادات",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                color = IosTextPrimary
            ),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        // Rounded card holding settings items
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = IosSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Font Size Settings
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF007AFF), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FormatSize, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("حجم خط القراءة", style = MaterialTheme.typography.bodyLarge.copy(color = IosTextPrimary))
                        }
                        Text("${(scaleValue * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium.copy(color = IosTextSecondary))
                    }
                    Slider(
                        value = scaleValue,
                        onValueChange = { scaleValue = it },
                        valueRange = 0.8f..1.4f,
                        colors = SliderDefaults.colors(
                            thumbColor = IosSurface,
                            activeTrackColor = Color(0xFF007AFF),
                            inactiveTrackColor = IosSeparator
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }

                HorizontalDivider(color = IosSeparator, modifier = Modifier.padding(start = 56.dp))

                // Notifications Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFFFF3B30), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("التنبيهات", style = MaterialTheme.typography.bodyLarge.copy(color = IosTextPrimary))
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = IosSurface,
                            checkedTrackColor = Color(0xFF34C759),
                            uncheckedThumbColor = IosSurface,
                            uncheckedTrackColor = IosSeparator,
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }

                HorizontalDivider(color = IosSeparator, modifier = Modifier.padding(start = 56.dp))

                // About App
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDialog = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFF8E8E93), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("حول التطبيق", style = MaterialTheme.typography.bodyLarge.copy(color = IosTextPrimary))
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = IosTextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = {
                    Text("تطبيق Hukm AI", color = IosTextPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                },
                text = {
                    Text(
                        text = "موسوعة فقهية رقمية شاملة مبسطة وموثقة مأخوذة من بطون كتب الفقه المعتمدة على المذاهب الأربعة لتسهيل الوصول للأحكام الشرعية والعبادات والمعاملات الفقهية.\nنسخة 1.0.0",
                        color = IosTextPrimary,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("إغلاق", color = Color(0xFF007AFF), fontWeight = FontWeight.Medium)
                    }
                },
                containerColor = IosSurface,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

// ==========================================
// AI RESPONSE TEXT PARSER
// The AI uses special markers:
//   **bold**       → Bold text
//   ((hadith))     → Blue text (hadith quotes)
//   ﴿quran﴾       → Red text (quran verses)
//   ££citation££   → Gray text (citations / تخريجات / هوامش)
//   §§summary§§    → Extracted into خلاصة القول card (handled before this)
// ==========================================
private fun parseAiResponse(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            // Check for bold **
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
            // Check for hadith ((
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
            // Check for quran ﴿
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
            // Check for citation ££...££ → gray text (no bg, normal size)
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

/**
 * Extracts the خلاصة القول section from §§...§§ markers.
 * Returns (summaryText, remainingTextWithoutMarkers).
 */
private fun extractSummarySection(text: String): Pair<String?, String> {
    val marker = "§§"
    val startIndex = text.indexOf(marker)
    if (startIndex == -1) return Pair(null, text)

    val contentStart = startIndex + marker.length
    val endIndex = text.indexOf(marker, contentStart)
    if (endIndex == -1) return Pair(null, text)

    val summary = text.substring(contentStart, endIndex).trim()
    if (summary.isEmpty()) return Pair(null, text)

    // Remove the §§...§§ section from the text
    val remaining = (text.substring(0, startIndex) + text.substring(endIndex + marker.length)).trim()
    return Pair(summary, remaining)
}

// ==========================================
// ELEGANT BOTTOM TAB BAR COMPONENT
// ==========================================
@Composable
fun ElegantBottomBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    Column {
        HorizontalDivider(color = IosSeparator, thickness = 0.5.dp)
        NavigationBar(
            containerColor = IosSurface,
            tonalElevation = 0.dp,
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(64.dp)
        ) {
            NavigationBarItem(
                selected = currentTab == AppTab.HOME,
                onClick = { onTabSelected(AppTab.HOME) },
                label = { Text("الموسوعة", fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = IbmPlexArabicFontFamily) },
                icon = {
                    Icon(
                        imageVector = if (currentTab == AppTab.HOME) Icons.Default.MenuBook else Icons.Outlined.MenuBook,
                        contentDescription = "Home"
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = IslamicDeepGreen,
                    selectedTextColor = IslamicDeepGreen,
                    unselectedIconColor = IosTextSecondary,
                    unselectedTextColor = IosTextSecondary,
                    indicatorColor = IosSurface
                )
            )

            NavigationBarItem(
                selected = currentTab == AppTab.AI,
                onClick = { onTabSelected(AppTab.AI) },
                label = { Text("الذكاء الاصطناعي", fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = IbmPlexArabicFontFamily) },
                icon = {
                    Icon(
                        imageVector = if (currentTab == AppTab.AI) Icons.Default.AutoAwesome else Icons.Outlined.AutoAwesome,
                        contentDescription = "AI"
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = IslamicDeepGreen,
                    selectedTextColor = IslamicDeepGreen,
                    unselectedIconColor = IosTextSecondary,
                    unselectedTextColor = IosTextSecondary,
                    indicatorColor = IosSurface
                )
            )

            NavigationBarItem(
                selected = currentTab == AppTab.SETTINGS,
                onClick = { onTabSelected(AppTab.SETTINGS) },
                label = { Text("الإعدادات", fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = IbmPlexArabicFontFamily) },
                icon = {
                    Icon(
                        imageVector = if (currentTab == AppTab.SETTINGS) Icons.Default.Settings else Icons.Outlined.Settings,
                        contentDescription = "Settings"
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = IslamicDeepGreen,
                    selectedTextColor = IslamicDeepGreen,
                    unselectedIconColor = IosTextSecondary,
                    unselectedTextColor = IosTextSecondary,
                    indicatorColor = IosSurface
                )
            )
        }
    }
}
