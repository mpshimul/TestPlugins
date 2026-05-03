package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

@Suppress("UNCHECKED_CAST")
class ExampleProvider : MainAPI() {
    override var name = "DhakaMovie BDIX"
    override var lang = "bn"
    override var mainUrl = "http://dhakamovie.com"
    override val hasMainPage = true
    override val hasQuickSearch = true

    private val apiBaseUrl = "http://dhakamovie.com:8080/api/movies"
    private val advancedSearchBase = "http://dhakamovie.com:8080/api/advanced-search"
    private val mapper = jacksonObjectMapper()

    companion object {
        val movieStore = mutableMapOf<String, MovieData>()
    }

    data class MovieData(
        val slug: String,
        val title: String,
        val streamUrl: String,
        val poster: String,
        val backdrop: String,
        val plot: String,
        val year: Int?,
        val rating: Double?,
        val duration: Int?,
        val director: String,
        val genres: List<String>
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept" to "application/json",
        "Referer" to mainUrl
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePageLists = mutableListOf<HomePageList>()

        val latestMovies = fetchMovieList("$apiBaseUrl/latest")
        if (latestMovies.isNotEmpty()) homePageLists.add(HomePageList("Latest Movies", latestMovies))

        val newReleases = fetchMovieList("$apiBaseUrl/new-releases")
        if (newReleases.isNotEmpty()) homePageLists.add(HomePageList("New Releases", newReleases))

        val southIndianUrl = "$advancedSearchBase?query=&type=movies&page=1&per_page=28&category=South%20Indian&order_by=Latest"
        val southIndian = fetchMovieList(southIndianUrl)
        if (southIndian.isNotEmpty()) homePageLists.add(HomePageList("South Indian", southIndian))

        val trending = fetchMovieList("$apiBaseUrl/trending")
        if (trending.isNotEmpty()) homePageLists.add(HomePageList("Trending", trending))

        val top10 = fetchMovieList("$apiBaseUrl/top-10")
        if (top10.isNotEmpty()) homePageLists.add(HomePageList("Top 10", top10))

        return newHomePageResponse(homePageLists)
    }

    private suspend fun fetchMovieList(url: String): List<SearchResponse> {
        return try {
            val response = app.get(url, headers = headers).text
            val json = mapper.readValue<Map<String, Any>>(response)

            // Detect if it's advanced-search or normal API
            val movies = if (json.containsKey("results")) {
                val results = json["results"] as? Map<String, Any>
                val moviesObj = results?.get("movies") as? Map<String, Any>
                moviesObj?.get("data") as? List<Map<String, Any>> ?: emptyList()
            } else {
                json["data"] as? List<Map<String, Any>> ?: emptyList()
            }

            movies.mapNotNull { movie ->
                val slug = movie["slug"] as? String ?: return@mapNotNull null
                val title = movie["title"] as? String ?: return@mapNotNull null
                val poster = movie["poster_url"] as? String ?: movie["image"] as? String ?: ""
                val year = (movie["year"] as? String)?.toIntOrNull()
                val streamUrl = movie["stream_url"] as? String ?: ""
                val backdrop = movie["backdrop_url"] as? String ?: poster
                val plot = movie["overview"] as? String ?: "No plot available"
                val rating = (movie["rating"] as? String)?.toDoubleOrNull()
                val duration = (movie["runtime"] as? String)?.toIntOrNull()
                val director = movie["director"] as? String ?: "Unknown"
                val genresStr = movie["genres"] as? String ?: ""
                val genres = genresStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                val movieUrl = "$mainUrl/movies/$slug"
                movieStore[movieUrl] = MovieData(
                    slug, title, streamUrl, poster, backdrop, plot,
                    year, rating, duration, director, genres
                )

                newMovieSearchResponse(title, movieUrl, TvType.Movie, false) {
                    this.posterUrl = poster
                    this.year = year
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val allMovies = mutableListOf<Map<String, Any>>()
        for (page in 1..2) {
            try {
                val url = "$apiBaseUrl?page=$page"
                val response = app.get(url, headers = headers).text
                val json = mapper.readValue<Map<String, Any>>(response)
                val movies = json["data"] as? List<Map<String, Any>> ?: break
                allMovies.addAll(movies)
                if (movies.size < 12) break
            } catch (e: Exception) { break }
        }

        val filtered = if (query.isNotBlank() && query != "a") {
            allMovies.filter { movie ->
                val title = movie["title"] as? String ?: ""
                title.contains(query, ignoreCase = true)
            }
        } else { allMovies }

        return filtered.mapNotNull { movie ->
            val slug = movie["slug"] as? String ?: return@mapNotNull null
            val title = movie["title"] as? String ?: return@mapNotNull null
            val poster = movie["poster_url"] as? String ?: ""
            val year = (movie["year"] as? String)?.toIntOrNull()
            val streamUrl = movie["stream_url"] as? String ?: ""
            val backdrop = movie["backdrop_url"] as? String ?: poster
            val plot = movie["overview"] as? String ?: "No plot available"
            val rating = (movie["rating"] as? String)?.toDoubleOrNull()
            val duration = (movie["runtime"] as? String)?.toIntOrNull()
            val director = movie["director"] as? String ?: "Unknown"
            val genresStr = movie["genres"] as? String ?: ""
            val genres = genresStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val movieUrl = "$mainUrl/movies/$slug"
            movieStore[movieUrl] = MovieData(
                slug, title, streamUrl, poster, backdrop, plot,
                year, rating, duration, director, genres
            )
            newMovieSearchResponse(title, movieUrl, TvType.Movie, false) {
                this.posterUrl = poster
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val movie = movieStore[url] ?: throw Error("Movie not found for URL: $url")
        return newMovieLoadResponse(movie.title, movie.streamUrl, TvType.Movie, movie.streamUrl) {
            this.plot = movie.plot
            this.year = movie.year
            this.posterUrl = movie.poster
            this.backgroundPosterUrl = movie.backdrop
            this.duration = movie.duration
            val tagsList = mutableListOf<String>()
            if (movie.director.isNotBlank()) tagsList.add("Director: ${movie.director}")
            tagsList.addAll(movie.genres)
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
            newExtractorLink(source = name, name = "Direct", url = streamUrl) {
                this.referer = mainUrl
                this.quality = quality
            }
        )
        return true
    }
}