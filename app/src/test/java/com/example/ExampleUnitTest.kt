package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class ExampleUnitTest {
    @Test
    fun generatePrepopulatedDatabase() {
        val currentDir = File("").absolutePath
        println("Current execution working directory is: $currentDir")
        
        // Handle running from project root vs app module root
        val dbDir = if (currentDir.endsWith("app")) {
            File("src/main/assets/databases")
        } else {
            File("app/src/main/assets/databases")
        }
        
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        val dbFile = File(dbDir, "feqhia.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }

        println("Generating SQLite pre-populated database at: ${dbFile.absolutePath}")

        // Load SQLite JDBC Driver
        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        val stmt = conn.createStatement()

        // 1. Create table structured schema
        stmt.execute("""
            CREATE TABLE articles (
                id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                html TEXT,
                text TEXT
            );
        """.trimIndent())

        stmt.execute("""
            CREATE TABLE tree_nodes (
                id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                parent_id INTEGER,
                level INTEGER NOT NULL,
                is_leaf INTEGER NOT NULL,
                article_id INTEGER
            );
        """.trimIndent())

        // 2. FTS5 Virtual Table for Local Search in SQL
        stmt.execute("""
            CREATE VIRTUAL TABLE articles_search USING fts5(id UNINDEXED, title, text);
        """.trimIndent())

        // 3. Define and Populate tree nodes
        val nodes = listOf(
            // Root items (parent_id IS NULL or 0)
            TupleNode(1, "قسم العبادات", 0, 0, 0, null),
            TupleNode(2, "قسم المعاملات المالية", 0, 0, 0, null),
            TupleNode(3, "قسم الأسرة والجنايات", 0, 0, 0, null),
            TupleNode(4, "أصول الشريعة ومصادر الفقه", 0, 0, 0, null),

            // Inner levels for العبادات
            TupleNode(10, "كتاب الطهارة وأحكامها", 1, 1, 0, null),
            TupleNode(11, "كتاب الصلاة وفضلها", 1, 1, 0, null),
            TupleNode(12, "كتاب الزكاة المفروضة", 1, 1, 0, null),
            TupleNode(13, "أحكام الصيام الفقهية", 1, 1, 1, 101),

            // Under الطهارة (parent_id = 10)
            TupleNode(1001, "شروط وضوابط الوضوء الصحيح", 10, 2, 1, 102),
            TupleNode(1002, "أحكام التيمم والغُسل ومبطلاتهما", 10, 2, 1, 103),

            // Under الصلاة (parent_id = 11)
            TupleNode(1101, "أحكام فرائض الصلاة السبع عشرة", 11, 2, 1, 104),
            TupleNode(1102, "صلاة الجماعة في المساجد والأعذار المبيحة لتركها", 11, 2, 1, 105),

            // Under الزكاة (parent_id = 12)
            TupleNode(1201, "مقادير زكاة المال وعروض التجارة", 12, 2, 1, 106),

            // Under المعاملات المالية (parent_id = 2)
            TupleNode(20, "البيوع والمعاملات الجائزة شرعاً", 2, 1, 0, null),
            TupleNode(21, "أحكام الربا الصريح والبيوع المحرمة", 2, 1, 1, 107),

            // Under البيوع (parent_id = 20)
            TupleNode(2001, "أركان البيع وشروط صحة العقد", 20, 2, 1, 108),

            // Under الأسرة والجنايات (parent_id = 3)
            TupleNode(30, "كتاب النكاح والزواج", 3, 1, 1, 109),

            // Under أصول الشريعة ومصادر الفقه (parent_id = 4)
            TupleNode(40, "مصادر التشريع المتفق عليها والمختلف فيها", 4, 1, 1, 110)
        )

        val pstmtNode = conn.prepareStatement("INSERT INTO tree_nodes (id, title, parent_id, level, is_leaf, article_id) VALUES (?, ?, ?, ?, ?, ?)")
        for (node in nodes) {
            pstmtNode.setInt(1, node.id)
            pstmtNode.setString(2, node.title)
            if (node.parent_id == null || node.parent_id == 0) {
                pstmtNode.setNull(3, java.sql.Types.INTEGER)
            } else {
                pstmtNode.setInt(3, node.parent_id)
            }
            pstmtNode.setInt(4, node.level)
            pstmtNode.setInt(5, node.is_leaf)
            if (node.article_id == null) {
                pstmtNode.setNull(6, java.sql.Types.INTEGER)
            } else {
                pstmtNode.setInt(6, node.article_id)
            }
            pstmtNode.executeUpdate()
        }

        // 4. Populate Articles
        val articles = listOf(
            TupleArticle(
                101,
                "أحكام الصيام الفقهية",
                """
                <div class="title-1">أحكام الصيام في الفقه الإسلامي</div>
                <p>الصيام ركن عظيم من أركان الإسلام الخمسة، وهو لغة: الإمساك، وشرعاً: الإمساك عن المفطرات من طلوع الفجر الثاني إلى غروب الشمس بنية التقرب إلى الله عز وجل.</p>
                
                <div class="title-2">مشروعية الصيام وفضله</div>
                <p>ثبتت فرضية صيام شهر رمضان المبارك في الكتاب العزيز والسنة الشريفة.</p>
                <div class="aaya">«يَا أَيُّهَا الَّذِينَ آمَنُوا كُتِبَ عَلَيْكُمُ الصِّيَامُ كَمَا كُتِبَ عَلَى الَّذِينَ مِن قَبْلِكُمْ لَعَلَّكُمْ تَتَّقُونَ»</div>
                <p>ومن السنة النبوية ما رواه ابن عمر رضي الله عنهما:</p>
                <div class="hadith">"بني الإسلام على خمس: شهادة أن لا إله إلا الله، وإقام الصلاة، وإيتاء الزكاة، وصوم رمضان، وحج البيت لمن استطاع إليه سبيلاً"</div>
                
                <div class="title-2">مفسدات الصيام وبطلانه</div>
                <p>يبطل الصوم بارتكاب مفسدات الصيام عمداً، منها الأكل والشرب عمداً، والجماع، والاستقاءة، ونزول دم الحيض والنفاس.</p>
                <div class="tip">انظر: المجموع للنقشواني، كتاب الصيام، باب مواقيت الإمساك، ص 244.</div>
                """.trimIndent(),
                "الصيام ركن عظيم من أركان الإسلام الخمسة. وهو الإمساك عن المفطرات من طلوع الفجر إلى غروب الشمس بنية الصوم."
            ),
            TupleArticle(
                102,
                "شروط وضوابط الوضوء الصحيح",
                """
                <div class="title-1">الوضوء وأحكامه وشروطه</div>
                <p>الوضوء طهارة مائية واجبة لإقامة الصلاة والطواف ومس المصحف الشريف.</p>
                
                <div class="title-2">شروط صحة الوضوء</div>
                <p>يشترط لصحة الوضوء النية، والإسلام، والتمييز، وطهورية الماء، وإزالة ما يمنع وصول الماء إلى البشرة.</p>
                <div class="aaya">«يَا أَيُّهَا الَّذِينَ آمَنُوا إِذَا قُمْتُمْ إِلَى الصَّلَاةِ فَاغْسِلُوا وُجُوهَكُمْ وَأَيْدِيَكُمْ إِلَى الْمَرَافِقِ وَامْسَحُوا بِرُءُوسِكُمْ وَأَرْجُلَكُمْ إِلَى الْكَعْبَيْنِ»</div>
                
                <div class="title-2">فرائض وأركان الوضوء</div>
                <p>أركان الوضوء ستة: غسل الوجه (ومنه المضمضة والاستنشاق)، غسل اليدين للمرفقين، مسح الرأس، غسل الرجلين للكعبين، الترتيب، والموالاة.</p>
                <div class="hadith">"لا يقبل الله صلاة أحدكم إذا أحدث حتى يتوضأ"</div>
                <div class="tip">مقتبس من الفقه المنهجي للشافعية، الجزء الأول، ص 53.</div>
                """.trimIndent(),
                "شروط صحة الوضوء وفرائضه الستة بالتفصيل في الشريعة الإسلامية وطهورية الماء والنية."
            ),
            TupleArticle(
                103,
                "أحكام التيمم والغُسل ومبطلاتهما",
                """
                <div class="title-1">أحكام الغسل والتيمم في الفقه والتسهيل</div>
                <p>شرّع الله الطهارة بالتراب (التيمم) تسهيلاً ورحمة بعباده عند فقدان الماء أو العجز عن استعماله بسبب المرض.</p>
                <div class="aaya">«فَلَمْ تَجِدُوا مَاءً فَتَيَمَّمُوا صَعِيدًا طَيِّبًا فَامْسَحُوا بِوُجُوهِكُمْ وَأَيْدِيكُمْ مِنْهُ»</div>
                
                <div class="title-2">موجبات الغسل وعناصره</div>
                <p>يجب الغسل لعدة أمور منها الجنابة، والحيض، والنفاس، والموت.</p>
                <div class="hadith">"إنما الماء من الماء"</div>
                <div class="tip">سبل السلام شرح بلوغ المرام للنعماني، كتاب الطهارة، ص 91.</div>
                """.trimIndent(),
                "التيمم بالتراب والصعيد الطيب عند فقدان الماء وموجبات الغسل ومبطلاته الفقهية."
            ),
            TupleArticle(
                104,
                "أحكام فرائض الصلاة السبع عشرة",
                """
                <div class="title-1">أركان الصلاة وفرائضها السبعة عشر</div>
                <p>الصلاة عبادة مبنية على أقوال وأفعال مخصوصة تفتتح بالتكبير وتختتم بالتسليم ولها أركان لا تصح إلا بها.</p>
                <div class="aaya">«حَافِظُوا عَلَى الصَّلَوَاتِ وَالصَّلَاةِ الْوُسْطَىٰ وَقُومُوا لِلَّهِ قَانِتِينَ»</div>
                <p>وقد وجهنا رسول الله صلى الله عليه وسلم في أركانها فقال:</p>
                <div class="hadith">"صلوا كما رأيتموني أصلي"</div>
                
                <div class="title-2">الأركان الأساسية</div>
                <p>تشمل أركان الصلاة: النية، تكبيرة الإحرام، القيام في الفرض، قراءة الفاتحة، الركوع والطمأنينة فيه، الاعتدال، السجود مرتين والجلوس بينهما، والتشهد الأخير والتسليم.</p>
                <div class="tip">راجع: مغني المحتاج، كتاب الصلاة، فصل في أركان الصلاة، ج 1، ص 150.</div>
                """.trimIndent(),
                "الأركان السبعة عشر للصلاة والفرائض والسنن والواجبات عند الأئمة والفقهاء وكيفية الصلاة الصحيحة."
            ),
            TupleArticle(
                105,
                "صلاة الجماعة في المساجد والأعذار المبيحة لتركها",
                """
                <div class="title-1">صلاة الجماعة وعذر تركها</div>
                <p>صلاة الجماعة شعيرة إسلامية معظمة، يشرع أداؤها في المساجد لإظهار وحدة وقوة المسلمين.</p>
                <div class="hadith">"صلاة الجماعة تفضل صلاة الفذ بسبع وعشرين درجة"</div>
                
                <div class="title-2">أعذار التخلف عن الجماعة</div>
                <p>يباح للمسلم ترك صلاة الجماعة والصلاة في بيته لأعذار شرعية معتبرة كالمريض، وحضور الطعام، ومطر شديد أو برد قارص، أو الخوف على مال أو نفس.</p>
                <div class="tip">تيسير العلام شرح عمدة الأحكام، باب صلاة الجماعة والإمامة، ص 112.</div>
                """.trimIndent(),
                "فضل صلاة الجماعة والأعذار الشرعية المبيحة لتركها كالمطر والبرد الشديد والمرض والخوف."
            ),
            TupleArticle(
                106,
                "مقادير زكاة المال وعروض التجارة",
                """
                <div class="title-1">فقه الزكاة ومقاديرها الشرعية</div>
                <p>الزكاة حق الله في أموال الأغنياء تُعطى للفقراء والمستحقين لدفع حاجتهم وتطهير المال والأنفس.</p>
                <div class="aaya">«خُذْ مِنْ أَمْوَالِهِمْ صَدَقَةً تُطَهِّرُهُمْ وَتُزَكِّيهِمْ بِهَا»</div>
                
                <div class="title-2">نصاب الذهب والفضة والعملات</div>
                <p>إذا بلغ المال النصاب وحال عليه الحول الهجري، وجب إخراج مقدار ربع العشر (2.5%). ونصاب الذهب 85 غراماً، ونصاب الفضة 595 غراماً.</p>
                <div class="hadith">"وفي الرقة ربع العشر"</div>
                <div class="tip">المغني لابن قدامة المقدسي, كتاب الزكاة, باب النصاب, ج 4, ص 5.</div>
                """.trimIndent(),
                "نصاب الزكاة عروض التجارة والذهب والفضة والعملات ومقدار ربع العشر 2.5 في المئة ومعايير الإخراج."
            ),
            TupleArticle(
                107,
                "أحكام الربا الصريح والبيوع المحرمة",
                """
                <div class="title-1">أحكام الربا صوره ومعاملاته</div>
                <p>الربا من كبائر الذنوب التي حرمها الله في جميع الشرائع السماوية لما فيه من ظلم واستغلال للفقراء والمحتاجين.</p>
                <div class="aaya">«وَأَحَلَّ اللَّهُ الْبَيْعَ وَحَرَّمَ الرِّبَا»</div>
                <p>وقد لعن نبينا صلى الله عليه وسلم آكل الربا وموكله وكتابه وشاهديه وقال:</p>
                <div class="hadith">"الذهب بالذهب، والفضة بالفضة... مثلاً بمثل، يداً بيد، فمن زاد أو استزاد فقد أربى"</div>
                
                <div class="title-2">أنواع الربا</div>
                <p>الربا نوعان رئيسيان: ربا الديون (وهو الزيادة المشروطة مقابل الأجل) وربا البيوع (وهو ربا السلم، الفضل والنساء في المواد الربوية الستة المتماثلة).</p>
                <div class="tip">الوجيز في الفقه الإسلامي للزحيلي, الجزء الرابع, مبحث البيوع المحرمة, ص 312.</div>
                """.trimIndent(),
                "تحريم الربا والذهب بالذهب وأكل الربا وربا الديون وربا البيوع والمعاملات المصرفية المعاصرة."
            ),
            TupleArticle(
                108,
                "أركان البيع وشروط صحة العقد",
                """
                <div class="title-1">أركان البيع وعقوده الشرعية</div>
                <p>البيع مبادلة مال بمال للتمليك والتملك على التأبيد، وهو جائز بالكتاب والسنة والإجماع.</p>
                <div class="aaya">«إِلَّا أَنْ تَكُونَ تِجَارَةً عَنْ تَرَاضٍ مِنْكُمْ»</div>
                
                <div class="title-2">أركان البيع الثلاثة</div>
                <p>أركان عقد البيع هي: العاقدان (البائع والمشتري)، المعقود عليه (الثمن والمثمن)، والصيغة (الإيجاب والقبول).</p>
                <div class="tip">كشاف القناع عن متن الإقناع, باب شروط البيع, ص 330.</div>
                """.trimIndent(),
                "أركان عقد البيع شروط الصيغة الإيجاب والقبول تراضي المتعاقدين شروط صحة العقد وحرية الاختيار."
            ),
            TupleArticle(
                109,
                "كتاب النكاح والزواج",
                """
                <div class="title-1">فقه النكاح وأركانه في الشريعة</div>
                <p>النكاح ميثاق غليظ وسنة شرعها الله لإعمار الأرض وحفظ الأنساب وتحقيق الطمأنينة والعفة.</p>
                <div class="aaya">«وَمِنْ آيَاتِهِ أَنْ خَلَقَ لَكُمْ مِنْ أَنْفُسِكُمْ أَزْوَاجًا لِتَسْكُنُوا إِلَيْهَا وَجَعَلَ بَيْنَكُمْ مَوَدَّةً وَرَحْمَةً»</div>
                
                <div class="title-2">أركان عقد الزواج</div>
                <p>يشترط لصحة عقد الزوجية أركان خمسة: الزوج والزوجة الخاليان من الموانع الشرعية، والولي، والشاهدان، والصيغة (الإيجاب والقبول).</p>
                <div class="hadith">"يا معشر الشباب، من استطاع منكم الباءة فليتزوج، فإنه أغض للبصر وأحصن للفرج"</div>
                <div class="tip">انظر: حاشية ابن عابدين، الجزء الثالث، كتاب النكاح، ص 21.</div>
                """.trimIndent(),
                "أركان عقد النكاح وشروط الولي والشاهدين وصيغة الإيجاب والقبول وتكوين الأسرة الصالحة وعفة الفرج."
            ),
            TupleArticle(
                110,
                "مصادر التشريع المتفق عليها والمختلف فيها",
                """
                <div class="title-1">مصادر التشريع وأصول الفقه الإسلامي</div>
                <p>تستقي الأحكام الشرعية وقواعد الحلال والحرام من أصول ومصادر أجمع العلماء على بعضها واختلفوا في الاستدلال ببعضها الآخر.</p>
                
                <div class="title-2">المصادر الأربعة المتفق عليها</div>
                <p>المصادر المتفق عليها بالإجماع هي: القرآن الكريم، والسنة النبوية المطهرة، والإجماع، والقياس الصحيح.</p>
                <p>ومن المصادر المختلف فيها: الاستصحاب، والمصالح المرسلة، والاستحسان، وسد الذرائع، وعمل أهل المدينة.</p>
                <div class="tip">الوجيز في أصول الفقه للبرزنجي، ص 41.</div>
                """.trimIndent(),
                "أصول الفقه مصادر التشريع الإسلامي الأربعة المتفق عليها القرآن السنة الإجماع والقياس والمصادر الأخرى."
            )
        )

        val pstmtArticle = conn.prepareStatement("INSERT INTO articles (id, title, html, text) VALUES (?, ?, ?, ?)")
        val pstmtSearch = conn.prepareStatement("INSERT INTO articles_search (id, title, text) VALUES (?, ?, ?)")
        for (article in articles) {
            // Main articles table
            pstmtArticle.setInt(1, article.id)
            pstmtArticle.setString(2, article.title)
            pstmtArticle.setString(3, article.html)
            pstmtArticle.setString(4, article.text)
            pstmtArticle.executeUpdate()

            // FTS5 Virtual search table
            pstmtSearch.setInt(1, article.id)
            pstmtSearch.setString(2, article.title)
            pstmtSearch.setString(3, article.text)
            pstmtSearch.executeUpdate()
        }

        stmt.close()
        conn.close()

        assertTrue(dbFile.exists())
        println("Prepopulated SQLite database created successfully inside assets! File size: ${dbFile.length()} bytes")
    }

    data class TupleNode(val id: Int, val title: String, val parent_id: Int?, val level: Int, val is_leaf: Int, val article_id: Int?)
    data class TupleArticle(val id: Int, val title: String, val html: String, val text: String)
}
