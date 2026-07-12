import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    alias(libs.plugins.android.application)
    id("jacoco")
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
 * - LAN: móvil físico en la misma red Wi-Fi -> MOVEON_LAN_BASE_URL
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

    // El proyecto no contiene fuentes Kotlin. AGP 9 activa Kotlin integrado por defecto
    // y, al heredar targetCompatibility=25, crea tareas Kotlin con un jvmTarget que el
    // compilador incluido todavía no admite. Desactivarlo evita esas tareas y mantiene
    // toda la compilación real del módulo en Java 25.
    enableKotlin = false

    // Android 17 (API 37) sigue en beta. Se mantiene API 36 hasta completar
    // las pruebas de compatibilidad y los cambios de comportamiento de target 37.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.proyecto.moveon"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.7a"

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
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
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

    // La app permite cambiar el idioma en ejecución. El bundle debe incluir
    // todos los recursos de idioma para que AppCompat pueda aplicarlos localmente.
    bundle {
        language {
            @Suppress("UnstableApiUsage")
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    // Deja los esquemas accesibles para futuros tests de migración con Room.
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    // Permite que los unit tests JVM usen stubs Android sin lanzar "Method ... not mocked".
    // Los tests que necesitan valores reales inyectan recursos/preferencias mediante MemoryContext.
    // includeAndroidResources = true habilita que Robolectric resuelva R.string.*,
    // R.string-arrays.* y R.raw.* desde los assets reales del módulo app.
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}


jacoco {
    // JaCoCo 0.8.14 soporta Java 25; evita los "Unsupported class file major version 69".
    toolVersion = "0.8.14"
}

// Robolectric 4.14.1 trae ASM 9.7.1 que NO soporta bytecode Java 25 (major 69).
// Forzamos ASM 9.8 en los classpaths de test para evitar
// "IllegalArgumentException: Unsupported class file major version 69" cuando el
// instrumentador de Robolectric procesa clases compiladas con JDK 25.
configurations.matching {
    it.name.startsWith("test") || it.name.contains("UnitTest")
}.configureEach {
    resolutionStrategy {
        force(
            "org.ow2.asm:asm:9.8",
            "org.ow2.asm:asm-commons:9.8",
            "org.ow2.asm:asm-tree:9.8",
            "org.ow2.asm:asm-util:9.8",
            "org.ow2.asm:asm-analysis:9.8"
        )
    }
}

tasks.withType<Test>().configureEach {
    // Robolectric carga su runtime nativo con System.load(). Java 25 exige
    // autorizar explícitamente ese acceso para evitar el warning de JEP 472.
    jvmArgs("--enable-native-access=ALL-UNNAMED")

    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf(
            "jdk.*",
            "java.*",
            "javax.*",
            "sun.*",
            "com.sun.*",
            "org.jcp.*",
            "jdk.internal.*"
        )
    }

    finalizedBy("jacocoTestReport")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Genera un informe HTML/XML de cobertura JaCoCo para tests unitarios debug."

    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/databinding/**/*.*",
        "**/generated/**/*.*",
        "**/*_Factory.*",
        "**/*_MembersInjector.*",

        // El informe de unit tests JVM debe medir código de negocio testeable sin emulador.
        // Estas clases dependen de framework Android, ciclo de vida, vistas, Room generado o WorkManager.
        "**/app/**",
        "**/ui/**/*Activity*.*",
        "**/ui/**/*Fragment*.*",
        "**/ui/**/*BottomSheet*.*",
        "**/ui/**/*Dialog*.*",
        "**/ui/**/*Adapter*.*",
        "**/ui/**/*ViewModel*.*",
        "**/ui/**/*View*.*",
        "**/ui/**/SessionUiHelper*.*",
        "**/ui/**/TopSnackbar*.*",
        "**/ui/home/InicioFragment*.*",
        "**/ui/home/TrackingAlertBottomSheet*.*",
        "**/ui/home/tracking/TrackingService*.*",
        "**/ui/home/tracking/TrackingServiceController*.*",
        "**/ui/home/tracking/TrackingSessionStore*.*",
        "**/ui/profile/ShareRoute*.*",
        "**/ui/profile/ProfileDialogHelper*.*",
        "**/ui/profile/ProfileTrackingHelper*.*",
        "**/data/local/dao/**",
        "**/data/local/db/**",
        "**/workers/**",
        "**/data/remote/retrofit/*Api*.*",
        "**/data/remote/retrofit/RetrofitProvider*.*",
        "**/data/remote/rutas/**"
    )

    val javaDebugTree = fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")) {
        exclude(fileFilter)
    }

    val javaLegacyDebugTree = fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
        exclude(fileFilter)
    }

    val kotlinDebugTree = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude(fileFilter)
    }

    sourceDirectories.setFrom(files("$projectDir/src/main/java"))
    classDirectories.setFrom(files(javaDebugTree, javaLegacyDebugTree, kotlinDebugTree))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "outputs/code_coverage/debugAndroidTest/connected/*coverage.ec"
            )
        }
    )
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

    // Autenticación con Google.
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // Pruebas (Testing Unitario y de Interfaz)
    testImplementation(libs.junit)
    // MockWebServer para tests JVM puros que verifican la capa de red real
    // (OkHttp + Retrofit) sin emulador y sin tocar el backend.
    // Misma línea que okhttp 5.x para mantener la compatibilidad de cliente.
    testImplementation(libs.mockwebserver)
    // Robolectric 4.14.x ya soporta hasta SDK 35; el proyecto usa
    // compileSdk/targetSdk = 36 pero tests JVM ejecutarán con sdk=35
    // (configurado en src/test/resources/robolectric.properties).
    testImplementation(libs.robolectric)
    // androidx.test:core publica ApplicationProvider, que es la API
    // recomendada para obtener el Context de la aplicación en tests
    // basados en Robolectric.
    testImplementation(libs.core)
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
