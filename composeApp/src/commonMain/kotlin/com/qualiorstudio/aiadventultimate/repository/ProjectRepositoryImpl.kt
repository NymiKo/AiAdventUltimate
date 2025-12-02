package com.qualiorstudio.aiadventultimate.repository

import com.qualiorstudio.aiadventultimate.model.Project
import com.qualiorstudio.aiadventultimate.storage.ProjectStorage
import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class ProjectRepositoryImpl : ProjectRepository {
    private val storage = ProjectStorage()
    private val _currentProject = MutableStateFlow<Project?>(null)
    override val currentProject: StateFlow<Project?> = _currentProject.asStateFlow()

    override suspend fun openProject(path: String) {
        val file = File(path)
        if (!file.exists() || !file.isDirectory) {
            throw IllegalArgumentException("Path does not exist or is not a directory: $path")
        }
        
        val project = Project(
            path = path,
            name = file.name,
            openedAt = currentTimeMillis()
        )
        
        _currentProject.value = project
        storage.saveProject(project)
    }

    override suspend fun closeProject() {
        _currentProject.value = null
        storage.clearProject()
    }

    override suspend fun loadLastProject() {
        val project = storage.loadProject()
        if (project != null) {
            val file = File(project.path)
            if (file.exists() && file.isDirectory) {
                _currentProject.value = project
            } else {
                storage.clearProject()
            }
        }
    }
}

