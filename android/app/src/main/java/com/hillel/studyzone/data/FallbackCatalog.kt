package com.hillel.studyzone.data

import com.hillel.studyzone.BuildConfig
import com.hillel.studyzone.model.Chapter
import com.hillel.studyzone.model.Course
import com.hillel.studyzone.model.Lesson
import com.hillel.studyzone.model.SearchResult
import com.hillel.studyzone.model.Section

/**
 * Keeps the APK usable while an older StudyZone server is still deployed.
 * Full metadata/content replaces this list automatically when /api/mobile is available.
 */
object FallbackCatalog {
    private data class Definition(
        val id: String,
        val title: String,
        val description: String,
        val category: String,
        val icon: String = "book"
    )

    private val definitions = listOf(
        Definition("dapar", "דפ״ר", "הכנה לחשיבה כמותית, אנלוגיות והבנת הוראות.", "other"),
        Definition("signals-and-systems", "אותות ומערכות", "אותות, מערכות וטורי פורייה.", "engineering", "activity"),
        Definition("data-structures", "מבנה נתונים ואלגוריתמים 1", "מבני נתונים, גרפים ואלגוריתמים.", "engineering", "code"),
        Definition("groups", "תורת החבורות", "חבורות, הומומורפיזמים ומשפטי סילו.", "math", "math"),
        Definition("mans-search-for-meaning", "האדם מחפש משמעות", "ספרות החכמה במקרא ובמזרח הקדום.", "judaism"),
        Definition("job-from-the-storm", "מן הסערה: עיונים בספר איוב", "עיונים בספר איוב ובשאלת הסבל.", "judaism"),
        Definition("jeremiah", "האל, האדם והעם בספר ירמיהו", "נבואות ירמיהו על רקע החורבן.", "judaism"),
        Definition("sages-and-their-wisdom", "חכמים וחכמתם", "מושג החכמה מן המקרא ועד חז״ל.", "judaism"),
        Definition("infi2", "אינפי 2", "סדרות, טורים ופונקציות מרובות משתנים.", "math", "math"),
        Definition("infi3", "אינפי 3", "אנליזה במרחבים מטריים ואינטגרלים רב־ממדיים.", "math", "math"),
        Definition("infi4", "אינפי 4", "אנליזה מתקדמת במרחבים מרובי משתנים.", "math", "math"),
        Definition("topology", "טופולוגיה", "מרחבים טופולוגיים, קומפקטיות וקשירות.", "math", "math"),
        Definition("ode", "מד״ר", "משוואות דיפרנציאליות רגילות והתמרת לפלס.", "engineering", "math"),
        Definition("linear-systems", "מערכות לינאריות", "מודלים, יציבות ותגובות בזמן ובתדר.", "engineering", "function"),
        Definition("intro-ee", "מבוא להנדסת חשמל", "יסודות מעגלים ומערכות חשמליות.", "engineering", "electricity"),
        Definition("mechanics", "מכניקה", "קינמטיקה, דינמיקה וחוקי ניוטון.", "engineering", "math"),
        Definition("probability", "הסתברות", "משתנים מקריים, התפלגויות וסטטיסטיקה.", "math", "probability"),
        Definition("harmonic", "אנליזה הרמונית", "טורי פורייה והתמרת פורייה.", "engineering", "wave"),
        Definition("complex", "פונקציות מרוכבות", "קושי־רימן, אינטגרלים ושאריות.", "math", "complex"),
        Definition("tanakh-exams", "מבחנים בתנ״ך", "למידה עצמית ותרגול לבחינות.", "school"),
        Definition("bagrut-tanakh", "בגרות בתנ״ך", "מיקוד ותרגול לבגרות בתנ״ך.", "school"),
        Definition("bagrut-gemara", "בגרות בגמרא", "סוגיות מרכזיות והכנה לבגרות.", "school"),
        Definition("civics", "אזרחות לבגרות", "המדינה היהודית והדמוקרטית.", "school"),
        Definition("history-bagrut", "בגרות בהיסטוריה", "סיכומים ותרגול לבגרות בהיסטוריה.", "school", "history"),
        Definition("history-exam", "היסטוריה למבחן", "העליות, מלחמת העולם הראשונה והצהרת בלפור.", "school", "history"),
        Definition("lashon-bagrut", "בגרות בלשון", "תחביר והכנה לבגרות בלשון.", "school"),
        Definition("bagrut-halacha", "בגרות בהלכה", "הלכה למעשה והכנה לבגרות.", "school"),
        Definition("machshevet", "בגרות במחשבת", "תורה, מצוות, חגים ותפילה.", "school")
    )

    val courses: List<Course> by lazy {
        definitions.map { definition ->
            Course(
                id = definition.id,
                title = definition.title,
                description = definition.description,
                category = definition.category,
                iconId = definition.icon,
                chapters = listOf(
                    Chapter(
                        id = "1",
                        title = "הקורס המלא",
                        sections = listOf(Section("1.1", "פתיחת הגרסה האינטראקטיבית"))
                    )
                )
            )
        }
    }

    fun lesson(courseId: String, sectionId: String): Lesson? {
        val course = courses.firstOrNull { it.id == courseId } ?: return null
        return Lesson(
            courseId = course.id,
            courseTitle = course.title,
            chapterId = "1",
            sectionId = sectionId.ifBlank { "1.1" },
            title = course.title,
            content = "",
            interactiveUrl = "${BuildConfig.WEB_BASE_URL.trimEnd('/')}/#${course.id}"
        )
    }

    fun search(query: String): List<SearchResult> {
        val normalized = query.trim()
        if (normalized.length < 2) return emptyList()
        return courses.filter {
            it.title.contains(normalized, ignoreCase = true) || it.description.contains(normalized, ignoreCase = true)
        }.map {
            SearchResult(it.id, it.title, "1", "1.1", it.title, it.description)
        }
    }
}
