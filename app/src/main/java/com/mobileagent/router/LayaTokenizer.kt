package com.mobileagent.router

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

interface LayaTokenizer {
    val clsTokenId: Int
    val sepTokenId: Int
    val maskTokenId: Int
    val padTokenId: Int
    fun encode(text: String): IntArray
}

class JsonLayaTokenizer private constructor(
    private val vocabulary: Map<String, Int>,
    private val merges: Map<Pair<String, String>, Int>,
    private val unknownToken: String,
    override val clsTokenId: Int,
    override val sepTokenId: Int,
    override val maskTokenId: Int,
    override val padTokenId: Int,
) : LayaTokenizer {
    override fun encode(text: String): IntArray {
        val normalized = text.replace(" ", "▁")
        val prepared = if (normalized.startsWith("▁")) normalized else "▁$normalized"
        val pieces = preTokenize(prepared)
        val output = ArrayList<Int>()
        pieces.forEach { piece -> output += bpe(piece).map(::idFor) }
        return output.toIntArray()
    }

    private fun preTokenize(prepared: String): List<String> {
        val segments = prepared.split("▁")
        val pieces = ArrayList<String>()
        segments.forEachIndexed { index, segment ->
            when {
                segment.isNotEmpty() -> pieces += "▁$segment"
                index > 0 -> pieces += "▁"
            }
        }
        return if (pieces.isEmpty()) listOf("▁") else pieces
    }

    private fun bpe(word: String): List<String> {
        var symbols: ArrayList<String> = word.map { character -> character.toString() }.toCollection(ArrayList())
        while (symbols.size > 1) {
            val candidates = ArrayList<Pair<Int, Int>>()
            for (index in 0 until symbols.lastIndex) {
                val rank = merges[Pair(symbols[index], symbols[index + 1])]
                if (rank != null) candidates += rank to index
            }
            val selected = candidates.minWithOrNull(
                compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second },
            ) ?: break
            val pair = Pair(symbols[selected.second], symbols[selected.second + 1])
            val merged = ArrayList<String>()
            var index = 0
            while (index < symbols.size) {
                if (index + 1 < symbols.size && symbols[index] == pair.first && symbols[index + 1] == pair.second) {
                    merged += pair.first + pair.second
                    index += 2
                } else {
                    merged += symbols[index]
                    index++
                }
            }
            symbols = merged
        }
        return symbols
    }

    private fun idFor(token: String): Int = vocabulary[token] ?: vocabulary[unknownToken] ?: 0

    companion object {
        fun load(file: File): JsonLayaTokenizer {
            val root = JSONObject(file.readText(StandardCharsets.UTF_8))
            val model = root.getJSONObject("model")
            val vocabularyJson = model.getJSONObject("vocab")
            val vocabulary = HashMap<String, Int>(vocabularyJson.length() * 2)
            vocabularyJson.keys().forEach { token -> vocabulary[token] = vocabularyJson.getInt(token) }
            val mergesJson = model.optJSONArray("merges") ?: JSONArray()
            val merges = HashMap<Pair<String, String>, Int>(mergesJson.length() * 2)
            for (index in 0 until mergesJson.length()) {
                val value = mergesJson.get(index)
                val pair = when (value) {
                    is JSONArray -> value.getString(0) to value.getString(1)
                    is String -> value.split(" ", limit = 2).let { parts ->
                        if (parts.size == 2) parts[0] to parts[1] else value to value
                    }
                    else -> null
                } ?: continue
                merges[pair] = index
            }
            fun tokenId(name: String, fallback: Int): Int = vocabulary[name] ?: fallback
            return JsonLayaTokenizer(
                vocabulary = vocabulary,
                merges = merges,
                unknownToken = model.optString("unk_token", "<unk>"),
                clsTokenId = tokenId("<bos>", 2),
                sepTokenId = tokenId("<eos>", 1),
                maskTokenId = tokenId("<mask>", 4),
                padTokenId = tokenId("<pad>", 0),
            )
        }
    }
}
