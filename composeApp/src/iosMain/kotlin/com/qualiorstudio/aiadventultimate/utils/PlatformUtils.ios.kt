package com.qualiorstudio.aiadventultimate.utils

import platform.Foundation.NSDate
import kotlin.uuid.Uuid

actual fun currentTimeMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).toLong()
}

actual fun generateUUID(): String {
    return Uuid.random().toString()
}

