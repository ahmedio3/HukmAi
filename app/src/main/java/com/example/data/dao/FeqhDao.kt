package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.data.model.Article
import com.example.data.model.ChatMessage
import com.example.data.model.TreeNode
import kotlinx.coroutines.flow.Flow

@Dao
interface FeqhDao {
    @Query("SELECT * FROM tree_nodes WHERE parent_id IS NULL OR parent_id = 0 ORDER BY id ASC")
    fun getRootNodes(): Flow<List<TreeNode>>

    @Query("SELECT * FROM tree_nodes WHERE parent_id = :parentId ORDER BY id ASC")
    fun getChildrenNodes(parentId: Int): Flow<List<TreeNode>>

    @Query("SELECT * FROM tree_nodes WHERE parent_id IS NULL OR parent_id = 0 ORDER BY id ASC")
    suspend fun getRootNodesSync(): List<TreeNode>

    @Query("SELECT * FROM tree_nodes WHERE parent_id IN (:parentIds) ORDER BY id ASC")
    suspend fun getChildrenNodesSync(parentIds: List<Int>): List<TreeNode>

    @Query("""
        WITH RECURSIVE under_chapter AS (
            SELECT id, title, is_leaf, article_id, parent_id, level FROM tree_nodes WHERE parent_id IN (:chapterIds)
            UNION ALL
            SELECT t.id, t.title, t.is_leaf, t.article_id, t.parent_id, t.level
            FROM tree_nodes t
            JOIN under_chapter u ON t.parent_id = u.id
        )
        SELECT * FROM tree_nodes WHERE id IN (SELECT id FROM under_chapter WHERE is_leaf = 1)
    """)
    suspend fun getLeafTopics(chapterIds: List<Int>): List<TreeNode>

    @Query("SELECT * FROM articles WHERE id IN (:articleIds)")
    suspend fun getArticlesByIds(articleIds: List<Int>): List<Article>

    @Query("SELECT * FROM tree_nodes WHERE id = :nodeId")
    suspend fun getNodeById(nodeId: Int): TreeNode?

    @Query("SELECT * FROM articles WHERE id = :articleId")
    suspend fun getArticleById(articleId: Int): Article?

    @Query("SELECT * FROM articles WHERE id = :articleId")
    fun getArticleFlow(articleId: Int): Flow<Article?>

    // Use RawQuery to completely bypass Room's compile-time schema validation for FTS5 tables!
    @RawQuery
    suspend fun searchArticlesRaw(rawQuery: SupportSQLiteQuery): List<Article>

    @Query("SELECT * FROM articles WHERE title LIKE :likeQuery OR text LIKE :likeQuery")
    suspend fun searchArticlesLike(likeQuery: String): List<Article>

    // ---- Chat Messages ----
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllChatMessages(): Flow<List<ChatMessage>>

    @Insert
    suspend fun insertChatMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllChatMessages()
}
