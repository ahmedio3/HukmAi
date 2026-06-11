package com.example

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import com.example.data.db.FeqhDatabase
import com.example.data.db.ChatDatabase
import com.example.data.repository.FeqhRepository
import com.example.ui.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.FeqhViewModel

class MainActivity : ComponentActivity() {

    private val database by lazy { FeqhDatabase.getDatabase(applicationContext) }
    private val chatDatabase by lazy { ChatDatabase.getDatabase(applicationContext) }
    private val repository by lazy { FeqhRepository(database.feqhDao(), chatDatabase.chatDao()) }
    
    private val viewModel: FeqhViewModel by viewModels {
        FeqhViewModel.Factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        setContent {
            var fontErrorMsg by remember { mutableStateOf<String?>(null) }
            
            LaunchedEffect(Unit) {
                // Diagnose IBM Plex Font manually at startup to extract real programmatic error if any
                val request = FontRequest(
                    "com.google.android.gms.fonts",
                    "com.google.android.gms",
                    "IBM Plex Sans Arabic",
                    R.array.com_google_android_gms_fonts_certs
                )
                
                FontsContractCompat.requestFont(
                    this@MainActivity,
                    request,
                    object : FontsContractCompat.FontRequestCallback() {
                        override fun onTypefaceRetrieved(typeface: Typeface) {
                            // Success! The font downloaded/loaded properly.
                            fontErrorMsg = null
                        }

                        override fun onTypefaceRequestFailed(reason: Int) {
                            val msg = when (reason) {
                                FAIL_REASON_PROVIDER_NOT_FOUND -> "Provider not found (GMS not installed)"
                                FAIL_REASON_WRONG_CERTIFICATES -> "Wrong certificates (Invalid Base64 in font_certs.xml)"
                                FAIL_REASON_FONT_LOAD_ERROR -> "Font load error"
                                FAIL_REASON_SECURITY_VIOLATION -> "Security violation"
                                FAIL_REASON_FONT_NOT_FOUND -> "Font not found on Google Fonts"
                                FAIL_REASON_MALFORMED_QUERY -> "Malformed Query"
                                else -> "Unknown Error Code: $reason"
                            }
                            fontErrorMsg = "فشل تحميل خط IBM Plex من مكتبة Google Fonts:\nالسبب البرمجي: $msg (Code: $reason)"
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            }
            
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
                
                if (fontErrorMsg != null) {
                    val clipboard = LocalClipboardManager.current
                    AlertDialog(
                        onDismissRequest = { fontErrorMsg = null },
                        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                        title = { Text("خطأ في جلب خط IBM Plex") },
                        text = {
                            SelectionContainer {
                                Text(fontErrorMsg ?: "")
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                clipboard.setText(AnnotatedString(fontErrorMsg!!))
                            }) {
                                Text("نسخ الخطأ الفني")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { fontErrorMsg = null }) {
                                Text("تجاهل")
                            }
                        }
                    )
                }
            }
        }
    }
}
