package com.clarohome.core.design

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun ClaroContactPhotoPicker(name: String, photoUri: String?, onPhotoPicked: (String) -> Unit) {
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let { onPhotoPicked(it.toString()) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClaroAvatar(name = name, photoUri = photoUri, size = 48.dp)
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }) {
            Text(stringResource(if (photoUri == null) R.string.design_add_photo else R.string.design_change_photo))
        }
    }
}
