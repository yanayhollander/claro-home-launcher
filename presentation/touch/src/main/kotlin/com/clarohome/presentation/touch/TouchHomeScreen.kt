package com.clarohome.presentation.touch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.TileAccent

@Composable
fun TouchHomeScreen(
    home: HomeSnapshot,
    onUseClassicMode: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ClaroDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(home.greeting, color = Color.White)
                Text("Wednesday · 20:06", color = Color.White)
            }
            Button(
                onClick = onUseClassicMode,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Text("Classic")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            home.favorites.forEach { person ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(ClaroColors.Surface, CircleShape)
                            .semantics { contentDescription = "${person.displayName}, favorite person" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(person.displayName.take(1), color = ClaroColors.Charcoal)
                    }
                    Text(person.displayName, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
            verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            items(home.tiles, key = { it.id }) { tile ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .semantics { contentDescription = tile.label },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(tile.accent.color()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(tile.label, color = Color.White)
                    }
                }
            }
        }
    }
}

private fun TileAccent.color(): Color = when (this) {
    TileAccent.GREEN -> ClaroColors.Green
    TileAccent.BLUE -> ClaroColors.Blue
    TileAccent.ORANGE -> ClaroColors.Orange
    TileAccent.RED -> ClaroColors.Red
    TileAccent.VIOLET -> ClaroColors.Violet
}
