package com.example.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TreeNode
import com.example.ui.theme.IosSeparator
import com.example.ui.theme.IosTextPrimary
import com.example.ui.theme.IslamicDeepGreen
import com.example.viewmodel.FeqhViewModel

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
                    isLast = index == rootNodes.lastIndex,
                    ancestorLines = emptySet(),
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
            }
        }
    }
}

@Composable
private fun TreeNodeItem(
    node: TreeNode,
    depth: Int,
    isLast: Boolean,
    ancestorLines: Set<Int>,
    expandedNodes: Set<Int>,
    onToggle: (Int) -> Unit,
    onOpenArticle: (Int) -> Unit
) {
    val isExpanded = expandedNodes.contains(node.id)
    val isLeaf = node.isLeaf == 1
    val lineColor = Color(0xFFB0B0B6)
    val indentWidth = 28.dp
    val strokeWidth = 2.dp

    Column(
        modifier = Modifier.animateContentSize(animationSpec = tween(200))
    ) {
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
                .padding(end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Tree connector lines
                if (depth > 0) {
                    Box(
                        modifier = Modifier
                            .width(indentWidth * depth)
                            .fillMaxHeight()
                            .drawBehind {
                                val indentPx = indentWidth.toPx()
                                val midPx = indentPx / 2f
                                val midY = size.height / 2f
                                val sw = strokeWidth.toPx()

                                // Draw ancestor vertical lines (levels 0 to depth-2)
                                for (a in 0 until depth - 1) {
                                    if (a in ancestorLines) {
                                        val colCenter = (depth - 1 - a) * indentPx + midPx
                                        drawLine(
                                            color = lineColor,
                                            start = Offset(colCenter, 0f),
                                            end = Offset(colCenter, size.height),
                                            strokeWidth = sw
                                        )
                                    }
                                }

                                // Parent level (depth-1, leftmost column) — vertical line only
                                val parentColCenter = midPx
                                if (depth - 1 in ancestorLines) {
                                    drawLine(
                                        color = lineColor,
                                        start = Offset(parentColCenter, 0f),
                                        end = Offset(parentColCenter, size.height),
                                        strokeWidth = sw
                                    )
                                }

                                // Vertical continuation at parent center going down (if not last child or has children)
                                if (!isLast || (!isLeaf && isExpanded)) {
                                    drawLine(
                                        color = lineColor,
                                        start = Offset(parentColCenter, midY),
                                        end = Offset(parentColCenter, size.height),
                                        strokeWidth = sw
                                    )
                                }
                            }
                    )
                }

                // Expand/collapse or leaf icon
                if (!isLeaf) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.ArrowForward,
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

        // Horizontal separator line at end of expanded section
        if (!isLeaf && isExpanded) {
            HorizontalDivider(
                color = IosSeparator,
                modifier = Modifier.padding(start = 56.dp, end = 16.dp)
            )
        }

        // Render children if expanded
        if (!isLeaf && isExpanded) {
            TreeNodeChildren(
                parentId = node.id,
                depth = depth + 1,
                parentIsLast = isLast,
                ancestorLines = ancestorLines + if (!isLast) setOf(depth) else emptySet(),
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
    parentIsLast: Boolean,
    ancestorLines: Set<Int>,
    expandedNodes: Set<Int>,
    onToggle: (Int) -> Unit,
    onOpenArticle: (Int) -> Unit
) {
    val ctx = LocalContext.current
    val dao = remember { com.example.data.db.FeqhDatabase.getDatabase(ctx).feqhDao() }
    var children by remember { mutableStateOf<List<TreeNode>>(emptyList()) }

    LaunchedEffect(parentId) {
        dao.getChildrenNodes(parentId).collect { list ->
            children = list
        }
    }

    children.forEachIndexed { index, child ->
        TreeNodeItem(
            node = child,
            depth = depth,
            isLast = index == children.lastIndex,
            ancestorLines = ancestorLines,
            expandedNodes = expandedNodes,
            onToggle = onToggle,
            onOpenArticle = onOpenArticle
        )
    }
}
