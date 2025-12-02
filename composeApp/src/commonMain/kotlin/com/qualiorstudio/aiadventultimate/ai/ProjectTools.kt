package com.qualiorstudio.aiadventultimate.ai

import com.qualiorstudio.aiadventultimate.api.DeepSeekFunction
import com.qualiorstudio.aiadventultimate.api.DeepSeekTool
import com.qualiorstudio.aiadventultimate.model.Project
import kotlinx.serialization.json.*
import java.io.File

class ProjectTools(private val project: Project?) {
    
    fun getTools(): List<DeepSeekTool> {
        if (project == null) {
            return emptyList()
        }
        
        return listOf(
            createListFilesTool(),
            createReadFileTool(),
            createWriteFileTool(),
            createSearchInFilesTool(),
            createGetFileInfoTool()
        )
    }
    
    private fun createListFilesTool(): DeepSeekTool {
        return DeepSeekTool(
            type = "function",
            function = DeepSeekFunction(
                name = "list_files",
                description = "Получить список файлов и папок в указанной директории проекта. Возвращает имена файлов/папок с их типами.",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "Относительный путь к директории в проекте (пустая строка для корня проекта)")
                        }
                    }
                    putJsonArray("required") {
                        add("path")
                    }
                }
            )
        )
    }
    
    private fun createReadFileTool(): DeepSeekTool {
        return DeepSeekTool(
            type = "function",
            function = DeepSeekFunction(
                name = "read_file",
                description = "Прочитать содержимое файла из проекта",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "Относительный путь к файлу в проекте")
                        }
                    }
                    putJsonArray("required") {
                        add("path")
                    }
                }
            )
        )
    }
    
    private fun createWriteFileTool(): DeepSeekTool {
        return DeepSeekTool(
            type = "function",
            function = DeepSeekFunction(
                name = "write_file",
                description = "Записать или обновить содержимое файла в проекте",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "Относительный путь к файлу в проекте")
                        }
                        putJsonObject("content") {
                            put("type", "string")
                            put("description", "Содержимое для записи в файл")
                        }
                    }
                    putJsonArray("required") {
                        add("path")
                        add("content")
                    }
                }
            )
        )
    }
    
    private fun createSearchInFilesTool(): DeepSeekTool {
        return DeepSeekTool(
            type = "function",
            function = DeepSeekFunction(
                name = "search_in_files",
                description = "Поиск текста во всех файлах проекта. Возвращает список файлов и строк, содержащих искомый текст.",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", "string")
                            put("description", "Текст для поиска")
                        }
                        putJsonObject("file_pattern") {
                            put("type", "string")
                            put("description", "Шаблон имени файла (например, *.kt, *.java). По умолчанию * (все файлы)")
                        }
                    }
                    putJsonArray("required") {
                        add("query")
                    }
                }
            )
        )
    }
    
    private fun createGetFileInfoTool(): DeepSeekTool {
        return DeepSeekTool(
            type = "function",
            function = DeepSeekFunction(
                name = "get_file_info",
                description = "Получить информацию о файле или директории (размер, дата изменения, тип)",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "Относительный путь к файлу или директории в проекте")
                        }
                    }
                    putJsonArray("required") {
                        add("path")
                    }
                }
            )
        )
    }
    
    suspend fun executeTool(functionName: String, arguments: JsonObject): JsonElement {
        if (project == null) {
            return buildJsonObject {
                put("error", "Проект не открыт")
            }
        }
        
        return when (functionName) {
            "list_files" -> listFiles(arguments)
            "read_file" -> readFile(arguments)
            "write_file" -> writeFile(arguments)
            "search_in_files" -> searchInFiles(arguments)
            "get_file_info" -> getFileInfo(arguments)
            else -> buildJsonObject {
                put("error", "Неизвестная функция: $functionName")
            }
        }
    }
    
    private fun listFiles(arguments: JsonObject): JsonElement {
        val relativePath = arguments["path"]?.jsonPrimitive?.content ?: ""
        val dir = File(project!!.path, relativePath)
        
        if (!dir.exists()) {
            return buildJsonObject {
                put("error", "Директория не существует: $relativePath")
            }
        }
        
        if (!dir.isDirectory) {
            return buildJsonObject {
                put("error", "Указанный путь не является директорией: $relativePath")
            }
        }
        
        val files = dir.listFiles()?.map { file ->
            buildJsonObject {
                put("name", file.name)
                put("type", if (file.isDirectory) "directory" else "file")
                put("size", file.length())
            }
        } ?: emptyList()
        
        return buildJsonObject {
            put("path", relativePath)
            put("files", JsonArray(files))
        }
    }
    
    private fun readFile(arguments: JsonObject): JsonElement {
        val relativePath = arguments["path"]?.jsonPrimitive?.content ?: ""
        val file = File(project!!.path, relativePath)
        
        if (!file.exists()) {
            return buildJsonObject {
                put("error", "Файл не существует: $relativePath")
            }
        }
        
        if (file.isDirectory) {
            return buildJsonObject {
                put("error", "Указанный путь является директорией: $relativePath")
            }
        }
        
        try {
            val content = file.readText()
            return buildJsonObject {
                put("path", relativePath)
                put("content", content)
                put("size", file.length())
            }
        } catch (e: Exception) {
            return buildJsonObject {
                put("error", "Ошибка чтения файла: ${e.message}")
            }
        }
    }
    
    private fun writeFile(arguments: JsonObject): JsonElement {
        val relativePath = arguments["path"]?.jsonPrimitive?.content ?: ""
        val content = arguments["content"]?.jsonPrimitive?.content ?: ""
        val file = File(project!!.path, relativePath)
        
        try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            return buildJsonObject {
                put("success", true)
                put("path", relativePath)
                put("size", file.length())
            }
        } catch (e: Exception) {
            return buildJsonObject {
                put("error", "Ошибка записи файла: ${e.message}")
            }
        }
    }
    
    private fun searchInFiles(arguments: JsonObject): JsonElement {
        val query = arguments["query"]?.jsonPrimitive?.content ?: ""
        val filePattern = arguments["file_pattern"]?.jsonPrimitive?.content ?: "*"
        
        val results = mutableListOf<JsonObject>()
        val projectDir = File(project!!.path)
        
        fun searchInDirectory(dir: File, pattern: String) {
            dir.listFiles()?.forEach { file ->
                when {
                    file.isDirectory && !file.name.startsWith(".") -> {
                        searchInDirectory(file, pattern)
                    }
                    file.isFile && matchesPattern(file.name, pattern) -> {
                        try {
                            val lines = file.readLines()
                            lines.forEachIndexed { index, line ->
                                if (line.contains(query, ignoreCase = true)) {
                                    val relativePath = file.relativeTo(projectDir).path
                                    results.add(buildJsonObject {
                                        put("file", relativePath)
                                        put("line", index + 1)
                                        put("content", line.trim())
                                    })
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }
        
        searchInDirectory(projectDir, filePattern)
        
        return buildJsonObject {
            put("query", query)
            put("pattern", filePattern)
            put("results", JsonArray(results))
            put("total_matches", results.size)
        }
    }
    
    private fun getFileInfo(arguments: JsonObject): JsonElement {
        val relativePath = arguments["path"]?.jsonPrimitive?.content ?: ""
        val file = File(project!!.path, relativePath)
        
        if (!file.exists()) {
            return buildJsonObject {
                put("error", "Файл или директория не существует: $relativePath")
            }
        }
        
        return buildJsonObject {
            put("path", relativePath)
            put("name", file.name)
            put("type", if (file.isDirectory) "directory" else "file")
            put("size", file.length())
            put("last_modified", file.lastModified())
            put("can_read", file.canRead())
            put("can_write", file.canWrite())
        }
    }
    
    private fun matchesPattern(fileName: String, pattern: String): Boolean {
        if (pattern == "*") return true
        
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .toRegex(RegexOption.IGNORE_CASE)
        
        return regex.matches(fileName)
    }
}

