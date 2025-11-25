package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun FilePickerButton(
    onFilesSelected: (List<String>) -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = { onFilesSelected(emptyList()) },
        enabled = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.UploadFile,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Загрузка файлов не поддерживается на iOS")
    }
}

