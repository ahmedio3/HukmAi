package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Article
import com.example.ui.theme.*

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
                text = article.title ?: "",
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
