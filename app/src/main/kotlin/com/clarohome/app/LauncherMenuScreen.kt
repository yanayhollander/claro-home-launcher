package com.clarohome.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.model.HomeTile

@Composable
fun LauncherMenuScreen(
    tiles: List<HomeTile>,
    onBack: () -> Unit,
    onTileSelected: (HomeTile) -> Unit,
    onUseTouchStyle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClaroColors.Charcoal)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Menu",
            modifier = Modifier.semantics { heading() },
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(tiles, key = { it.id }) { tile ->
                Button(
                    onClick = { onTileSelected(tile) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ClaroDimensions.PrimaryTarget),
                ) {
                    Text(tile.label, textAlign = TextAlign.Center)
                }
            }
            item {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ClaroDimensions.PrimaryTarget),
                ) {
                    Text("Contacts")
                }
            }
            item {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ClaroDimensions.PrimaryTarget),
                ) {
                    Text("Protected Settings")
                }
            }
        }
        Button(
            onClick = onUseTouchStyle,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ClaroDimensions.PrimaryTarget),
        ) {
            Text("Use Touch style")
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ClaroDimensions.PrimaryTarget),
        ) {
            Text("Back")
        }
    }
}
