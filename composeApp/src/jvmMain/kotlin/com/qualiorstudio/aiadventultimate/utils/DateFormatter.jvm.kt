package com.qualiorstudio.aiadventultimate.utils

import java.text.SimpleDateFormat
import java.util.*

actual fun formatDate(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

