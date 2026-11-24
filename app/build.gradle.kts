import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties().apply {
    val propFile = rootProject.file("keystore.properties")
    if (propFile.isFile) {
        propFile.inputStream().use { load(it) }
    }
}

fun signingValue(envName: String, propName: String): String? {
    return System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propName)?.takeIf { it.isNotBlank() }
}

val customStorePath = signingValue("LAUNOTOR_KEYSTORE_PATH", "storeFile")
val customStorePassword = signingValue("LAUNOTOR_KEYSTORE_PASSWORD", "storePassword")
val customKeyAlias = signingValue("LAUNOTOR_KEY_ALIAS", "keyAlias")
val customKeyPassword = signingValue("LAUNOTOR_KEY_PASSWORD", "keyPassword")
val customKeystoreFile = customStorePath?.let { rootProject.file(it) }?.takeIf { it.isFile }

val hasCustomReleaseSigning = customKeystoreFile != null &&
    customStorePassword != null &&
    customKeyAlias != null &&
    customKeyPassword != null

android {
    namespace = "jp.yukirawa.launotor"
    compileSdk = 37

    defaultConfig {
        applicationId = "jp.yukirawa.launotor"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasCustomReleaseSigning) {
            create("release") {
                storeFile = customKeystoreFile
                storePassword = customStorePassword
                keyAlias = customKeyAlias
                keyPassword = customKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            // Use custom release keystore when provided; otherwise sign with standard Android debug
            // key so local/CI assembleRelease produces an immediately installable APK that also
            // upgrades cleanly over v0.1.0-debug.apk.
            signingConfig = if (hasCustomReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs("-Drobolectric.logging=stdout")
            }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
