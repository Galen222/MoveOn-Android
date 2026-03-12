import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * Lee una propiedad desde local.properties (raíz del proyecto).
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
 * Selector de backend para la app Android (LOCAL / PRODUCCION).
 * SOLO cambia a qué backend apunta la app (y por tanto, qué BD usa ese backend).
 */
val moveonBackend = localProp("MOVEON_BACKEND", "LOCAL").trim().uppercase()

/**
 * URL del backend según selector.
 * - Emulador Android + backend local en tu PC -> http://10.0.2.2:8000
 * - Producción -> https://dominio.com
 */
val moveonBaseUrl = when (moveonBackend) {
    "PRODUCCION" -> "https://moaning-vanessa-moveonapp-2268f000.koyeb.app/"
    else -> "http://10.0.2.2:8000"
}

/**
 * APP_ID para handshake del backend.
 * (se saca de local.properties para no hardcodearlo en el código Java)
 */
val appId = localProp("APP_ID", "")

/**
 * TTL de caché para app-session (ms).
 * (se saca de local.properties para poder ajustarlo sin tocar Java)
 */
val appSessionCacheTtlMs = localProp("APP_SESSION_CACHE_TTL_MS", "240000").trim()

/**
 * Google Maps API Key.
 * Se lee desde local.properties como MAPS_API_KEY y se inyecta en el
 * AndroidManifest a través del manifestPlaceholders.
 */
val mapsApiKey = localProp("MAPS_API_KEY", "")

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

        // Variables accesibles desde Java: BuildConfig.BASE_URL, etc.
        buildConfigField("String", "BASE_URL", "\"$moveonBaseUrl\"")
        buildConfigField("String", "MOVEON_BACKEND", "\"$moveonBackend\"")
        buildConfigField("String", "APP_ID", "\"$appId\"")
        buildConfigField("long", "APP_SESSION_CACHE_TTL_MS", "${appSessionCacheTtlMs}L")

        // Inyecta MAPS_API_KEY en AndroidManifest.xml como ${MAPS_API_KEY}
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        // Exporta el esquema de Room para poder auditar migraciones y escribir tests.
        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Deja los esquemas accesibles para futuros tests de migración con Room.
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

dependencies {
    // UI y Componentes Base de Android
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.core.splashscreen)

    // Arquitectura y Ciclo de Vida (Lifecycle)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.viewmodel)

    // Red y Peticiones API (Retrofit)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.converter.gson)

    // Carga y Procesamiento de Imágenes
    implementation(libs.glide)

    // Persistencia local / Sincronización offline-first y Trabajo en Background
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler) // Nota: Si usas Kotlin, considera cambiar a ksp(libs.room.compiler)
    implementation(libs.work.runtime)

    // Google Maps SDK y Localización
    implementation(libs.play.services.maps)

    // FusedLocationProviderClient

    implementation(libs.play.services.location)

    // Maps Android SDK Utility Library (PolyUtil.encode para encoded polyline)
    implementation(libs.android.maps.utils)

    // Pruebas (Testing Unitario y de Interfaz)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
