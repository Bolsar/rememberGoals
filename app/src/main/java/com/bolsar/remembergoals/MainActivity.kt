package com.bolsar.remembergoals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bolsar.remembergoals.ui.theme.NotebookMargin
import com.bolsar.remembergoals.ui.theme.RememberGoalsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RememberGoalsTheme {
                Scaffold(
                    topBar = {
                        Text(
                            "Goals",
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(start = NotebookMargin, top = 24.dp, bottom = 8.dp),
                        )
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) // ponytail: list arrives in chunk 3
                }
            }
        }
    }
}
