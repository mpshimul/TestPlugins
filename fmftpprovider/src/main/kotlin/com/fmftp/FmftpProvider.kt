package com.fmftp

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

@Suppress("UNCHECKED_CAST")
class FmftpProvider : MainAPI() {
    override var name = "Fmftp BDIX"
    override var lang = "bn"
    override var mainUrl = "https://fmftp.net"
    override val hasMainPage = true
    override val hasQuickSearch = true

    private val apiUrl = "$mainUrl/api/movies?limit=2000&sort=release_date"
    private val mapper = jacksonObjectMapper()

    companion object {
        val movieStore = mutableMapOf<String, MovieData>()
    }

    data class MovieData(
        val title: String,
        val streamUrl: String,
        val poster: String,
        val plot: String,
        val year: Int?,
        val rating: Double?,
        val genres: List<String>,
        val cast: List<String>
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept" to "application/json"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val all = fetchMovies()
        if (all.isEmpty()) return newHomePageResponse(emptyList())

        val latest = all.values.take(30)
        return newHomePageResponse(listOf(HomePageList("Latest Movies", latest)))
    }

    private suspend fun fetchMovies(): Map<String, SearchResponse> {
        return try {
            val response = app.get(apiUrl, headers = headers).text
            val json = mapper.readValue<Map<String, Any>>(response)
            val movies = json["data"] as? List<Map<String, Any>> ?: return emptyMap()

            movies.mapNotNull { movie ->
                val id = movie["id"]?.toString() ?: return@mapNotNull null
                val title = movie["title"] as? String ?: return@mapNotNull null
                val year = (movie["year"] as? String)?.toIntOrNull()
                val plot = movie["overview"] as? String ?: ""
                val rating = (movie["online_rating"] as? Number)?.toDouble()
                val genreStr = movie["genre"] as? String ?: ""
                val genres = genreStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val castsStr = movie["casts"] as? String ?: ""
                val castList = castsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val relativeUrl = movie["url"] as? String ?: ""
                val streamUrl = "$mainUrl$relativeUrl"
                val poster = ""

                val detailUrl = "http://fmftp.local/$id"
                movieStore[detailUrl] = MovieData(
                    title, streamUrl, poster, plot, year, rating, genres, castList
                )

                val searchResp = newMovieSearchResponse(title, detailUrl, TvType.Movie, false) {
                    this.posterUrl = poster
                    this.year = year
                }
                detailUrl to searchResp
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val all = fetchMovies()
        return all.values.filter { it.name?.contains(query, ignoreCase = true) == true }
    }

    override suspend fun load(url: String): LoadResponse {
        val movie = movieStore[url] ?: throw Error("Movie not found")
        return newMovieLoadResponse(movie.title, url, TvType.Movie, movie.streamUrl) {
            this.plot = movie.plot
            this.year = movie.year
            this.posterUrl = movie.poster
            val tagsList = mutableListOf<String>()
            tagsList.addAll(movie.genres)
            if (movie.cast.isNotEmpty()) {
                tagsList.add("Cast: ${movie.cast.joinToString(", ")}")
            }
            if (tagsList.isNotEmpty()) this.tags = tagsList
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val streamUrl = data
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