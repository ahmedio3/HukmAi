package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun SettingsTabScreen() {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var scaleValue by remember { mutableFloatStateOf(1.0f) }
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .statusBarsPadding()
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
