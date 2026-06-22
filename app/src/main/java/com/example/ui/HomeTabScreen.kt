package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TreeNode
import com.example.ui.theme.*
import com.example.viewmodel.FeqhViewModel
import com.example.viewmodel.ViewMode
import kotlinx.coroutines.launch

@Composable
fun HomeTabScreen(viewModel: FeqhViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val isLoading by viewModel.isCategoriesLoading.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    var showViewModeMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .systemBarsPadding()
    ) {
        // Title row: "الموسوعة الفقهية" on the right, view mode button on the left
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "الموسوعة الفقهية",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = IosTextPrimary
                )
            )

            // View mode button
            Box {
                Row(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showViewModeMenu = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "شكل العرض",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF007AFF),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Redesigned dropdown as modern Card with vector icons
                DropdownMenu(
                    expanded = showViewModeMenu,
                    onDismissRequest = { showViewModeMenu = false }
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = IosSurface)
                    ) {
                        Column {
                            DropdownMenuItem(
                                text = { Text("قائمة", fontWeight = if (viewMode == ViewMode.LIST) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    viewModel.setViewMode(ViewMode.LIST)
                                    showViewModeMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = if (viewMode == ViewMode.LIST) Color(0xFF007AFF) else IosTextSecondary
                                    )
                                }
                            )
                            HorizontalDivider(color = IosSeparator.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                text = { Text("شجري", fontWeight = if (viewMode == ViewMode.TREE) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    viewModel.setViewMode(ViewMode.TREE)
                                    showViewModeMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.AccountTree,
                                        contentDescription = null,
                                        tint = if (viewMode == ViewMode.TREE) Color(0xFF007AFF) else IosTextSecondary
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // iOS-style search bar
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
                    contentDescription = "Search",
                    tint = IosTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = IosTextPrimary),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "بحث في الموسوعة الفقهية",
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
                            .size(18.dp)
                            .clickable { viewModel.clearSearch() }
                    )
                }
            }
        }

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
            } else {
                // Quick action row: random article + recently viewed
                val recentIds by viewModel.recentlyViewedIds.collectAsState()
                if (recentIds.isNotEmpty() || true) {  // always show this row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Random article
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IosSurface,
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.openRandomArticle() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = null,
                                    tint = Color(0xFF007AFF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "مقال عشوائي",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = IosTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                        // Last opened
                        if (recentIds.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = IosSurface,
                                shadowElevation = 1.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.openArticle(recentIds.first()) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.History,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9500),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "آخر مقال",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = IosTextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (viewMode == ViewMode.TREE) {
                    CategoryTreeView(viewModel = viewModel)
                } else {
                    CategoryHierarchyView(viewModel = viewModel)
                }
            }
        }
    }
}

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
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                colors = CardDefaults.cardColors(containerColor = IosSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = currentSubCategories.map { it.id },
                    transitionSpec = {
                        fadeIn(animationSpec = tween(250)) togetherWith
                        fadeOut(animationSpec = tween(200))
                    },
                    label = "categoryTransition"
                ) { _ ->
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
        val maxVisible = 3
        val showEllipsis = stack.size > maxVisible
        val visibleItems = if (showEllipsis) stack.takeLast(2) else stack

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

        if (showEllipsis) {
            Text(
                text = " / ",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = IosTextSecondary,
                    fontSize = 14.sp
                )
            )
            Text(
                text = "...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = IosTextSecondary,
                    fontSize = 14.sp
                ),
                modifier = Modifier.clickable { onNavigate(-1) }
            )
        }

        val startIndex = if (showEllipsis) stack.size - visibleItems.size else 0
        visibleItems.forEachIndexed { localIndex, node ->
            val globalIndex = startIndex + localIndex
            Text(
                text = " / ",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = IosTextSecondary,
                    fontSize = 14.sp
                )
            )
            Text(
                text = node.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (globalIndex == stack.lastIndex) FontWeight.Bold else FontWeight.Medium,
                    color = if (globalIndex == stack.lastIndex) IosTextPrimary else Color(0xFF007AFF),
                    fontSize = 15.sp
                ),
                modifier = Modifier.clickable { onNavigate(globalIndex) }.weight(1f, fill = false)
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
        // Small dot for leaf articles
        if (node.articleId != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(IslamicDeepGreen, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowLeft,
            contentDescription = "فتح",
            tint = IosTextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
