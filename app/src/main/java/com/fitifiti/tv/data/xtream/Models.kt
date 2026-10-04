package com.fitifiti.tv.data.xtream

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val id: String,
    val server: String,
    val username: String,
    val password: String,
    val label: String = "",
)

@Serializable data class Category(val id: String, val name: String)

/** Aynı yapımın farklı kopyaları: Türkçe dublaj / Orijinal dil / 4K … */
@Serializable data class Variant(val id: Int, val ext: String? = null, val label: String)

@Serializable
data class Movie(
    val id: Int,
    val name: String,
    val icon: String? = null,
    val categoryId: String? = null,
    val ext: String? = null,
    val rating: Double = 0.0,
    val added: Long = 0,
    val year: String? = null,
    val genre: String? = null,
    val plot: String? = null,
    val tmdb: String? = null,
    val runtimeMin: Int? = null,
    val variants: List<Variant> = emptyList(),
)

@Serializable
data class Series(
    val id: Int,
    val name: String,
    val cover: String? = null,
    val categoryId: String? = null,
    val rating: Double = 0.0,
    val added: Long = 0,
    val year: String? = null,
    val genre: String? = null,
    val plot: String? = null,
    val backdrop: String? = null,
    val cast: String? = null,
    val variants: List<Variant> = emptyList(),
)

@Serializable
data class Channel(
    val id: Int,
    val name: String,
    val icon: String? = null,
    val categoryId: String? = null,
    val num: Int = 0,
    val epgId: String? = null,
)

data class Episode(
    val id: String,
    val season: Int,
    val num: Int,
    val title: String,
    val ext: String,
    val plot: String? = null,
    val image: String? = null,
    val durationSecs: Int? = null,
    val tmdbId: String? = null,
)

data class SeriesInfo(
    val seasons: Map<Int, List<Episode>>,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val country: String? = null,
    val age: String? = null,
    val tmdbId: String? = null,
    val backdrop: String? = null,
    val releaseDate: String? = null,
    val rating: Double = 0.0,
    val trailer: String? = null,
)

data class VodInfo(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val durationSecs: Int? = null,
    val backdrop: String? = null,
    val releaseDate: String? = null,
    val rating: Double = 0.0,
    val tmdbId: String? = null,
    val country: String? = null,
    val age: String? = null,
    val trailer: String? = null,
)

data class EpgItem(val title: String, val description: String, val start: Long, val end: Long)

data class UserInfo(val username: String?, val status: String?, val expDate: Long?, val maxConnections: Int?, val activeConnections: Int?)
