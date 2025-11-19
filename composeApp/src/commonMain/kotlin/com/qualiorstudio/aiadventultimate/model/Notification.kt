package com.qualiorstudio.aiadventultimate.model

import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val id: String,
    val title: String,
    val content: String,
    val isExpanded: Boolean = false
)

