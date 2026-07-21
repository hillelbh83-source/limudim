# StudyZone for Android

אפליקציית Android Native ב־Kotlin וב־Jetpack Compose, המחוברת לאותו שרת ואותו תוכן של אתר StudyZone.

## מבנה

- `MainActivity` — Activity יחידה, edge-to-edge, Splash מערכת ו־Activity Result לבחירת PDF.
- `AppViewModel` — מצב UI מרכזי ופעולות משתמש.
- `data/` — תוכן Native מקומי, API, cookie session מתמשך ו־DataStore מקומי.
- `model/` — מודלים בלתי תלויים ב־UI.
- `ui/theme/` — ערכת מערכת כברירת מחדל, Light ו־Dark.
- `ui/components/` — משטחי glass, כפתורים מונפשים וקורא שיעורים עם KaTeX מקומי.
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

ה־workflow ב־`.github/workflows/android-apk.yml` רץ בכל push,
בונה APK מותקן וממוזער במצב Release ומעלה אותו עם checksum כ־GitHub Actions artifact למשך 14 יום.

## תוכן ו־LaTeX

האפליקציה כוללת ב־APK קטלוג של 28 קורסים ו־779 סעיפים, ולכן מסך הבית והשיעורים אינם
ממתינים ל־`/api/mobile` ואינם נפתחים בדפדפן. אינדקס של קורס נפתח בזיכרון רק כאשר
זקוקים לו. רענון פרופיל, התקדמות והרשאות מתבצע מול השרת ברקע לאחר הצגת המסך הראשון.
גם מאגרי התרגול הסטטיים נכללים בתוכן ה־Native: 360 שאלות ממבחני קורס מלאים ועוד
563 שאלות ותרגילים שהיו רכיבים אינטראקטיביים באתר, עם תשובות והסברים זמינים לקריאה.

נוסחאות נשמרות עם delimiters של LaTeX, מנורמלות מתוכן ה־TSX ומרונדרות בכיוון LTR
בתוך מסמך RTL באמצעות KaTeX שמצורף לאפליקציה, ללא CDN. הגרסה האינטראקטיבית באתר
נשארת פעולה משנית מפורשת בלבד. אם `/api/mobile` ייפרס בעתיד, שכבת הנתונים יודעת
להשתמש בו כמקור משלים בלי לפגוע במסלול המקומי המהיר.

## הפצת Release

ה־workflow מפיק Release APK אופטימלי אך חותם אותו במפתח debug של סביבת CI כדי שיהיה ניתן להתקנה. להפצה ב־Play Store צריך להוסיף
keystore מאובטח ב־GitHub Secrets ולהוסיף job נפרד ל־AAB חתום; אין לשמור מפתח חתימה ב־repository.
זהות חתימת ה־preview נשמרת ב־GitHub Actions cache כדי ש־APK עוקבים מאותו workflow יוכלו להתעדכן זה מעל זה; מחיקת ה־cache תחייב הסרת התקנה קודמת.
