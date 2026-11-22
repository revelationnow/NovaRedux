import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// NovaRedux Connect: the small companion app that goes online for NovaRedux,
// so the launcher itself never holds the internet permission. Today it does
// one thing: web suggestions while a search is typed (SuggestionsProvider),
// answered only to NovaRedux's own packages. The launcher's side of the
// contract is app/.../connect/NovaConnect.kt.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
}

// Release signing as the launcher's (see app/build.gradle.kts): the same
// keystore.properties, so both apps carry one key. Without it, release
// builds come out unsigned.
val keystoreProperties = Properties().apply {
    listOf("local/keystore.properties", "keystore.properties")
        .map(rootProject::file)
        .firstOrNull { it.exists() }
        ?.inputStream()?.use(::load)
}

android {
    namespace = "io.github.revelationnow.novaredux.connect"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.revelationnow.novaredux.connect"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "v0.1.0"
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // The debug build keeps the release ID, unlike the launcher's: one
        // Connect, under the one provider authority, answers both the
        // launcher and its debug build.
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // As the launcher: no Google-encrypted dependency report in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // As the launcher, plus OldTargetApi: it targets the launcher's API
        // level, and the two are raised together.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable", "OldTargetApi")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

detekt {
    buildUponDefaultConfig = true
}

// LiveEndpointsTest asks the real engines. It runs only with
// NOVAREDUX_LIVE_SUGGEST=1 (the Suggestion endpoints workflow), and then
// every time, never from a cache, with what each engine said in the log.
val liveSuggest = providers.environmentVariable("NOVAREDUX_LIVE_SUGGEST").orNull == "1"
tasks.withType<Test>().configureEach {
    environment("NOVAREDUX_LIVE_SUGGEST", if (liveSuggest) "1" else "0")
    inputs.property("liveSuggest", liveSuggest)
    if (liveSuggest) {
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
    }
    testLogging {
        showStandardStreams = liveSuggest
        events("failed")
    }
}

dependencies {
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
