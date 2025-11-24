package com.qualiorstudio.aiadventultimate.utils

class HtmlParser {
    fun extractText(html: String): String {
        var text = html
        
        text = text.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("<noscript[^>]*>.*?</noscript>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        
        text = text.replace(Regex("<[^>]+>", RegexOption.IGNORE_CASE), " ")
        
        text = text.replace("&nbsp;", " ")
        text = text.replace("&lt;", "<")
        text = text.replace("&gt;", ">")
        text = text.replace("&amp;", "&")
        text = text.replace("&quot;", "\"")
        text = text.replace("&#39;", "'")
        text = text.replace("&apos;", "'")
        text = text.replace(Regex("&[a-zA-Z]+;", RegexOption.IGNORE_CASE), " ")
        
        text = text.replace(Regex("\\s+"), " ")
        text = text.replace(Regex("\\n\\s*\\n"), "\n\n")
        
        return text.trim()
    }
    
    fun extractTitle(html: String): String? {
        val titleRegex = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val match = titleRegex.find(html)
        return match?.groupValues?.get(1)?.let { extractText(it).trim() }
    }
    
    fun extractMetaDescription(html: String): String? {
        val metaRegex = Regex("<meta[^>]*name=[\"']description[\"'][^>]*content=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE)
        val match = metaRegex.find(html)
        return match?.groupValues?.get(1)?.trim()
    }
}

