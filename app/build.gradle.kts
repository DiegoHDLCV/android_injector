// En app/build.gradle.kts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android) // Agrega el plugin de Hilt
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.vigatec.android_injector"
    compileSdk = 35

    sourceSets {
        named("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    defaultConfig {
        applicationId = "com.vigatec.android_injector"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- CORRECCIÓN AQUÍ ---
        // Mueve el bloque ndk dentro de ESTE defaultConfig
        // y usa listOf() o setOf() para addAll
        ndk {
            // Forzar solo la ABI que tiene tu librería .so crítica
            abiFilters.clear() // Limpia cualquier filtro anterior
            abiFilters.add("armeabi-v7a")
        }
        // --- FIN CORRECCIÓN ---
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    // --- ELIMINA ESTE BLOQUE defaultConfig DUPLICADO ---
    // defaultConfig {
    //     // ...
    //     ndk {
    //         abiFilters.addAll("armeabi-v7a", "arm64-v8a") // Ejemplo
    //     }
    // }
    // --- FIN ELIMINACIÓN ---
}

dependencies {
    // ... (tus dependencias permanecen igual) ...

    implementation(fileTree(mapOf("dir" to "../shared-libs", "include" to listOf("*.jar"), "exclude" to listOf("core-3.2.1.jar"))))

    implementation(libs.hilt.android)
    implementation(project(":format"))
    ksp(libs.hilt.compiler)
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation(libs.androidx.security.crypto)

    implementation(project(":manufacturer"))
    implementation(project(":config"))
    implementation(project(":persistence"))
    implementation(project(":communication"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}