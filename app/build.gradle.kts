import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * Lee una propiedad desde local.properties (raíz del proyecto)
 */
fun localProp(key: String, defaultValue: String = ""): String {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { props.load(it) }
    }
    return props.getProperty(key, defaultValue)
}

/**
 * Selector de backend para la app Android (LOCAL / PRODUCCION)
 * SOLO cambia a qué backend apunta la app (y por tanto, qué BD usa ese backend).
 */
val moveonBackend = localProp("MOVEON_BACKEND", "LOCAL").trim().uppercase()

/**
 * URL del backend según selector
 * - Emulador Android + backend local en tu PC -> http://10.0.2.2:8000
 * - Producción -> https://dominio.com
 */
val moveonBaseUrl = when (moveonBackend) {
    "PRODUCCION" -> "https://dominio.com"
    else -> "http://10.0.2.2:8000"
}

/**
 * APP_ID_SECRET para handshake del backend
 * (lo sacamos de local.properties para no hardcodearlo en el código Java)
 */
val appIdSecret = localProp("APP_ID_SECRET", "")

android {
    namespace = "com.proyecto.moveon"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.proyecto.moveon"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ Variables accesibles desde Java: BuildConfig.BASE_URL, BuildConfig.MOVEON_BACKEND, BuildConfig.APP_ID_SECRET
        buildConfigField("String", "BASE_URL", "\"$moveonBaseUrl\"")
        buildConfigField("String", "MOVEON_BACKEND", "\"$moveonBackend\"")
        buildConfigField("String", "APP_ID_SECRET", "\"$appIdSecret\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Asegura generación de BuildConfig
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.core.splashscreen)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}