package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

@Suppress("UNCHECKED_CAST")
class ExampleProvider : MainAPI() {
    override var name = "DhakaMovie BDIX"
    override var lang = "bn"
    override var mainUrl = "http://dhakamovie.com:8080"
    override val hasMainPage = false
    override val hasQuickSearch = true

    private val apiEndpoint = "$mainUrl/api/movies"
    private val mapper = jacksonObjectMapper()

    // Common headers to mimic a real browser
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Referer" to mainUrl,
        "Accept-Language" to "en-US,en;q=0.9,bn;q=0.8"
    )

    override suspend fun search(query: String): List<SearchResponse> {
        // Note: The API may ignore the 'search' parameter.
        // We'll fetch the first page and filter locally.
        val url = "$apiEndpoint?page=1"
        val response = app.get(url, headers = headers).text
        val json = mapper.readValue<Map<String, Any>>(response)
        val movies = json["data"] as? List<Map<String, Any>> ?: return emptyList()

        // Local filtering by title (case‑insensitive)
        val filtered = movies.filter { movie ->
            val title = movie["title"] as? String ?: ""
            title.contains(query, ignoreCase = true)
        }

        return filtered.mapNotNull { movie ->
            val id = movie["id"]?.toString() ?: return@mapNotNull null
            val title = movie["title"] as? String ?: return@mapNotNull null
            val poster = movie["poster_url"] as? String ?: ""
            val year = (movie["year"] as? String)?.toIntOrNull()

            newMovieSearchResponse(title, "$apiEndpoint/$id", TvType.Movie, false) {
                this.posterUrl = poster
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url, headers = headers).text
        val movie = mapper.readValue<Map<String, Any>>(response)

        val id = movie["id"]?.toString() ?: throw Error("No id")
        val title = movie["title"] as? String ?: throw Error("No title")
        val plot = movie["overview"] as? String ?: ""
        val year = (movie["year"] as? String)?.toIntOrNull()
        val poster = movie["poster_url"] as? String ?: ""
        val streamUrl = movie["stream_url"] as? String ?: ""

        return newMovieLoadResponse(title, "$apiEndpoint/$id/stream", TvType.Movie, streamUrl) {
            this.plot = plot
            this.year = year
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val idMatch = Regex("/api/movies/(\\d+)/stream").find(data)
        val id = idMatch?.groupValues?.get(1) ?: return false

        val movieUrl = "$apiEndpoint/$id"
        val response = app.get(movieUrl, headers = headers).text
        val movie = mapper.readValue<Map<String, Any>>(response)
        val streamUrl = movie["stream_url"] as? String ?: return false

        val quality = when {
            streamUrl.contains("1080") -> 1080
            streamUrl.contains("720") -> 720
            streamUrl.contains("480") -> 480
            else -> 0
        }

        callback.invoke(
            newExtractorLink(
                source = name,
                name = "Direct",
                url = streamUrl
            ) {
                this.referer = mainUrl
                this.quality = quality
            }
        )
        return true
    }
}