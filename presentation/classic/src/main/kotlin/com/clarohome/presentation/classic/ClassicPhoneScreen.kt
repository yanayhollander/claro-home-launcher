package com.clarohome.presentation.classic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.model.HomeSnapshot

@Composable
fun ClassicPhoneScreen(
    home: HomeSnapshot,
    onUseTouchMode: () -> Unit,
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
                Text("20:06", color = Color.White)
                Text("Wednesday", color = Color.White)
            }
            Button(onClick = onUseTouchMode) { Text("Touch") }
        }
        Text("People", color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            home.favorites.forEach { Text(it.displayName, color = Color.White) }
        }
        Text("Messages · SOS", color = Color.White)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .height(268.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")) { key ->
                Button(
                    onClick = {},
                    modifier = Modifier.height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Surface),
                ) {
                    Text(key, color = ClaroColors.Charcoal)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {},
                modifier = Modifier
                    .widthIn(min = 140.dp)
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Green),
            ) {
                Text("Call")
            }
            Button(
                onClick = {},
                modifier = Modifier
                    .widthIn(min = 140.dp)
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Red),
            ) {
                Text("Home")
            }
        }
    }
}
