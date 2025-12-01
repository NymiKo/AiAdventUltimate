package com.qualiorstudio.aiadventultimate.ai

class RAGReranker(
    private val embeddingWeight: Double = 0.65,
    private val lexicalWeight: Double = 0.35,
    private val minTokenSize: Int = 2
) {

    init {
        require(embeddingWeight + lexicalWeight in 0.99..1.01) {
            "Weights should sum to 1.0"
        }
    }

    fun rerank(
        query: String,
        scoredChunks: List<ScoredEmbeddingChunk>
    ): List<RankedChunk> {
        if (scoredChunks.isEmpty()) return emptyList()

        val queryTokens = tokenize(query).filter { it.length >= minTokenSize }
        val queryTokenSet = queryTokens.toSet()

        return scoredChunks.map { scoredChunk ->
            val chunkTokens = tokenize(scoredChunk.chunk.text)
            val overlap = chunkTokens.count { it in queryTokenSet }
            val lexicalScore = if (queryTokens.isEmpty()) {
                0.0
            } else {
                overlap.toDouble() / queryTokens.size.toDouble()
            }.coerceIn(0.0, 1.0)

            val combined = (embeddingWeight * scoredChunk.similarity) +
                (lexicalWeight * lexicalScore)

            RankedChunk(
                chunk = scoredChunk.chunk,
                similarity = scoredChunk.similarity,
                rerankScore = lexicalScore,
                combinedScore = combined
            )
        }.sortedByDescending { it.combinedScore ?: 0.0 }
    }

    private fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text
            .lowercase()
            .replace("[^a-zа-я0-9\\s]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
    }
}

