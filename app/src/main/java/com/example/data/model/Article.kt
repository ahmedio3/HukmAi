package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "articles", primaryKeys = ["id"])
data class Article(
    @ColumnInfo(name = "id") val id: Int?,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "html") val html: String?,
    @ColumnInfo(name = "text") val text: String?
)