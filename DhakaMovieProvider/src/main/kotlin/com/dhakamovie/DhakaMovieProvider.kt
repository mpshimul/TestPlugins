package com.dhakamovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URLEncoder

@Suppress("UNCHECKED_CAST")
class DhakaMovieProvider : MainAPI() {
    override var name = "DhakaMovie BDIX"
    override var lang = "bn"
    override var mainUrl = "http://dhakamovie.com"
    override val hasMainPage = true
    override val hasQuickSearch = true

    private val apiMoviesBase = "http://dhakamovie.com:8080/api/movies"
    private val apiTvSeriesBase = "http://dhakamovie.com:8080/api/tv-series"
    private val advancedSearchBase = "http://dhakamovie.com:8080/api/advanced-search"
    private val mapper = jacksonObjectMapper()

    companion object {
        val movieStore = mutableMapOf<String, MovieData>()
        val seriesStore = mutableMapOf<String, SeriesData>()
        val episodeStore = mutableMapOf<String, String>()
    }

    data class MovieData(
        val title: String, val streamUrl: String, val poster: String,
        val backdrop: String, val plot: String, val year: Int?,
        val rating: Double?, val duration: Int?, val director: String, val genres: List<String>
    )

    data class EpisodeData(
        val title: String, val episodeNumber: Int, val filePath: String,
        val runtime: Int?, val poster: String
    )

    data class SeasonData(val seasonNumber: Int, val episodes: List<EpisodeData>)

    data class SeriesData(
        val title: String, val poster: String, val backdrop: String,
        val plot: String, val year: Int?, val rating: Double?,
        val genres: List<String>, val seasons: List<SeasonData> = emptyList()
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept" to "application/json",
        "Referer" to mainUrl
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val lists = mutableListOf<HomePageList>()

        // Featured Hero Carousel
        val featured = fetchMovies("$apiMoviesBase/trending")
        if (featured.isNotEmpty()) lists.add(HomePageList("Featured", featured))

        val movieRows = listOf(
            "Latest Movies" to "$advancedSearchBase?query=&type=movies&page=1&per_page=700&order_by=Latest",
            "New Releases" to "$apiMoviesBase/new-releases",
            "Top 10" to "$apiMoviesBase/top-10",
            "South Indian Movies" to "$advancedSearchBase?query=&type=movies&page=1&per_page=700&category=South%20Indian&order_by=Latest",
            "NetFlix Movies" to "$advancedSearchBase?query=&type=movies&page=1&per_page=700&category=Netflix&order_by=Latest",
            "Prime Movies" to "$advancedSearchBase?query=&type=movies&page=1&per_page=700&category=Prime&order_by=Latest",
            "Hindi Movies" to "$advancedSearchBase?query=&type=movies&page=1&per_page=700&category=Bollywood&order_by=Latest",
            "Hollywood Movies" to "$advancedSearchBase?query=&type=movies&page=1&per_page=700&category=Hollywood&order_by=Latest",
            "Indian Bangla Movies" to "$advancedSearchBase?query=&type=movies&page=1&per_page=700&category=Indian+Bangla&order_by=Latest"
        )

        val seriesRows = listOf(
            "TV Series (Latest)" to "$advancedSearchBase?query=&type=tv_series&page=1&per_page=700&order_by=Latest",
            "Most Popular TV Series" to "$advancedSearchBase?query=&type=tv_series&page=1&per_page=700&views=Most+Popular&order_by=Latest",
            "Trending TV Series" to "$advancedSearchBase?query=&type=tv_series&page=1&per_page=700&views=Trending&order_by=Latest",
            "Korean TV Series" to "$advancedSearchBase?query=&type=tv_series&page=1&per_page=700&category=Korean&order_by=Latest",
            "NetFlix TV Series" to "$advancedSearchBase?query=&type=tv_series&page=1&per_page=700&category=Netflix&order_by=Latest",
            "Prime TV Series" to "$advancedSearchBase?query=&type=tv_series&page=1&per_page=700&category=Prime&order_by=Latest",
            "Hindi TV Series" to "$advancedSearchBase?query=&type=tv_series&page=1&per_page=700&category=Hindi&order_by=Latest",
            "Hollywood TV Series" to "$advancedSearchBase?query=&type=tv_series&page=1&per_page=700&category=English&order_by=Latest",
            "Indian Bangla TV Series" to "$advancedSearchBase?query=&type=tv_series&page=1&per_page=700&category=Indian+Bangla&order_by=Latest"
        )

        movieRows.forEach { (name, url) ->
            val data = fetchMovies(url)
            if (data.isNotEmpty()) lists.add(HomePageList(name, data))
        }

        seriesRows.forEach { (name, url) ->
            val data = fetchSeries(url)
            if (data.isNotEmpty()) lists.add(HomePageList(name, data))
        }

        return newHomePageResponse(lists)
    }

