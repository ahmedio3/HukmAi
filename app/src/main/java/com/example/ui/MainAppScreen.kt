package com.example.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import com.example.viewmodel.AppTab
import com.example.viewmodel.FeqhViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainAppScreen(viewModel: FeqhViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val activeArticle by viewModel.activeArticle.collectAsState()
    val categoryStack by viewModel.categoryStack.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    var showBookmarks by remember { mutableStateOf(false) }
    var showRandomArticleLoading by remember { mutableStateOf(false) }

    // Enforce full RTL layout direction matching requested Sharia design guidelines
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        
        // Handle android system back button stack navigation interceptors
        if (activeArticle != null) {
            BackHandler { viewModel.closeArticle() }
        } else if (categoryStack.isNotEmpty()) {
            BackHandler { viewModel.popCategory() }
        } else if (isSearching) {
            BackHandler { viewModel.clearSearch() }
        } else if (showBookmarks) {
            BackHandler { showBookmarks = false }
        }

        // Root container — layers content, dock, and overlays
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IosBackground)
        ) {

            // Layer 1: Tab content — REVERSED iOS-style push/pop slide
            // Forward (Home → Settings): Settings enters from RIGHT, Home slides LEFT
            // Back (Settings → Home): Home enters from LEFT, Settings slides RIGHT
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val isForward = targetState.ordinal > initialState.ordinal
                    if (isForward) {
                        // Push: new screen slides in from RIGHT, old exits LEFT
                        (slideInHorizontally(tween(350)) { w -> -w } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(tween(350)) { w -> w / 3 } + fadeOut(tween(200)))
                    } else {
                        // Pop: new screen slides in from LEFT, old exits RIGHT
                        (slideInHorizontally(tween(350)) { w -> w / 3 } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(tween(350)) { w -> -w } + fadeOut(tween(200)))
                    }
                },
                label = "tab_transitions"
            ) { targetTab ->
                when (targetTab) {
                    AppTab.HOME -> HomeTabScreen(viewModel = viewModel)
                    AppTab.AI -> AiTabScreen(viewModel = viewModel)
                    AppTab.SETTINGS -> SettingsTabScreen(
                        viewModel = viewModel,
                        onShowBookmarks = { showBookmarks = true }
                    )
                }
            }

            // Layer 2: Floating dock — true overlay aligned to BottomCenter
            val showDock = !WindowInsets.isImeVisible
                    && activeArticle == null
                    && currentTab != AppTab.AI
            AnimatedVisibility(
                visible = showDock,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ElegantBottomBar(
                        currentTab = currentTab,
                        onTabSelected = { viewModel.setTab(it) }
                    )
                }
            }

            // Layer 3: AI back button — iOS-style circle, dark chevron
            AnimatedVisibility(
                visible = currentTab == AppTab.AI && activeArticle == null,
                enter = fadeIn(tween(250)) + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
                ),
                exit = fadeOut(tween(200)) + scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(200)
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 6.dp, start = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(4.dp, CircleShape)
                        .background(IosSurface, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.setTab(AppTab.HOME) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = "العودة للموسوعة",
                        tint = IosTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Layer 4: Article viewer — top-most overlay
            AnimatedVisibility(
                visible = activeArticle != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                activeArticle?.let { article ->
                    ArticleViewerScreen(
                        article = article,
                        onClose = { viewModel.closeArticle() },
                        viewModel = viewModel
                    )
                }
            }

            // Layer 5: Bookmarks screen — opens from Settings
            AnimatedVisibility(
                visible = showBookmarks,
                enter = slideInHorizontally(tween(350)) { w -> -w } + fadeIn(tween(250)),
                exit = slideOutHorizontally(tween(350)) { w -> -w } + fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                BookmarksScreen(
                    viewModel = viewModel,
                    onClose = { showBookmarks = false },
                    onArticleClick = { id ->
                        showBookmarks = false
                        viewModel.openArticle(id)
                    }
                )
            }
        }
    }
}

@Composable
fun ElegantBottomBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .width(260.dp)
            .height(64.dp)
            .background(
                color = Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(32.dp)
            )
            .border(
                width = 0.5.dp,
                color = Color.Black.copy(alpha = 0.08f),
                shape = RoundedCornerShape(32.dp)
            )
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(32.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.10f)
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Right Tab (RTL order): Home
        DockIcon(
            icon = if (currentTab == AppTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
            contentDescription = "الموسوعة",
            isSelected = currentTab == AppTab.HOME,
            onClick = { onTabSelected(AppTab.HOME) }
        )

        // Center Tab: Search (Replacing AI)
        DockIcon(
            icon = if (currentTab == AppTab.AI) Icons.Filled.Search else Icons.Outlined.Search,
            contentDescription = "الذكاء الاصطناعي",
            isSelected = currentTab == AppTab.AI,
            onClick = { onTabSelected(AppTab.AI) }
        )

        // Left Tab: Archive (representing settings)
        DockIcon(
            icon = if (currentTab == AppTab.SETTINGS) Icons.Filled.Archive else Icons.Outlined.Archive,
            contentDescription = "الإعدادات",
            isSelected = currentTab == AppTab.SETTINGS,
            onClick = { onTabSelected(AppTab.SETTINGS) }
        )
    }
}

@Composable
private fun DockIcon(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dock_icon_scale"
    )
    val view = LocalView.current

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Haptic feedback for tactile feel
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        HapticFeedbackConstants.CONFIRM
                    else
                        HapticFeedbackConstants.VIRTUAL_KEY
                )
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isSelected) Color(0xFF007AFF) else Color(0xFF8E8E93),
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Animated indicator dot
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(Color(0xFF007AFF), CircleShape)
                )
            }
        }
    }
}
