package com.qualiorstudio.aiadventultimate.utils

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeInterval

actual fun formatDate(timestamp: Long): String {
    val date = NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0)
    val formatter = NSDateFormatter()
    formatter.dateFormat = "dd.MM.yyyy HH:mm"
    return formatter.stringFromDate(date) ?: ""
}

