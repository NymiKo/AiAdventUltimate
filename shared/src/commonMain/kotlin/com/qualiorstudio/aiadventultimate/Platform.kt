package com.qualiorstudio.aiadventultimate

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform