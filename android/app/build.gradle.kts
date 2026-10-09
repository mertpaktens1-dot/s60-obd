import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pakten.volvoobd"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.pakten.volvoobd"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.4.0"

        // "Claude'a gonder" yukleme kimligi: imza/yukleme.properties (depoya GIRMEZ).
        val yukleme = Properties().apply {
            file("imza/yukleme.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "YUKLEME_KULLANICI", "\"${yukleme.getProperty("kullanici", "")}\"")
        buildConfigField("String", "YUKLEME_SIFRE", "\"${yukleme.getProperty("sifre", "")}\"")
    }

    signingConfigs {
        create("yayin") {
            // Anahtar deposu depoya GIRMEZ. Ayni anahtarla imzalanmayan surum guncelleme olarak kurulamaz.
            val depo = file("imza/volvo-obd.jks")
            if (depo.exists()) {
                storeFile = depo
                storePassword = System.getenv("VOLVO_APK_SIFRE") ?: "volvoobd"
                keyAlias = "volvo-obd"
                keyPassword = System.getenv("VOLVO_APK_SIFRE") ?: "volvoobd"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("yayin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
    // android.jar'daki org.json birim testinde bos taslaktir; gercegini ekle.
    testImplementation("org.json:json:20240303")
}
