package com.example.collage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.collage.ui.theme.SnapNestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SnapNestTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val vm: CollageViewModel = viewModel()
                    LaunchUiRoot(vm)
                }
            }
        }
    }
}
