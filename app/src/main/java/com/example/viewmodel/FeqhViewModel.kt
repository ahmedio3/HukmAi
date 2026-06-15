package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Article
import com.example.data.model.ChatMessage
import com.example.data.model.TreeNode
import com.example.data.repository.FeqhRepository
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

    fun toggleViewMode() {
        val newMode = if (_viewMode.value == ViewMode.LIST) ViewMode.TREE else ViewMode.LIST
        _viewMode.value = newMode
        prefs.edit().putInt("view_mode", newMode.ordinal).apply()
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        prefs.edit().putInt("view_mode", mode.ordinal).apply()
    }

    private val aiLogicEngine = com.example.data.api.AILogicEngine(repository)

    // ---- Chat State ----
    private val _aiProgress = MutableStateFlow<com.example.data.api.AiProgress>(com.example.data.api.AiProgress.Idle)
    val aiProgress: StateFlow<com.example.data.api.AiProgress> = _aiProgress.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessage>> = repository.getAllChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Store last user question for retry
    private var _lastUserQuestion = ""

    fun clearAiState() {
        _aiProgress.value = com.example.data.api.AiProgress.Idle
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.deleteAllChatMessages()
        }
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

        viewModelScope.launch {
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
            e.printStackTrace()
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
        }
    }

    fun closeArticle() {
        _activeArticle.value = null
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
