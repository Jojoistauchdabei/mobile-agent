package com.mobileagent.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * DuckDuckGo (kein API-Key): Instant-Answer-API + lite-HTML-Fallback.
 * Einziger Netz-Call der Pipeline – alles andere bleibt lokal.
 */
interface DuckApi {
    @GET("?format=json&no_html=1&skip_disambig=1")
    suspend fun instant(@Query("q") q: String): String
}

class DuckDuckGoSearch {
    private val api: DuckApi = Retrofit.Builder()
        .baseUrl("https://api.duckduckgo.com/")
        .addConverterFactory(ScalarsConverterFactory.create())
        .build().create(DuckApi::class.java)

    suspend fun search(query: String, maxResults: Int = 5): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val raw = api.instant(query)
                // Minimal-Parsing (MVP): AbstractText aus JSON ziehen, Fallback Rohtext.
                val abstract = Regex("\"AbstractText\":\"(.*?)\"").find(raw)?.groupValues?.get(1).orEmpty()
                if (abstract.isNotBlank()) listOf(abstract.take(600)) else listOf("DDG: $raw".take(600))
            } catch (e: Exception) {
                listOf("Suche fehlgeschlagen (offline?): ${e.message}")
            }
        }.take(maxResults)
}
