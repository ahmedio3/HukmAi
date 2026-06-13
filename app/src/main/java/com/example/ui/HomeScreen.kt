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
import androidx.compose.ui.geometry.Offset
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
import com.example.viewmodel.ViewMode
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
}

// ==========================================
// BROWSE & SEARCH TAB
// ==========================================
@Composable
fun HomeTabScreen(viewModel: FeqhViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val isLoading by viewModel.isCategoriesLoading.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
    ) {
        // iOS Style Search Bar — moved up, no large header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color(0xFFE2E2E7), shape = RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = IosTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = IosTextPrimary),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "بحث عن أحكام...",
                                style = MaterialTheme.typography.bodyLarge.copy(color = IosTextSecondary)
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = IosTextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { viewModel.clearSearch() }
                    )
                }
            }
        }

        // View mode toggle row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (viewMode == ViewMode.TREE) "☰ قائمة" else "🌳 شجرة",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF007AFF),
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { viewModel.toggleViewMode() }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Contents layout loader toggle
        if (isSearching) {
            if (searchLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = IslamicDeepGreen, modifier = Modifier.size(32.dp))
                }
            } else {
                SearchResultsView(
                    searchResults = searchResults,
                    query = searchQuery,
                    onArticleClick = { it.id?.let { id -> viewModel.openArticle(id) } }
                )
            }
        } else {
            if (isLoading) {
                SkeletonLoadingView()
            } else if (viewMode == ViewMode.TREE) {
                CategoryTreeView(viewModel = viewModel)
            } else {
                CategoryHierarchyView(viewModel = viewModel)
            }
        }
    }
}

