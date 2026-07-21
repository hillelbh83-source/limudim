# StudyZone for Android

אפליקציית Android Native ב־Kotlin וב־Jetpack Compose, המחוברת לאותו שרת ואותו תוכן של אתר StudyZone.

## מבנה

- `MainActivity` — Activity יחידה, edge-to-edge, Splash מערכת ו־Activity Result לבחירת PDF.
- `AppViewModel` — מצב UI מרכזי ופעולות משתמש.
- `data/` — API, cookie session מתמשך ו־DataStore מקומי.
- `model/` — מודלים בלתי תלויים ב־UI.
- `ui/theme/` — ערכת מערכת כברירת מחדל, Light ו־Dark.
- `ui/components/` — משטחי glass, כפתורים מונפשים וקורא שיעורים עם MathJax.
- `ui/screens/` — קורסים, חיפוש, שמורים, כלים, פרופיל, PDF, Pythi וניהול.

## פתיחה והרצה

פתחו את התיקייה `android/` ב־Android Studio Quail 2 ומעלה. הפרויקט משתמש ב־JDK 17,
AGP 9.3, Gradle 9.5 ו־`compileSdk 36`.

כתובות ברירת המחדל מוגדרות ב־`app/build.gradle.kts`. לסביבת פיתוח אפשר להעביר:

```properties
STUDYZONE_API_URL=https://your-api.example.com
STUDYZONE_WEB_URL=https://your-web.example.com
```

כ־Gradle properties. תקשורת HTTP לא מוצפנת חסומה כברירת מחדל.

## בנייה אוטומטית

ה־workflow ב־`.github/workflows/android-apk.yml` רץ בכל push שמשנה את פרויקט Android,
בונה APK להתקנה ומעלה אותו כ־GitHub Actions artifact למשך 14 יום.

## תוכן ו־LaTeX

האפליקציה מביאה קטלוג ושיעורים מ־`/api/mobile`. השרת מגלה את קבצי ה־TSX לפי חוזה
השמות של האתר ותומך גם בסעיפים מקוננים. נוסחאות נשמרות עם delimiters של LaTeX
ומרונדרות בכיוון LTR בתוך מסמך RTL. לכל שיעור יש גם קישור לגרסה האינטראקטיבית באתר.

אם גרסת השרת שפרוסה כרגע עדיין לא כוללת את `/api/mobile`, האפליקציה עוברת אוטומטית
לקטלוג fallback ופותחת את הקורס המלא במצב האינטראקטיבי המוטמע. כך ה־APK נשאר שימושי
גם בזמן rollout הדרגתי של השרת.

## הפצת Release

ה־workflow מפיק Debug APK חתום במפתח debug של סביבת CI. להפצה ב־Play Store צריך להוסיף
keystore מאובטח ב־GitHub Secrets ולהוסיף job נפרד ל־AAB חתום; אין לשמור מפתח חתימה ב־repository.
