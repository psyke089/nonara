plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val nonaraVersionCode = 5
val nonaraVersionName = "0.3.2"

// Release signing key: supplied from ~/.gradle/gradle.properties (`nonara.*`) or CI env vars.
// Never commit the keystore, its passwords or this file's values.
fun signingValue(property: String, env: String): String? =
    providers.gradleProperty(property).orNull ?: System.getenv(env)

val releaseStoreFile = signingValue("nonara.storeFile", "NONARA_STORE_FILE")

android {
    namespace = "app.nonara.notes"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.nonara.notes"
        minSdk = 33
        targetSdk = 37
        versionCode = nonaraVersionCode
        versionName = nonaraVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("nonara") {
            if (releaseStoreFile != null) {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("nonara.storePassword", "NONARA_STORE_PASSWORD")
                keyAlias = signingValue("nonara.keyAlias", "NONARA_KEY_ALIAS") ?: "nonara"
                keyPassword = signingValue("nonara.keyPassword", "NONARA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Without the key (contributor checkout) fall back to debug signing so the APK is
            // still installable; CI asserts the published APK is signed with CN=Nonara.
            signingConfig = signingConfigs.getByName(if (releaseStoreFile != null) "nonara" else "debug")
            optimization {
                enable = false
            }
            ndk {
                abiFilters += setOf("arm64-v8a")
            }
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("nonara-$nonaraVersionName.apk")
        }
    }
}

dependencies {
    implementation("com.k2fsa:sherpa-onnx:2.0.1@aar")
    implementation("org.yaml:snakeyaml:2.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
