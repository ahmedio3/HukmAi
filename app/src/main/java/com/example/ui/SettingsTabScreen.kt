package com.example.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.ui.theme.*
import com.example.viewmodel.FeqhViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabScreen(
    viewModel: FeqhViewModel,
    onShowBookmarks: () -> Unit = {}
) {
    val fontScale by viewModel.fontScale.collectAsState()
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsState()
    val ctx = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .systemBarsPadding()
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
                SettingsSection(
                    icon = Icons.Filled.FormatSize,
                    iconBg = Color(0xFF007AFF),
                    title = "حجم خط القراءة",
                    trailing = {
                        Text(
                            "${(fontScale * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(color = IosTextSecondary)
                        )
                    }
                ) {
                    Slider(
                        value = fontScale,
                        onValueChange = { viewModel.setFontScale(it) },
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

                // Bookmarks
                SettingsSection(
                    icon = Icons.Filled.Bookmark,
                    iconBg = Color(0xFFFFB300),
                    title = "الإشارات المرجعية",
                    trailing = {
                        Text(
                            "${bookmarkedIds.size}",
                            style = MaterialTheme.typography.bodyMedium.copy(color = IosTextSecondary)
                        )
                    },
                    onClick = onShowBookmarks
                )

                HorizontalDivider(color = IosSeparator, modifier = Modifier.padding(start = 56.dp))

                // Notifications Toggle
                var notificationsEnabled by remember { mutableStateOf(true) }
                SettingsSection(
                    icon = Icons.Filled.Notifications,
                    iconBg = Color(0xFFFF3B30),
                    title = "التنبيهات"
                ) {
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

                // Clear cache
                SettingsSection(
                    icon = Icons.Filled.CleaningServices,
                    iconBg = Color(0xFF5856D6),
                    title = "مسح ذاكرة التخزين المؤقتة",
                    onClick = { showClearCacheDialog = true }
                )

                HorizontalDivider(color = IosSeparator, modifier = Modifier.padding(start = 56.dp))

                // Share
                SettingsSection(
                    icon = Icons.Filled.Share,
                    iconBg = Color(0xFF34C759),
                    title = "شارك التطبيق مع أصدقائك",
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "تطبيق Hukm AI - موسوعة فقهية شاملة بالذكاء الاصطناعي"
                            )
                        }
                        ctx.startActivity(Intent.createChooser(intent, "مشاركة التطبيق"))
                    }
                )

                HorizontalDivider(color = IosSeparator, modifier = Modifier.padding(start = 56.dp))

                // Open source
                SettingsSection(
                    icon = Icons.Filled.HistoryEdu,
                    iconBg = Color(0xFF8E8E93),
                    title = "المصادر المفتوحة",
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = IosTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = { /* open licenses screen */ }
                )

                HorizontalDivider(color = IosSeparator, modifier = Modifier.padding(start = 56.dp))

                // About App
                SettingsSection(
                    icon = Icons.Filled.Info,
                    iconBg = Color(0xFF8E8E93),
                    title = "حول التطبيق",
                    trailing = {
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium.copy(color = IosTextSecondary)
                        )
                    },
                    onClick = { showAboutDialog = true }
                )
            }
        }
    }

    // About dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    "تطبيق Hukm AI",
                    color = IosTextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column {
                    Text(
                        text = "موسوعة فقهية رقمية شاملة مبسطة وموثقة مأخوذة من بطون كتب الفقه المعتمدة على المذاهب الأربعة لتسهيل الوصول للأحكام الشرعية والعبادات والمعاملات الفقهية.",
                        color = IosTextPrimary,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "الإصدار ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall.copy(color = IosTextSecondary),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAboutDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إغلاق", color = Color(0xFF007AFF), fontWeight = FontWeight.Medium)
                }
            },
            containerColor = IosSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Clear cache dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("مسح ذاكرة التخزين؟", color = IosTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "سيتم مسح البيانات المخزنة مؤقتاً. لن تتأثر الموسوعة الأساسية أو محفوظاتك.",
                    color = IosTextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    ctx.cacheDir.deleteRecursively()
                }) {
                    Text("مسح", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("إلغاء", color = Color(0xFF007AFF))
                }
            },
            containerColor = IosSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

/**
 * Reusable settings row with optional content trailing.
 */
@Composable
private fun SettingsSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    title: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
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
                        .background(iconBg, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(color = IosTextPrimary)
                )
            }
            trailing?.invoke()
        }
        content()
    }
}
