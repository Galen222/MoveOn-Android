import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

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
 * Exige una propiedad no vacía en local.properties.
 */
fun requiredLocalProp(key: String): String {
    val value = localProp(key, "").trim()
    if (value.isEmpty()) {
        throw GradleException("Falta la propiedad obligatoria '$key' en local.properties")
    }
    return value
}

/**
 * Exige una URL no vacía en local.properties y garantiza que termine con '/'.
 */
fun requiredUrlProp(key: String): String = ensureTrailingSlash(requiredLocalProp(key))

/**
 * Asegura que la BASE_URL termine con '/'.
 */
fun ensureTrailingSlash(url: String): String {
    val trimmed = url.trim()
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
}

/**
 * Selector de backend para la app Android (LOCAL / LAN / PRODUCCION).
 * SOLO cambia a qué backend apunta la app (y por tanto, qué BD usa ese backend).
 */
val moveonBackend = localProp("MOVEON_BACKEND", "LOCAL").trim().uppercase()

private val allowedBackends = setOf("LOCAL", "LAN", "PRODUCCION")

if (moveonBackend !in allowedBackends) {
    throw GradleException(
        "MOVEON_BACKEND='$moveonBackend' no es válido. Usa uno de: ${allowedBackends.joinToString(", ")}"
    )
}

/**
 * URL LAN configurable desde local.properties.
 * Pensado para usar el móvil físico contra el backend levantado en tu PC.
 * Se permite HTTP en desarrollo para no romper el flujo LAN existente.
 */
val moveonLanBaseUrl = ensureTrailingSlash(
    localProp("MOVEON_LAN_BASE_URL", "http://192.168.68.105:8000")
)

/**
 * URL de producción configurable desde local.properties.
 * Solo es obligatoria cuando MOVEON_BACKEND=PRODUCCION.
 */
val moveonProdBaseUrl = when (moveonBackend) {
    "PRODUCCION" -> requiredUrlProp("MOVEON_PROD_BASE_URL")
    else -> ""
}

/**
 * URL del backend según selector.
 * - LOCAL: emulador Android Studio + backend local en tu PC -> http://10.0.2.2:8000/
 * - LAN: móvil físico en la misma red Wi‑Fi -> MOVEON_LAN_BASE_URL
 * - PRODUCCION: backend desplegado -> MOVEON_PROD_BASE_URL
 */
val moveonBaseUrl = when (moveonBackend) {
    "PRODUCCION" -> moveonProdBaseUrl
    "LAN" -> moveonLanBaseUrl
    else -> "http://10.0.2.2:8000/"
}

/**
 * APP_ID para handshake del backend.
 * Obligatorio: si falta, el build falla antes de llegar a runtime.
 */
val appId = requiredLocalProp("APP_ID")

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
val googleWebClientId = localProp("GOOGLE_WEB_CLIENT_ID", "").trim()

/**
 * Activa el envío automático de diagnósticos de tracking al backend
 * al finalizar actividades de builds internas.
 */
val debugActivities = localProp("DEBUG_ACTIVITIES", "false").trim().equals("true", ignoreCase = true)

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
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Variables accesibles desde Java: BuildConfig.BASE_URL, etc.
        buildConfigField("String", "BASE_URL", "\"$moveonBaseUrl\"")
        buildConfigField("String", "MOVEON_BACKEND", "\"$moveonBackend\"")
        buildConfigField("String", "APP_ID", "\"$appId\"")
        buildConfigField("long", "APP_SESSION_CACHE_TTL_MS", "${appSessionCacheTtlMs}L")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("boolean", "ACTIVITY_DIAGNOSTICS_ENABLED", debugActivities.toString())

        // Inyecta claves necesarias en recursos/manifest.
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

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
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
    testImplementation(libs.core.testing)
    annotationProcessor(libs.room.compiler)
    implementation(libs.work.runtime)

    // Google Maps SDK y Localización
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.android.maps.utils)

    // Autenticación con Google
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // Pruebas (Testing Unitario y de Interfaz)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

tasks.register<Javadoc>("generateDebugJavadocs") {
    group = "documentation"
    description = "Genera documentación Javadoc HTML para el código Java de la app"

    val compileDebugJava = tasks.named<JavaCompile>("compileDebugJavaWithJavac")
    dependsOn(compileDebugJava)

    source = fileTree("src/main/java") {
        include("**/*.java")
        exclude("**/R.java")
        exclude("**/BuildConfig.java")
        exclude("**/*Test.java")
    }

    destinationDir = layout.buildDirectory.dir("docs/javadoc").get().asFile
    isFailOnError = false

    options.encoding = "UTF-8"

    (options as StandardJavadocDocletOptions).apply {
        charSet = "UTF-8"
        docEncoding = "UTF-8"
        author(true)
        version(true)
        addBooleanOption("Xdoclint:none", true)
        addStringOption("windowtitle", "MoveOn Android")
        addStringOption("doctitle", "MoveOn Android - Documentación técnica")
        addStringOption("header", "MoveOn Android")
        links("https://developer.android.com/reference/")
        links("https://docs.oracle.com/en/java/javase/11/docs/api/")
    }

    doFirst {
        val javaCompile = compileDebugJava.get()
        classpath = files(androidComponents.sdkComponents.bootClasspath.get()) +
                javaCompile.classpath +
                files(javaCompile.destinationDirectory.get().asFile)
    }
}
