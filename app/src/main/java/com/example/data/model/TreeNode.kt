package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tree_nodes")
data class TreeNode(
    @PrimaryKey val id: Int?,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "parent_id") val parentId: Int?,
    @ColumnInfo(name = "level") val level: Int,
    @ColumnInfo(name = "is_leaf") val isLeaf: Int,
    @ColumnInfo(name = "article_id") val articleId: Int?
)
