import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signaturdaten nur lokal (keystore.properties ist in .gitignore), nie im Repository.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.eiji_der_sonnige.conversa"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.eiji_der_sonnige.conversa"
        minSdk = 33
        targetSdk = 37
        versionCode = 8
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!keystoreProps.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (!keystoreProps.isEmpty) signingConfig = signingConfigs.getByName("release")
            // R8 entfernt nur ungenutzten Bibliothekscode (Größe). Optimierung und Umbenennung bleiben aus
            // (siehe proguard-rules.pro): Die teilweise Optimierung (packageScope) machte Kotlin-Klassen
            // unzugänglich -> IllegalAccessError beim Start (v1.2.0).
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // Eine APK pro Prozessortyp: Die WebRTC-Bibliothek ist je Typ ~6–13 MB groß, ein Handy braucht nur eine.
    // arm64-v8a = fast alle Handys, armeabi-v7a = sehr alte 32-Bit-Geräte, x86_64 = Android-Emulator auf dem
    // PC und manche Chromebooks. 32-Bit-x86 (praktisch keine Geräte mehr) entfällt. Die Universal-APK enthält
    // alle drei – für alle, die nicht wissen, welche sie brauchen.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES",
                // Tabellen eines Post-Quanten-Verfahrens aus Bouncy Castle, das Conversa nicht nutzt (~1,2 MB)
                "org/bouncycastle/pqc/**",
            )
        }
        jniLibs {
            // 32-Bit-x86 auch aus der Universal-APK entfernen (siehe splits)
            excludes += setOf("lib/x86/**")
        }
    }
}

tasks.withType<Test>().configureEach {
    // Netzwerkprobe für Medien-Server nur auf ausdrücklichen Wunsch: -Pblossom.probe=true
    systemProperty("blossom.probe", providers.gradleProperty("blossom.probe").getOrElse("false"))
    testLogging { showStandardStreams = true }
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
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.bouncycastle)
    implementation(libs.webrtc)
    implementation(libs.zxing.core)
    // QR-Scanner (Kontakt-ID): Kamerabild wird nur lokal ausgewertet
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}