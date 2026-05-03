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
    private val apiTvBase = "http://dhakamovie.com:8080/api/tv-series"
    private val advancedSearchBase = "http://dhakamovie.com:8080/api/advanced-search"
    private val mapper = jacksonObjectMapper()

    companion object {
        val movieStore = mutableMapOf<String, MovieData>()
        val seriesStore = mutableMapOf<String, SeriesData>()
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
        val homePageLists = mutableListOf<HomePageList>()

        val latestMovies = fetchMovieList("$apiMoviesBase/latest")
        if (latestMovies.isNotEmpty()) homePageLists.add(HomePageList("Latest Movies", latestMovies))

        val newReleases = fetchMovieList("$apiMoviesBase/new-releases")
        if (newReleases.isNotEmpty()) homePageLists.add(HomePageList("New Releases", newReleases))

        val tvSeries = fetchSeriesList("$apiTvBase?page=1")
        if (tvSeries.isNotEmpty()) homePageLists.add(HomePageList("TV Series", tvSeries))

        val southIndianUrl = "$advancedSearchBase?query=&type=movies&page=1&per_page=28&category=South%20Indian&order_by=Latest"
        val southIndian = fetchMovieList(southIndianUrl)
        if (southIndian.isNotEmpty()) homePageLists.add(HomePageList("South Indian", southIndian))

        val trending = fetchMovieList("$apiMoviesBase/trending")
        if (trending.isNotEmpty()) homePageLists.add(HomePageList("Trending", trending))

        val top10 = fetchMovieList("$apiMoviesBase/top-10")
        if (top10.isNotEmpty()) homePageLists.add(HomePageList("Top 10", top10))

        return newHomePageResponse(homePageLists)
    }

    private suspend fun fetchMovieList(url: String): List<SearchResponse> {
        return try {
            val response = app.get(url, headers = headers).text
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

    private suspend fun fetchSeriesList(url: String): List<SearchResponse> {
        return try {
            val response = app.get(url, headers = headers).text
            val json = mapper.readValue<Map<String, Any>>(response)
            val seriesList = json["data"] as? List<Map<String, Any>> ?: return emptyList()

            seriesList.mapNotNull { series ->
                val id = series["id"] as? Int ?: return@mapNotNull null
                val slug = series["slug"] as? String ?: return@mapNotNull null
                val title = series["title"] as? String ?: return@mapNotNull null
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
    // 👇 Remove the "server1/" prefix if it exists
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

                val seriesUrl = "$mainUrl/tv/$slug"
                seriesStore[seriesUrl] = SeriesData(
                    id, slug, title, fullPoster, fullBackdrop, plot,
                    year, rating, genres, seasonsList
                )

                newTvSeriesSearchResponse(title, seriesUrl, TvType.TvSeries) {
                    this.posterUrl = fullPoster
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
        } else if (seriesStore.containsKey(url)) {
            val series = seriesStore[url]!!
            val episodes = mutableListOf<Episode>()
            for (seasonData in series.seasons) {
                for (ep in seasonData.episodes) {
                    val episodeUrl = "episode://${series.slug}/${seasonData.seasonNumber}/${ep.episodeNumber}"
                    episodes.add(
                        newEpisode(episodeUrl) {
                            this.name = ep.title
                            this.season = seasonData.seasonNumber
                            this.episode = ep.episodeNumber
                            this.posterUrl = ep.poster
                            this.runTime = ep.runtime
                        }
                    )
                }
            }
            return newTvSeriesLoadResponse(series.title, url, TvType.TvSeries, episodes) {
                this.plot = series.plot
                this.year = series.year
                this.posterUrl = series.poster
                this.backgroundPosterUrl = series.backdrop
                // If Score.from is available, use it; otherwise omit
                if (series.rating != null) {
                    // this.score = Score.from(series.rating, 10)
                }
                if (series.genres.isNotEmpty()) this.tags = series.genres
            }
        } else {
            throw Error("Unknown URL type: $url")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Episode pattern: episode://slug/season/episode
        val episodePattern = Regex("episode://(.+)/(\\d+)/(\\d+)")
        val match = episodePattern.find(data)
        if (match != null) {
            val slug = match.groupValues[1]
            val seasonNum = match.groupValues[2].toInt()
            val episodeNum = match.groupValues[3].toInt()
            val seriesUrl = "$mainUrl/tv/$slug"
            val series = seriesStore[seriesUrl] ?: return false
            val season = series.seasons.find { it.seasonNumber == seasonNum } ?: return false
            val episode = season.episodes.find { it.episodeNumber == episodeNum } ?: return false

            // episode.fileUrl should already be cleaned (without "server1/")
            val streamUrl = "http://server1.dhakamovie.com/${episode.fileUrl}"
            val encodedUrl = streamUrl.replace(" ", "%20")

            val quality = when {
                encodedUrl.contains("1080") -> 1080
                encodedUrl.contains("720") -> 720
                encodedUrl.contains("480") -> 480
                else -> 0
            }

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "Episode ${episodeNum}",
                    url = encodedUrl
                ) {
                    this.referer = mainUrl
                    this.quality = quality
                }
            )
            return true
        }


        // For movies (direct stream URL)
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