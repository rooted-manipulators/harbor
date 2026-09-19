plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.harbor"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.harbor"
        minSdk = 26
        targetSdk = 37
        // Bumped for every build that reaches a phone. Android refuses an
        // APK whose versionCode is below the installed one, so a participant
        // who gets builds out of order is told no rather than quietly
        // downgraded onto a version that may read their data differently.
        versionCode = 5
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Where the backend is, or nothing.
        //
        // Both come from gradle.properties or the environment, and both
        // default to empty. An empty URL is not a broken build: it is sync
        // switched off, and Harbor behaves exactly as it did before it had a
        // server -- see Backend.configured. That is what lets a study build
        // ship without the backend while the backend is still being decided,
        // and it is the honest default for a feature nothing yet depends on.
        //
        // The anon key is public by design; Supabase protects rows with RLS,
        // not with this. It is still read from properties rather than pasted
        // here, so a project can be pointed somewhere else without a commit.
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"" + (project.findProperty("harbor.supabaseUrl") as String? ?: "") + "\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"" + (project.findProperty("harbor.supabaseAnonKey") as String? ?: "") + "\"",
        )

        // The number people forward their class chats to (ADR-014). Empty
        // until a WhatsApp Business number exists, and empty is the same
        // supported state as above: the card that offers this simply does not
        // appear, and the week is drawn by hand as before.
        buildConfigField(
            "String",
            "WHATSAPP_NUMBER",
            "\"" + (project.findProperty("harbor.whatsappNumber") as String? ?: "") + "\"",
        )
    }

    // One debug signature for every machine that builds Harbor.
    //
    // Without this, Gradle signs debug builds with whatever throwaway keystore
    // it finds in the builder's home directory -- a different one on your
    // laptop, on a teammate's, and on every single CI run. Installing one
    // build over another then fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE,
    // and the only way through is to uninstall, which deletes the ledger.
    //
    // During the study that would mean handing a participant an update and
    // erasing their garden -- the one thing we are measuring. So the key is
    // checked in, and every build shares it.
    //
    // Committing a keystore is safe *because* this one is worthless: it is the
    // stock Android debug identity, its password is the published constant
    // "android", and Play will refuse an APK signed with it. Nothing is ever
    // released with this key. A real release key does not go in this repo.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            optimization {
                enable = false
            }
            // Signed with the debug identity, deliberately, and this is a
            // decision rather than the accident the note here used to warn
            // against.
            //
            // What a teammate needs is a build that is not a *test* build.
            // Studio's Run stamps `testOnly` on its APKs, which is what makes
            // them refuse to install by tap, and a debug build also ships the
            // Compose tooling and the debuggable flag. A release build is
            // none of those things, and that is the whole difference being
            // asked for.
            //
            // It is not the difference between this and a Play release. Play
            // will refuse this signature for ever, exactly as the debug
            // config's own note says. The reason to take that now is the
            // reason that config exists at all: one signature across every
            // machine and every build means an update installs over the last
            // one instead of demanding an uninstall, and an uninstall takes
            // the ledger with it. Handing a tester a differently-signed APK
            // costs them their garden, which is the thing being measured.
            //
            // So this stands until there is a real release key, and taking
            // that step means everyone reinstalls once, on purpose, on a day
            // chosen for it.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.play.services.location)
    implementation(libs.androidx.glance.appwidget)
    testImplementation(libs.junit)
    // Android ships org.json as stubs that throw at runtime, so any unit test
    // touching a wire format needs a real implementation on its own classpath.
    // Only the tests: the app uses the platform's.
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}