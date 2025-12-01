package com.qualiorstudio.aiadventultimate.utils

import java.util.UUID

actual fun currentTimeMillis(): Long {
    return System.currentTimeMillis()
}

actual fun generateUUID(): String {
    return UUID.randomUUID().toString()
}

actual fun isDesktop(): Boolean {
    return false
}