    private suspend fun fetchMovies(apiUrl: String): List<SearchResponse> {
        return try {
            val response = app.get(apiUrl, headers = headers).text
            val json = mapper.readValue<Map<String, Any>>(response)
            val movies = when {
                json.containsKey("results") -> {
                    val res = json["results"] as? Map<String, Any>
                    (res?.get("movies") as? Map<String, Any>)?.get("data") as? List<Map<String, Any>> ?: emptyList()
                }
                json.containsKey("data") -> json["data"] as? List<Map<String, Any>> ?: emptyList()
                else -> emptyList()
            }

            movies.mapNotNull { movie ->
                val slug = movie["slug"] as? String ?: return@mapNotNull null
                val title = movie["title"] as? String ?: return@mapNotNull null
                val poster = movie["poster_url"] as? String ?: movie["image"] as? String ?: ""
                val fullPoster = if (poster.startsWith("/")) "$mainUrl:8080$poster" else poster
                val internalUrl = "http://movie.local/$slug"

                movieStore[internalUrl] = MovieData(
                    title = title,
                    streamUrl = movie["stream_url"] as? String ?: "",
                    poster = fullPoster,
                    backdrop = (movie["backdrop_url"] as? String) ?: fullPoster,
                    plot = movie["overview"] as? String ?: "",
                    year = (movie["year"] as? String)?.toIntOrNull(),
                    rating = (movie["rating"] as? String)?.toDoubleOrNull(),
                    duration = (movie["runtime"] as? String)?.toIntOrNull(),
                    director = movie["director"] as? String ?: "",
                    genres = (movie["genres"] as? String)?.split(",")?.map { it.trim() } ?: emptyList()
                )

                newMovieSearchResponse(title, internalUrl, TvType.Movie) { this.posterUrl = fullPoster }
            }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun fetchSeries(apiUrl: String): List<SearchResponse> {
        return try {
            val response = app.get(apiUrl, headers = headers).text
            val json = mapper.readValue<Map<String, Any>>(response)
            val results = json["results"] as? Map<String, Any>
            val seriesObj = results?.get("series") as? Map<String, Any>
            val seriesList = seriesObj?.get("data") as? List<Map<String, Any>> ?: emptyList()

            seriesList.mapNotNull { series ->
                val slug = series["slug"] as? String ?: return@mapNotNull null
                val title = series["title"] as? String ?: return@mapNotNull null
                val poster = series["image"] as? String ?: ""
                val fullPoster = if (poster.startsWith("/")) "$mainUrl:8080$poster" else poster
                val internalUrl = "http://tv.local/$slug"

                newTvSeriesSearchResponse(title, internalUrl, TvType.TvSeries) { this.posterUrl = fullPoster }
            }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun load(url: String): LoadResponse {
        if (url.startsWith("http://tv.local/")) {
            val slug = url.removePrefix("http://tv.local/")
            val series = fetchFullSeries(slug) ?: throw Error("Series load failed")
            val episodes = mutableListOf<Episode>()
            series.seasons.forEach { season ->
                season.episodes.forEach { ep ->
                    val epUrl = "http://episode.local/$slug/${season.seasonNumber}/${ep.episodeNumber}"
                    episodeStore[epUrl] = ep.filePath
                    episodes.add(newEpisode(epUrl) {
                        this.name = ep.title
                        this.season = season.seasonNumber
                        this.episode = ep.episodeNumber
                        this.posterUrl = ep.poster
                    })
                }
            }
            return newTvSeriesLoadResponse(series.title, url, TvType.TvSeries, episodes) {
                this.posterUrl = series.poster
                this.plot = series.plot
                this.year = series.year
            }
        }

        val movie = movieStore[url] ?: throw Error("Movie not in store")
        return newMovieLoadResponse(movie.title, url, TvType.Movie, movie.streamUrl) {
            this.posterUrl = movie.poster
            this.plot = movie.plot
            this.year = movie.year
            this.tags = movie.genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val streamUrl = if (data.startsWith("http://episode.local/")) {
            val path = episodeStore[data] ?: return false
            "http://server1.dhakamovie.com/$path".replace(" ", "%20")
        } else {
            data.replace(" ", "%20")
        }

        val qualityInt = when {
            streamUrl.contains("1080") -> 1080
            streamUrl.contains("720") -> 720
            else -> 0
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "Direct",
                url = streamUrl
            ) {
                this.referer = mainUrl
                this.quality = qualityInt
            }
        )
        return true
    }

    private suspend fun fetchFullSeries(slug: String): SeriesData? {
        return try {
            val response = app.get("$apiTvSeriesBase/${URLEncoder.encode(slug, "UTF-8")}", headers = headers).text
            val series = mapper.readValue<Map<String, Any>>(response)
            val seasonsRaw = series["seasons"] as? List<Map<String, Any>> ?: emptyList()
            val seasons = seasonsRaw.map { s ->
                val epRaw = s["episodes"] as? List<Map<String, Any>> ?: emptyList()
                SeasonData(
                    seasonNumber = s["season_number"] as? Int ?: 1,
                    episodes = epRaw.mapNotNull { e ->
                        val property = e["property"] as? Map<String, Any>
                        val rawPath = property?.get("file_path") as? String
                        if (rawPath.isNullOrEmpty()) return@mapNotNull null
                        val cleanedPath = rawPath.removePrefix("server1/")

                        // Extract episode poster (full URL)
                        val epPoster = e["poster_url"] as? String
                        val fullEpPoster = if (!epPoster.isNullOrEmpty() && epPoster.startsWith("/")) {
                            "$mainUrl:8080$epPoster"
                        } else epPoster ?: ""

                        // Extract runtime (minutes) from property
                        val runtime = property["runtime"] as? Int

                        // Optionally, you can extract overview and file size (not used in Episode UI)
                        // val overview = e["overview"] as? String
                        // val fileSize = property["formatted_file_size"] as? String

                        EpisodeData(
                            title = e["title"] as? String ?: "Episode ${e["episode_number"]}",
                            episodeNumber = e["episode_number"] as? Int ?: 1,
                            filePath = cleanedPath,
                            runtime = runtime,
                            poster = fullEpPoster
                        )
                    }
                )
            }
            SeriesData(
                title = series["title"] as? String ?: "Unknown",
                poster = (series["poster_url"] as? String ?: "").let { if (it.startsWith("/")) "$mainUrl:8080$it" else it },
                backdrop = "",
                plot = series["overview"] as? String ?: "",
                year = (series["year"] as? String)?.toIntOrNull(),
                rating = null,
                genres = emptyList(),
                seasons = seasons
            )
        } catch (e: Exception) { null }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // The regular API doesn't support ?query=, so fetch two pages and filter locally
        val allMovies = mutableListOf<SearchResponse>()
        for (page in 1..2) {
            val results = fetchMovies("$apiMoviesBase?page=$page")
            allMovies.addAll(results)
            if (results.size < 12) break
        }
        return allMovies.filter { it.name?.contains(query, ignoreCase = true) == true }
    }
}