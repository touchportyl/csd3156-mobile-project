
package com.example.tiltmaster.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSelectScreen(
    onBack: () -> Unit,
    onSelectLevel: (Int) -> Unit
) {
    val levels = (1..5).toList() // only 5 levels

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Level") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(levels) { lvl ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectLevel(lvl) }
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Level $lvl")
                        Text("Best: --.--") // later: from Room
                    }
                }
            }
        }
    }
}