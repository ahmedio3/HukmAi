package com.example.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.data.dao.FeqhDao
import com.example.data.model.Article
import com.example.data.model.ChatMessage
import com.example.data.model.TreeNode
import kotlinx.coroutines.flow.Flow

class FeqhRepository(private val feqhDao: FeqhDao) {
    fun getRootNodes(): Flow<List<TreeNode>> = feqhDao.getRootNodes()

    fun getChildrenNodes(parentId: Int): Flow<List<TreeNode>> = feqhDao.getChildrenNodes(parentId)

    fun getArticleFlow(articleId: Int): Flow<Article?> = feqhDao.getArticleFlow(articleId)

    suspend fun getRootNodesSync(): List<TreeNode> = feqhDao.getRootNodesSync()

    suspend fun getChildrenNodesSync(parentIds: List<Int>): List<TreeNode> = feqhDao.getChildrenNodesSync(parentIds)

    suspend fun getLeafTopics(chapterIds: List<Int>): List<TreeNode> = feqhDao.getLeafTopics(chapterIds)

    suspend fun getArticlesByIds(articleIds: List<Int>): List<Article> = feqhDao.getArticlesByIds(articleIds)

    suspend fun getArticleById(articleId: Int): Article? = feqhDao.getArticleById(articleId)

    suspend fun getNodeById(nodeId: Int): TreeNode? = feqhDao.getNodeById(nodeId)

    // ---- Chat Messages ----
    fun getAllChatMessages(): Flow<List<ChatMessage>> = feqhDao.getAllChatMessages()

    suspend fun getAllChatMessagesSync(): List<ChatMessage> = feqhDao.getAllChatMessagesSync()

    suspend fun insertChatMessage(message: ChatMessage) = feqhDao.insertChatMessage(message)

    suspend fun deleteLastAiMessage() = feqhDao.deleteLastAiMessage()

    suspend fun deleteAllChatMessages() = feqhDao.deleteAllChatMessages()

    suspend fun search(query: String): List<Article> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Ask SQLite FTS5 Match with room-shielded SimpleSQLiteQuery execution
        try {
            // Tokenize query terms for FTS5 prefix expansion (e.g. "صل*" and "صي*")
            val ftsQuery = trimmed.split("\\s+".toRegex())
                .filter { it.isNotEmpty() }
                .joinToString(" AND ") { "$it*" }
            
            val rawQuery = SimpleSQLiteQuery(
                "SELECT * FROM articles WHERE id IN (SELECT id FROM articles_search WHERE articles_search MATCH ?)",
                arrayOf(ftsQuery)
            )
            
            val ftsResults = feqhDao.searchArticlesRaw(rawQuery)
            if (ftsResults.isNotEmpty()) {
                return ftsResults
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fall back cleanly to LIKE query if FTS5 is mismatched or syntax exception triggers
        return try {
            val likeQuery = "%$trimmed%"
            feqhDao.searchArticlesLike(likeQuery)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
