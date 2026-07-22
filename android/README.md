# StudyZone for Android

Repository: https://github.com/hillelbh83-source/limudim

GitHub Actions: https://github.com/hillelbh83-source/limudim/actions

האפליקציה עצמה היא אפליקציית Kotlin + Compose מקומית. מסכי הבית, כרטיסי הקורסים,
החיפוש, הפרופיל, ההגדרות, ההתחברות ופיתי אינם React ואינם WebView. רק לאחר פתיחת
קורס מופעל renderer מקומי ייעודי של תוכן השיעורים והבחנים מתוך ה־APK.
הוא אינו כולל את App, מסכי החשבון, הניווט או הצ'אט של גרסת הדפדפן.

## מבנה

- `MainActivity.kt` — שורש Compose המקומי וכל הניווט של האפליקציה.
- `LocalCourseWebView.kt` — WebView תחום למסך קורס בלבד.
- `app/src/main/assets/android.html` — נקודת כניסה ייעודית ל־CourseRenderer בלבד.
- `app/src/main/assets/assets/` — רכיבי תוכן הקורסים והבחנים המקומיים.
- `res/` — אייקון adaptive/monochrome, splash וערכות יום/לילה.

הקבצים מוגשים דרך `WebViewAssetLoader` תחת
`https://appassets.androidplatform.net/app/`. זוהי כתובת origin בטוחה לקבצים מתוך
ה־APK, לא כתובת אתר או שרת מרוחק.

## בנייה מקומית

נדרשים JDK 17 ו־Android SDK 36. ה־React bundle כבר נמצא בתוך תיקיית ה־assets:

```bash
cd android
./gradlew :app:assembleDebug
```

כתובת ה־API בלבד ניתנת להחלפה דרך Gradle property או environment variable:

```properties
STUDYZONE_API_URL=https://your-api.example.com
```

אין property לכתובת אתר. ה־WebView של הקורס מאפשר ניווט ראשי רק ב־origin המקומי
`appassets.androidplatform.net`; הוא לא יכול לנווט לפרונטנד מרוחק.

## CI ואבטחה

ה־workflow ב־`.github/workflows/android-apk.yml`:

1. מאמת את תוכן הקורסים המקומי שנמצא ב־assets.
2. בונה Release APK מתוך תיקיית `android/` בלבד.
3. מעלה APK יחד עם checksum.

ה־Release ב־CI חתום במפתח preview בלבד. הפצת Play Store דורשת keystore פרטי
ו־AAB חתום, ואין לשמור מפתח כזה ב־repository.
