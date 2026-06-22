package com.example.data.api

import com.example.BuildConfig
import com.example.data.model.Article
import com.example.data.model.TreeNode
import com.example.util.AppLogger
import kotlinx.coroutines.delay
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

                **قواعد التنسيق الإلزامية - استخدم هذه العلامات بالضبط:**
                1. **نص غامق** → ضع النص بين نجمتين مزدوجتين: **النص** (للتشديد على المصطلحات المهمة وأسماء الكتب والأبواب).
                2. حديث نبوي → ضع النص بين قوسين مزدوجين: ((نص الحديث)) ثم اكتب التخريج كاملاً بالرقم بين علامتي ££: ££رواه البخاري ٢٣٤££ أو ££رواه مسلم ١££. إذا كان للحديث مصدران: ££رواه البخاري ٢٣٤ ومسلم ١££.
                3. آية قرآنية → ضع الآية بين ﴿ و ﴾: ﴿نص الآية﴾ ثم اذكر السورة ورقم الآية بين ££: ££سورة البقرة ٢٥٥££.
                4. قول عالم أو إمام → ضع النص الحرفي بين قوسين مزدوجين: ((نص القول)) ثم اكتب التخريج كاملاً مع الجزء والصفحة بين ££: ££المجموع للنووي، جـ ٢ صـ ٤٥££.
                5. كل التخريجات والهوامش بدون استثناء توضع بين علامتي ££...££ (مثل: ££رواه البخاري££, ££المجموع للنووي، جـ ٢ صـ ٤٥££).
                6. لا تجعل الهامش بخط غامق أبداً. ££...££ للهوامش فقط و **...** للعنويات المهمة فقط.
                7. جميع الاقتباسات الحرفية (أحاديث، آثار، أقوال) يجب أن تكون منصوصة حرفياً كما وردت في المصادر المرفقة، وليس تلخيصاً.

                **تحذير بالغ الأهمية - ممنوع القياس والاستنباط:**
                أنت لست مجتهداً ولا يحق لك القياس أو الاستنباط الفقهي. دورك الوحيد هو النقل الحرفي لما في النصوص المرفقة.
                
                - إذا لم تجد في النصوص المرفقة جواباً **مباشراً وصريحاً** للسؤال، فقل بوضوح: "لم أجد في المصادر المرفقة ما يخص هذا السؤال تحديداً."
                - ممنوع** تماماً** القياس (أي استنتاج حكم من نص مشابه). مثال ممنوع: "إذا كان حكم كذا هو كذا، فإن حكم المسألة المشابهة هو كذا" — هذا استنباط غير جائز.
                - ممنوع** تماماً** إعطاء فتوى أو حكم لم يرد بنص صريح في المصادر المرفقة.
                - إذا كان السؤال يحتاج لاجتهاد أو قياس أو تلفيق بين أدلة من مصادر مختلفة، فقل: "هذا السؤال يحتاج لنظر فقهي متخصص، ولم أجد إجابة مباشرة له في المصادر المتاحة."
                - تذكر: أنت ناقل أمين، لست مفتياً. سلامة المستخدم من الفتوى الخطأ أهم من إعطاء إجابة ناقصة.

                **عرض الخلاف الفقهي (مهم جداً):**
                - إذا وُجد في النصوص المرفقة أكثر من قول فقهي أو أكثر من مذهب في المسألة، فلا تكتفِ بعرض رأي واحد.
                - اعرض الأقوال المختلفة بشكل منظم وواضح، مثلاً:
                  1. القول الأول (الاتجاه/المذهب): شرح مختصر + الدليل أو التعليل.
                  2. القول الثاني (الاتجاه/المذهب): شرح مختصر + الدليل أو التعليل.
                  3. القول الثالث (الاتجاه/المذهب): شرح مختصر + الدليل أو التعليل.
                - لا تقتصر على المذهب أو القول الذي ركز عليه المصدر أكثر.
                - لا تهمل الأقوال الأخرى الموجودة في النصوص المسترجعة.
                
                **الترجيح:**
                - إذا كان المصدر نفسه قد رجَّح أحد الأقوال صراحةً، فاعرض الأقوال جميعاً أولاً، ثم أضف في النهاية سطراً مستقلاً بعنوان:
                  الترجيح:
                  واذكر فيه القول الذي رجحه المصدر باختصار شديد.
                - أما إذا لم يوجد ترجيح صريح في المصادر، فلا تنشئ ترجيحاً من عندك.
                - ممنوع** تماماً** الترجيح بين الأقوال أو إنشاء ترجيح غير موجود في النصوص.

                **تعليمات مهمة جداً - التزم بها بدقة:**
                - ابدأ إجابتك فوراً بعلامة §§ ثم اكتب ملخصاً بسيطاً وسهل الفهم للحكم الفقهي في ٢-٣ جمل ثم أغلقه بعلامة §§. هذا إلزامي في كل رد. مثال: §§يجوز الوضوء بالماء المستعمل لأن الأصل في الماء الطهارة، وهذا قول الجمهور.§§
                - لا تبدأ بأي عبارات افتتاحية قبل §§ (الحمد لله، سبحان الله، أما بعد، إلخ).
                - بعد §§الخلاصة§§، فصّل في الإجابة مع الاستشهاد بالأدلة والنصوص الحرفية.
                - كل تخريج أو هامش أو مصدر يكتب بين ££...££.
                - لا تضع قائمة "المصادر:" أو "المراجع:" في نهاية الإجابة أبداً.
                - وجّه المستخدم إلى المصادر الأصلية ضمن النص نفسه.

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
        val responseText = callWithFallback(model, request)
        if (responseText == null) return null
        return try {
            adapter.fromJson(responseText)
        } catch (e: Exception) {
            AppLogger.e("Gemini", "Failed to parse JSON response: $responseText", e)
            null
        }
    }

    private suspend fun callGeminiFinal(prompt: String): String? {
        val model = "gemini-3.1-flash-lite"
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.3f)
        )
        // Main + fallback key with retry
        var responseText = executeGeminiCallWithRetry(model, BuildConfig.API_KEY_ANSWER, request)
        if (responseText == null && BuildConfig.API_KEY_ROUTER_FALLBACK.isNotEmpty()) {
            responseText = executeGeminiCallWithRetry(model, BuildConfig.API_KEY_ROUTER_FALLBACK, request)
        }
        return responseText
    }

    private suspend fun executeGeminiCall(model: String, key: String, request: GenerateContentRequest): String? {
        if (key.isEmpty()) return null
        return try {
            val response = RetrofitClient.service.generateContent(model, key, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            AppLogger.e("Gemini", "executeGeminiCall failed for model=$model", e)
            null
        }
    }

    /**
     * Execute a Gemini call with automatic retry on transient failures.
     * Uses exponential backoff: 500ms → 1s → 2s (max 3 attempts).
     */
    private suspend fun executeGeminiCallWithRetry(
        model: String,
        key: String,
        request: GenerateContentRequest,
        maxAttempts: Int = 3
    ): String? {
        if (key.isEmpty()) return null
        var attempt = 0
        var backoffMs = 500L
        while (attempt < maxAttempts) {
            attempt++
            val result = executeGeminiCall(model, key, request)
            if (result != null) return result
            if (attempt < maxAttempts) {
                AppLogger.w("Gemini", "Retry $attempt/$maxAttempts after ${backoffMs}ms")
                delay(backoffMs)
                backoffMs *= 2
            }
        }
        return null
    }

    /**
     * Try main then fallback API key, with retries on each.
     */
    private suspend fun callWithFallback(
        model: String,
        request: GenerateContentRequest
    ): String? {
        var responseText = executeGeminiCallWithRetry(model, BuildConfig.API_KEY_ROUTER_MAIN, request)
        if (responseText == null && BuildConfig.API_KEY_ROUTER_FALLBACK.isNotEmpty()) {
            responseText = executeGeminiCallWithRetry(model, BuildConfig.API_KEY_ROUTER_FALLBACK, request)
        }
        return responseText
    }
}
