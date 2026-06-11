package com.example.data.api

import com.example.BuildConfig
import com.example.data.model.Article
import com.example.data.model.TreeNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

sealed class AiProgress {
    object Idle : AiProgress()
    object ReceivedQuestion : AiProgress()
    object AnalyzingScope : AiProgress()
    data class BooksSelected(val bookNames: List<String>) : AiProgress()
    data class OutOfScope(val reason: String = "عفواً، سؤالك خارج نطاق الموسوعة الفقهية.") : AiProgress()
    object SelectingBabs : AiProgress()
    data class BabsSelected(val babNames: List<String>) : AiProgress()
    object SelectingTopics : AiProgress()
    data class TopicsSelected(val count: Int) : AiProgress()
    object PreparingSources : AiProgress()
    object GeneratingAnswer : AiProgress()
    data class Completed(val text: String, val sources: List<Article>) : AiProgress()
    data class Error(val error: String) : AiProgress()
}

class AILogicEngine(
    private val repository: com.example.data.repository.FeqhRepository
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    // 1. Stage 1 JSON Adapter
    private val stage1Adapter = moshi.adapter(BookRouterResponse::class.java)
    // 2. Stage 2 JSON Adapter
    private val stage2Adapter = moshi.adapter(BabRouterResponse::class.java)
    // 3. Stage 3 JSON Adapter
    private val stage3Adapter = moshi.adapter(TopicRouterResponse::class.java)

    fun answerQuestion(question: String): Flow<AiProgress> = flow {
        try {
            emit(AiProgress.ReceivedQuestion)
            emit(AiProgress.AnalyzingScope)

            // Stage 1: Book Router
            val rootNodes = repository.getRootNodesSync()
            val booksContext = rootNodes.joinToString("\n") { "ID: ${it.id} | Name: ${it.title}" }
            
            val stage1Prompt = """
                أنت مساعد فقهي خبير. وظيفتك تحليل سؤال المستخدم واختيار الكتب الفقهية المناسبة من القائمة للبحث فيها.
                القائمة:
                $booksContext
                
                السؤال: "$question"
                
                إذا كان السؤال فقهياً، اختر من 1 إلى 3 كتب كحد أقصى.
                إذا كان السؤال خارج النطاق (مثال: رياضيات، فيزياء، سيارات، مدن)، ضع is_out_of_scope = true وتجاهل الكتب.
                يجب أن يكون الرد بصيغة JSON فقط بهذا الشكل:
                {
                  "is_out_of_scope": boolean,
                  "books": [1, 2]
                }
            """.trimIndent()
            
            val stage1Result = callGeminiJson<BookRouterResponse>(stage1Prompt, stage1Adapter)
            if (stage1Result == null) {
                emit(AiProgress.Error("فشل في تحليل نطاق السؤال."))
                return@flow
            }
            if (stage1Result.isOutOfScope || stage1Result.books.isNullOrEmpty()) {
                emit(AiProgress.OutOfScope())
                return@flow
            }
            
            val selectedBooks = rootNodes.filter { stage1Result.books.contains(it.id) }
            emit(AiProgress.BooksSelected(selectedBooks.map { it.title }))
            
            // Stage 2: Bab Router
            emit(AiProgress.SelectingBabs)
            val childrenNodes = repository.getChildrenNodesSync(stage1Result.books)
            if (childrenNodes.isEmpty()) {
                emit(AiProgress.Error("لم يتم العثور على أبواب داخل الكتب المختارة."))
                return@flow
            }
            val babsContext = childrenNodes.joinToString("\n") { "ID: ${it.id} | Name: ${it.title}" }
            
            val stage2Prompt = """
                أنت مساعد فقهي خبير. بناءً على السؤال، اختر الأبواب الفقهية المناسبة للبحث فيها من القائمة.
                القائمة:
                $babsContext
                
                السؤال: "$question"
                
                اختر الأبواب الأكثر صلة. الرد بصيغة JSON فقط:
                {
                  "babs": [5, 12]
                }
            """.trimIndent()
            
            val stage2Result = callGeminiJson<BabRouterResponse>(stage2Prompt, stage2Adapter)
            if (stage2Result == null || stage2Result.babs.isEmpty()) {
                emit(AiProgress.Error("فشل في تحديد الأبواب المطلوبة."))
                return@flow
            }
            val selectedBabs = childrenNodes.filter { stage2Result.babs.contains(it.id) }
            emit(AiProgress.BabsSelected(selectedBabs.map { it.title }))
            
            // Stage 3: Topic Router
            emit(AiProgress.SelectingTopics)
            val leafTopics = repository.getLeafTopics(stage2Result.babs)
            if (leafTopics.isEmpty()) {
                emit(AiProgress.Error("لم يتم العثور على موضوعات فرعية."))
                return@flow
            }
            val topicsContext = leafTopics.joinToString("\n") { "ID: ${it.articleId} | Name: ${it.title}" }
            val stage3Prompt = """
                أنت مساعد فقهي خبير. هدفك هو إيجاد المواضيع الفقهية الدقيقة التي تحتوي على إجابة سؤال المستخدم.
                القائمة الدقيقة (ID يعود للمقال، Name هو عنوان المقال):
                $topicsContext
                
                السؤال: "$question"
                
                اختر من 1 إلى 6 مواضيع كحد أقصى (أرقام ID الخاصة بهم).
                الرد بصيغة JSON فقط:
                {
                  "topics": [ids]
                }
            """.trimIndent()
            
            val stage3Result = callGeminiJson<TopicRouterResponse>(stage3Prompt, stage3Adapter)
            if (stage3Result == null || stage3Result.topics.isEmpty()) {
                emit(AiProgress.Error("عفواً، لم أجد موضوعات تغطي استفسارك بالتحديد في الموسوعة."))
                return@flow
            }
            val actualIds = stage3Result.topics.filterNotNull().take(6)
            emit(AiProgress.TopicsSelected(actualIds.size))
            
            // Stage 4: Preparing texts & Generation
            emit(AiProgress.PreparingSources)
            val articles = repository.getArticlesByIds(actualIds).filter { !it.text.isNullOrEmpty() }
            if (articles.isEmpty()) {
                emit(AiProgress.Error("فشل في تحميل النصوص الفقهية للموضوعات المختارة."))
                return@flow
            }
            
            emit(AiProgress.GeneratingAnswer)
            val articlesContext = articles.joinToString("\n\n---\n\n") { "العنوان: ${it.title}\nالنص:\n${it.text}" }
            
            val finalPrompt = """
                أنت عالم فقهي محقق. أجب على السؤال التالي بناءً على النصوص الفقهية المرفقة فقط.
                لا تستخدم أي معرفة خارجية ولا تخترع إجابات.
                تحدث دائماً باللغة العربية الفصحى بصيغة فقهية وقورة وموثقة.
                إذا لم يكن الجواب موجوداً في هذه النصوص، صرّح بذلك بوضوح وموضوعية.

                **قواعد التنسيق الإلزامية:**
                1. استخدم **النص الغامق** للتشديد على المصطلحات المهمة وأسماء الكتب والأبواب.
                2. عندما تقتبس نصاً من حديث نبوي حرفياً، ضعه بين قوسين مزدوجين: ((نص الحديث)) ثم اكتب التخريج كما هو في المصدر تماماً مع الرقم مثل: (رواه البخاري ٢٣٤) أو (رواه مسلم ١).
                3. عندما تقتبس آية قرآنية، ضعها بين رمزي ﴿ و ﴾ هكذا: ﴿نص الآية الكريمة﴾ ثم اذكر السورة ورقم الآية بين معقوفين.
                4. عندما تنقل قولاً عن عالم أو إمام، ضع نص القول الحرفي بين قوسين مزدوجين: ((نص القول)) ثم اذكر اسم الكتاب فقط بين معقوفين مثل: [المجموع] - بدون ذكر الجزء أو الصفحة.
                5. جميع الاقتباسات الحرفية يجب أن تكون منصوصة حرفياً كما وردت في المصادر المرفقة، وليس تلخيصاً.

                **تعليمات مهمة جداً:**
                - ابدأ إجابتك مباشرة بعنوان "**خلاصة القول:**" ثم اكتب ملخصاً للحكم الفقهي في ٢-٣ جمل، ولا تقل أي عبارات افتتاحية مثل الحمد لله أو سبحان الله أو أما بعد.
                - بعد الخلاصة، فصّل في الإجابة مع الاستشهاد بالأدلة والنصوص الحرفية.
                - لا تضع قائمة "المصادر:" أو "المراجع:" في نهاية الإجابة.
                - وجّه المستخدم إلى المصادر الأصلية لمزيد من التفصيل ضمن النص نفسه.

                السؤال: "$question"

                النصوص المرجعية (اقتبس منها حرفياً عند الحاجة):
                $articlesContext
            """.trimIndent()

            val finalAnswer = callGeminiFinal(finalPrompt)
            if (finalAnswer == null) {
                emit(AiProgress.Error("حدث خطأ أثناء توليد الإجابة النهائية."))
            } else {
                emit(AiProgress.Completed(finalAnswer, articles))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emit(AiProgress.Error("عفوا حدث خطأ غير متوقع: ${e.message}"))
        }
    }

    private suspend fun <T> callGeminiJson(prompt: String, adapter: com.squareup.moshi.JsonAdapter<T>): T? {
        val model = "gemini-3.1-flash-lite"
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2f)
        )
        // Try Main Router Key
        var responseText = executeGeminiCall(model, BuildConfig.API_KEY_ROUTER_MAIN, request)
        if (responseText == null && BuildConfig.API_KEY_ROUTER_FALLBACK.isNotEmpty()) {
            // Try Fallback
            responseText = executeGeminiCall(model, BuildConfig.API_KEY_ROUTER_FALLBACK, request)
        }
        if (responseText == null) return null
        
        return try {
            adapter.fromJson(responseText)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun callGeminiFinal(prompt: String): String? {
        val model = "gemini-3.1-flash-lite"
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.3f)
        )
        return executeGeminiCall(model, BuildConfig.API_KEY_ANSWER, request)
    }

    private suspend fun executeGeminiCall(model: String, key: String, request: GenerateContentRequest): String? {
        if (key.isEmpty()) return null
        return try {
            val response = RetrofitClient.service.generateContent(model, key, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
