package com.mobileagent.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String? = null,
)

interface SearchClient {
    suspend fun search(query: String, maxResults: Int = 5): List<SearchResult>
}

class DuckDuckGoSearch(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
) : SearchClient {
    override suspend fun search(query: String, maxResults: Int): List<SearchResult> =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val request = Request.Builder()
                .url("https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q=$encoded")
                .header("User-Agent", "MobileAgent/0.1")
                .build()
            try {
                execute(request, maxResults)
            } catch (error: CancellationException) {
                throw error
            }
        }

    private suspend fun execute(request: Request, maxResults: Int): List<SearchResult> =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                            val result = parseResponse(it.body?.string().orEmpty(), maxResults)
                            if (continuation.isActive) continuation.resumeWith(Result.success(result))
                        }
                    } catch (error: Throwable) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                    }
                }
            })
        }

    companion object {
        fun parseResponse(raw: String, maxResults: Int): List<SearchResult> {
            if (raw.isBlank()) return emptyList()
            val results = LinkedHashMap<String, SearchResult>()
            runCatching {
                val root = JSONObject(raw)
                addResult(
                    results,
                    root.optString("Heading").takeIf { it.isNotBlank() } ?: "DuckDuckGo",
                    root.optString("AbstractText"),
                    root.optString("AbstractURL").takeIf { it.isNotBlank() },
                )
                val related = root.optJSONArray("RelatedTopics")
                collectTopics(related, results, 0)
            }.onFailure {
                if (results.isEmpty()) {
                    results["raw"] = SearchResult("Websuche", raw.take(600))
                }
            }
            return results.values.take(maxResults.coerceAtLeast(0))
        }

        private fun collectTopics(
            topics: JSONArray?,
            results: MutableMap<String, SearchResult>,
            depth: Int,
        ) {
            if (topics == null || depth > 3) return
            for (index in 0 until topics.length()) {
                val topic = topics.optJSONObject(index) ?: continue
                val text = topic.optString("Text")
                if (text.isNotBlank()) {
                    addResult(
                        results,
                        topic.optString("Name").takeIf { it.isNotBlank() } ?: "DuckDuckGo",
                        text,
                        topic.optString("FirstURL").takeIf { it.isNotBlank() },
                    )
                }
                collectTopics(topic.optJSONArray("Topics"), results, depth + 1)
            }
        }

        private fun addResult(
            results: MutableMap<String, SearchResult>,
            title: String,
            snippet: String,
            url: String?,
        ) {
            val cleanSnippet = snippet.trim()
            if (cleanSnippet.isBlank()) return
            val key = url ?: "$title|$cleanSnippet"
            results.putIfAbsent(key, SearchResult(title, cleanSnippet.take(800), url))
        }
    }
}
