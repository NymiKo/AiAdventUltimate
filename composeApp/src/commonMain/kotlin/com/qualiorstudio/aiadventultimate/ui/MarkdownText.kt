package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val lines = text.lines()
    val processedBlocks = parseMarkdown(lines)
    
    Column(modifier = modifier) {
        processedBlocks.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is MarkdownBlock.Heading -> {
                    Text(
                        text = block.text,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineLarge
                            2 -> MaterialTheme.typography.headlineMedium
                            3 -> MaterialTheme.typography.headlineSmall
                            4 -> MaterialTheme.typography.titleLarge
                            5 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                is MarkdownBlock.ListItem -> {
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            text = "• ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatInlineMarkdown(block.text),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is MarkdownBlock.NumberedItem -> {
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            text = "${block.number}. ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatInlineMarkdown(block.text),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = formatInlineMarkdown(block.text),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun formatInlineMarkdown(text: String) = buildAnnotatedString {
    var currentIndex = 0
    val boldRegex = """\*\*(.*?)\*\*""".toRegex()
    val italicRegex = """\*(.*?)\*""".toRegex()
    val codeRegex = """`(.*?)`""".toRegex()
    
    val allMatches = mutableListOf<Pair<IntRange, MarkdownStyle>>()
    
    boldRegex.findAll(text).forEach { match ->
        allMatches.add(match.range to MarkdownStyle.Bold)
    }
    italicRegex.findAll(text).forEach { match ->
        if (!allMatches.any { it.first.contains(match.range.first) }) {
            allMatches.add(match.range to MarkdownStyle.Italic)
        }
    }
    codeRegex.findAll(text).forEach { match ->
        allMatches.add(match.range to MarkdownStyle.Code)
    }
    
    allMatches.sortedBy { it.first.first }.forEach { (range, style) ->
        if (currentIndex < range.first) {
            append(text.substring(currentIndex, range.first))
        }
        
        val content = when (style) {
            MarkdownStyle.Bold -> text.substring(range.first + 2, range.last - 1)
            MarkdownStyle.Italic -> text.substring(range.first + 1, range.last)
            MarkdownStyle.Code -> text.substring(range.first + 1, range.last)
        }
        
        withStyle(
            when (style) {
                MarkdownStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                MarkdownStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                MarkdownStyle.Code -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            }
        ) {
            append(content)
        }
        
        currentIndex = range.last + 1
    }
    
    if (currentIndex < text.length) {
        append(text.substring(currentIndex))
    }
}

private fun parseMarkdown(lines: List<String>): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    
    while (i < lines.size) {
        val line = lines[i]
        
        if (line.trim().startsWith("```")) {
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n")))
            i++
        }
        else if (line.trim().startsWith("#")) {
            val level = line.takeWhile { it == '#' }.length
            val text = line.drop(level).trim()
            blocks.add(MarkdownBlock.Heading(level, text))
            i++
        }
        else if (line.trim().matches("""^\d+\.\s+.*""".toRegex())) {
            val number = line.trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 1
            val text = line.trim().dropWhile { it.isDigit() }.drop(1).trim()
            blocks.add(MarkdownBlock.NumberedItem(number, text))
            i++
        }
        else if (line.trim().startsWith("-") || line.trim().startsWith("*") || line.trim().startsWith("+")) {
            val text = line.trim().drop(1).trim()
            blocks.add(MarkdownBlock.ListItem(text))
            i++
        }
        else if (line.trim().isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(line.trim()))
            i++
        }
        else {
            i++
        }
    }
    
    return blocks
}

private sealed class MarkdownBlock {
    data class CodeBlock(val code: String) : MarkdownBlock()
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class ListItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val number: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

private enum class MarkdownStyle {
    Bold, Italic, Code
}

