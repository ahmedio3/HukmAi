package com.example.data.api

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json

@JsonClass(generateAdapter = true)
data class BookRouterResponse(
    @Json(name = "is_out_of_scope") val isOutOfScope: Boolean,
    @Json(name = "books") val books: List<Int>?
)

@JsonClass(generateAdapter = true)
data class BabRouterResponse(
    @Json(name = "babs") val babs: List<Int>
)

@JsonClass(generateAdapter = true)
data class TopicRouterResponse(
    @Json(name = "topics") val topics: List<Int>
)