// ==========================================
// CATEGORY TREE VIEWER
// ==========================================
@Composable
fun CategoryHierarchyView(viewModel: FeqhViewModel) {
    val categoryStack by viewModel.categoryStack.collectAsState()
    val currentSubCategories by viewModel.currentSubCategories.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Path navigation breadcrumbs trail
        BreadcrumbIndicator(
            stack = categoryStack,
            onNavigate = { index -> viewModel.navigateToBreadcrumb(index) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (currentSubCategories.isEmpty()) {
            SkeletonLoadingView()
        } else {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = IosSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(currentSubCategories) { index, node ->
                        CategoryItemCard(
                            node = node,
                            onClick = { viewModel.pushCategory(node) }
                        )
                        if (index < currentSubCategories.lastIndex) {
                            HorizontalDivider(color = IosSeparator, modifier = Modifier.padding(start = 56.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BreadcrumbIndicator(
    stack: List<TreeNode>,
    onNavigate: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Home tab anchor
        Text(
            text = "الرئيسية",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (stack.isEmpty()) FontWeight.Bold else FontWeight.Medium,
                color = if (stack.isEmpty()) IosTextPrimary else Color(0xFF007AFF),
                fontSize = 15.sp
            ),
            modifier = Modifier.clickable { onNavigate(-1) }
        )

        stack.forEachIndexed { index, node ->
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack, // logical RTL back arrow acts as forward chevron
                contentDescription = "Divider",
                tint = IosTextSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .padding(horizontal = 2.dp)
            )
            Text(
                text = node.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (index == stack.lastIndex) FontWeight.Bold else FontWeight.Medium,
                    color = if (index == stack.lastIndex) IosTextPrimary else Color(0xFF007AFF),
                    fontSize = 15.sp
                ),
                modifier = Modifier.clickable { onNavigate(index) }.weight(1f, fill=false)
            )
        }
    }
}

@Composable
fun CategoryItemCard(
    node: TreeNode,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            val isLeaf = node.isLeaf == 1
            val iconAlpha = if (isLeaf) 1f else 0.8f
            Icon(
                imageVector = if (isLeaf) Icons.Outlined.Description else Icons.Outlined.Folder,
                contentDescription = null,
                tint = if (isLeaf) IslamicDeepGreen.copy(alpha = iconAlpha) else Color(0xFF007AFF).copy(alpha = iconAlpha),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = node.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = IosTextPrimary,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowLeft,
            contentDescription = "فتح",
            tint = IosTextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ==========================================
// SEARCH RESULTS VIEW
// ==========================================
@Composable
fun SearchResultsView(
    searchResults: List<Article>,
    query: String,
    onArticleClick: (Article) -> Unit
) {
    if (searchResults.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = IosTextSecondary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "لا توجد نتائج مطابقة لبحثك",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = IosTextPrimary,
                    fontSize = 18.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "جرب كلمات بديلة مثل: صيام، صلاة، وضوء، بيع، أو نصاب الزكاة.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = IosTextSecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "نتائج البحث (${searchResults.size} نتائج):",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = IosTextSecondary,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = IosSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(searchResults) { index, article ->
                        SearchResultCard(
                            article = article,
                            query = query,
                            onClick = { onArticleClick(article) }
                        )
                        if (index < searchResults.lastIndex) {
                            HorizontalDivider(color = IosSeparator, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    article: Article,
    query: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = IosTextPrimary,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = IosTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        
        // Build snippets highlight
        val textSnippet = article.text ?: ""
        val highlightedSnippet = remember(textSnippet, query) {
            highlightText(
                text = if (textSnippet.length > 120) textSnippet.take(120) + "..." else textSnippet,
                query = query,
                highlightColor = HighlightAmber
            )
        }
        
        Text(
            text = highlightedSnippet,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = IosTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Substring highlights builder helper
fun highlightText(
    text: String,
    query: String,
    highlightColor: Color
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    if (query.isEmpty()) {
        builder.append(text)
        return builder.toAnnotatedString()
    }

    val trimmedQuery = query.trim()
    val patternIndex = text.indexOf(trimmedQuery, ignoreCase = true)
    
    if (patternIndex == -1) {
        builder.append(text)
        return builder.toAnnotatedString()
    }

    var lastIndex = 0
    var index = patternIndex
    
    while (index != -1) {
        builder.append(text.substring(lastIndex, index))
        builder.pushStyle(SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold))
        builder.append(text.substring(index, index + trimmedQuery.length))
        builder.pop()
        
        lastIndex = index + trimmedQuery.length
        index = text.indexOf(trimmedQuery, lastIndex, ignoreCase = true)
    }
    
    if (lastIndex < text.length) {
        builder.append(text.substring(lastIndex))
    }
    
    return builder.toAnnotatedString()
}

// ==========================================
// NATIVE HTML ARTISANAL ARTICLE VIEWER
// ==========================================
sealed class HtmlElement {
    data class Title1(val text: String) : HtmlElement()
    data class Title2(val text: String) : HtmlElement()
    data class Aaya(val text: String) : HtmlElement()
    data class Hadith(val text: String) : HtmlElement()
    data class Paragraph(val text: String) : HtmlElement()
    data class Tip(val text: String) : HtmlElement()
}

/**
 * Parser for the feqhia database HTML format.
 * 
 * The HTML uses <br/> as line separators and <span class="xxx"> tags for
 * semantic elements like title-1, title-2, aaya (Quran), hadith, and tip.
 * Lines without special spans are treated as regular paragraphs.
 */
fun parseHtmlToElements(html: String): List<HtmlElement> {
    if (html.isEmpty()) return emptyList()

    val list = mutableListOf<HtmlElement>()

    // Split the HTML into logical lines by <br/> tags
    val lines = html.split(Regex("<br\\s*/?>"))

    // Regex to match opening <span class="known-class">...</span> — non-greedy
    val spanRegex = Regex(
        """<span class="(title-1|title-2|aaya|hadith|tip)">(.*?)</span>""",
        RegexOption.DOT_MATCHES_ALL
    )
    // Regex for other spans we may want to skip or treat as text (sora, etc)
    val otherSpanRegex = Regex("""<span[^>]*>.*?</span>""", RegexOption.DOT_MATCHES_ALL)

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        // Check each line for known span tags
        var lastEnd = 0
        var hasKnownSpan = false

        for (match in spanRegex.findAll(trimmed)) {
            val before = trimmed.substring(lastEnd, match.range.first).trim()
            // Emit text before this span as a paragraph (if non-empty)
            if (before.isNotEmpty()) {
                val cleanBefore = before
                    .replace(otherSpanRegex, "")       // remove any other spans
                    .replace(Regex("<[^>]*>"), "")     // strip remaining tags
                    .trim()
                if (cleanBefore.isNotEmpty()) {
                    list.add(HtmlElement.Paragraph(cleanBefore))
                }
            }

            val className = match.groupValues[1]
            val rawContent = match.groupValues[2].trim()
            // Clean inner content: strip any nested tags (like <a>, <i>)
            val content = rawContent
                .replace(Regex("<[^>]*>"), "")
                .trim()

            when (className) {
                "title-1" -> {
                    list.add(HtmlElement.Title1(content))
                    // Add a small spacer after title-1
                }
                "title-2" -> list.add(HtmlElement.Title2(content))
                "aaya"     -> list.add(HtmlElement.Aaya(content))
                "hadith"   -> list.add(HtmlElement.Hadith(content))
                "tip"      -> list.add(HtmlElement.Tip(content))
            }
            hasKnownSpan = true
            lastEnd = match.range.last + 1
        }

        // Any remaining text after the last known span
        val after = trimmed.substring(lastEnd).trim()
        if (after.isNotEmpty()) {
            val cleanAfter = after
                .replace(otherSpanRegex, "")
                .replace(Regex("<[^>]*>"), "")
                .trim()
            if (cleanAfter.isNotEmpty()) {
                list.add(HtmlElement.Paragraph(cleanAfter))
            }
        }

        // If no known spans at all, treat the whole line as a plain paragraph
        if (!hasKnownSpan) {
            val cleanLine = trimmed
                .replace(Regex("<[^>]*>"), "")
                .trim()
            if (cleanLine.isNotEmpty()) {
                list.add(HtmlElement.Paragraph(cleanLine))
            }
        }
    }

    if (list.isEmpty()) {
        list.add(HtmlElement.Paragraph(html.replace(Regex("<[^>]*>"), "")))
    }

    return list
}

@Composable
fun ArticleViewerScreen(
    article: Article,
    onClose: () -> Unit
) {
    val items = remember(article.html) { parseHtmlToElements(article.html ?: "") }

    Surface(
        color = IosBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Clean Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IosSurface)
                    .padding(vertical = 12.dp, horizontal = 16.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF007AFF),
                    modifier = Modifier.size(24.dp).clickable { onClose() }.padding(4.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = IosTextPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = IosSeparator, thickness = 0.5.dp)

            // Article Content Flow body
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                items(items.size) { index ->
                    when (val el = items[index]) {
                        is HtmlElement.Title1 -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = el.text,
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        color = IosTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        is HtmlElement.Title2 -> {
                            Text(
                                text = el.text,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = IosTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        is HtmlElement.Aaya -> {
                            // Centered beautiful Quranic text block
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "﴿ ${el.text} ﴾",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = IslamicDeepGreen,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 32.sp
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        is HtmlElement.Hadith -> {
                            Text(
                                text = "« ${el.text} »",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFF007AFF),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontStyle = FontStyle.Normal,
                                    lineHeight = 28.sp
                                )
                            )
                        }
                        is HtmlElement.Paragraph -> {
                            Text(
                                text = el.text,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = IosTextPrimary,
                                    fontSize = 16.sp,
                                    lineHeight = 28.sp,
                                    textAlign = TextAlign.Justify
                                )
                            )
                        }
                        is HtmlElement.Tip -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE5F0FF), shape = RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = Color(0xFF007AFF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = el.text,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = IosTextPrimary,
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// AI MODEL SELECTION
// ==========================================
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

// ==========================================
// TAB 2: AI ASSISTANT — CHAT INTERFACE
// ==========================================
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AiTabScreen(viewModel: com.example.viewmodel.FeqhViewModel) {
    var questionText by remember { mutableStateOf("") }
    val aiProgress by viewModel.aiProgress.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    var showSourcesSheet by remember { mutableStateOf(false) }
    var sourcesForSheet by remember { mutableStateOf<List<com.example.data.model.Article>>(emptyList()) }
    var sourceLoadError by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val listState = rememberLazyListState()
    val isAiThinking = aiProgress !is com.example.data.api.AiProgress.Idle
    val coroutineScope = rememberCoroutineScope()
    
    // AI Model Selection
    var selectedModel by remember { mutableStateOf<AiModel>(AiModel.Gemini) }
    var showModelSelector by remember { mutableStateOf(false) }
    
    // DeepSeek-style large input
    var isInputFocused by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new messages arrive or progress changes
    LaunchedEffect(chatMessages.size, aiProgress) {
        if (chatMessages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Messages area — fills all space above input
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
                    contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
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
        }

        // ── DeepSeek-Style Input with AI Model Selector ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // AI Model Selector Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(AiModel.Gemini, AiModel.NvidiaDeepSeek).forEach { model ->
                    val isSelected = model == selectedModel
                    val index = listOf(AiModel.Gemini, AiModel.NvidiaDeepSeek).indexOf(model)
                    if (index > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clickable { selectedModel = model }
                            .background(
                                if (isSelected) Color(0xFF007AFF).copy(alpha = 0.1f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = model.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFF007AFF) else IosTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSelected) Color(0xFF007AFF) else IosTextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }

            // Input Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(
                        Color.White.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isInputFocused) Color(0xFF007AFF).copy(alpha = 0.5f) else IosSeparator.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                .heightIn(min = 32.dp, max = 120.dp),
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

                    Spacer(modifier = Modifier.width(12.dp))

                    // Send Button
                    val canSend = questionText.isNotBlank() && !isAiThinking
                    val scaleAnim = remember { androidx.compose.animation.core.Animatable(1f) }
                    
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
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                enabled = canSend
                            ) {
                                coroutineScope.launch {
                                    scaleAnim.snapTo(1f)
                                    scaleAnim.animateTo(0.85f, androidx.compose.animation.core.tween(80))
                                    scaleAnim.animateTo(1.05f, androidx.compose.animation.core.tween(100))
                                    scaleAnim.animateTo(1f, androidx.compose.animation.core.tween(60))
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
}

@Composable
fun UserChatBubble(message: String, timestamp: Long) {
    val timeText = remember(timestamp) { formatTimestamp(timestamp) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start // User on RIGHT in RTL
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
        // Timestamp below user bubble
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
        horizontalAlignment = Alignment.Start // AI on RIGHT
    ) {
        if (isError) {
            // Error message with retry button
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
            // Extract خلاصة القول section
            val (summary, remainingText) = remember(message) { extractSummarySection(message) }
            val formattedMainText = remember(remainingText) { parseAiResponse(remainingText) }

            // Show خلاصة القول in a nice card if present
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

            // Main AI response text with ClickableText for citations
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

            // Sources button (if available)
            if (sourcesJson != null && sourcesJson != "[]" && sourcesJson != "null") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),  // Fully rounded pill
                        color = Color(0xFFC8E6C9),  // Slightly darker green
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

        // Timestamp
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
// SKELETON LOADING VIEW — shimmer placeholder for encyclopedia
// ==========================================
@Composable
fun SkeletonLoadingView() {
    val shimmerColors = listOf(
        Color(0xFFE2E2E7),
        Color(0xFFF2F2F7),
        Color(0xFFE2E2E7)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value, 0f),
        end = Offset(translateAnim.value + 200f, 0f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(8) { index ->
            val widthFraction = when (index % 3) {
                0 -> 0.92f
                1 -> 0.75f
                else -> 0.85f
            }
            val height = if (index % 4 == 0) 20.dp else 14.dp
            Row(
                modifier = Modifier.fillMaxWidth().height(height),
                horizontalArrangement = Arrangement.Start
            ) {
                // Icon placeholder
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.width(16.dp))
                // Text placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = widthFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
            }
        }
    }
}

// ==========================================
// CATEGORY TREE VIEW — expandable dropdown
// ==========================================
@Composable
fun CategoryTreeView(viewModel: FeqhViewModel) {
    val rootNodes by viewModel.rootCategories.collectAsState()
    var expandedNodes by remember { mutableStateOf(setOf<Int>()) }

    if (rootNodes.isEmpty()) {
        SkeletonLoadingView()
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(rootNodes) { index, node ->
                TreeNodeItem(
                    node = node,
                    depth = 0,
                    expandedNodes = expandedNodes,
                    onToggle = { id ->
                        expandedNodes = if (expandedNodes.contains(id)) {
                            expandedNodes - id
                        } else {
                            expandedNodes + id
                        }
                    },
                    onOpenArticle = { articleId -> viewModel.openArticle(articleId) }
                )
                if (index < rootNodes.lastIndex) {
                    HorizontalDivider(
                        color = IosSeparator,
                        modifier = Modifier.padding(start = 40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeNodeItem(
    node: TreeNode,
    depth: Int,
    expandedNodes: Set<Int>,
    onToggle: (Int) -> Unit,
    onOpenArticle: (Int) -> Unit
) {
    val isExpanded = expandedNodes.contains(node.id)
    val isLeaf = node.isLeaf == 1
    val indentPadding = 16.dp * depth

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isLeaf) {
                        node.articleId?.let(onOpenArticle)
                    } else {
                        onToggle(node.id)
                    }
                }
                .padding(start = 16.dp + indentPadding, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Expand/collapse or leaf icon
                if (!isLeaf) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowLeft,
                        contentDescription = if (isExpanded) "طيّ" else "فتح",
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = IslamicDeepGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = node.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = IosTextPrimary,
                        fontWeight = if (isLeaf) FontWeight.Normal else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Render children if expanded
        if (!isLeaf && isExpanded) {
            TreeNodeChildren(
                parentId = node.id,
                depth = depth + 1,
                expandedNodes = expandedNodes,
                onToggle = onToggle,
                onOpenArticle = onOpenArticle
            )
        }
    }
}

@Composable
private fun TreeNodeChildren(
    parentId: Int,
    depth: Int,
    expandedNodes: Set<Int>,
    onToggle: (Int) -> Unit,
    onOpenArticle: (Int) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { com.example.data.db.FeqhDatabase.getDatabase(ctx).feqhDao() }
    var children by remember { mutableStateOf<List<TreeNode>>(emptyList()) }

    LaunchedEffect(parentId) {
        dao.getChildrenNodes(parentId).collect { list ->
            children = list
        }
    }

    children.forEach { child ->
        TreeNodeItem(
            node = child,
            depth = depth,
            expandedNodes = expandedNodes,
            onToggle = onToggle,
            onOpenArticle = onOpenArticle
        )
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
