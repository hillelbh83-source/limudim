import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

val studyZoneApiUrl = providers.gradleProperty("STUDYZONE_API_URL")
    .orElse(providers.environmentVariable("STUDYZONE_API_URL"))
    .getOrElse("https://studyzone-cpav.onrender.com")
android {
    namespace = "com.hillel.studyzone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hillel.studyzone"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "2.2.0"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"$studyZoneApiUrl\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Installable performance build for CI previews. Play Store releases must use a private keystore.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.webkit:webkit:1.16.0")
    testImplementation("junit:junit:4.13.2")
}
