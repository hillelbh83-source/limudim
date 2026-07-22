# StudyZone for Android

האפליקציה היא מעטפת Android דקה שמריצה build מקומי של ממשק ה־React מתוך ה־APK.
היא אינה טוענת את אתר StudyZone ואינה מכילה fallback לפרונטנד מרוחק. גישה לרשת
מותרת רק ל־API ולשירותי משתמש כגון התחברות, סנכרון ופיתי.

## מבנה

- `MainActivity.kt` — WebView יחיד וארוך־חיים, system splash, בחירת קבצים ו־bridge מצומצם.
- `../android.html` — נקודת הכניסה המקומית עם CSP ייעודי לאפליקציה.
- `app/src/main/assets/` — bundle מקומי של React שמצורף ל־APK ונבנה ב־repository הראשי.
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

אין property לכתובת אתר. `MainActivity` חוסמת במפורש את hosts הישנים של
הפרונטנד, וקישור חיצוני אחר יכול לצאת לדפדפן רק בעקבות gesture של המשתמש.

## CI ואבטחה

ה־workflow ב־`.github/workflows/android-apk.yml`:

1. מאמת את תוכן הקורסים המקומי שנמצא ב־assets.
2. בונה Release APK מתוך תיקיית `android/` בלבד.
3. מעלה APK יחד עם checksum.

ה־Release ב־CI חתום במפתח preview בלבד. הפצת Play Store דורשת keystore פרטי
ו־AAB חתום, ואין לשמור מפתח כזה ב־repository.
