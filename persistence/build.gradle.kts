// build.gradle (Module :persistence)
// Ningún cambio es necesario aquí basado en tu última versión,
// ya que ksp(libs.hilt.compiler) ya estaba presente.
// La clave es la corrección en libs.versions.toml.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android) // Correcto: plugin de Hilt
    id("com.google.devtools.ksp")    // Correcto: plugin KSP
}

android {
    namespace = "com.vigatec.persistence"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
}

dependencies {

    // Room con KSP
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler) // Correcto: compilador de Room con KSP
    implementation(libs.gson)

    implementation(libs.hilt.android) // Correcto: Hilt runtime
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp(libs.hilt.compiler) // Correcto: compilador de Hilt con KSP (requiere corrección en libs.versions.toml)
    implementation(project(":utils"))
    // Dependencias básicas
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}