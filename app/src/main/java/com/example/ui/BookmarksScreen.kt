package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Article
import com.example.ui.theme.*
import com.example.viewmodel.FeqhViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    viewModel: FeqhViewModel,
    onClose: () -> Unit,
    onArticleClick: (Int) -> Unit
) {
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsState()
    val scope = rememberCoroutineScope()
    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Load all articles
    LaunchedEffect(bookmarkedIds) {
        isLoading = true
        val loaded = mutableListOf<Article>()
        for (id in bookmarkedIds) {
            val article = viewModel.getArticleByIdSync(id)
            if (article != null) loaded.add(article)
        }
        articles = loaded
        isLoading = false
    }

    val filtered = articles.filter {
        it.title?.contains(searchQuery, ignoreCase = true) == true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IosSurface)
                    .statusBarsPadding()
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color(0xFF007AFF)
                    )
                }
                Text(
                    text = "إشاراتي المرجعية",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = IosTextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                Text(
                    text = "${bookmarkedIds.size}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = IosTextSecondary
                    )
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            // Search bar
            if (bookmarkedIds.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(Color(0xFF767680).copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "بحث",
                            tint = IosTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = IosTextPrimary),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "ابحث في إشاراتك...",
                                        style = MaterialTheme.typography.bodyLarge.copy(color = IosTextSecondary)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            // Content
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = IslamicDeepGreen, modifier = Modifier.size(36.dp))
                }
            } else if (bookmarkedIds.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        tint = IosTextSecondary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "لا توجد إشارات مرجعية بعد",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = IosTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "اضغط على أيقونة الإشارة المرجعية في أي مقال لإضافته هنا. ستجد كل محفوظاتك في هذه الصفحة.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium.copy(color = IosTextSecondary),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "لا توجد نتائج للبحث",
                        style = MaterialTheme.typography.bodyLarge.copy(color = IosTextSecondary)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(filtered) { index, article ->
                        BookmarkCard(
                            article = article,
                            onClick = { onArticleClick(article.id) },
                            onRemove = { viewModel.toggleBookmark(article.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(
    article: Article,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = IosSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = article.title ?: "مقال بدون عنوان",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = IosTextPrimary,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.BookmarkRemove,
                    contentDescription = "إزالة",
                    tint = IosTextSecondary
                )
            }
        }
    }
}
