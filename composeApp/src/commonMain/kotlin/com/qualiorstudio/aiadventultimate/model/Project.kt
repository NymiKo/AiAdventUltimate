package com.qualiorstudio.aiadventultimate.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val path: String,
    val name: String,
    val openedAt: Long = 0
)

