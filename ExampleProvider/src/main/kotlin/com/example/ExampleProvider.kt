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
    private val advancedSearchBase = "http://dhakamovie.com:8080/api/advanced-search"
    private val mapper = jacksonObjectMapper()

    companion object {
        val movieStore = mutableMapOf<String, MovieData>()
        val seriesStore = mutableMapOf<String, SeriesData>()
        val episodeStore = mutableMapOf<String, String>()
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

    data class EpisodeData(
        val title: String,
        val episodeNumber: Int,
        val fileUrl: String,
        val runtime: Int?,
        val poster: String
    )

    data class SeasonData(
        val seasonNumber: Int,
        val episodes: List<EpisodeData>
    )

    data class SeriesData(
        val id: Int,
        val slug: String,
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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val lists = mutableListOf<HomePageList>()

        val allMoviesUrl = "$advancedSearchBase?query=&type=movies&page=1&per_page=1000&order_by=Latest"
        val allMovies = fetchMovieList(allMoviesUrl)
        if (allMovies.isNotEmpty()) lists.add(HomePageList("All Movies (1000+)", allMovies))

        val tvSeriesUrl = "$advancedSearchBase?query=&type=tv_series&page=1&per_page=1000&order_by=Latest"
        val tvSeriesBasic = fetchSeriesBasicList(tvSeriesUrl)
        if (tvSeriesBasic.isNotEmpty()) lists.add(HomePageList("TV Series (1000+)", tvSeriesBasic))

        val latest = fetchMovieList("$apiMoviesBase/latest")
        if (latest.isNotEmpty()) lists.add(HomePageList("Latest Movies", latest))

        val newReleases = fetchMovieList("$apiMoviesBase/new-releases")
        if (newReleases.isNotEmpty()) lists.add(HomePageList("New Releases", newReleases))

        val southIndianUrl = "$advancedSearchBase?query=&type=movies&page=1&per_page=1000&category=South%20Indian&order_by=Latest"
        val southIndian = fetchMovieList(southIndianUrl)
        if (southIndian.isNotEmpty()) lists.add(HomePageList("South Indian (1000+)", southIndian))

        val trending = fetchMovieList("$apiMoviesBase/trending")
        if (trending.isNotEmpty()) lists.add(HomePageList("Trending", trending))

        val top10 = fetchMovieList("$apiMoviesBase/top-10")
        if (top10.isNotEmpty()) lists.add(HomePageList("Top 10", top10))

        return newHomePageResponse(lists)
    }

    private suspend fun fetchMovieList(baseUrl: String): List<SearchResponse> {
        return try {
            val response = app.get(baseUrl, headers = headers).text
            val json = mapper.readValue<Map<String, Any>>(response)

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

                // IMPORTANT: Use the detail page URL, NOT the stream URL
                val movieDetailUrl = "$mainUrl/movies/$slug"

                movieStore[movieDetailUrl] = MovieData(
                    slug, title, streamUrl, fullPoster, backdrop, plot,
                    year, rating, duration, director, genres
                )

                newMovieSearchResponse(title, movieDetailUrl, TvType.Movie, false) {
                    this.posterUrl = fullPoster
                    this.year = year
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchSeriesBasicList(baseUrl: String): List<SearchResponse> {
        return try {
            val response = app.get(baseUrl, headers = headers).text
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

                // Use a custom scheme that CloudStream won't modify
                val seriesUrl = "tvseries:$slug"
                newTvSeriesSearchResponse(title, seriesUrl, TvType.TvSeries) {
                    this.posterUrl = fullPoster
                    this.year = year
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchFullSeries(slug: String): SeriesData? {
        val url = "$mainUrl/tv-series/$slug"
        return try {
            val response = app.get(url, headers = headers).text
            val series = mapper.readValue<Map<String, Any>>(response)

            val id = series["id"] as? Int ?: return null
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
                    val fileUrl = ep["file_url"] as? String ?: return@mapNotNull null
                    val cleanedFileUrl = fileUrl.removePrefix("server1/")
                    val runtime = (ep["property"] as? Map<String, Any>)?.get("runtime") as? Int
                    val epPoster = ep["poster_url"] as? String ?: ""
                    val fullEpPoster = if (epPoster.startsWith("/")) "$mainUrl:8080$epPoster" else epPoster
                    EpisodeData(epTitle, epNum, cleanedFileUrl, runtime, fullEpPoster)
                }
                if (episodes.isNotEmpty()) {
                    seasonsList.add(SeasonData(seasonNumber, episodes))
                }
            }

            SeriesData(id, slug, title, fullPoster, fullBackdrop, plot, year, rating, genres, seasonsList)
        } catch (e: Exception) {
            null
        }
    }

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

            val movieUrl = "$mainUrl/movies/$slug"
            movieStore[movieUrl] = MovieData(
                slug, title, streamUrl, fullPoster, backdrop, plot,
                year, rating, duration, director, genres
            )
            newMovieSearchResponse(title, movieUrl, TvType.Movie, false) {
                this.posterUrl = fullPoster
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        // TV series - check for custom scheme
        if (url.startsWith("tvseries:")) {
            val slug = url.removePrefix("tvseries:")
            val series = fetchFullSeries(slug) ?: throw Error("Could not fetch series details")
            val seriesUrl = "$mainUrl/tv/${series.slug}"
            seriesStore[seriesUrl] = series

            val episodes = mutableListOf<Episode>()
            for (seasonData in series.seasons) {
                for (ep in seasonData.episodes) {
                    val episodeUrl = "episode://${series.slug}/${seasonData.seasonNumber}/${ep.episodeNumber}"
                    episodes.add(
                        newEpisode(episodeUrl) {
                            name = ep.title
                            season = seasonData.seasonNumber
                            episode = ep.episodeNumber
                            posterUrl = ep.poster
                            runTime = ep.runtime
                        }
                    )
                    episodeStore[episodeUrl] = ep.fileUrl
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

        // Movies
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // TV episode
        if (data.startsWith("episode://")) {
            val fileUrl = episodeStore[data] ?: return false
            val streamUrl = "http://server1.dhakamovie.com/$fileUrl"
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

        // Movie
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