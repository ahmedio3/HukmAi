package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Article
import com.example.data.model.ChatMessage
import com.example.data.model.TreeNode
import com.example.data.repository.FeqhRepository
import com.example.util.AppLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray

enum class AppTab {
    HOME, AI, SETTINGS
}

enum class ViewMode {
    LIST, TREE
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class AccentColor(val hex: Long, val displayName: String) {
    BLUE(0xFF007AFF, "أزرق"),
    GREEN(0xFF2E5A36, "أخضر"),
    PURPLE(0xFF5856D6, "بنفسجي"),
    ORANGE(0xFFFF9500, "برتقالي"),
    RED(0xFFFF3B30, "أحمر"),
    TEAL(0xFF30B0C7, "فيروزي")
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class FeqhViewModel(application: Application, private val repository: FeqhRepository) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("hukm_prefs", Context.MODE_PRIVATE)

    // Current Bottom Navigation Tab State
    private val _currentTab = MutableStateFlow(AppTab.HOME)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Real-time user input query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Dictates whether user is actively querying or browsing categories
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Query matched search results
    private val _searchResults = MutableStateFlow<List<Article>>(emptyList())
    val searchResults: StateFlow<List<Article>> = _searchResults.asStateFlow()

    // Search query activity loader
    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    // Nested Category Stack History
    private val _categoryStack = MutableStateFlow<List<TreeNode>>(emptyList())
    val categoryStack: StateFlow<List<TreeNode>> = _categoryStack.asStateFlow()

    // Dedicated Article Viewer target article state
    private val _activeArticle = MutableStateFlow<Article?>(null)
    val activeArticle: StateFlow<Article?> = _activeArticle.asStateFlow()

    // Fallback cached Root Nodes for instant loading
    val rootCategories: StateFlow<List<TreeNode>> = repository.getRootNodes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Categories loading flag for skeleton
    private val _isCategoriesLoading = MutableStateFlow(true)
    val isCategoriesLoading: StateFlow<Boolean> = _isCategoriesLoading.asStateFlow()

    // View mode: LIST (breadcrumb) or TREE (expandable)
    private val _viewMode = MutableStateFlow(
        ViewMode.values()[prefs.getInt("view_mode", ViewMode.LIST.ordinal)]
    )
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    // Persisted font scale for article viewer (0.8f..1.4f)
    val fontScale: StateFlow<Float> = MutableStateFlow(prefs.getFloat("font_scale", 1.0f))
    fun setFontScale(value: Float) {
        (fontScale as MutableStateFlow).value = value.coerceIn(0.8f, 1.4f)
        prefs.edit().putFloat("font_scale", (fontScale as MutableStateFlow).value).apply()
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        prefs.edit().putInt("view_mode", mode.ordinal).apply()
    }

    fun toggleViewMode() {
        val newMode = if (_viewMode.value == ViewMode.LIST) ViewMode.TREE else ViewMode.LIST
        setViewMode(newMode)
    }

    // ---- Chat State ----
    private val _aiProgress = MutableStateFlow<com.example.data.api.AiProgress>(com.example.data.api.AiProgress.Idle)
    val aiProgress: StateFlow<com.example.data.api.AiProgress> = _aiProgress.asStateFlow()

    // Deduplicate consecutive messages with same role/content (defensive)
    val chatMessages: StateFlow<List<ChatMessage>> = repository.getAllChatMessages()
        .map { list ->
            if (list.isEmpty()) emptyList() else {
                val result = mutableListOf<ChatMessage>()
                var prev: ChatMessage? = null
                for (msg in list) {
                    if (prev == null || prev!!.content != msg.content || prev!!.role != msg.role) {
                        result.add(msg)
                    }
                    prev = msg
                }
                result
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Store last user question for retry
    private var _lastUserQuestion = ""

    // Scroll position retention across tab switches
    private val _aiScrollIndex = MutableStateFlow(0)
    val aiScrollIndex: StateFlow<Int> = _aiScrollIndex.asStateFlow()
    private val _aiScrollOffset = MutableStateFlow(0)
    val aiScrollOffset: StateFlow<Int> = _aiScrollOffset.asStateFlow()

    // ---- Bookmarks (favorites) ----
    private val _bookmarkedIds = MutableStateFlow(loadBookmarks())
    val bookmarkedIds: StateFlow<Set<Int>> = _bookmarkedIds.asStateFlow()
    fun isBookmarked(id: Int?): Boolean = id != null && id in _bookmarkedIds.value
    fun toggleBookmark(id: Int?) {
        if (id == null) return
        val next = if (id in _bookmarkedIds.value) _bookmarkedIds.value - id else _bookmarkedIds.value + id
        _bookmarkedIds.value = next
        prefs.edit().putStringSet("bookmarks", next.map { it.toString() }.toSet()).apply()
    }

    // ---- Theme preferences ----
    private val _themeMode = MutableStateFlow(
        ThemeMode.values().getOrNull(prefs.getInt("theme_mode", 2)) ?: ThemeMode.SYSTEM
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putInt("theme_mode", mode.ordinal).apply()
    }

    private val _accentColor = MutableStateFlow(
        AccentColor.values().getOrNull(prefs.getInt("accent_color", 0)) ?: AccentColor.BLUE
    )
    val accentColor: StateFlow<AccentColor> = _accentColor.asStateFlow()
    fun setAccentColor(color: AccentColor) {
        _accentColor.value = color
        prefs.edit().putInt("accent_color", color.ordinal).apply()
    }

    private fun loadBookmarks(): Set<Int> {
        return prefs.getStringSet("bookmarks", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    // ---- Recently viewed articles ----
    private val _recentlyViewedIds = MutableStateFlow(loadRecent())
    val recentlyViewedIds: StateFlow<List<Int>> = _recentlyViewedIds.asStateFlow()

    private fun loadRecent(): List<Int> {
        return prefs.getString("recently_viewed", "")?.split(',')?.mapNotNull { it.toIntOrNull() } ?: emptyList()
    }

    // ---- Per-article scroll position persistence ----
    private val articleScrollIndex = mutableMapOf<Int, Int>()
    private val articleScrollOffset = mutableMapOf<Int, Int>()

    fun getArticleScrollIndex(articleId: Int): Int {
        if (articleId !in articleScrollIndex) {
            articleScrollIndex[articleId] = prefs.getInt("art_scroll_$articleId", 0)
        }
        return articleScrollIndex[articleId] ?: 0
    }

    fun getArticleScrollOffset(articleId: Int): Int {
        if (articleId !in articleScrollOffset) {
            articleScrollOffset[articleId] = prefs.getInt("art_scroll_offset_$articleId", 0)
        }
        return articleScrollOffset[articleId] ?: 0
    }

    fun saveArticleScroll(articleId: Int, index: Int, offset: Int) {
        articleScrollIndex[articleId] = index
        articleScrollOffset[articleId] = offset
        prefs.edit()
            .putInt("art_scroll_$articleId", index)
            .putInt("art_scroll_offset_$articleId", offset)
            .apply()
    }

    private fun saveRecent(ids: List<Int>) {
        prefs.edit().putString("recently_viewed", ids.joinToString(",")).apply()
    }

    private fun pushRecent(articleId: Int) {
        val list = _recentlyViewedIds.value.toMutableList()
        list.remove(articleId)
        list.add(0, articleId)
        val trimmed = list.take(20)
        _recentlyViewedIds.value = trimmed
        saveRecent(trimmed)
    }

    // ---- Last AI response cache (so the tab is not empty after a process death) ----
    private val _lastAiResponsePreview = MutableStateFlow(prefs.getString("last_ai_preview", "") ?: "")
    val lastAiResponsePreview: StateFlow<String> = _lastAiResponsePreview.asStateFlow()
    private fun saveLastAiResponsePreview(text: String) {
        val trimmed = if (text.length > 500) text.take(500) else text
        _lastAiResponsePreview.value = trimmed
        prefs.edit().putString("last_ai_preview", trimmed).apply()
    }

    fun saveAiScrollPosition(index: Int, offset: Int) {
        _aiScrollIndex.value = index
        _aiScrollOffset.value = offset
    }

    fun clearAiState() {
        _aiProgress.value = com.example.data.api.AiProgress.Idle
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.deleteAllChatMessages()
            _lastAiResponsePreview.value = ""
            prefs.edit().remove("last_ai_preview").apply()
        }
    }

    // ---- Cancellation: stop the running AI generation ----
    @Volatile
    private var aiJob: kotlinx.coroutines.Job? = null

    fun stopAiGeneration() {
        aiJob?.cancel()
        aiJob = null
        _aiProgress.value = com.example.data.api.AiProgress.Idle
    }

    fun retryLastQuestion() {
        if (_lastUserQuestion.isNotEmpty()) {
            viewModelScope.launch {
                repository.deleteLastAiMessage()
                submitAiQuestionInternal(_lastUserQuestion)
            }
        }
    }

    suspend fun loadSourcesFromJson(sourcesJson: String?): List<Article> {
        if (sourcesJson.isNullOrEmpty()) return emptyList()
        return try {
            val ids = JSONArray(sourcesJson).let { arr ->
                (0 until arr.length()).map { arr.getInt(it) }
            }
            if (ids.isNotEmpty()) repository.getArticlesByIds(ids) else emptyList()
        } catch (e: Exception) {
            AppLogger.e("ViewModel", "loadSourcesFromJson failed", e)
            emptyList()
        }
    }

    fun submitAiQuestion(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return
        _lastUserQuestion = trimmed
        submitAiQuestionInternal(trimmed)
    }

    private fun submitAiQuestionInternal(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return

        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            // 1. Save user message
            val userMsg = ChatMessage(role = "user", content = trimmed)
            repository.insertChatMessage(userMsg)

            // 2. Run AI and save response
            aiLogicEngine.answerQuestion(trimmed).collect { progress ->
                _aiProgress.value = progress
                when (progress) {
                    is com.example.data.api.AiProgress.Completed -> {
                        val sourcesJson = if (progress.sources.isNotEmpty()) {
                            JSONArray(progress.sources.map { it.id }).toString()
                        } else null
                        val aiMsg = ChatMessage(
                            role = "ai",
                            content = progress.text,
                            sourcesJson = sourcesJson
                        )
                        repository.insertChatMessage(aiMsg)
                        saveLastAiResponsePreview(progress.text)
                        _aiProgress.value = com.example.data.api.AiProgress.Idle
                    }
                    is com.example.data.api.AiProgress.Error -> {
                        val errorMsg = ChatMessage(
                            role = "ai",
                            content = "⚠️ ${progress.error}"
                        )
                        repository.insertChatMessage(errorMsg)
                        _aiProgress.value = com.example.data.api.AiProgress.Idle
                    }
                    is com.example.data.api.AiProgress.OutOfScope -> {
                        val outMsg = ChatMessage(
                            role = "ai",
                            content = progress.reason
                        )
                        repository.insertChatMessage(outMsg)
                        _aiProgress.value = com.example.data.api.AiProgress.Idle
                    }
                    else -> { /* still loading - update progress UI */ }
                }
            }
        }
    }

    // Reactive subcategory fetch based on current top category of the breadcrumb stack
    val currentSubCategories: StateFlow<List<TreeNode>> = _categoryStack
        .flatMapLatest { stack ->
            if (stack.isEmpty()) {
                repository.getRootNodes()
            } else {
                repository.getChildrenNodes(stack.last().id ?: 0)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // All leaf articles (for "random article" feature)
    private val _allLeafArticles = MutableStateFlow<List<Article>>(emptyList())
    fun allLeafArticles(): StateFlow<List<Article>> {
        viewModelScope.launch {
            try {
                val articles = repository.search("")  // Empty returns nothing — needs separate query
                _allLeafArticles.value = articles
            } catch (e: Exception) {
                AppLogger.e("ViewModel", "allLeafArticles fetch failed", e)
            }
        }
        return _allLeafArticles
    }

    private val aiLogicEngine = com.example.data.api.AILogicEngine(repository)

    init {
        // Collect search flow with standard debounce to shield search operations
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    performSearch(query)
                }
        }
        // Track initial root loading for skeleton
        viewModelScope.launch {
            rootCategories.drop(1).first() // skip initial empty, wait for actual data
            _isCategoriesLoading.value = false
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _isSearching.value = query.isNotEmpty()
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _isSearching.value = false
        _searchResults.value = emptyList()
    }

    private suspend fun performSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        _searchLoading.value = true
        try {
            val results = repository.search(trimmed)
            _searchResults.value = results
        } catch (e: Exception) {
            AppLogger.e("ViewModel", "search failed for query=$query", e)
        } finally {
            _searchLoading.value = false
        }
    }

    // Category browsing controls
    fun pushCategory(node: TreeNode) {
        if (node.isLeaf == 1) {
            node.articleId?.let { openArticle(it) }
        } else {
            val current = _categoryStack.value.toMutableList()
            current.add(node)
            _categoryStack.value = current
        }
    }

    fun popCategory() {
        val current = _categoryStack.value
        if (current.isNotEmpty()) {
            _categoryStack.value = current.dropLast(1)
        }
    }

    fun navigateToBreadcrumb(index: Int) {
        val current = _categoryStack.value
        if (index == -1) {
            _categoryStack.value = emptyList()
        } else if (index < current.size) {
            _categoryStack.value = current.take(index + 1)
        }
    }

    // Article selection triggers
    fun openArticle(articleId: Int) {
        viewModelScope.launch {
            val article = repository.getArticleById(articleId)
            _activeArticle.value = article
            if (article != null) {
                pushRecent(articleId)
            }
        }
    }

    suspend fun getArticleByIdSync(articleId: Int): Article? = repository.getArticleById(articleId)

    fun closeArticle() {
        _activeArticle.value = null
    }

    fun openRandomArticle() {
        viewModelScope.launch {
            val searchResults = repository.search("ال")  // common Arabic particle to get many hits
            val pick = searchResults.firstOrNull { it.title != null && it.text != null }
                ?: searchResults.firstOrNull()
            if (pick != null) openArticle(pick.id)
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val application: Application, private val repository: FeqhRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FeqhViewModel::class.java)) {
                return FeqhViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
