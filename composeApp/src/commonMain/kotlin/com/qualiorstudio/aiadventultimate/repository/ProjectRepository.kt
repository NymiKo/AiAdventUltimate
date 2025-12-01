package com.qualiorstudio.aiadventultimate.repository

import com.qualiorstudio.aiadventultimate.model.Project
import kotlinx.coroutines.flow.StateFlow

interface ProjectRepository {
    val currentProject: StateFlow<Project?>
    suspend fun openProject(path: String)
    suspend fun closeProject()
    suspend fun loadLastProject()
}

