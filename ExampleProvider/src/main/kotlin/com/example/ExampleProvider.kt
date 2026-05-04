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

    private val apiMoviesBase = "http://dhakamovie.com:8080/api/movies"
    private val apiTvSeriesBase = "http://dhakamovie.com:8080/api/tv-series"
    private val advancedSearchBase = "http://dhakamovie.com:8080/api/advanced-search"
    private val mapper = jacksonObjectMapper()

    companion object {
        val movieStore = mutableMapOf<String, MovieData>()
        val episodeStore = mutableMapOf<String, String>()
        val seriesStore = mutableMapOf<String, SeriesData>()
    }

    data class MovieData(
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

    data class EpisodeData(
        val title: String,
        val episodeNumber: Int,
        val filePath: String,
        val runtime: Int?,
        val poster: String
    )

    data class SeasonData(
        val seasonNumber: Int,
        val episodes: List<EpisodeData>
    )

    data class SeriesData(
        val title: String,
        val poster: String,
        val backdrop: String,
        val plot: String,
        val year: Int?,
        val rating: Double?,
        val genres: List<String>,
        val seasons: List<SeasonData>
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept" to "application/json",
        "Referer" to mainUrl
    )

    // ------------------------------------------------------------
    // Main page (all rows)
    // ------------------------------------------------------------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val lists = mutableListOf<HomePageList>()

        // All Movies (advanced search, up to 1000)
        val allMovies = fetchMovieListAdvanced("$advancedSearchBase?query=&type=movies&page=1&per_page=1000&order_by=Latest")
        if (allMovies.isNotEmpty()) lists.add(HomePageList("All Movies (1000+)", allMovies))

        // TV Series (advanced search, up to 1000)
        val tvSeries = fetchSeriesListAdvanced("$advancedSearchBase?query=&type=tv_series&page=1&per_page=1000&order_by=Latest")
        if (tvSeries.isNotEmpty()) lists.add(HomePageList("TV Series (1000+)", tvSeries))

        // TV Series (advanced search, up to 1000)
        val tvSeriesKor = fetchSeriesListAdvanced("$advancedSearchBase?query=&type=tv_series&page=1&per_page=1000&category=Korean&order_by=Latest")
        if (tvSeriesKor.isNotEmpty()) lists.add(HomePageList("Korean TV Series", tvSeriesKor))

        // Latest Movies
        val latest = fetchMovieList("$apiMoviesBase/latest")
        if (latest.isNotEmpty()) lists.add(HomePageList("Latest Movies", latest))

        // New Releases
        val newReleases = fetchMovieList("$apiMoviesBase/new-releases")
        if (newReleases.isNotEmpty()) lists.add(HomePageList("New Releases", newReleases))

        // South Indian (advanced search)
        val southIndian = fetchMovieListAdvanced("$advancedSearchBase?query=&type=movies&page=1&per_page=1000&category=South%20Indian&order_by=Latest")
        if (southIndian.isNotEmpty()) lists.add(HomePageList("South Indian (1000+)", southIndian))

        // Trending
        val trending = fetchMovieList("$apiMoviesBase/trending")
        if (trending.isNotEmpty()) lists.add(HomePageList("Trending", trending))

        // Top 10
        val top10 = fetchMovieList("$apiMoviesBase/top-10")
        if (top10.isNotEmpty()) lists.add(HomePageList("Top 10", top10))

        return newHomePageResponse(lists)
    }

    // ---------- Fetch movies from regular endpoints (returns fake "http://movie.local/slug") ----------
    private suspend fun fetchMovieList(apiUrl: String): List<SearchResponse> {
        return try {
            val response = app.get(apiUrl, headers = headers).text
            val json = mapper.readValue<Map<String, Any>>(response)
            val movies = json["data"] as? List<Map<String, Any>> ?: emptyList()

            movies.mapNotNull { movie ->
                val slug = movie["slug"] as? String ?: return@mapNotNull null
                val title = movie["title"] as? String ?: return@mapNotNull null
                val poster = movie["poster_url"] as? String ?: ""
                val fullPoster = if (poster.startsWith("/")) "$mainUrl:8080$poster" else poster
                val year = (movie["year"] as? String)?.toIntOrNull()
                val streamUrl = movie["stream_url"] as? String ?: ""
                val backdrop = movie["backdrop_url"] as? String ?: fullPoster
                val plot = movie["overview"] as? String ?: "No plot available"
                val rating = (movie["rating"] as? String)?.toDoubleOrNull()
                val duration = (movie["runtime"] as? String)?.toIntOrNull()
                val director = movie["director"] as? String ?: "Unknown"
                val genresStr = movie["genres"] as? String ?: ""
                val genres = genresStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                val fakeUrl = "http://movie.local/$slug"
                movieStore[fakeUrl] = MovieData(
                    title, streamUrl, fullPoster, backdrop, plot,
                    year, rating, duration, director, genres
                )

                newMovieSearchResponse(title, fakeUrl, TvType.Movie, false) {
                    this.posterUrl = fullPoster
                    this.year = year
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- Fetch movies from advanced search (All Movies, South Indian) ----------
    private suspend fun fetchMovieListAdvanced(apiUrl: String): List<SearchResponse> {
        return try {
            val response = app.get(apiUrl, headers = headers).text
            val json = mapper.readValue<Map<String, Any>>(response)
            val results = json["results"] as? Map<String, Any> ?: return emptyList()
            val moviesObj = results["movies"] as? Map<String, Any> ?: return emptyList()
            val movies = moviesObj["data"] as? List<Map<String, Any>> ?: emptyList()

            movies.mapNotNull { movie ->
                val slug = movie["slug"] as? String ?: return@mapNotNull null
                val title = movie["title"] as? String ?: return@mapNotNull null
                val poster = movie["image"] as? String ?: ""
                val fullPoster = if (poster.startsWith("/")) "$mainUrl:8080$poster" else poster
                val year = (movie["year"] as? String)?.toIntOrNull()
                val streamUrl = movie["stream_url"] as? String ?: ""

                val fakeUrl = "http://movie.local/$slug"
                movieStore[fakeUrl] = MovieData(
                    title, streamUrl, fullPoster, "", "No plot available",
                    year, null, null, "Unknown", emptyList()
                )

                newMovieSearchResponse(title, fakeUrl, TvType.Movie, false) {
                    this.posterUrl = fullPoster
                    this.year = year
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- Fetch TV series from advanced search (returns fake "http://tv.local/slug") ----------
    private suspend fun fetchSeriesListAdvanced(apiUrl: String): List<SearchResponse> {
        return try {
            val response = app.get(apiUrl, headers = headers).text
            val json = mapper.readValue<Map<String, Any>>(response)
            val results = json["results"] as? Map<String, Any> ?: return emptyList()
            val seriesObj = results["series"] as? Map<String, Any> ?: return emptyList()
            val seriesList = seriesObj["data"] as? List<Map<String, Any>> ?: return emptyList()

            seriesList.mapNotNull { series ->
                val slug = series["slug"] as? String ?: return@mapNotNull null
                val title = series["title"] as? String ?: return@mapNotNull null
                val poster = series["image"] as? String ?: ""
                val fullPoster = if (poster.startsWith("/")) "$mainUrl:8080$poster" else poster
                val year = (series["year"] as? String)?.toIntOrNull()

                val fakeUrl = "http://tv.local/$slug"
                seriesStore[fakeUrl] = SeriesData(
                    title, fullPoster, fullPoster, "", year, null, emptyList(), emptyList()
                )

                newTvSeriesSearchResponse(title, fakeUrl, TvType.TvSeries) {
                    this.posterUrl = fullPoster
                    this.year = year
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- Fetch full TV series details (including seasons/episodes) from API ----------
    private suspend fun fetchFullSeries(slug: String): SeriesData? {
        val url = "$apiTvSeriesBase/$slug"
        return try {
            val response = app.get(url, headers = headers).text
            val series = mapper.readValue<Map<String, Any>>(response)

            val title = series["title"] as? String ?: return null
            val poster = series["poster_url"] as? String ?: ""
            val fullPoster = if (poster.startsWith("/")) "$mainUrl:8080$poster" else poster
            val year = (series["year"] as? String)?.toIntOrNull()
            val plot = series["overview"] as? String ?: "No plot available"
            val rating = when (val r = series["rating"]) {
                is Number -> r.toDouble()
                is String -> r.toDoubleOrNull()
                else -> null
            }
            val genresStr = series["genres"] as? String ?: ""
            val genres = genresStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val backdrop = (series["property"] as? Map<String, Any>)?.get("backdrop_path") as? String ?: fullPoster
            val fullBackdrop = if (backdrop.startsWith("/")) "$mainUrl:8080$backdrop" else backdrop

            val seasonsList = mutableListOf<SeasonData>()
            val seasonsRaw = series["seasons"] as? List<Map<String, Any>> ?: emptyList()
            for (seasonRaw in seasonsRaw) {
                val seasonNumber = seasonRaw["season_number"] as? Int ?: continue
                val episodesRaw = seasonRaw["episodes"] as? List<Map<String, Any>> ?: continue
                val episodes = episodesRaw.mapNotNull { ep ->
                    val epNum = ep["episode_number"] as? Int ?: return@mapNotNull null
                    val epTitle = ep["title"] as? String ?: "Episode $epNum"
                    val property = ep["property"] as? Map<String, Any>
                    val rawPath = property?.get("file_path") as? String
                    if (rawPath.isNullOrEmpty()) return@mapNotNull null
                    val cleanedPath = rawPath.removePrefix("server1/")
                    val runtime = property?.get("runtime") as? Int
                    val epPoster = ep["poster_url"] as? String ?: ""
                    val fullEpPoster = if (epPoster.startsWith("/")) "$mainUrl:8080$epPoster" else epPoster
                    EpisodeData(epTitle, epNum, cleanedPath, runtime, fullEpPoster)
                }
                if (episodes.isNotEmpty()) {
                    seasonsList.add(SeasonData(seasonNumber, episodes))
                }
            }

            SeriesData(title, fullPoster, fullBackdrop, plot, year, rating, genres, seasonsList)
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Search (movies only) ----------
    override suspend fun search(query: String): List<SearchResponse> {
        val allMovies = mutableListOf<Map<String, Any>>()
        for (page in 1..2) {
            try {
                val url = "$apiMoviesBase?page=$page"
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
        } else {
            allMovies
        }

        return filtered.mapNotNull { movie ->
            val slug = movie["slug"] as? String ?: return@mapNotNull null
            val title = movie["title"] as? String ?: return@mapNotNull null
            val poster = movie["poster_url"] as? String ?: ""
            val fullPoster = if (poster.startsWith("/")) "$mainUrl:8080$poster" else poster
            val year = (movie["year"] as? String)?.toIntOrNull()
            val streamUrl = movie["stream_url"] as? String ?: ""
            val backdrop = movie["backdrop_url"] as? String ?: fullPoster
            val plot = movie["overview"] as? String ?: "No plot available"
            val rating = (movie["rating"] as? String)?.toDoubleOrNull()
            val duration = (movie["runtime"] as? String)?.toIntOrNull()
            val director = movie["director"] as? String ?: "Unknown"
            val genresStr = movie["genres"] as? String ?: ""
            val genres = genresStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val fakeUrl = "http://movie.local/$slug"
            movieStore[fakeUrl] = MovieData(
                title, streamUrl, fullPoster, backdrop, plot,
                year, rating, duration, director, genres
            )
            newMovieSearchResponse(title, fakeUrl, TvType.Movie, false) {
                this.posterUrl = fullPoster
                this.year = year
            }
        }
    }

    // ---------- Load details (movie or series) ----------
    override suspend fun load(url: String): LoadResponse {
        // Guard: direct video URLs should never reach load()
        if (url.startsWith("http://server1.dhakamovie.com")) {
            throw Error("Direct video URL passed to load() – this is a bug. URL: $url")
        }

        // TV series detail (fake URL "http://tv.local/slug")
        if (url.startsWith("http://tv.local/")) {
            val slug = url.removePrefix("http://tv.local/")
            val series = fetchFullSeries(slug) ?: throw Error("Could not fetch series details")
            val seriesUrl = "http://tv.local/${series.title}" // placeholder
            seriesStore[seriesUrl] = series

            val episodes = mutableListOf<Episode>()
            for (seasonData in series.seasons) {
                for (ep in seasonData.episodes) {
                    val episodeUrl = "http://episode.local/${series.title}/${seasonData.seasonNumber}/${ep.episodeNumber}"
                    episodes.add(
                        newEpisode(episodeUrl) {
                            name = ep.title
                            season = seasonData.seasonNumber
                            episode = ep.episodeNumber
                            posterUrl = ep.poster
                            runTime = ep.runtime
                        }
                    )
                    episodeStore[episodeUrl] = ep.filePath
                }
            }

            return newTvSeriesLoadResponse(series.title, seriesUrl, TvType.TvSeries, episodes) {
                this.plot = series.plot
                this.year = series.year
                this.posterUrl = series.poster
                this.backgroundPosterUrl = series.backdrop
                if (series.genres.isNotEmpty()) this.tags = series.genres
            }
        }

        // Movie detail (fake URL "http://movie.local/slug")
        if (movieStore.containsKey(url)) {
            val movie = movieStore[url]!!
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

        throw Error("Unknown URL type: $url")
    }

    // ---------- Extract video links (movie or episode) ----------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // TV episode
        if (data.startsWith("http://episode.local/")) {
            val filePath = episodeStore[data] ?: return false
            val streamUrl = "http://server1.dhakamovie.com/$filePath"
            val encodedUrl = streamUrl.replace(" ", "%20")
            val quality = when {
                encodedUrl.contains("1080") -> 1080
                encodedUrl.contains("720") -> 720
                else -> 0
            }
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "Direct",
                    url = encodedUrl
                ) {
                    this.referer = mainUrl
                    this.quality = quality
                }
            )
            return true
        }

        // Movie direct stream URL (data is the real video URL)
        val quality = when {
            data.contains("1080") -> 1080
            data.contains("720") -> 720
            data.contains("480") -> 480
            else -> 0
        }
        callback.invoke(
            newExtractorLink(
                source = name,
                name = "Direct",
                url = data
            ) {
                this.referer = mainUrl
                this.quality = quality
            }
        )
        return true
    }
}