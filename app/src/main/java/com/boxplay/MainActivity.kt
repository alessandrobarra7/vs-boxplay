package com.boxplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.boxplay.ui.screens.BoxPlayScreen
import com.boxplay.ui.theme.BoxPlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BoxPlayTheme {
                BoxPlayScreen()
            }
        }
    }
}
