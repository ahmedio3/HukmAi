package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tree_nodes",
    indices = [
        Index(value = ["article_id"], name = "idx_tree_article"),
        Index(value = ["parent_id"], name = "idx_tree_parent")
    ]
)
data class TreeNode(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "parent_id") val parentId: Int?,
    @ColumnInfo(name = "level") val level: Int,
    @ColumnInfo(name = "is_leaf") val isLeaf: Int,
    @ColumnInfo(name = "article_id") val articleId: Int?
)