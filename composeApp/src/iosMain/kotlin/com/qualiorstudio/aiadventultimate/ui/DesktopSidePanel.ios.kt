package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeInterval

@Composable
actual fun JsonFilePickerButton(
    onFileSelected: (String?) -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = { onFileSelected(null) },
        enabled = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.UploadFile,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Загрузка JSON файлов не поддерживается на iOS")
    }
}

actual fun formatDateString(dateString: String): String {
    return try {
        val cleanedDate = dateString.trim()
        val isoFormatter = NSDateFormatter()
        isoFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        isoFormatter.timeZone = platform.Foundation.NSTimeZone.timeZoneWithName("UTC")
        
        var date = isoFormatter.dateFromString(cleanedDate)
        if (date == null) {
            isoFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
            date = isoFormatter.dateFromString(cleanedDate)
        }
        if (date == null) {
            isoFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ssZ"
            date = isoFormatter.dateFromString(cleanedDate)
        }
        
        if (date != null) {
            val formatter = NSDateFormatter()
            formatter.dateFormat = "dd.MM.yyyy HH:mm"
            formatter.stringFromDate(date) ?: dateString
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}
